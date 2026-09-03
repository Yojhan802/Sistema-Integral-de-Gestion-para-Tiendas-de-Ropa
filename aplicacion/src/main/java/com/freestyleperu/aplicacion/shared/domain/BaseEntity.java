package com.freestyleperu.aplicacion.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Aislamiento multi-tenant (conversión a SaaS, ver plan aprobado): Hibernate agrega
     * automáticamente {@code WHERE tenant_id = ?} a toda consulta contra esta entidad y llena
     * este campo solo al insertar — nunca se asigna a mano. Verificado en un spike aislado
     * (Fase 0 del plan) contra Hibernate 7.4.1 antes de aplicarlo aquí: filtra correctamente
     * {@code entityManager.find()}, los métodos derivados de Spring Data, y hasta el acceso a
     * un proxy perezoso de una fila ajena (lanza {@code EntityNotFoundException}, no filtra el
     * dato). {@code CompanySettings} (la fila que representa al tenant mismo) y {@code Permiso}
     * (catálogo fijo de la aplicación, no dato de negocio) NO extienden {@code BaseEntity} y no
     * llevan este campo — se quedan fuera del aislamiento a propósito.
     */
    @TenantId
    private Long tenantId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
