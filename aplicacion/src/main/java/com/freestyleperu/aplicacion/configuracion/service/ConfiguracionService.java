package com.freestyleperu.aplicacion.configuracion.service;

import com.freestyleperu.aplicacion.configuracion.domain.BusinessVertical;
import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.domain.StoreTemplate;
import com.freestyleperu.aplicacion.plataforma.service.ModuloGate;
import com.freestyleperu.aplicacion.configuracion.dto.request.ActualizarCompanySettingsRequest;
import com.freestyleperu.aplicacion.configuracion.dto.request.ActualizarIdentidadEmpresaRequest;
import com.freestyleperu.aplicacion.configuracion.dto.request.ActualizarStorefrontRequest;
import com.freestyleperu.aplicacion.configuracion.dto.request.ActualizarSuscripcionRequest;
import com.freestyleperu.aplicacion.configuracion.dto.response.BrandingResponse;
import com.freestyleperu.aplicacion.configuracion.dto.response.CompanySettingsResponse;
import com.freestyleperu.aplicacion.configuracion.dto.response.ContextoNegocioIAResponse;
import com.freestyleperu.aplicacion.configuracion.dto.response.SystemInfoResponse;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.shared.audit.AuditResult;
import com.freestyleperu.aplicacion.shared.audit.AuditService;
import com.freestyleperu.aplicacion.shared.exception.OperacionNoPermitidaException;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.security.TenantContext;
import com.freestyleperu.aplicacion.shared.util.ImageUploadService;
import com.freestyleperu.aplicacion.shared.validation.RucValidator;
import com.freestyleperu.aplicacion.usuario.repository.UsuarioRepository;
import com.freestyleperu.aplicacion.tienda.service.StoreCatalogSyncService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class ConfiguracionService {

    private final CompanySettingsRepository companySettingsRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditService auditService;
    private final ImageUploadService imageUploadService;
    private final ObjectProvider<BuildProperties> buildPropertiesProvider;
    private final ModuloGate moduloGate;
    private final StoreCatalogSyncService storeCatalogSyncService;

    public ConfiguracionService(
            CompanySettingsRepository companySettingsRepository,
            UsuarioRepository usuarioRepository,
            AuditService auditService,
            ImageUploadService imageUploadService,
            ObjectProvider<BuildProperties> buildPropertiesProvider,
            ModuloGate moduloGate,
            StoreCatalogSyncService storeCatalogSyncService) {
        this.companySettingsRepository = companySettingsRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditService = auditService;
        this.imageUploadService = imageUploadService;
        this.buildPropertiesProvider = buildPropertiesProvider;
        this.moduloGate = moduloGate;
        this.storeCatalogSyncService = storeCatalogSyncService;
    }

    public CompanySettingsResponse obtener() {
        return toResponse(buscarOFallar());
    }

    /** Solo datos operativos — ver RN-26. La identidad de empresa se actualiza con {@link #actualizarIdentidad}. */
    @Transactional
    @CacheEvict(cacheNames = "storeCatalogPaymentMethods", keyGenerator = "tenantAwareKeyGenerator")
    public CompanySettingsResponse actualizar(ActualizarCompanySettingsRequest request, Long userId) {
        CompanySettings settings = buscarOFallar();
        settings.setCurrencyCode(request.currencyCode());
        settings.setCurrencySymbol(request.currencySymbol());
        settings.setIgvRate(request.igvRate());
        settings.setTicketFooter(request.ticketFooter());
        settings.setShippingFlatRate(request.shippingFlatRate());
        settings.setReservationDepositAmount(request.reservationDepositAmount());
        settings.setReservationExpirationDays(request.reservationExpirationDays());
        if (request.onlinePaymentsEnabled() != null) {
            settings.setOnlinePaymentsEnabled(request.onlinePaymentsEnabled());
        }
        if (request.electronicInvoicingEnabled() != null) {
            if (request.electronicInvoicingEnabled() && !RucValidator.isValid(settings.getRuc())) {
                throw new OperacionNoPermitidaException(
                        "La facturacion electronica requiere configurar un RUC valido de 11 digitos");
            }
            settings.setElectronicInvoicingEnabled(request.electronicInvoicingEnabled());
        }
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(userId);
        auditService.log("CONFIGURACION_ACTUALIZADA", "COMPANY_SETTINGS", settings.getId(), null, request, AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return toResponse(settings);
    }

    /** Razón social, RUC, dirección y contacto — reservado al operador de la plataforma (RN-26, CONFIGURACION_IDENTIDAD_EDITAR). */
    @Transactional
    public CompanySettingsResponse actualizarIdentidad(ActualizarIdentidadEmpresaRequest request, Long userId) {
        CompanySettings settings = buscarOFallar();
        if (settings.isElectronicInvoicingEnabled() && !RucValidator.isValid(request.ruc())) {
            throw new OperacionNoPermitidaException(
                    "No puedes quitar o cambiar el RUC por uno invalido mientras la facturacion electronica esta activa");
        }
        settings.setName(request.name());
        settings.setRuc(request.ruc());
        settings.setAddress(request.address());
        settings.setPhone(request.phone());
        settings.setEmail(request.email());
        settings.setBusinessVertical(request.businessVertical());
        settings.setBusinessDescription(request.businessDescription());
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(userId);
        auditService.log("CONFIGURACION_IDENTIDAD_ACTUALIZADA", "COMPANY_SETTINGS", settings.getId(), null, request, AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return toResponse(settings);
    }

    @Transactional
    public CompanySettingsResponse actualizarLogo(MultipartFile file, Long userId) {
        CompanySettings settings = buscarOFallar();
        settings.setLogoUrl(imageUploadService.guardar(file, "logo"));
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(userId);
        auditService.log("CONFIGURACION_LOGO_ACTUALIZADO", "COMPANY_SETTINGS", settings.getId(), null, settings.getLogoUrl(), AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return toResponse(settings);
    }

    /** Usado internamente por el módulo de pedidos para calcular el envío. */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public BigDecimal obtenerTarifaEnvio() {
        return buscarOFallar().getShippingFlatRate();
    }

    /** Usado internamente por {@code ReservaService} — monto de seña por defecto (el cajero puede ajustarlo). */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public BigDecimal obtenerMontoSenaPorDefecto() {
        return buscarOFallar().getReservationDepositAmount();
    }

    /** Usado internamente por {@code ReservaService} — días para completar el pago antes de que la separación venza. */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public int obtenerDiasVencimientoReserva() {
        return buscarOFallar().getReservationExpirationDays();
    }

    /**
     * Identidad visual pública (nombre + logo) — para pintar el logo correcto
     * en login, "servicio suspendido" y la tienda pública, antes de que haya
     * sesión. Ver {@link BrandingResponse}.
     */
    public BrandingResponse obtenerBranding() {
        CompanySettings settings = buscarOFallar();
        return new BrandingResponse(settings.getName(), settings.getLogoUrl());
    }

    /** Configuracion visual minima que puede consumir la tienda publica. */
    public StoreTemplate obtenerPlantillaTienda() {
        CompanySettings settings = buscarOFallar();
        return settings.getStoreTemplate() == null ? StoreTemplate.CLASSIC : settings.getStoreTemplate();
    }

    /**
     * Configuracion publica de la tienda: apariencia saneada por columnas tipadas y
     * colores hex, mas la identificacion del proveedor y el IGV que la tienda debe
     * declarar al comprador. Nunca incluye credenciales ni datos de suscripcion.
     */
    public com.freestyleperu.aplicacion.tienda.dto.response.PublicStorefrontConfigResponse obtenerConfiguracionTienda() {
        CompanySettings settings = buscarOFallar();
        return new com.freestyleperu.aplicacion.tienda.dto.response.PublicStorefrontConfigResponse(
                settings.getStoreTemplate() == null ? StoreTemplate.CLASSIC : settings.getStoreTemplate(),
                colorOrDefault(settings.getStorePrimaryColor(), "#17324D"),
                colorOrDefault(settings.getStoreAccentColor(), "#17324D"),
                colorOrDefault(settings.getStoreBackgroundColor(), "#F5F7FA"),
                settings.getName(),
                settings.getRuc(),
                settings.getAddress(),
                settings.getPhone(),
                settings.getEmail(),
                settings.getIgvRate(),
                settings.getCurrencyCode(),
                settings.getCurrencySymbol());
    }

    @Transactional
    public CompanySettingsResponse publicarTienda(ActualizarStorefrontRequest request, Long userId) {
        if (!moduloGate.activo("TIENDA")) {
            throw new OperacionNoPermitidaException("La personalizacion de la tienda requiere el modulo Tienda virtual");
        }
        CompanySettings settings = buscarOFallar();
        settings.setStoreTemplate(request.template());
        settings.setStorePrimaryColor(normalizeColor(request.primaryColor(), settings.getStorePrimaryColor(), "#17324D"));
        settings.setStoreAccentColor(normalizeColor(request.accentColor(), settings.getStoreAccentColor(), "#17324D"));
        settings.setStoreBackgroundColor(normalizeColor(request.backgroundColor(), settings.getStoreBackgroundColor(), "#F5F7FA"));
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(userId);
        auditService.log("TIENDA_APARIENCIA_PUBLICADA", "COMPANY_SETTINGS", settings.getId(), null, request, AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return toResponse(settings);
    }

    /** Ver Javadoc de {@link ContextoNegocioIAResponse}. */
    public ContextoNegocioIAResponse obtenerContextoIA() {
        CompanySettings settings = buscarOFallar();
        String frase = settings.getBusinessDescription() != null && !settings.getBusinessDescription().isBlank()
                ? settings.getBusinessDescription()
                : settings.getBusinessVertical() == BusinessVertical.CLOTHING ? "un negocio de ropa en Perú" : "un negocio en Perú";
        return new ContextoNegocioIAResponse(settings.getBusinessVertical(), frase);
    }

    /** Ficha pública mínima para un panel externo de monitoreo — ver SystemInfoResponse. */
    public SystemInfoResponse obtenerInfoSistema() {
        return construirInfoSistema(buscarOFallar());
    }

    /**
     * Usado solo por OpsApiKeyAuthenticationFilter (panel externo de monitoreo) — nunca por el
     * cliente. La ruta ops está exenta de {@code TenantResolutionFilter} (no llega por subdominio),
     * así que NO usa el contexto ambiental de tenant — el tenant a actualizar viene explícito en
     * {@code request.tenantId()}.
     */
    @Transactional
    public SystemInfoResponse actualizarSuscripcion(ActualizarSuscripcionRequest request) {
        CompanySettings settings = companySettingsRepository.findById(request.tenantId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Configuración de empresa", request.tenantId()));
        settings.setSubscriptionStatus(request.subscriptionStatus());
        if (request.nextPaymentDue() != null) {
            settings.setNextPaymentDue(request.nextPaymentDue());
        }
        auditService.log("SUSCRIPCION_ACTUALIZADA_OPS", "COMPANY_SETTINGS", settings.getId(), null, request, AuditResult.SUCCESS);
        return construirInfoSistema(settings);
    }

    private SystemInfoResponse construirInfoSistema(CompanySettings settings) {
        BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
        String version = buildProperties != null ? buildProperties.getVersion() : "dev";
        return new SystemInfoResponse(
                settings.getName(), settings.getPlan(), version, settings.getSubscriptionStatus(), settings.getNextPaymentDue());
    }

    private CompanySettings buscarOFallar() {
        Long tenantId = TenantContext.getOrDefault();
        return companySettingsRepository.findById(tenantId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Configuración de empresa", tenantId));
    }

    private CompanySettingsResponse toResponse(CompanySettings settings) {
        String updatedByUsername = settings.getUpdatedBy() == null
                ? null
                : usuarioRepository.findById(settings.getUpdatedBy()).map(u -> u.getUsername()).orElse(null);
        return new CompanySettingsResponse(
                settings.getName(), settings.getRuc(), settings.getAddress(), settings.getPhone(), settings.getEmail(),
                settings.getLogoUrl(), settings.getBusinessVertical(), settings.getBusinessDescription(),
                settings.getCurrencyCode(), settings.getCurrencySymbol(), settings.getIgvRate(),
                settings.getTicketFooter(), settings.getShippingFlatRate(), settings.getReservationDepositAmount(),
                settings.getReservationExpirationDays(), settings.getPlan(), settings.getSubscriptionStatus(),
                settings.getNextPaymentDue(), settings.isOnlinePaymentsEnabled(), settings.isElectronicInvoicingEnabled(),
                settings.getUpdatedAt(), updatedByUsername, settings.getStoreTemplate(), settings.getStorePrimaryColor(),
                settings.getStoreAccentColor(), settings.getStoreBackgroundColor());
    }

    private String normalizeColor(String value, String current, String fallback) {
        if (value == null || value.isBlank()) return colorOrDefault(current, fallback);
        return value.trim().toUpperCase();
    }

    private String colorOrDefault(String value, String fallback) {
        return value != null && value.matches("^#[0-9A-Fa-f]{6}$") ? value.toUpperCase() : fallback;
    }
}
