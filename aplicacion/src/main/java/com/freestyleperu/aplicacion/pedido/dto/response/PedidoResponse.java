package com.freestyleperu.aplicacion.pedido.dto.response;

import com.freestyleperu.aplicacion.pedido.domain.PedidoBillingDocumentType;
import com.freestyleperu.aplicacion.pedido.domain.PedidoStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponse(
        Long id,
        String orderNumber,
        Long customerId,
        String customerName,
        BigDecimal subtotal,
        BigDecimal shippingCost,
        BigDecimal total,
        PedidoStatus status,
        Long paymentMethodId,
        String paymentMethodName,
        String paymentReference,
        String paymentProofUrl,
        String recipientDni,
        String recipientFirstName,
        String recipientLastNamePaterno,
        String recipientLastNameMaterno,
        String phone,
        String address,
        String department,
        String province,
        String district,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt,
        String confirmedByUsername,
        LocalDateTime cancelledAt,
        String cancellationReason,
        Long saleId,
        List<PedidoItemResponse> items,
        PedidoBillingDocumentType billingDocumentType,
        String billingDocumentNumber,
        String billingName,
        /** Prueba de que el comprador aceptó los Términos y Condiciones al contratar. */
        LocalDateTime termsAcceptedAt,
        String termsVersion) {

    /** Compatibilidad con respuestas construidas por pruebas y consumidores antiguos. */
    public PedidoResponse(
            Long id,
            String orderNumber,
            Long customerId,
            String customerName,
            BigDecimal subtotal,
            BigDecimal shippingCost,
            BigDecimal total,
            PedidoStatus status,
            Long paymentMethodId,
            String paymentMethodName,
            String paymentReference,
            String paymentProofUrl,
            String recipientDni,
            String recipientFirstName,
            String recipientLastNamePaterno,
            String recipientLastNameMaterno,
            String phone,
            String address,
            String department,
            String province,
            String district,
            String notes,
            LocalDateTime createdAt,
            LocalDateTime confirmedAt,
            String confirmedByUsername,
            LocalDateTime cancelledAt,
            String cancellationReason,
            Long saleId,
            List<PedidoItemResponse> items) {
        this(id, orderNumber, customerId, customerName, subtotal, shippingCost, total, status,
                paymentMethodId, paymentMethodName, paymentReference, paymentProofUrl,
                recipientDni, recipientFirstName, recipientLastNamePaterno, recipientLastNameMaterno,
                phone, address, department, province, district, notes, createdAt, confirmedAt,
                confirmedByUsername, cancelledAt, cancellationReason, saleId, items,
                PedidoBillingDocumentType.TICKET, null, null, null, null);
    }
}
