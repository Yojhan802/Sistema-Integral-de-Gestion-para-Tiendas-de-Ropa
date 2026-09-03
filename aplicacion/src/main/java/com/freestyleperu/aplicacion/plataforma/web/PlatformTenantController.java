package com.freestyleperu.aplicacion.plataforma.web;

import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import com.freestyleperu.aplicacion.plataforma.dto.request.ActualizarModulosRequest;
import com.freestyleperu.aplicacion.plataforma.dto.request.CambiarOperadorRequest;
import com.freestyleperu.aplicacion.plataforma.dto.request.RegistrarPagoRequest;
import com.freestyleperu.aplicacion.plataforma.dto.request.ActualizarTenantRequest;
import com.freestyleperu.aplicacion.plataforma.dto.request.CrearTenantRequest;
import com.freestyleperu.aplicacion.plataforma.dto.response.CambioPaqueteResponse;
import com.freestyleperu.aplicacion.plataforma.dto.response.CatalogoModulosResponse;
import com.freestyleperu.aplicacion.plataforma.dto.response.CrearTenantResponse;
import com.freestyleperu.aplicacion.plataforma.dto.response.ModulosTenantResponse;
import com.freestyleperu.aplicacion.plataforma.dto.response.PagoSuscripcionResponse;
import com.freestyleperu.aplicacion.plataforma.dto.response.RenovacionResponse;
import com.freestyleperu.aplicacion.plataforma.dto.response.TenantResponse;
import com.freestyleperu.aplicacion.plataforma.dto.response.UsuarioEmpresaResponse;
import com.freestyleperu.aplicacion.plataforma.service.PlatformModuleService;
import com.freestyleperu.aplicacion.plataforma.service.PlatformOperatorService;
import com.freestyleperu.aplicacion.plataforma.service.PlatformTenantService;
import com.freestyleperu.aplicacion.plataforma.service.SubscriptionRenewalService;
import com.freestyleperu.aplicacion.shared.security.AuthenticatedUser;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import com.freestyleperu.aplicacion.shared.util.ImageUploadService;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/platform/tenants")
@PreAuthorize("hasAuthority('" + Permisos.PLATAFORMA_EMPRESAS_GESTIONAR + "')")
public class PlatformTenantController {

    private final PlatformTenantService service;
    private final PlatformModuleService moduleService;
    private final PlatformOperatorService operatorService;
    private final SubscriptionRenewalService renewalService;
    private final ImageUploadService imageUploadService;
    private final String uploadsDir;

    public PlatformTenantController(PlatformTenantService service, PlatformModuleService moduleService,
            PlatformOperatorService operatorService, SubscriptionRenewalService renewalService,
            ImageUploadService imageUploadService, @Value("${app.uploads.dir}") String uploadsDir) {
        this.uploadsDir = uploadsDir;
        this.service = service;
        this.moduleService = moduleService;
        this.operatorService = operatorService;
        this.renewalService = renewalService;
        this.imageUploadService = imageUploadService;
    }

    @GetMapping
    public List<TenantResponse> listar(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SubscriptionStatus status) {
        return service.listar(search, status);
    }

    @PostMapping
    public CrearTenantResponse crear(@Valid @RequestBody CrearTenantRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return service.crear(request, currentUser.id(), currentUser.username());
    }

    @PutMapping("/{tenantId}")
    public TenantResponse actualizar(@PathVariable Long tenantId, @Valid @RequestBody ActualizarTenantRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return service.actualizar(tenantId, request, currentUser.id());
    }

    /** Catálogo sin empresa detrás: lo usa el alta para elegir el paquete inicial. */
    @GetMapping("/modules/catalog")
    public CatalogoModulosResponse catalogoModulos() {
        return moduleService.catalogo();
    }

    /** Módulos contratados por la empresa: es lo que decide a qué tiene acceso. */
    @GetMapping("/{tenantId}/modules")
    public ModulosTenantResponse modulos(@PathVariable Long tenantId) {
        return moduleService.obtener(tenantId);
    }

    @PutMapping("/{tenantId}/modules")
    public ModulosTenantResponse actualizarModulos(@PathVariable Long tenantId,
            @Valid @RequestBody ActualizarModulosRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return moduleService.actualizar(tenantId, request, currentUser.id(), currentUser.username());
    }

    /** Quién subió o bajó el paquete y cuándo — para sustentar una factura discutida. */
    @GetMapping("/{tenantId}/modules/history")
    public List<CambioPaqueteResponse> historialModulos(@PathVariable Long tenantId) {
        return moduleService.historial(tenantId);
    }

    /** Registra el pago de la mensualidad y mueve el vencimiento. */
    @PostMapping("/{tenantId}/subscription/payments")
    public RenovacionResponse renovar(@PathVariable Long tenantId,
            @Valid @RequestBody RegistrarPagoRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return renewalService.renovar(tenantId, request, currentUser.id(), currentUser.username());
    }

    /** Adjunta la captura del pago; opcional, se sube después de registrarlo. */
    @PostMapping("/{tenantId}/subscription/payments/{pagoId}/proof")
    public PagoSuscripcionResponse adjuntarComprobante(@PathVariable Long tenantId, @PathVariable Long pagoId,
            @RequestParam("file") MultipartFile file) {
        return renewalService.adjuntarComprobante(tenantId, pagoId, imageUploadService.guardar(file, "suscripciones"));
    }

    /**
     * Sirve el comprobante. Hereda el {@code @PreAuthorize} de la clase, así que solo lo ve
     * un operador de plataforma; la ruta estática {@code /uploads/suscripciones/**} está
     * bloqueada justamente para que este sea el único camino.
     */
    @GetMapping("/{tenantId}/subscription/payments/{pagoId}/proof")
    public ResponseEntity<byte[]> verComprobante(@PathVariable Long tenantId, @PathVariable Long pagoId) {
        SubscriptionRenewalService.Comprobante comprobante =
                renewalService.comprobanteDe(tenantId, pagoId, Path.of(uploadsDir));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(comprobante.contentType()));
        headers.setContentLength(comprobante.contenido().length);
        headers.setContentDisposition(ContentDisposition.inline().filename(comprobante.nombre()).build());
        return new ResponseEntity<>(comprobante.contenido(), headers, 200);
    }

    @GetMapping("/{tenantId}/subscription/payments")
    public List<PagoSuscripcionResponse> pagos(@PathVariable Long tenantId) {
        return renewalService.historial(tenantId);
    }

    /** Usuarios de la empresa, para conceder o retirar el acceso al módulo Empresas. */
    @GetMapping("/{tenantId}/users")
    public List<UsuarioEmpresaResponse> usuarios(@PathVariable Long tenantId) {
        return operatorService.usuariosDe(tenantId);
    }

    @PatchMapping("/{tenantId}/users/{userId}/operator")
    public List<UsuarioEmpresaResponse> cambiarOperador(@PathVariable Long tenantId, @PathVariable Long userId,
            @Valid @RequestBody CambiarOperadorRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        operatorService.cambiar(userId, request.operador(), currentUser.id());
        return operatorService.usuariosDe(tenantId);
    }
}
