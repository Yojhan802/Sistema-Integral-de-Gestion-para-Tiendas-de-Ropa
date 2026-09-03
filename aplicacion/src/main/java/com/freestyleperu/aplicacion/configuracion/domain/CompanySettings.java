package com.freestyleperu.aplicacion.configuracion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una fila por negocio (conversión a SaaS multi-tenant, ver plan aprobado) — esta es la tabla
 * de tenants: su propio {@code id} es el {@code tenant_id} que referencian todas las demás
 * tablas. Antes de la conversión era una fila única (id = 1, asignado a mano); ver
 * docs/03-modelo-datos.md "company_settings" y la migración V38. A propósito NO extiende
 * {@code BaseEntity} y no lleva {@code @TenantId}: es el tenant, no un dato aislado por tenant.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "company_settings")
public class CompanySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Subdominio del negocio (ej. "tiendax" → tiendax.qynex.pe) — lo resuelve TenantResolutionFilter (Fase 2 de la conversión). */
    @Column(name = "slug", nullable = false, unique = true, length = 63)
    private String slug;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** Decide qué instrucciones de IA se activan (ver BusinessVertical) — default CLOTHING. */
    @Enumerated(EnumType.STRING)
    @Column(name = "business_vertical", nullable = false, length = 20)
    private BusinessVertical businessVertical = BusinessVertical.CLOTHING;

    /** Frase libre para el framing del prompt (ej. "una ferretería en Perú"); si es null se arma
     * un texto genérico a partir de businessVertical (ver AsistenteTiendaService). */
    @Column(name = "business_description", length = 255)
    private String businessDescription;

    @Column(name = "ruc", length = 15)
    private String ruc;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 120)
    private String email;

    /**
     * Si esta empresa cuenta como ingreso. La tienda propia y el demo funcionan igual que
     * las demás, pero nadie paga por ellas: incluirlas falsea el ingreso mensual.
     */
    @Column(name = "billable", nullable = false)
    private boolean billable = true;

    @Column(name = "logo_url", length = 255)
    private String logoUrl;

    /** Plantilla visual de la tienda publica; la logica de catalogo y checkout es compartida. */
    @Enumerated(EnumType.STRING)
    @Column(name = "store_template", nullable = false, length = 30)
    private StoreTemplate storeTemplate = StoreTemplate.CLASSIC;

    @Column(name = "store_primary_color", length = 7)
    private String storePrimaryColor = "#17324D";

    @Column(name = "store_accent_color", length = 7)
    private String storeAccentColor = "#17324D";

    @Column(name = "store_background_color", length = 7)
    private String storeBackgroundColor = "#F5F7FA";

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "currency_symbol", nullable = false, length = 5)
    private String currencySymbol;

    @Column(name = "igv_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal igvRate;

    @Column(name = "ticket_footer", length = 255)
    private String ticketFooter;

    @Column(name = "shipping_flat_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingFlatRate;

    /** Monto de seña por defecto para separaciones — el cajero puede ajustarlo caso por caso. */
    @Column(name = "reservation_deposit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal reservationDepositAmount;

    /** Días para completar el pago de una separación antes de que venza y la prenda vuelva a stock. */
    @Column(name = "reservation_expiration_days", nullable = false)
    private int reservationExpirationDays;

    /** No editable por el cliente vía API — solo lo cambia el operador de la plataforma directo en la base de datos. */
    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 20)
    private Plan plan;

    /** No editable por el cliente vía API — la marca SuscripcionScheduler o el operador directo en la base de datos. */
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 20)
    private SubscriptionStatus subscriptionStatus;

    @Column(name = "next_payment_due")
    private LocalDate nextPaymentDue;

    /** Interruptor maestro por tenant para las pasarelas de pago online. */
    @Column(name = "online_payments_enabled", nullable = false)
    private boolean onlinePaymentsEnabled;

    /** Interruptor maestro por tenant para la facturación electrónica. */
    @Column(name = "electronic_invoicing_enabled", nullable = false)
    private boolean electronicInvoicingEnabled;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
