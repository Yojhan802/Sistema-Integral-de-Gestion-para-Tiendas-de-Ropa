package com.freestyleperu.aplicacion.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Ver Javadoc de {@code BaseEntity.tenantId} — esta entidad no extiende BaseEntity pero
     * también se aísla por tenant. Hibernate lo llena solo al insertar; el builder de esta
     * clase no debe usarse para asignarlo a mano.
     */
    @TenantId
    private Long tenantId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "entity", nullable = false, length = 40)
    private String entity;

    @Column(name = "entity_id")
    private Long entityId;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "old_value")
    private String oldValue;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "new_value")
    private String newValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 10)
    private AuditResult result;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
