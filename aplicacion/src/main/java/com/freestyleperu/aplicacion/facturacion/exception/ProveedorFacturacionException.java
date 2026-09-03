package com.freestyleperu.aplicacion.facturacion.exception;

import com.freestyleperu.aplicacion.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ProveedorFacturacionException extends BusinessException {

    public ProveedorFacturacionException(String message) {
        super(HttpStatus.BAD_GATEWAY, "BILLING_PROVIDER_UNAVAILABLE", message);
    }
}
