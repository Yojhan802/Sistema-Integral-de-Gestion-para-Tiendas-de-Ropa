package com.freestyleperu.aplicacion.pago.exception;

import com.freestyleperu.aplicacion.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** Fallo técnico al comunicarse con una pasarela, sin exponer su respuesta privada. */
public class ProveedorPagoException extends BusinessException {

    public ProveedorPagoException(String message) {
        super(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_UNAVAILABLE", message);
    }
}
