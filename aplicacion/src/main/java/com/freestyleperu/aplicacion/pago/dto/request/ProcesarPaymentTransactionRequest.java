package com.freestyleperu.aplicacion.pago.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Token efímero generado por el checkout del proveedor; no se persiste. */
public record ProcesarPaymentTransactionRequest(
        @NotBlank @Size(max = 100) String sourceId) {
}
