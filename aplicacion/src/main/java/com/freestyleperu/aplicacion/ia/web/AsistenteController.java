package com.freestyleperu.aplicacion.ia.web;

import com.freestyleperu.aplicacion.ia.AsistenteTiendaService;
import com.freestyleperu.aplicacion.ia.dto.AsistenteChatRequest;
import com.freestyleperu.aplicacion.ia.dto.AsistenteChatResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chat del asistente de la tienda pública — sin autenticación, gateado por
 * el plan IA (superset del plan ECOMMERCE, ver {@code Plan}).
 */
@RestController
@PreAuthorize("@modulos.activo('IA')")
public class AsistenteController {

    private final AsistenteTiendaService asistenteTiendaService;

    public AsistenteController(AsistenteTiendaService asistenteTiendaService) {
        this.asistenteTiendaService = asistenteTiendaService;
    }

    @PostMapping("/api/store/assistant/chat")
    public AsistenteChatResponse chat(@Valid @RequestBody AsistenteChatRequest request) {
        return new AsistenteChatResponse(asistenteTiendaService.responder(request.message(), request.historyOrEmpty()));
    }

    /** El frontend lo consulta antes de mostrar el widget flotante — 403 si el plan no incluye IA. */
    @GetMapping("/api/store/assistant/enabled")
    public void habilitado() {
    }
}
