package com.freestyleperu.aplicacion.plataforma.service;

import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.plataforma.domain.SubscriptionPayment;
import com.freestyleperu.aplicacion.plataforma.dto.request.RegistrarPagoRequest;
import com.freestyleperu.aplicacion.plataforma.dto.response.PagoSuscripcionResponse;
import com.freestyleperu.aplicacion.plataforma.dto.response.RenovacionResponse;
import com.freestyleperu.aplicacion.plataforma.repository.SubscriptionPaymentRepository;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renovación de la mensualidad de una empresa.
 *
 * <p>El cobro es <strong>anticipado</strong>: se paga el mes que viene, no el que pasó.
 * De ahí que el periodo arranque en {@code max(vencimiento, hoy)}:
 *
 * <ul>
 *   <li>Si paga antes de vencer, arranca en el vencimiento y no pierde los días que ya
 *       tenía pagados.</li>
 *   <li>Si vuelve después de estar fuera, arranca hoy. No se le cobra el hueco: durante
 *       ese tiempo no tuvo el sistema, así que no hay nada que deber.</li>
 * </ul>
 *
 * <p>Por eso no existe la noción de mora acumulada: quien no paga simplemente se queda
 * sin servicio al terminar lo pagado, y al volver compra un mes nuevo.
 */
@Service
@Transactional(readOnly = true)
public class SubscriptionRenewalService {

    private static final int HISTORIAL_VISIBLE = 24;

    private final CompanySettingsRepository companySettingsRepository;
    private final SubscriptionPaymentRepository paymentRepository;

    public SubscriptionRenewalService(CompanySettingsRepository companySettingsRepository,
            SubscriptionPaymentRepository paymentRepository) {
        this.companySettingsRepository = companySettingsRepository;
        this.paymentRepository = paymentRepository;
    }

    public List<PagoSuscripcionResponse> historial(Long tenantId) {
        return paymentRepository
                .findAllByTenantIdOrderByPaidAtDescIdDesc(tenantId, PageRequest.of(0, HISTORIAL_VISIBLE))
                .stream().map(SubscriptionRenewalService::toResponse).toList();
    }

    /**
     * Registra el pago y mueve el vencimiento. Si la empresa estaba suspendida se reactiva
     * sola: cobrar y tener que acordarse de reactivar a mano era el paso que se olvidaba.
     */
    @Transactional
    public RenovacionResponse renovar(Long tenantId, RegistrarPagoRequest request, Long actorId, String actorUsername) {
        CompanySettings empresa = companySettingsRepository.findById(tenantId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Empresa", tenantId));

        LocalDate hoy = LocalDate.now();
        LocalDate vencimiento = empresa.getNextPaymentDue();
        // max(vencimiento, hoy): pagar por adelantado no pierde días, y volver tras una
        // pausa no cobra el tiempo en que la empresa no tuvo el sistema.
        LocalDate inicio = vencimiento == null || vencimiento.isBefore(hoy) ? hoy : vencimiento;
        LocalDate fin = inicio.plusMonths(request.mesesODefecto());

        SubscriptionPayment pago = new SubscriptionPayment();
        pago.setTenantId(tenantId);
        pago.setPaidAt(LocalDateTime.now());
        pago.setAmount(request.monto());
        pago.setMethod(request.metodo().trim());
        pago.setReference(blankToNull(request.referencia()));
        pago.setPeriodStart(inicio);
        pago.setPeriodEnd(fin);
        pago.setSource(SubscriptionPayment.Origen.MANUAL);
        pago.setRegisteredBy(actorId);
        pago.setRegisteredByUsername(actorUsername);
        pago.setNotes(blankToNull(request.nota()));
        SubscriptionPayment guardado = paymentRepository.save(pago);

        // Al arrancar en max(vencimiento, hoy) el periodo siempre llega al futuro, así que
        // todo pago deja a la empresa cubierta y se puede reactivar sin más condiciones.
        boolean estabaSuspendida = empresa.getSubscriptionStatus() == SubscriptionStatus.SUSPENDIDA;
        empresa.setSubscriptionStatus(SubscriptionStatus.ACTIVA);
        empresa.setNextPaymentDue(fin);
        empresa.setUpdatedAt(LocalDateTime.now());
        empresa.setUpdatedBy(actorId);
        companySettingsRepository.save(empresa);

        return new RenovacionResponse(tenantId, fin, empresa.getSubscriptionStatus(),
                estabaSuspendida, toResponse(guardado));
    }

    /**
     * Adjunta la captura del pago. Va en una llamada aparte porque es multipart y porque el
     * comprobante es opcional: registrar el cobro no debe depender de tener la imagen.
     */
    @Transactional
    public PagoSuscripcionResponse adjuntarComprobante(Long tenantId, Long pagoId, String url) {
        SubscriptionPayment pago = paymentRepository.findById(pagoId)
                .filter(fila -> fila.getTenantId().equals(tenantId))
                .orElseThrow(() -> RecursoNoEncontradoException.de("Pago", pagoId));
        pago.setProofUrl(url);
        return toResponse(paymentRepository.save(pago));
    }

    /**
     * Lee el comprobante desde disco para servirlo por un endpoint autenticado.
     *
     * <p>El nombre viene siempre de {@code proof_url}, que lo generó el propio servidor con
     * un UUID: nunca de la petición. Aun así se comprueba que la ruta resuelta caiga dentro
     * de la carpeta esperada, para que un valor manipulado en base de datos no pueda leer
     * archivos de otro sitio.
     */
    public Comprobante comprobanteDe(Long tenantId, Long pagoId, Path uploadsDir) {
        SubscriptionPayment pago = paymentRepository.findById(pagoId)
                .filter(fila -> fila.getTenantId().equals(tenantId))
                .orElseThrow(() -> RecursoNoEncontradoException.de("Pago", pagoId));
        if (pago.getProofUrl() == null) {
            throw RecursoNoEncontradoException.de("Comprobante", pagoId);
        }
        Path carpeta = uploadsDir.resolve("suscripciones").normalize();
        Path archivo = carpeta.resolve(Path.of(pago.getProofUrl()).getFileName().toString()).normalize();
        if (!archivo.startsWith(carpeta) || !Files.isRegularFile(archivo)) {
            throw RecursoNoEncontradoException.de("Comprobante", pagoId);
        }
        try {
            String tipo = Files.probeContentType(archivo);
            return new Comprobante(Files.readAllBytes(archivo),
                    tipo != null ? tipo : "application/octet-stream",
                    archivo.getFileName().toString());
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo leer el comprobante", ex);
        }
    }

    /** Contenido del comprobante con su tipo real, para servirlo sin adivinarlo. */
    public record Comprobante(byte[] contenido, String contentType, String nombre) {
    }

    /**
     * Primer periodo de una empresa recién dada de alta. El costo de implementación cubre
     * el primer mes, así que se registra como su primer pago: si no, el historial arranca
     * vacío y la empresa parece no haber pagado nunca.
     */
    @Transactional
    public void registrarImplementacion(Long tenantId, java.math.BigDecimal monto, LocalDate hasta,
            Long actorId, String actorUsername) {
        SubscriptionPayment pago = new SubscriptionPayment();
        pago.setTenantId(tenantId);
        pago.setPaidAt(LocalDateTime.now());
        pago.setAmount(monto);
        pago.setMethod("IMPLEMENTACION");
        pago.setPeriodStart(LocalDate.now());
        pago.setPeriodEnd(hasta);
        pago.setSource(SubscriptionPayment.Origen.MANUAL);
        pago.setRegisteredBy(actorId);
        pago.setRegisteredByUsername(actorUsername);
        pago.setNotes("Implementación: cubre el primer mes");
        paymentRepository.save(pago);
    }

    private static PagoSuscripcionResponse toResponse(SubscriptionPayment pago) {
        return new PagoSuscripcionResponse(pago.getId(), pago.getPaidAt(), pago.getAmount(), pago.getMethod(),
                pago.getReference(), pago.getProofUrl(), pago.getPeriodStart(), pago.getPeriodEnd(),
                pago.getSource().name(), pago.getRegisteredByUsername(), pago.getNotes());
    }

    private static String blankToNull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
