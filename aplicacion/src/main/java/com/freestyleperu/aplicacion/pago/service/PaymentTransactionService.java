package com.freestyleperu.aplicacion.pago.service;

import com.freestyleperu.aplicacion.pago.domain.PaymentProvider;
import com.freestyleperu.aplicacion.pago.domain.PaymentProviderType;
import com.freestyleperu.aplicacion.pago.domain.PaymentTransaction;
import com.freestyleperu.aplicacion.pago.domain.PaymentTransactionStatus;
import com.freestyleperu.aplicacion.pago.dto.request.ProcesarPaymentTransactionRequest;
import com.freestyleperu.aplicacion.pago.dto.request.CrearPaymentTransactionRequest;
import com.freestyleperu.aplicacion.pago.dto.response.PaymentProviderCheckoutResponse;
import com.freestyleperu.aplicacion.pago.dto.response.PaymentTransactionResponse;
import com.freestyleperu.aplicacion.pago.exception.ProveedorPagoException;
import com.freestyleperu.aplicacion.facturacion.service.ElectronicDocumentService;
import com.freestyleperu.aplicacion.pago.port.PaymentProviderChargeCommand;
import com.freestyleperu.aplicacion.pago.port.PaymentProviderChargeResult;
import com.freestyleperu.aplicacion.pago.port.PaymentProviderCheckoutCommand;
import com.freestyleperu.aplicacion.pago.port.PaymentProviderCheckoutResult;
import com.freestyleperu.aplicacion.pago.repository.PaymentTransactionRepository;
import com.freestyleperu.aplicacion.pedido.domain.Pedido;
import com.freestyleperu.aplicacion.pedido.domain.PedidoStatus;
import com.freestyleperu.aplicacion.pedido.repository.PedidoRepository;
import com.freestyleperu.aplicacion.pedido.service.PedidoService;
import com.freestyleperu.aplicacion.shared.audit.AuditResult;
import com.freestyleperu.aplicacion.shared.audit.AuditService;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.exception.ReglaDeNegocioException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Orquesta el ciclo de vida interno de un cobro, separado del contrato de
 * cada pasarela. Los adaptadores de Niubiz, Culqi e Izipay podrán reutilizar
 * estos estados sin mezclar sus payloads específicos con el dominio.
 */
@Service
@Transactional(readOnly = true)
public class PaymentTransactionService {

    private final PaymentTransactionRepository transactionRepository;
    private final PedidoRepository pedidoRepository;
    private final PaymentProviderConfigurationService providerConfigurationService;
    private final AuditService auditService;
    private final List<PaymentProvider> providers;
    private final PedidoService pedidoService;
    private final ElectronicDocumentService electronicDocumentService;

    public PaymentTransactionService(
            PaymentTransactionRepository transactionRepository,
            PedidoRepository pedidoRepository,
            PaymentProviderConfigurationService providerConfigurationService,
            AuditService auditService,
            List<PaymentProvider> providers,
            PedidoService pedidoService,
            ElectronicDocumentService electronicDocumentService) {
        this.transactionRepository = transactionRepository;
        this.pedidoRepository = pedidoRepository;
        this.providerConfigurationService = providerConfigurationService;
        this.auditService = auditService;
        this.providers = providers;
        this.pedidoService = pedidoService;
        this.electronicDocumentService = electronicDocumentService;
    }

    /**
     * Crea una sola operación lógica por clave de idempotencia. Repetir la
     * misma petición devuelve el intento existente; reutilizar la clave para
     * otro pedido o proveedor se rechaza.
     */
    @Transactional
    public PaymentTransactionResponse crearParaPedido(
            Long orderId, Long customerId, CrearPaymentTransactionRequest request, String rawIdempotencyKey) {
        String idempotencyKey = normalizarIdempotencyKey(rawIdempotencyKey);
        Pedido pedido = pedidoRepository.findById(orderId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Pedido", orderId));
        if (!pedido.getCustomer().getId().equals(customerId)) {
            throw RecursoNoEncontradoException.de("Pedido", orderId);
        }

        PaymentTransaction existente = transactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existente != null) {
            validarReintento(existente, pedido, request.provider());
            return toResponse(existente);
        }

        if (pedido.getStatus() != PedidoStatus.PENDING_PAYMENT) {
            throw new ReglaDeNegocioException("Solo se puede iniciar el pago de un pedido pendiente");
        }
        if (!providerConfigurationService.estaDisponible(request.provider())) {
            throw new ReglaDeNegocioException(
                    "La pasarela " + request.provider() + " no está habilitada o configurada para esta empresa");
        }

        if (pedido.getPaymentMethod().getType() != com.freestyleperu.aplicacion.pago.domain.PaymentMethodType.CARD) {
            throw new ReglaDeNegocioException("La transacciÃ³n online solo puede usarse con un mÃ©todo de tarjeta");
        }

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrder(pedido);
        transaction.setProvider(request.provider());
        transaction.setAmount(pedido.getTotal());
        transaction.setCurrencyCode("PEN");
        transaction.setStatus(PaymentTransactionStatus.CREATED);
        transaction.setIdempotencyKey(idempotencyKey);
        PaymentTransaction saved = transactionRepository.save(transaction);

        auditService.log("PAGO_ONLINE_CREADO", "PAYMENT_TRANSACTION", saved.getId(), null,
                new Object[] { saved.getOrder().getOrderNumber(), saved.getProvider(), saved.getAmount() },
                AuditResult.SUCCESS);
        return toResponse(saved);
    }

    /**
     * Crea la sesión efímera del checkout. El token de sesión solo viaja en
     * esta respuesta al navegador; no se persiste en la base de datos.
     */
    @Transactional
    public PaymentProviderCheckoutResponse inicializarCheckout(Long id, Long customerId) {
        PaymentTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Transacción de pago", id));
        validarPropiedad(transaction, customerId);
        if (transaction.getStatus() == PaymentTransactionStatus.APPROVED) {
            throw new ReglaDeNegocioException("El pago ya fue aprobado");
        }
        if (transaction.getStatus() != PaymentTransactionStatus.CREATED
                && transaction.getStatus() != PaymentTransactionStatus.PENDING) {
            throw new ReglaDeNegocioException("La transacción ya no puede inicializar su checkout");
        }

        PaymentProvider provider = providers.stream()
                .filter(candidate -> candidate.type() == transaction.getProvider())
                .findFirst()
                .orElseThrow(() -> new ReglaDeNegocioException(
                        "La pasarela " + transaction.getProvider() + " todavía no tiene adaptador habilitado"));
        var configuration = providerConfigurationService.obtenerParaBackend(transaction.getProvider())
                .orElseThrow(() -> new ReglaDeNegocioException(
                        "La configuración de " + transaction.getProvider() + " no está disponible"));
        Pedido pedido = transaction.getOrder();

        PaymentProviderCheckoutResult result;
        try {
            result = provider.initializeCheckout(
                    new PaymentProviderCheckoutCommand(
                            transaction.getId(),
                            pedido.getOrderNumber(),
                            transaction.getAmount(),
                            transaction.getCurrencyCode(),
                            pedido.getCustomer().getEmail(),
                            pedido.getCustomer().getFullName(),
                            pedido.getCustomer().getPhone(),
                            pedido.getAddress(),
                            pedido.getRecipientDni()),
                    configuration);
        } catch (UnsupportedOperationException ex) {
            throw new ReglaDeNegocioException(
                    "La pasarela " + transaction.getProvider() + " no ofrece checkout desacoplado");
        } catch (ProveedorPagoException ex) {
            throw new ReglaDeNegocioException(ex.getMessage());
        }

        if (transaction.getStatus() == PaymentTransactionStatus.CREATED) {
            registrarResultado(id, transaction.getProvider(), PaymentTransactionStatus.PENDING,
                    result.correlationId(), null, null, null);
        }
        return new PaymentProviderCheckoutResponse(
                result.provider(),
                result.sessionToken(),
                result.scriptUrl(),
                result.merchantCode(),
                result.correlationId(),
                result.publicKey(),
                pedido.getOrderNumber(),
                transaction.getAmount().toPlainString(),
                transaction.getCurrencyCode(),
                result.expirationMinutes());
    }

    public PaymentTransactionResponse obtenerPropia(Long id, Long customerId) {
        PaymentTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Transacción de pago", id));
        if (transaction.getOrder() == null
                || !transaction.getOrder().getCustomer().getId().equals(customerId)) {
            throw RecursoNoEncontradoException.de("Transacción de pago", id);
        }
        return toResponse(transaction);
    }

    /** Procesa un token efímero sin persistirlo en la base de datos. */
    @Transactional
    public PaymentTransactionResponse procesar(Long id, Long customerId, ProcesarPaymentTransactionRequest request) {
        PaymentTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Transacción de pago", id));
        validarPropiedad(transaction, customerId);

        if (transaction.getStatus() == PaymentTransactionStatus.APPROVED
                || transaction.getStatus() == PaymentTransactionStatus.PENDING
                || transaction.getStatus() == PaymentTransactionStatus.PROCESSING) {
            return toResponse(transaction);
        }
        if (transaction.getStatus() != PaymentTransactionStatus.CREATED
                && transaction.getStatus() != PaymentTransactionStatus.FAILED) {
            throw new ReglaDeNegocioException("La transacción ya no puede procesarse");
        }

        PaymentProvider provider = providers.stream()
                .filter(candidate -> candidate.type() == transaction.getProvider())
                .findFirst()
                .orElseThrow(() -> new ReglaDeNegocioException(
                        "La pasarela " + transaction.getProvider() + " todavía no tiene adaptador habilitado"));
        var configuration = providerConfigurationService.obtenerParaBackend(transaction.getProvider())
                .orElseThrow(() -> new ReglaDeNegocioException(
                        "La configuración de " + transaction.getProvider() + " no está disponible"));

        Pedido pedido = transaction.getOrder();
        registrarResultado(id, transaction.getProvider(), PaymentTransactionStatus.PROCESSING, null, null, null, null);

        PaymentProviderChargeResult result;
        try {
            result = provider.charge(
                    new PaymentProviderChargeCommand(
                            transaction.getId(),
                            pedido.getOrderNumber(),
                            transaction.getAmount(),
                            transaction.getCurrencyCode(),
                            pedido.getCustomer().getEmail(),
                            pedido.getCustomer().getFullName(),
                            pedido.getCustomer().getPhone(),
                            pedido.getAddress(),
                            pedido.getRecipientDni(),
                            request.sourceId().trim()),
                    configuration);
        } catch (ProveedorPagoException ex) {
            result = new PaymentProviderChargeResult(
                    PaymentTransactionStatus.FAILED, null, null, "PROVIDER_CONFIGURATION_ERROR", ex.getMessage());
        } catch (RuntimeException ex) {
            result = new PaymentProviderChargeResult(
                    PaymentTransactionStatus.FAILED, null, null, "PROVIDER_UNEXPECTED_ERROR",
                    "No se pudo procesar el pago en este momento");
        }

        return registrarResultado(id, transaction.getProvider(), result.status(),
                result.providerTransactionId(), result.providerReference(), result.failureCode(), result.failureMessage());
    }

    /**
     * Registra el resultado recibido por un adaptador o webhook. La operación
     * es idempotente para callbacks repetidos y rechaza retrocesos de estado.
     */
    @Transactional
    public PaymentTransactionResponse registrarResultado(
            Long id,
            PaymentProviderType provider,
            PaymentTransactionStatus nextStatus,
            String providerTransactionId,
            String providerReference,
            String failureCode,
            String failureMessage) {
        PaymentTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Transacción de pago", id));
        if (transaction.getProvider() != provider) {
            throw new ReglaDeNegocioException("La transacción no pertenece a la pasarela indicada");
        }
        PaymentTransactionStatus currentStatus = transaction.getStatus();
        if (currentStatus == nextStatus) {
            if (providerTransactionId != null && !providerTransactionId.isBlank()
                    && transaction.getProviderTransactionId() != null
                    && !transaction.getProviderTransactionId().equals(providerTransactionId.trim())) {
                throw new ReglaDeNegocioException("El callback no coincide con la transacción registrada");
            }
            if (nextStatus == PaymentTransactionStatus.APPROVED) {
                materializarVentaSiCorresponde(transaction);
            }
            return toResponse(transaction);
        }
        if (!transicionPermitida(currentStatus, nextStatus)) {
            throw new ReglaDeNegocioException(
                    "No se puede cambiar una transacción de " + currentStatus + " a " + nextStatus);
        }

        transaction.setStatus(nextStatus);
        if (providerTransactionId != null && !providerTransactionId.isBlank()) {
            transaction.setProviderTransactionId(providerTransactionId.trim());
        }
        if (providerReference != null && !providerReference.isBlank()) {
            transaction.setProviderReference(providerReference.trim());
        }
        if (nextStatus == PaymentTransactionStatus.DECLINED
                || nextStatus == PaymentTransactionStatus.FAILED) {
            transaction.setFailureCode(blankToNull(failureCode));
            transaction.setFailureMessage(truncate(blankToNull(failureMessage), 1000));
        }
        if (nextStatus == PaymentTransactionStatus.APPROVED
                || nextStatus == PaymentTransactionStatus.REFUNDED) {
            transaction.setCompletedAt(java.time.LocalDateTime.now());
        }

        PaymentTransaction saved = transactionRepository.save(transaction);
        if (nextStatus == PaymentTransactionStatus.APPROVED) {
            materializarVentaSiCorresponde(saved);
        }
        auditService.log("PAGO_ONLINE_" + nextStatus.name(), "PAYMENT_TRANSACTION", saved.getId(),
                currentStatus.name(), new Object[] { saved.getProvider(), saved.getProviderTransactionId() },
                AuditResult.SUCCESS);
        return toResponse(saved);
    }

    private void materializarVentaSiCorresponde(PaymentTransaction transaction) {
        if (transaction.getOrder() == null) {
            return;
        }
        Pedido pedido = transaction.getOrder();
        if (transaction.getSale() == null && pedido.getStatus() == PedidoStatus.PENDING_PAYMENT) {
            pedidoService.confirmarPagoOnline(pedido.getId());
        }
        if (pedido.getSale() != null) {
            transaction.setSale(pedido.getSale());
            transactionRepository.save(transaction);
            programarEmisionElectronica(pedido);
        }
    }

    private void programarEmisionElectronica(Pedido pedido) {
        if (pedido.getBillingDocumentType() == null
                || pedido.getBillingDocumentType() == com.freestyleperu.aplicacion.pedido.domain.PedidoBillingDocumentType.TICKET
                || pedido.getSale() == null) {
            return;
        }
        Long saleId = pedido.getSale().getId();
        Runnable emitir = () -> electronicDocumentService.emitirAutomaticamenteParaVenta(saleId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emitir.run();
                }
            });
        } else {
            emitir.run();
        }
    }

    private void validarReintento(PaymentTransaction existente, Pedido pedido, PaymentProviderType provider) {
        if (existente.getOrder() == null
                || !existente.getOrder().getId().equals(pedido.getId())
                || existente.getProvider() != provider
                || existente.getAmount().compareTo(pedido.getTotal()) != 0) {
            throw new ReglaDeNegocioException("La clave de idempotencia ya pertenece a otra operación de pago");
        }
    }

    private String normalizarIdempotencyKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ReglaDeNegocioException("Debes enviar el encabezado Idempotency-Key para iniciar un pago");
        }
        String normalized = raw.trim();
        if (normalized.length() > 100) {
            throw new ReglaDeNegocioException("El encabezado Idempotency-Key no puede superar 100 caracteres");
        }
        return normalized;
    }

    private boolean transicionPermitida(PaymentTransactionStatus current, PaymentTransactionStatus next) {
        return switch (current) {
            case CREATED -> next == PaymentTransactionStatus.PENDING
                    || next == PaymentTransactionStatus.PROCESSING
                    || next == PaymentTransactionStatus.APPROVED
                    || next == PaymentTransactionStatus.DECLINED
                    || next == PaymentTransactionStatus.FAILED
                    || next == PaymentTransactionStatus.CANCELLED;
            case PENDING -> next == PaymentTransactionStatus.PROCESSING
                    || next == PaymentTransactionStatus.APPROVED
                    || next == PaymentTransactionStatus.DECLINED
                    || next == PaymentTransactionStatus.FAILED
                    || next == PaymentTransactionStatus.CANCELLED;
            case PROCESSING -> next == PaymentTransactionStatus.APPROVED
                    || next == PaymentTransactionStatus.PENDING
                    || next == PaymentTransactionStatus.DECLINED
                    || next == PaymentTransactionStatus.FAILED
                    || next == PaymentTransactionStatus.CANCELLED;
            case APPROVED -> next == PaymentTransactionStatus.REFUNDED;
            case DECLINED, CANCELLED, REFUNDED -> false;
            case FAILED -> next == PaymentTransactionStatus.PROCESSING;
        };
    }

    private void validarPropiedad(PaymentTransaction transaction, Long customerId) {
        if (transaction.getOrder() == null
                || !transaction.getOrder().getCustomer().getId().equals(customerId)) {
            throw RecursoNoEncontradoException.de("Transacción de pago", transaction.getId());
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private PaymentTransactionResponse toResponse(PaymentTransaction transaction) {
        return new PaymentTransactionResponse(
                transaction.getId(),
                transaction.getOrder() != null ? transaction.getOrder().getId() : null,
                transaction.getSale() != null ? transaction.getSale().getId() : null,
                transaction.getProvider(),
                transaction.getAmount(),
                transaction.getCurrencyCode(),
                transaction.getStatus(),
                transaction.getProviderTransactionId(),
                transaction.getProviderReference(),
                transaction.getFailureCode(),
                transaction.getFailureMessage(),
                transaction.getExpiresAt(),
                transaction.getCompletedAt(),
                transaction.getCreatedAt());
    }
}
