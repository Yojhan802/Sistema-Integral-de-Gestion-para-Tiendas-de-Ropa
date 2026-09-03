package com.freestyleperu.aplicacion.configuracion.web;

import com.freestyleperu.aplicacion.configuracion.dto.request.ActualizarCompanySettingsRequest;
import com.freestyleperu.aplicacion.configuracion.dto.request.ActualizarIdentidadEmpresaRequest;
import com.freestyleperu.aplicacion.configuracion.dto.request.ActualizarStorefrontRequest;
import com.freestyleperu.aplicacion.configuracion.dto.request.ActualizarSuscripcionRequest;
import com.freestyleperu.aplicacion.configuracion.dto.response.BrandingResponse;
import com.freestyleperu.aplicacion.configuracion.dto.response.CompanySettingsResponse;
import com.freestyleperu.aplicacion.configuracion.dto.response.SystemInfoResponse;
import com.freestyleperu.aplicacion.configuracion.service.ConfiguracionService;
import com.freestyleperu.aplicacion.shared.security.AuthenticatedUser;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ConfiguracionController {

    private final ConfiguracionService configuracionService;

    public ConfiguracionController(ConfiguracionService configuracionService) {
        this.configuracionService = configuracionService;
    }

    @GetMapping("/api/settings/company")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_VER + "')")
    public CompanySettingsResponse obtener() {
        return configuracionService.obtener();
    }

    @PutMapping("/api/settings/company")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "')")
    public CompanySettingsResponse actualizar(
            @Valid @RequestBody ActualizarCompanySettingsRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return configuracionService.actualizar(request, currentUser.id());
    }

    /** Razón social, RUC, dirección y contacto — reservado al operador de la plataforma (RN-26). */
    @PutMapping("/api/settings/company/identity")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_IDENTIDAD_EDITAR + "')")
    public CompanySettingsResponse actualizarIdentidad(
            @Valid @RequestBody ActualizarIdentidadEmpresaRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return configuracionService.actualizarIdentidad(request, currentUser.id());
    }

    @PostMapping("/api/settings/company/logo")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_IDENTIDAD_EDITAR + "')")
    public CompanySettingsResponse actualizarLogo(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return configuracionService.actualizarLogo(file, currentUser.id());
    }

    @PutMapping("/api/settings/company/storefront")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "') and @modulos.activo('TIENDA')")
    public CompanySettingsResponse publicarTienda(
            @Valid @RequestBody ActualizarStorefrontRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return configuracionService.publicarTienda(request, currentUser.id());
    }

    /**
     * Ficha pública mínima (nombre, plan, versión) para un panel externo de
     * monitoreo — sin autenticación, a propósito. Ver SystemInfoResponse.
     */
    @GetMapping("/api/system/info")
    public SystemInfoResponse infoSistema() {
        return configuracionService.obtenerInfoSistema();
    }

    /**
     * Nombre + logo, sin autenticación — para pintar el login, "servicio
     * suspendido" y la tienda pública antes de que haya sesión.
     */
    @GetMapping("/api/system/branding")
    public BrandingResponse branding() {
        return configuracionService.obtenerBranding();
    }

    /**
     * No usa login de usuario — protegido por OpsApiKeyAuthenticationFilter
     * (llave secreta propia de esta instalación, ver docs/03 §16). Pensado
     * para que el panel externo de monitoreo pueda marcar pagos/suspensiones
     * sin que nadie necesite entrar a la base de datos a mano.
     */
    @PutMapping("/api/system/subscription")
    @PreAuthorize("hasAuthority('OPS_API')")
    public SystemInfoResponse actualizarSuscripcion(@Valid @RequestBody ActualizarSuscripcionRequest request) {
        return configuracionService.actualizarSuscripcion(request);
    }
}
