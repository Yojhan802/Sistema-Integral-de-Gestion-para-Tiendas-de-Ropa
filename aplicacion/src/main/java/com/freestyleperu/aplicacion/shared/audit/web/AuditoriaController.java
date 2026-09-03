package com.freestyleperu.aplicacion.shared.audit.web;

import com.freestyleperu.aplicacion.shared.audit.AuditResult;
import com.freestyleperu.aplicacion.shared.audit.AuditoriaService;
import com.freestyleperu.aplicacion.shared.audit.dto.response.AuditLogResponse;
import com.freestyleperu.aplicacion.shared.audit.dto.response.AuditLogResumenResponse;
import com.freestyleperu.aplicacion.shared.dto.PageResponse;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/api/audit")
    @PreAuthorize("hasAuthority('" + Permisos.AUDITORIA_CONSULTAR + "') and @modulos.activo('AUDITORIA')")
    public PageResponse<AuditLogResumenResponse> listar(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) AuditResult result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Pageable pageable) {
        return auditoriaService.listar(userId, action, entity, result, from, to, pageable);
    }

    @GetMapping("/api/audit/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.AUDITORIA_CONSULTAR + "') and @modulos.activo('AUDITORIA')")
    public AuditLogResponse obtener(@PathVariable Long id) {
        return auditoriaService.obtener(id);
    }
}
