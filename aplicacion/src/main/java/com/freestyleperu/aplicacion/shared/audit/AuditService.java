package com.freestyleperu.aplicacion.shared.audit;

import com.freestyleperu.aplicacion.shared.security.AuthenticatedUser;
import com.freestyleperu.aplicacion.shared.util.RequestContextUtils;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Se ejecuta en una transacción independiente (REQUIRES_NEW) para que el
 * registro de auditoría sobreviva aunque la operación principal se revierta:
 * un intento denegado o fallido es precisamente lo que más interesa dejar constancia.
 *
 * El REQUIRES_NEW solo tiene efecto si la llamada pasa por el proxy de Spring, así que
 * log()/logAs() invocan write() a través de {@code self} (auto-inyección perezosa) en vez
 * de una llamada directa — una auto-invocación (this.write(...)) omite el proxy AOP por
 * completo y la anotación @Transactional se ignora en silencio.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final AuditService self;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper, @Lazy AuditService self) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.self = self;
    }

    public void log(String action, String entity, Long entityId, Object oldValue, Object newValue, AuditResult result) {
        AuthenticatedUser current = currentUser();
        Long userId = current == null ? null : current.id();
        String username = current == null ? "anonimo" : current.username();
        self.write(userId, username, action, entity, entityId, oldValue, newValue, result);
    }

    public void logAs(Long userId, String username, String action, String entity, Long entityId,
            Object oldValue, Object newValue, AuditResult result) {
        self.write(userId, username, action, entity, entityId, oldValue, newValue, result);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void write(Long userId, String username, String action, String entity, Long entityId,
            Object oldValue, Object newValue, AuditResult result) {
        try {
            AuditLog entry = AuditLog.builder()
                    .userId(userId)
                    .username(username)
                    .action(action)
                    .entity(entity)
                    .entityId(entityId)
                    .oldValue(serialize(oldValue))
                    .newValue(serialize(newValue))
                    .result(result)
                    .ipAddress(RequestContextUtils.currentIp())
                    .userAgent(RequestContextUtils.currentUserAgent())
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception ex) {
            log.error("No se pudo registrar auditoría para acción={} entidad={} entityId={}", action, entity, entityId, ex);
        }
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("No se pudo serializar valor de auditoría: {}", ex.getMessage());
            return null;
        }
    }

    private AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser)) {
            return null;
        }
        return authenticatedUser;
    }
}
