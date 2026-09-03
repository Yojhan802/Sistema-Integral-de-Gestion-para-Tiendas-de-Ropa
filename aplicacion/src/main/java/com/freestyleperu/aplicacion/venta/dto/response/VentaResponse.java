package com.freestyleperu.aplicacion.venta.dto.response;

import com.freestyleperu.aplicacion.pedido.domain.PedidoBillingDocumentType;
import com.freestyleperu.aplicacion.venta.domain.SaleStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record VentaResponse(
        Long id,
        String saleNumber,
        Long customerId,
        String customerName,
        Long promoterId,
        String promoterName,
        Long sellerId,
        String sellerName,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal shippingAmount,
        BigDecimal total,
        SaleStatus status,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt,
        String cancelledByUsername,
        String cancellationReason,
        List<VentaItemResponse> items,
        List<PagoResponse> payments,
        PedidoBillingDocumentType billingDocumentType,
        String billingDocumentNumber,
        String billingName) {
}
