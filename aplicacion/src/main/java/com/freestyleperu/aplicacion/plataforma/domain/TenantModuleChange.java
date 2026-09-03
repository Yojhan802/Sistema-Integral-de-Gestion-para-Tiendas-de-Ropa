package com.freestyleperu.aplicacion.plataforma.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un cambio de paquete. {@code tenant_modules} solo guarda el estado actual, así que sin
 * esto no hay forma de saber quién subió o bajó a una empresa ni cuándo — justo lo que
 * hace falta cuando un cliente discute su factura.
 *
 * <p>El nombre del usuario se guarda además del id: si la cuenta se da de baja, el
 * historial tiene que seguir diciendo quién hizo el cambio.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tenant_module_changes")
public class TenantModuleChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "changed_by_username", length = 50)
    private String changedByUsername;

    @Column(name = "previous_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal previousTotal = BigDecimal.ZERO;

    @Column(name = "new_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal newTotal = BigDecimal.ZERO;

    @Column(name = "added", length = 500)
    private String added;

    @Column(name = "removed", length = 500)
    private String removed;

    @Column(name = "modules", length = 500)
    private String modules;
}
