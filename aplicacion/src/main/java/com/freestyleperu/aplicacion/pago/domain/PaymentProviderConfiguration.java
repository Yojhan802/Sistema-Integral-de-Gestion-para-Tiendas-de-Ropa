package com.freestyleperu.aplicacion.pago.domain;

import com.freestyleperu.aplicacion.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_provider_configurations", uniqueConstraints = @UniqueConstraint(columnNames = { "tenant_id", "provider" }))
public class PaymentProviderConfiguration extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private PaymentProviderType provider;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 20)
    private PaymentProviderEnvironment environment = PaymentProviderEnvironment.TEST;

    @Column(name = "api_url", length = 500)
    private String apiUrl;

    /** Identificador público del comercio, según el proveedor (merchant ID/code). */
    @Column(name = "merchant_code", length = 100)
    private String merchantCode;

    /** Llave pública que el checkout del navegador puede recibir, si el proveedor la usa. */
    @Column(name = "public_key", length = 500)
    private String publicKey;

    /** JSON cifrado con credenciales privadas y campos específicos del proveedor. */
    @Lob
    @Column(name = "credentials_encrypted", columnDefinition = "TEXT")
    private String credentialsEncrypted;
}
