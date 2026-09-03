package com.freestyleperu.aplicacion.pago.web;

import com.freestyleperu.aplicacion.pago.service.CulqiWebhookService;
import com.freestyleperu.aplicacion.pago.exception.WebhookFirmaException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint público para notificaciones server-to-server de Culqi. */
@RestController
public class CulqiWebhookController {

    private final CulqiWebhookService service;

    public CulqiWebhookController(CulqiWebhookService service) {
        this.service = service;
    }

    @PostMapping("/api/webhooks/culqi")
    public ResponseEntity<Void> recibir(HttpServletRequest request) {
        service.procesar(leerBody(request));
        return ResponseEntity.noContent().build();
    }

    private String leerBody(HttpServletRequest request) {
        try {
            return new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new WebhookFirmaException("No se pudo leer la notificación de Culqi");
        }
    }
}
