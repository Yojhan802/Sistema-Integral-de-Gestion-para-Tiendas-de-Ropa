package com.freestyleperu.aplicacion.ia;

import com.freestyleperu.aplicacion.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AsistenteNoDisponibleException extends BusinessException {

    public AsistenteNoDisponibleException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "ASSISTANT_UNAVAILABLE", message);
    }
}
