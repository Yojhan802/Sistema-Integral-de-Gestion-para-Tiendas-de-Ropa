package com.freestyleperu.aplicacion.pago.web;

import com.freestyleperu.aplicacion.pago.service.IzipayWebhookService;
import com.freestyleperu.aplicacion.pago.exception.WebhookFirmaException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint pÃºblico que Izipay invoca desde su servidor, no desde el navegador. */
@RestController
public class IzipayWebhookController {

    private final IzipayWebhookService service;

    public IzipayWebhookController(IzipayWebhookService service) {
        this.service = service;
    }

    @PostMapping("/api/webhooks/izipay/{transactionId}")
    public ResponseEntity<Void> recibir(
            @PathVariable String transactionId,
            @RequestHeader("transactionId") String headerTransactionId,
            HttpServletRequest request) {
        service.procesar(transactionId, headerTransactionId, leerBody(request));
        return ResponseEntity.noContent().build();
    }

    private String leerBody(HttpServletRequest request) {
        try {
            return new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new WebhookFirmaException("No se pudo leer el callback de Izipay");
        }
    }
}
