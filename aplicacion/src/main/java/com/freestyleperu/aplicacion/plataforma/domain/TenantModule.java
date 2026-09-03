package com.freestyleperu.aplicacion.plataforma.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un módulo contratado por una empresa. La existencia de la fila es la que habilita el
 * acceso; {@code monthlyPrice} es el precio pactado con esa empresa, que puede diferir
 * del de lista porque el objetivo es ajustar el paquete a su presupuesto.
 *
 * <p>No lleva {@code @TenantId}: lo administra el operador de la plataforma, que trabaja
 * fuera del contexto de un tenant concreto.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tenant_modules",
        uniqueConstraints = @UniqueConstraint(columnNames = { "tenant_id", "module_code" }))
public class TenantModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "module_code", nullable = false, length = 30)
    private ModuloSistema module;

    @Column(name = "monthly_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyPrice = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public TenantModule(Long tenantId, ModuloSistema module, BigDecimal monthlyPrice) {
        this.tenantId = tenantId;
        this.module = module;
        this.monthlyPrice = monthlyPrice;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }
}
