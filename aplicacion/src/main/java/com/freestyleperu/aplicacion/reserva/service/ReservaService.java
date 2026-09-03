package com.freestyleperu.aplicacion.reserva.service;

import com.freestyleperu.aplicacion.caja.domain.CashSession;
import com.freestyleperu.aplicacion.caja.domain.CashSessionStatus;
import com.freestyleperu.aplicacion.caja.repository.CashSessionRepository;
import com.freestyleperu.aplicacion.caja.service.CajaService;
import com.freestyleperu.aplicacion.cliente.domain.Customer;
import com.freestyleperu.aplicacion.cliente.repository.CustomerRepository;
import com.freestyleperu.aplicacion.combo.domain.Combo;
import com.freestyleperu.aplicacion.combo.service.ComboService;
import com.freestyleperu.aplicacion.configuracion.service.ConfiguracionService;
import com.freestyleperu.aplicacion.inventario.service.InventarioService;
import com.freestyleperu.aplicacion.pago.domain.PaymentMethod;
import com.freestyleperu.aplicacion.pago.service.PagoService;
import com.freestyleperu.aplicacion.producto.domain.Product;
import com.freestyleperu.aplicacion.producto.domain.ProductVariant;
import com.freestyleperu.aplicacion.producto.repository.ProductVariantRepository;
import com.freestyleperu.aplicacion.promotor.domain.Promoter;
import com.freestyleperu.aplicacion.promotor.service.PromoterService;
import com.freestyleperu.aplicacion.reserva.domain.Reserva;
import com.freestyleperu.aplicacion.reserva.domain.ReservaDetail;
import com.freestyleperu.aplicacion.reserva.domain.ReservaStatus;
import com.freestyleperu.aplicacion.reserva.dto.request.CancelarReservaRequest;
import com.freestyleperu.aplicacion.reserva.dto.request.CompletarReservaRequest;
import com.freestyleperu.aplicacion.reserva.dto.request.CompletarVariasReservasRequest;
import com.freestyleperu.aplicacion.reserva.dto.request.CrearReservaRequest;
import com.freestyleperu.aplicacion.reserva.dto.request.ReservaItemRequest;
import com.freestyleperu.aplicacion.reserva.dto.response.PreviewCompletarVariasResponse;
import com.freestyleperu.aplicacion.reserva.dto.response.ReservaItemResponse;
import com.freestyleperu.aplicacion.reserva.dto.response.ReservaResponse;
import com.freestyleperu.aplicacion.reserva.dto.response.ReservaResumenResponse;
import com.freestyleperu.aplicacion.reserva.repository.ReservaRepository;
import com.freestyleperu.aplicacion.shared.audit.AuditResult;
import com.freestyleperu.aplicacion.shared.audit.AuditService;
import com.freestyleperu.aplicacion.shared.dto.PageResponse;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.exception.ReglaDeNegocioException;
import com.freestyleperu.aplicacion.shared.util.SequenceService;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import com.freestyleperu.aplicacion.usuario.repository.UsuarioRepository;
import com.freestyleperu.aplicacion.venta.domain.Payment;
import com.freestyleperu.aplicacion.venta.domain.PaymentStatus;
import com.freestyleperu.aplicacion.venta.domain.Sale;
import com.freestyleperu.aplicacion.venta.domain.SaleDetail;
import com.freestyleperu.aplicacion.venta.domain.SaleStatus;
import com.freestyleperu.aplicacion.venta.dto.request.PagoVentaRequest;
import com.freestyleperu.aplicacion.venta.repository.PaymentRepository;
import com.freestyleperu.aplicacion.venta.repository.SaleDetailRepository;
import com.freestyleperu.aplicacion.venta.repository.SaleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Separaciones (layaway) — plan PROFESIONAL. Una separación aparta uno o
 * varios productos de una vez ({@link ReservaDetail}) con una sola seña
 * para todo el grupo; a diferencia de un pedido online, retira stock de
 * inmediato al crearse (la prenda queda físicamente apartada) vía
 * {@code InventarioService.registrarPorReserva}. Un producto puede venir de
 * aplicar un combo (botón "+ Agregar combo" en el panel, mismo motor que
 * {@code VentaService}) — el descuento queda fijo desde la creación. Al
 * completar el pago pendiente genera una {@code Sale} normal (para que
 * entre en los mismos reportes de comisión por promotor); al cancelar o
 * vencer, libera el stock de todas sus líneas — la seña ya pagada no se
 * devuelve ni pasa por caja (ver docs/03-modelo-datos.md §17).
 */
@Service
@Transactional(readOnly = true)
public class ReservaService {

    private final ReservaRepository reservaRepository;
    private final CustomerRepository customerRepository;
    private final ProductVariantRepository variantRepository;
    private final CashSessionRepository cashSessionRepository;
    private final SaleRepository saleRepository;
    private final SaleDetailRepository saleDetailRepository;
    private final PaymentRepository paymentRepository;
    private final UsuarioRepository usuarioRepository;
    private final InventarioService inventarioService;
    private final CajaService cajaService;
    private final PagoService pagoService;
    private final PromoterService promoterService;
    private final ConfiguracionService configuracionService;
    private final ComboService comboService;
    private final SequenceService sequenceService;
    private final AuditService auditService;

    public ReservaService(ReservaRepository reservaRepository, CustomerRepository customerRepository,
            ProductVariantRepository variantRepository, CashSessionRepository cashSessionRepository,
            SaleRepository saleRepository, SaleDetailRepository saleDetailRepository, PaymentRepository paymentRepository,
            UsuarioRepository usuarioRepository, InventarioService inventarioService, CajaService cajaService,
            PagoService pagoService, PromoterService promoterService, ConfiguracionService configuracionService,
            ComboService comboService, SequenceService sequenceService, AuditService auditService) {
        this.reservaRepository = reservaRepository;
        this.customerRepository = customerRepository;
        this.variantRepository = variantRepository;
        this.cashSessionRepository = cashSessionRepository;
        this.saleRepository = saleRepository;
        this.saleDetailRepository = saleDetailRepository;
        this.paymentRepository = paymentRepository;
        this.usuarioRepository = usuarioRepository;
        this.inventarioService = inventarioService;
        this.cajaService = cajaService;
        this.pagoService = pagoService;
        this.promoterService = promoterService;
        this.configuracionService = configuracionService;
        this.comboService = comboService;
        this.sequenceService = sequenceService;
        this.auditService = auditService;
    }

    public PageResponse<ReservaResumenResponse> listar(ReservaStatus status, Long customerId, String buyerName, Pageable pageable) {
        return PageResponse.of(reservaRepository.buscar(status, customerId, buyerName, pageable), this::toResumen);
    }

    public ReservaResponse obtener(Long id) {
        return toResponse(buscarOFallar(id));
    }

    @Transactional
    public ReservaResponse crear(CrearReservaRequest request, Long userId) {
        Customer customer = null;
        String guestName = null;
        String guestPhone = null;
        if (request.customerId() != null) {
            customer = customerRepository.findById(request.customerId())
                    .orElseThrow(() -> RecursoNoEncontradoException.de("Cliente", request.customerId()));
        } else {
            if (request.guestName() == null || request.guestName().isBlank()) {
                throw new ReglaDeNegocioException("Elige un cliente registrado o indica el nombre del comprador");
            }
            guestName = request.guestName().trim();
            guestPhone = request.guestPhone() != null ? request.guestPhone().trim() : null;
        }
        PaymentMethod depositMethod = pagoService.obtenerActivoOFallar(request.depositPaymentMethodId());
        if (depositMethod.isAffectsCash()) {
            throw new ReglaDeNegocioException(
                    "La seña de una separación no puede pagarse en efectivo — usa Yape, Plin o transferencia");
        }
        Promoter promoter = request.promoterId() != null ? promoterService.obtenerActivoOFallar(request.promoterId()) : null;
        Usuario creador = usuarioRepository.findById(userId).orElseThrow(() -> RecursoNoEncontradoException.de("Usuario", userId));

        List<LineaCalculada> lineas = calcularItems(request.items());
        BigDecimal total = lineas.stream().map(LineaCalculada::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deposito = request.depositAmount() != null
                ? request.depositAmount()
                : configuracionService.obtenerMontoSenaPorDefecto();
        if (deposito.compareTo(total) > 0) {
            throw new ReglaDeNegocioException("La seña no puede ser mayor al total de la separación");
        }

        Reserva reserva = new Reserva();
        reserva.setReservationNumber(sequenceService.next("RESERVA", "RES", 8));
        reserva.setCustomer(customer);
        reserva.setGuestName(guestName);
        reserva.setGuestPhone(guestPhone);
        reserva.setDepositAmount(deposito);
        reserva.setDepositPaymentMethod(depositMethod);
        reserva.setDepositReference(request.depositReference());
        reserva.setPromoter(promoter);
        reserva.setStatus(ReservaStatus.RESERVADO);
        reserva.setExpiresAt(LocalDateTime.now().plusDays(configuracionService.obtenerDiasVencimientoReserva()));
        reserva.setNotes(request.notes());
        reserva.setCreatedBy(creador);
        reserva.setCreatedAt(LocalDateTime.now());
        for (LineaCalculada linea : lineas) {
            ReservaDetail detail = new ReservaDetail();
            detail.setReserva(reserva);
            detail.setVariant(linea.variant());
            detail.setQuantity(linea.quantity());
            detail.setUnitPrice(linea.unitPrice());
            detail.setDiscountAmount(linea.discountAmount());
            detail.setSubtotal(linea.subtotal());
            detail.setCombo(linea.combo());
            detail.setComboGroup(linea.comboGroup());
            reserva.getDetails().add(detail);
        }
        Reserva guardada = reservaRepository.save(reserva);

        // Orden global por variant_id (no el orden de las líneas) para que ninguna
        // transacción concurrente que toque las mismas variantes (venta, separación,
        // pedido, devolución) pueda hacer deadlock entre sí.
        for (ReservaDetail detail : guardada.getDetails().stream()
                .sorted(Comparator.comparing(d -> d.getVariant().getId())).toList()) {
            inventarioService.registrarPorReserva(detail.getVariant().getId(), detail.getQuantity(), guardada.getId(), userId);
        }

        auditService.log("RESERVA_CREADA", "RESERVA", guardada.getId(), null,
                new Object[] { guardada.getReservationNumber(), total }, AuditResult.SUCCESS);
        return toResponse(guardada);
    }

    @Transactional
    public ReservaResponse completar(Long id, CompletarReservaRequest request, Long userId) {
        Reserva reserva = buscarOFallar(id);
        if (reserva.getStatus() != ReservaStatus.RESERVADO) {
            throw new ReglaDeNegocioException("Solo se pueden completar separaciones en estado RESERVADO");
        }
        CashSession session = cashSessionRepository.findById(request.cashSessionId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Sesión de caja", request.cashSessionId()));
        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new ReglaDeNegocioException("No hay una sesión de caja abierta");
        }

        BigDecimal totalBruto = reserva.getDetails().stream()
                .map(d -> d.getUnitPrice().multiply(BigDecimal.valueOf(d.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal descuentoTotal = reserva.getDetails().stream()
                .map(ReservaDetail::getDiscountAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = reserva.getDetails().stream().map(ReservaDetail::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saldoPendiente = total.subtract(reserva.getDepositAmount());
        BigDecimal sumaPagos = request.payments().stream().map(PagoVentaRequest::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sumaPagos.compareTo(saldoPendiente) != 0) {
            throw new ReglaDeNegocioException(
                    "La suma de pagos (" + sumaPagos + ") no coincide con el saldo pendiente (" + saldoPendiente + ")");
        }

        Usuario vendedor = usuarioRepository.findById(userId).orElseThrow(() -> RecursoNoEncontradoException.de("Usuario", userId));

        Sale sale = new Sale();
        sale.setSaleNumber(sequenceService.next("VENTA", "V001", 8));
        sale.setCustomer(reserva.getCustomer());
        sale.setPromoter(reserva.getPromoter());
        sale.setUser(vendedor);
        sale.setCashSession(session);
        sale.setSubtotal(totalBruto);
        sale.setDiscountAmount(descuentoTotal);
        sale.setTotal(total);
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setNotes("Completa la separación " + reserva.getReservationNumber()
                + (reserva.getCustomer() == null ? " — comprador ocasional: " + reserva.getGuestName() : ""));
        sale.setCreatedAt(LocalDateTime.now());
        Sale guardada = saleRepository.save(sale);

        for (ReservaDetail detalle : reserva.getDetails()) {
            guardarSaleDetail(guardada, detalle.getVariant(), detalle.getQuantity(), detalle.getUnitPrice(),
                    detalle.getDiscountAmount(), detalle.getSubtotal(), detalle.getCombo());
        }
        // El stock ya se retiró al crear la separación (RESERVA) — no se vuelve a descontar aquí.

        Payment pagoSena = new Payment();
        pagoSena.setSale(guardada);
        pagoSena.setPaymentMethod(reserva.getDepositPaymentMethod());
        pagoSena.setAmount(reserva.getDepositAmount());
        pagoSena.setReference(reserva.getDepositReference());
        pagoSena.setStatus(PaymentStatus.COMPLETED);
        pagoSena.setCreatedAt(reserva.getCreatedAt());
        paymentRepository.save(pagoSena);

        for (PagoVentaRequest p : request.payments()) {
            PaymentMethod method = pagoService.obtenerActivoOFallar(p.paymentMethodId());
            Payment payment = new Payment();
            payment.setSale(guardada);
            payment.setPaymentMethod(method);
            payment.setAmount(p.amount());
            payment.setReference(p.reference());
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCreatedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            if (method.isAffectsCash()) {
                cajaService.registrarPorVenta(session.getId(), p.amount(), guardada.getId(), userId);
            }
        }

        reserva.setStatus(ReservaStatus.COMPLETADO);
        reserva.setSale(guardada);
        reserva.setCompletedAt(LocalDateTime.now());
        reserva.setCompletedBy(vendedor);

        auditService.log("RESERVA_COMPLETADA", "RESERVA", reserva.getId(), null, guardada.getSaleNumber(), AuditResult.SUCCESS);
        return toResponse(reserva);
    }

    /**
     * Solo lectura — usado por el panel para mostrarle al cajero cuánto hay
     * que cobrar (con combo ya aplicado si corresponde) antes de pedirle los
     * montos de pago concretos. Misma validación y detección de combo que
     * {@link #completarVarias}, sin tocar nada.
     */
    public PreviewCompletarVariasResponse previsualizarCompletarVarias(List<Long> reservationIds) {
        ResumenCompletarVarias resumen = calcularResumenCompletarVarias(reservationIds);
        return new PreviewCompletarVariasResponse(
                resumen.totalNormal(), resumen.totalFinal(),
                resumen.comboAplicado() != null ? resumen.comboAplicado().getName() : null,
                resumen.totalDeposito(), resumen.saldoPendiente());
    }

    private record ResumenCompletarVarias(
            List<Reserva> reservas, List<ReservaDetail> lineasFijas, List<ReservaDetail> lineasLibres,
            BigDecimal totalNormal, BigDecimal totalDeposito, Combo comboAplicado, BigDecimal totalFinal,
            BigDecimal saldoPendiente) {
    }

    private ResumenCompletarVarias calcularResumenCompletarVarias(List<Long> reservationIds) {
        List<Reserva> reservas = reservationIds.stream().map(this::buscarOFallar).toList();
        for (Reserva reserva : reservas) {
            if (reserva.getStatus() != ReservaStatus.RESERVADO) {
                throw new ReglaDeNegocioException("Solo se pueden completar separaciones en estado RESERVADO");
            }
        }
        Reserva primera = reservas.get(0);
        boolean mismoComprador = reservas.stream().allMatch(r -> mismoComprador(primera, r));
        if (!mismoComprador) {
            throw new ReglaDeNegocioException("Las separaciones seleccionadas deben ser del mismo comprador");
        }

        List<ReservaDetail> todasLasLineas = reservas.stream().flatMap(r -> r.getDetails().stream()).toList();
        // Las líneas que ya trajeron su combo fijado desde la creación no se
        // vuelven a tocar; solo las líneas sueltas participan en la
        // detección automática entre separaciones distintas (RN-28/RN-27).
        List<ReservaDetail> lineasFijas = todasLasLineas.stream().filter(d -> d.getCombo() != null).toList();
        List<ReservaDetail> lineasLibres = todasLasLineas.stream().filter(d -> d.getCombo() == null).toList();

        BigDecimal totalNormal = todasLasLineas.stream()
                .map(d -> d.getUnitPrice().multiply(BigDecimal.valueOf(d.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDeposito = reservas.stream().map(Reserva::getDepositAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Combo comboAplicado = lineasLibres.isEmpty() ? null : detectarComboEnLineas(lineasLibres);
        BigDecimal totalFijas = lineasFijas.stream().map(ReservaDetail::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLibresBruto = lineasLibres.stream()
                .map(d -> d.getUnitPrice().multiply(BigDecimal.valueOf(d.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLibres = comboAplicado != null ? comboAplicado.getPrice() : totalLibresBruto;
        BigDecimal totalFinal = totalFijas.add(totalLibres);
        BigDecimal saldoPendiente = totalFinal.subtract(totalDeposito);

        return new ResumenCompletarVarias(
                reservas, lineasFijas, lineasLibres, totalNormal, totalDeposito, comboAplicado, totalFinal, saldoPendiente);
    }

    /**
     * Completa varias separaciones del mismo comprador en una sola venta —
     * para cuando llega a recoger en persona lo que separó en distintos
     * momentos de un live. Las líneas que ya traían un combo fijado desde su
     * creación se guardan tal cual; entre las líneas sueltas, si las
     * cantidades seleccionadas calzan con algún combo activo, se aplica
     * automáticamente (RN-28); si no, cada línea suelta se cobra a su propio
     * precio, igual que {@link #completar}.
     */
    @Transactional
    public List<ReservaResponse> completarVarias(CompletarVariasReservasRequest request, Long userId) {
        ResumenCompletarVarias resumen = calcularResumenCompletarVarias(request.reservationIds());
        List<Reserva> reservas = resumen.reservas();
        Reserva primera = reservas.get(0);
        BigDecimal totalNormal = resumen.totalNormal();
        Combo comboAplicado = resumen.comboAplicado();
        BigDecimal totalFinal = resumen.totalFinal();
        BigDecimal descuentoTotal = totalNormal.subtract(totalFinal);
        BigDecimal saldoPendiente = resumen.saldoPendiente();

        CashSession session = cashSessionRepository.findById(request.cashSessionId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Sesión de caja", request.cashSessionId()));
        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new ReglaDeNegocioException("No hay una sesión de caja abierta");
        }

        BigDecimal sumaPagos = request.payments().stream().map(PagoVentaRequest::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sumaPagos.compareTo(saldoPendiente) != 0) {
            throw new ReglaDeNegocioException(
                    "La suma de pagos (" + sumaPagos + ") no coincide con el saldo pendiente (" + saldoPendiente + ")");
        }

        Usuario vendedor = usuarioRepository.findById(userId).orElseThrow(() -> RecursoNoEncontradoException.de("Usuario", userId));

        Sale sale = new Sale();
        sale.setSaleNumber(sequenceService.next("VENTA", "V001", 8));
        sale.setCustomer(primera.getCustomer());
        sale.setPromoter(primera.getPromoter());
        sale.setUser(vendedor);
        sale.setCashSession(session);
        sale.setSubtotal(totalNormal);
        sale.setDiscountAmount(descuentoTotal);
        sale.setTotal(totalFinal);
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setNotes("Completa " + reservas.size() + " separaciones: "
                + reservas.stream().map(Reserva::getReservationNumber).reduce((a, b) -> a + ", " + b).orElse("")
                + (comboAplicado != null ? " — combo " + comboAplicado.getName() : "")
                + (primera.getCustomer() == null ? " — comprador ocasional: " + primera.getGuestName() : ""));
        sale.setCreatedAt(LocalDateTime.now());
        Sale guardada = saleRepository.save(sale);

        for (ReservaDetail linea : resumen.lineasFijas()) {
            guardarSaleDetail(guardada, linea.getVariant(), linea.getQuantity(), linea.getUnitPrice(),
                    linea.getDiscountAmount(), linea.getSubtotal(), linea.getCombo());
        }

        List<ReservaDetail> lineasLibres = resumen.lineasLibres();
        if (!lineasLibres.isEmpty()) {
            List<BigDecimal> brutos = lineasLibres.stream()
                    .map(d -> d.getUnitPrice().multiply(BigDecimal.valueOf(d.getQuantity())))
                    .toList();
            BigDecimal totalLibresBruto = brutos.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal descuentoLibres = comboAplicado != null ? totalLibresBruto.subtract(comboAplicado.getPrice()) : BigDecimal.ZERO;
            BigDecimal[] descuentos = repartirProporcional(descuentoLibres, brutos, totalLibresBruto);
            for (int i = 0; i < lineasLibres.size(); i++) {
                ReservaDetail linea = lineasLibres.get(i);
                guardarSaleDetail(guardada, linea.getVariant(), linea.getQuantity(), linea.getUnitPrice(),
                        descuentos[i], brutos.get(i).subtract(descuentos[i]), comboAplicado);
            }
        }
        // El stock ya se retiró al crear cada separación — no se vuelve a descontar aquí.

        for (Reserva reserva : reservas) {
            Payment pagoSena = new Payment();
            pagoSena.setSale(guardada);
            pagoSena.setPaymentMethod(reserva.getDepositPaymentMethod());
            pagoSena.setAmount(reserva.getDepositAmount());
            pagoSena.setReference(reserva.getDepositReference());
            pagoSena.setStatus(PaymentStatus.COMPLETED);
            pagoSena.setCreatedAt(reserva.getCreatedAt());
            paymentRepository.save(pagoSena);
        }

        for (PagoVentaRequest p : request.payments()) {
            PaymentMethod method = pagoService.obtenerActivoOFallar(p.paymentMethodId());
            Payment payment = new Payment();
            payment.setSale(guardada);
            payment.setPaymentMethod(method);
            payment.setAmount(p.amount());
            payment.setReference(p.reference());
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCreatedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            if (method.isAffectsCash()) {
                cajaService.registrarPorVenta(session.getId(), p.amount(), guardada.getId(), userId);
            }
        }

        for (Reserva reserva : reservas) {
            reserva.setStatus(ReservaStatus.COMPLETADO);
            reserva.setSale(guardada);
            reserva.setCompletedAt(LocalDateTime.now());
            reserva.setCompletedBy(vendedor);
            auditService.log("RESERVA_COMPLETADA", "RESERVA", reserva.getId(), null, guardada.getSaleNumber(), AuditResult.SUCCESS);
        }

        return reservas.stream().map(this::toResponse).toList();
    }

    private void guardarSaleDetail(Sale sale, ProductVariant variant, int quantity, BigDecimal unitPrice,
            BigDecimal discountAmount, BigDecimal subtotal, Combo combo) {
        SaleDetail detail = new SaleDetail();
        detail.setSale(sale);
        detail.setVariant(variant);
        detail.setQuantity(quantity);
        detail.setUnitPrice(unitPrice);
        detail.setDiscountAmount(discountAmount);
        detail.setSubtotal(subtotal);
        detail.setCombo(combo);
        detail.setProductName(variant.getProduct().getName());
        detail.setVariantSku(variant.getSku());
        detail.setVariantLabel(variant.getVariantLabel());
        saleDetailRepository.save(detail);
    }

    private boolean mismoComprador(Reserva a, Reserva b) {
        if (a.getCustomer() != null && b.getCustomer() != null) {
            return a.getCustomer().getId().equals(b.getCustomer().getId());
        }
        if (a.getCustomer() == null && b.getCustomer() == null) {
            return a.getGuestName() != null && a.getGuestName().equalsIgnoreCase(b.getGuestName());
        }
        return false;
    }

    /** Primer combo activo cuyas líneas se cubren exactamente con las líneas libres dadas, o null si ninguno calza. */
    private Combo detectarComboEnLineas(List<ReservaDetail> lineas) {
        List<ComboService.CandidatoCombo> candidatos = new ArrayList<>();
        for (int i = 0; i < lineas.size(); i++) {
            ReservaDetail linea = lineas.get(i);
            Product product = linea.getVariant().getProduct();
            candidatos.add(new ComboService.CandidatoCombo(
                    i, linea.getVariant().getId(), product.getId(), product.getCategory().getId(),
                    product.getBrand() != null ? product.getBrand().getId() : null, linea.getQuantity()));
        }
        for (Combo combo : comboService.listarActivos()) {
            Optional<int[]> consumido = comboService.consumirCandidatos(combo, candidatos);
            if (consumido.isEmpty()) {
                continue;
            }
            boolean cubreTodo = true;
            for (int i = 0; i < consumido.get().length; i++) {
                if (consumido.get()[i] != candidatos.get(i).cantidad()) {
                    cubreTodo = false;
                    break;
                }
            }
            if (cubreTodo) {
                return combo;
            }
        }
        return null;
    }

    @Transactional
    public ReservaResponse cancelar(Long id, CancelarReservaRequest request, Long userId) {
        Reserva reserva = buscarOFallar(id);
        if (reserva.getStatus() != ReservaStatus.RESERVADO) {
            throw new ReglaDeNegocioException("Solo se pueden cancelar separaciones en estado RESERVADO");
        }
        liberarStock(reserva, userId);

        reserva.setStatus(ReservaStatus.CANCELADO);
        reserva.setCancelledAt(LocalDateTime.now());
        reserva.setCancelledBy(usuarioRepository.getReferenceById(userId));
        reserva.setCancellationReason(request.reason());

        auditService.log("RESERVA_CANCELADA", "RESERVA", reserva.getId(), null, request.reason(), AuditResult.SUCCESS);
        return toResponse(reserva);
    }

    /**
     * Invocado por ReservaScheduler — vence separaciones cuyo plazo ya pasó
     * y libera el stock de todas sus líneas. La seña se pierde (no hay
     * reversión). El movimiento de inventario queda a nombre de quien creó
     * la separación (no existe un usuario "sistema" en este modelo, y es
     * igual de trazable).
     */
    @Transactional
    public int vencerSeparacionesPendientes() {
        List<Reserva> vencidas = reservaRepository.findAllByStatusAndExpiresAtBefore(ReservaStatus.RESERVADO, LocalDateTime.now());
        for (Reserva reserva : vencidas) {
            liberarStock(reserva, reserva.getCreatedBy().getId());
            reserva.setStatus(ReservaStatus.VENCIDO);
            auditService.log("RESERVA_VENCIDA", "RESERVA", reserva.getId(), null, reserva.getReservationNumber(), AuditResult.SUCCESS);
        }
        return vencidas.size();
    }

    private void liberarStock(Reserva reserva, Long userId) {
        // Orden global por variant_id — mismo criterio de concurrencia que crear().
        for (ReservaDetail detail : reserva.getDetails().stream()
                .sorted(Comparator.comparing(d -> d.getVariant().getId())).toList()) {
            inventarioService.registrarPorLiberacionReserva(detail.getVariant().getId(), detail.getQuantity(), reserva.getId(), userId);
        }
    }

    private record LineaCalculada(
            ProductVariant variant, int quantity, BigDecimal unitPrice, BigDecimal discountAmount, BigDecimal bruto,
            BigDecimal subtotal, Combo combo, Integer comboGroup) {
    }

    /** Agrupa por combo + aplicación del combo (no solo por comboId) — ver {@link ReservaItemRequest}. */
    private record ComboGroupKey(Long comboId, Integer comboGroup) {
    }

    private List<LineaCalculada> calcularItems(List<ReservaItemRequest> items) {
        Map<Long, ProductVariant> variantesPorId = cargarVariantes(items);
        List<LineaCalculada> resultado = new ArrayList<>();

        Map<ComboGroupKey, List<ReservaItemRequest>> itemsPorGrupoCombo = new LinkedHashMap<>();
        for (ReservaItemRequest item : items) {
            if (item.comboId() != null) {
                itemsPorGrupoCombo.computeIfAbsent(new ComboGroupKey(item.comboId(), item.comboGroup()), k -> new ArrayList<>()).add(item);
            }
        }
        for (Map.Entry<ComboGroupKey, List<ReservaItemRequest>> entry : itemsPorGrupoCombo.entrySet()) {
            resultado.addAll(calcularLineasCombo(
                    entry.getKey().comboId(), entry.getKey().comboGroup(), entry.getValue(), variantesPorId));
        }

        for (ReservaItemRequest item : items) {
            if (item.comboId() != null) {
                continue;
            }
            ProductVariant variant = variantesPorId.get(item.variantId());
            BigDecimal unitPrice = precioVigente(variant);
            BigDecimal bruto = unitPrice.multiply(BigDecimal.valueOf(item.quantity()));
            resultado.add(new LineaCalculada(variant, item.quantity(), unitPrice, BigDecimal.ZERO, bruto, bruto, null, null));
        }
        return resultado;
    }

    private Map<Long, ProductVariant> cargarVariantes(List<ReservaItemRequest> items) {
        Map<Long, ProductVariant> resultado = new HashMap<>();
        for (ReservaItemRequest item : items) {
            resultado.computeIfAbsent(item.variantId(), id -> variantRepository.findById(id)
                    .orElseThrow(() -> RecursoNoEncontradoException.de("Variante", id)));
        }
        return resultado;
    }

    /**
     * Una aplicación de combo dentro de una separación: mismo algoritmo que
     * {@code VentaService.calcularDetallesCombo} — no se tolera que sobre
     * nada, toda línea del grupo debe quedar explicada por el combo, y el
     * descuento se reparte proporcionalmente con la última línea absorbiendo
     * el redondeo.
     */
    private List<LineaCalculada> calcularLineasCombo(
            Long comboId, Integer comboGroup, List<ReservaItemRequest> items, Map<Long, ProductVariant> variantesPorId) {
        Combo combo = comboService.obtenerActivoOFallar(comboId);

        record LineaCombo(ReservaItemRequest item, ProductVariant variant, BigDecimal unitPrice, BigDecimal bruto) {
        }
        List<LineaCombo> lineas = new ArrayList<>();
        BigDecimal totalNormal = BigDecimal.ZERO;
        for (ReservaItemRequest item : items) {
            ProductVariant variant = variantesPorId.get(item.variantId());
            BigDecimal unitPrice = precioVigente(variant);
            BigDecimal bruto = unitPrice.multiply(BigDecimal.valueOf(item.quantity()));
            lineas.add(new LineaCombo(item, variant, unitPrice, bruto));
            totalNormal = totalNormal.add(bruto);
        }

        List<ComboService.CandidatoCombo> candidatos = new ArrayList<>();
        for (int i = 0; i < lineas.size(); i++) {
            ProductVariant variant = lineas.get(i).variant();
            Product product = variant.getProduct();
            candidatos.add(new ComboService.CandidatoCombo(
                    i, variant.getId(), product.getId(), product.getCategory().getId(),
                    product.getBrand() != null ? product.getBrand().getId() : null, lineas.get(i).item().quantity()));
        }
        int[] consumido = comboService.consumirCandidatos(combo, candidatos)
                .orElseThrow(() -> new ReglaDeNegocioException("Faltan productos para completar el combo " + combo.getName()));
        for (int i = 0; i < consumido.length; i++) {
            if (consumido[i] != candidatos.get(i).cantidad()) {
                throw new ReglaDeNegocioException(
                        "Los productos elegidos no coinciden con la definición del combo " + combo.getName());
            }
        }

        BigDecimal descuentoTotal = totalNormal.subtract(combo.getPrice());
        if (descuentoTotal.signum() < 0) {
            throw new ReglaDeNegocioException(
                    "El precio del combo " + combo.getName() + " ya no es válido frente al precio actual de sus productos");
        }

        BigDecimal[] descuentos = repartirProporcional(descuentoTotal, lineas.stream().map(LineaCombo::bruto).toList(), totalNormal);
        List<LineaCalculada> resultado = new ArrayList<>();
        for (int i = 0; i < lineas.size(); i++) {
            LineaCombo linea = lineas.get(i);
            BigDecimal subtotal = linea.bruto().subtract(descuentos[i]);
            resultado.add(new LineaCalculada(
                    linea.variant(), linea.item().quantity(), linea.unitPrice(), descuentos[i], linea.bruto(), subtotal, combo,
                    comboGroup));
        }
        return resultado;
    }

    /** Reparte {@code totalDescuento} proporcionalmente a cada monto bruto; la última línea absorbe el redondeo. */
    private static BigDecimal[] repartirProporcional(BigDecimal totalDescuento, List<BigDecimal> montosBrutos, BigDecimal sumaBruta) {
        BigDecimal[] resultado = new BigDecimal[montosBrutos.size()];
        BigDecimal asignado = BigDecimal.ZERO;
        for (int i = 0; i < montosBrutos.size(); i++) {
            if (totalDescuento.signum() == 0) {
                resultado[i] = BigDecimal.ZERO;
            } else if (i == montosBrutos.size() - 1) {
                resultado[i] = totalDescuento.subtract(asignado);
            } else {
                resultado[i] = totalDescuento.multiply(montosBrutos.get(i)).divide(sumaBruta, 2, RoundingMode.HALF_UP);
                asignado = asignado.add(resultado[i]);
            }
        }
        return resultado;
    }

    private BigDecimal precioVigente(ProductVariant variant) {
        return variant.getProduct().getPromoPrice() != null ? variant.getProduct().getPromoPrice() : variant.getProduct().getPrice();
    }

    private Reserva buscarOFallar(Long id) {
        return reservaRepository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Separación", id));
    }

    private String nombreComprador(Reserva r) {
        return r.getCustomer() != null ? r.getCustomer().getFullName() : r.getGuestName();
    }

    private String itemsSummary(Reserva r) {
        return r.getDetails().stream()
                .map(d -> String.format("%s %s ×%d", d.getVariant().getProduct().getName(),
                        d.getVariant().getVariantLabel(), d.getQuantity()))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private ReservaResumenResponse toResumen(Reserva r) {
        BigDecimal total = r.getDetails().stream().map(ReservaDetail::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalQuantity = r.getDetails().stream().mapToInt(ReservaDetail::getQuantity).sum();
        return new ReservaResumenResponse(
                r.getId(), r.getReservationNumber(), nombreComprador(r), r.getCustomer() == null,
                itemsSummary(r), totalQuantity, total, r.getDepositAmount(), r.getStatus(), r.getExpiresAt(), r.getCreatedAt());
    }

    private ReservaItemResponse toItemResponse(ReservaDetail d) {
        return new ReservaItemResponse(
                d.getVariant().getId(), d.getVariant().getProduct().getName(), d.getVariant().getSku(),
                d.getVariant().getVariantLabel(), d.getQuantity(), d.getUnitPrice(),
                d.getDiscountAmount(), d.getSubtotal(), d.getCombo() != null ? d.getCombo().getId() : null,
                d.getCombo() != null ? d.getCombo().getName() : null, d.getComboGroup());
    }

    private ReservaResponse toResponse(Reserva r) {
        List<ReservaItemResponse> items = r.getDetails().stream().map(this::toItemResponse).toList();
        BigDecimal total = r.getDetails().stream().map(ReservaDetail::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReservaResponse(
                r.getId(), r.getReservationNumber(), r.getCustomer() != null ? r.getCustomer().getId() : null,
                nombreComprador(r), r.getCustomer() == null, r.getGuestPhone(),
                items, total, r.getDepositAmount(),
                r.getDepositPaymentMethod().getName(), r.getDepositReference(),
                r.getPromoter() != null ? r.getPromoter().getId() : null, r.getPromoter() != null ? r.getPromoter().getName() : null,
                r.getStatus(), r.getExpiresAt(), r.getNotes(), r.getCreatedBy().getUsername(), r.getCreatedAt(),
                r.getSale() != null ? r.getSale().getId() : null, r.getCompletedAt(),
                r.getCompletedBy() != null ? r.getCompletedBy().getUsername() : null, r.getCancelledAt(),
                r.getCancelledBy() != null ? r.getCancelledBy().getUsername() : null, r.getCancellationReason());
    }
}
