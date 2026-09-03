package com.freestyleperu.aplicacion.pago.service;

import com.freestyleperu.aplicacion.pago.domain.PaymentMethod;
import com.freestyleperu.aplicacion.pago.dto.request.ActualizarPaymentMethodRequest;
import com.freestyleperu.aplicacion.pago.dto.response.PaymentMethodResponse;
import com.freestyleperu.aplicacion.pago.repository.PaymentMethodRepository;
import com.freestyleperu.aplicacion.shared.audit.AuditResult;
import com.freestyleperu.aplicacion.shared.audit.AuditService;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.util.ImageUploadService;
import com.freestyleperu.aplicacion.tienda.service.StoreCatalogSyncService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class PagoService {

    private final PaymentMethodRepository paymentMethodRepository;
    private final AuditService auditService;
    private final ImageUploadService imageUploadService;
    private final StoreCatalogSyncService storeCatalogSyncService;

    public PagoService(PaymentMethodRepository paymentMethodRepository, AuditService auditService,
            ImageUploadService imageUploadService, StoreCatalogSyncService storeCatalogSyncService) {
        this.paymentMethodRepository = paymentMethodRepository;
        this.auditService = auditService;
        this.imageUploadService = imageUploadService;
        this.storeCatalogSyncService = storeCatalogSyncService;
    }

    public List<PaymentMethodResponse> listar() {
        return paymentMethodRepository.findAllByOrderBySortOrderAsc().stream().map(this::toResponse).toList();
    }

    public PaymentMethodResponse obtener(Long id) {
        return toResponse(buscarOFallar(id));
    }

    @Transactional
    public PaymentMethodResponse actualizar(Long id, ActualizarPaymentMethodRequest request) {
        PaymentMethod method = buscarOFallar(id);
        method.setName(request.name());
        method.setAccountHolder(request.accountHolder());
        method.setAccountNumber(request.accountNumber());
        method.setQrImageUrl(request.qrImageUrl());
        auditService.log("METODO_PAGO_ACTUALIZADO", "PAYMENT_METHOD", method.getId(), null, request, AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return toResponse(method);
    }

    @Transactional
    public PaymentMethodResponse actualizarQr(Long id, MultipartFile file) {
        PaymentMethod method = buscarOFallar(id);
        method.setQrImageUrl(imageUploadService.guardar(file, "payment-methods"));
        auditService.log("METODO_PAGO_QR_ACTUALIZADO", "PAYMENT_METHOD", method.getId(), null, method.getQrImageUrl(), AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return toResponse(method);
    }

    @Transactional
    public PaymentMethodResponse cambiarEstado(Long id, EstadoGeneral status) {
        PaymentMethod method = buscarOFallar(id);
        method.setStatus(status);
        storeCatalogSyncService.requestRefresh();
        return toResponse(method);
    }

    /** Usado internamente por el módulo de ventas para validar y registrar pagos. */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public PaymentMethod obtenerActivoOFallar(Long id) {
        PaymentMethod method = buscarOFallar(id);
        if (method.getStatus() != EstadoGeneral.ACTIVE) {
            throw new RecursoNoEncontradoException("El método de pago " + method.getName() + " no está disponible");
        }
        return method;
    }

    private PaymentMethod buscarOFallar(Long id) {
        return paymentMethodRepository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Método de pago", id));
    }

    private PaymentMethodResponse toResponse(PaymentMethod method) {
        return new PaymentMethodResponse(
                method.getId(), method.getCode(), method.getName(), method.getType(),
                method.isAffectsCash(), method.isRequiresReference(),
                method.getAccountHolder(), method.getAccountNumber(), method.getQrImageUrl(),
                method.getStatus(), method.getSortOrder());
    }
}
