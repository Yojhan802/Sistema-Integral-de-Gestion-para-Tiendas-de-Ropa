package com.freestyleperu.aplicacion.venta.dto.request;

import com.freestyleperu.aplicacion.pedido.domain.PedidoBillingDocumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CrearVentaRequest(
        Long customerId,
        Long promoterId,
        @NotNull Long cashSessionId,
        @DecimalMin(value = "0.00") BigDecimal discountAmount,
        @Size(max = 255) String notes,
        @NotEmpty @Valid List<ItemVentaRequest> items,
        @NotEmpty @Valid List<PagoVentaRequest> payments,
        PedidoBillingDocumentType billingDocumentType,
        @Size(max = 15) String billingDocumentNumber,
        @Size(max = 150) String billingName) {

    /**
     * Constructor legado usado por clientes internos que todavía no envían datos de comprobante.
     * Una venta POS existente continúa siendo una venta interna tipo ticket.
     */
    public CrearVentaRequest(
            Long customerId,
            Long promoterId,
            Long cashSessionId,
            BigDecimal discountAmount,
            String notes,
            List<ItemVentaRequest> items,
            List<PagoVentaRequest> payments) {
        this(customerId, promoterId, cashSessionId, discountAmount, notes, items, payments,
                null, null, null);
    }
}
