package com.freestyleperu.aplicacion.facturacion.domain;

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
@Table(name = "billing_configurations", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id"}))
public class BillingConfiguration extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private BillingProvider provider = BillingProvider.VERIFACT;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 20)
    private BillingProviderEnvironment environment = BillingProviderEnvironment.TEST;

    @Column(name = "api_url", length = 500)
    private String apiUrl;

    @Column(name = "invoice_series", length = 10)
    private String invoiceSeries;

    @Column(name = "receipt_series", length = 10)
    private String receiptSeries;

    @Column(name = "credit_note_series", length = 10)
    private String creditNoteSeries;

    @Column(name = "debit_note_series", length = 10)
    private String debitNoteSeries;

    @Lob
    @Column(name = "credentials_encrypted", columnDefinition = "TEXT")
    private String credentialsEncrypted;
}
