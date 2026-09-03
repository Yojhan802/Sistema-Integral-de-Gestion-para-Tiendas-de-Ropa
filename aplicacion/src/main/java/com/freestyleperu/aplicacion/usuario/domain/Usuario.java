package com.freestyleperu.aplicacion.usuario.domain;

import com.freestyleperu.aplicacion.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/**
 * {@code @DynamicUpdate}: sin esto, cada UPDATE (incluido el de cada login exitoso) toca las
 * ~12 columnas de la fila, no solo las que cambiaron — bajo login concurrente eso amplía
 * innecesariamente la ventana en la que InnoDB puede detectar un deadlock real entre dos
 * transacciones que compiten por la misma fila (ver ALTA PERF-01, confirmado con una prueba
 * de carga real con k6). No elimina el deadlock por sí solo — para eso está el reintento en
 * {@code AuthService.login()} — pero lo hace menos probable.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "tenant_id", "username" }),
        @UniqueConstraint(columnNames = { "tenant_id", "email" }),
        @UniqueConstraint(columnNames = { "tenant_id", "dni" }) })
@DynamicUpdate
public class Usuario extends BaseEntity {

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "email", length = 120)
    private String email;

    /** Solo el operador interno de la plataforma puede administrar empresas/tenants. */
    @Column(name = "platform_operator", nullable = false)
    private boolean platformOperator;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "dni", length = 15)
    private String dni;

    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UsuarioEstado status = UsuarioEstado.ACTIVE;

    @Column(name = "failed_attempts", nullable = false)
    private short failedAttempts;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Rol> roles = new HashSet<>();

    public boolean isBloqueadoTemporalmente() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public Set<String> permisosEfectivos() {
        return roles.stream()
                .flatMap(rol -> rol.getPermisos().stream())
                .map(Permiso::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }
}
