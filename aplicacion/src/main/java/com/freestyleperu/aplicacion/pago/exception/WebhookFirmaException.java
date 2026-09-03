package com.freestyleperu.aplicacion.pago.exception;

import com.freestyleperu.aplicacion.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class WebhookFirmaException extends BusinessException {

    public WebhookFirmaException(String message) {
        super(HttpStatus.UNAUTHORIZED, "INVALID_PAYMENT_WEBHOOK", message);
    }
}
