package com.freestyleperu.aplicacion.inventario.domain;

import com.freestyleperu.aplicacion.shared.domain.BaseEntity;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sucursal. En Fase 1 existe un único registro sembrado por migración; el
 * modelo ya soporta varias para no bloquear el multisucursal futuro
 * (docs/01-requisitos.md S-04).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "branches", uniqueConstraints = @UniqueConstraint(columnNames = { "tenant_id", "code" }))
public class Branch extends BaseEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EstadoGeneral status = EstadoGeneral.ACTIVE;
}
