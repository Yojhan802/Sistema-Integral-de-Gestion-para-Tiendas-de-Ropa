package com.freestyleperu.aplicacion.tienda.service;

import com.freestyleperu.aplicacion.shared.audit.AuditResult;
import com.freestyleperu.aplicacion.shared.audit.AuditService;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.exception.ReglaDeNegocioException;
import com.freestyleperu.aplicacion.shared.util.ImageUploadService;
import com.freestyleperu.aplicacion.tienda.domain.StorefrontBanner;
import com.freestyleperu.aplicacion.tienda.dto.request.CrearStorefrontBannerRequest;
import com.freestyleperu.aplicacion.tienda.dto.response.StorefrontBannerResponse;
import com.freestyleperu.aplicacion.tienda.repository.StorefrontBannerRepository;
import java.util.List;
import java.net.URI;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class StorefrontBannerService {

    private final StorefrontBannerRepository repository;
    private final ImageUploadService imageUploadService;
    private final AuditService auditService;
    private final StoreCatalogSyncService storeCatalogSyncService;

    public StorefrontBannerService(StorefrontBannerRepository repository, ImageUploadService imageUploadService,
            AuditService auditService, StoreCatalogSyncService storeCatalogSyncService) {
        this.repository = repository;
        this.imageUploadService = imageUploadService;
        this.auditService = auditService;
        this.storeCatalogSyncService = storeCatalogSyncService;
    }

    public List<StorefrontBannerResponse> listar() {
        return repository.findAllByStatusOrderBySortOrderAscIdAsc(EstadoGeneral.ACTIVE).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public StorefrontBannerResponse crear(CrearStorefrontBannerRequest request) {
        StorefrontBanner banner = new StorefrontBanner();
        aplicar(banner, request);
        StorefrontBanner saved = repository.save(banner);
        auditService.log("STOREFRONT_BANNER_CREADO", "STOREFRONT_BANNER", saved.getId(), null, saved.getHeadline(), AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return toResponse(saved);
    }

    @Transactional
    public StorefrontBannerResponse actualizar(Long id, CrearStorefrontBannerRequest request) {
        StorefrontBanner banner = buscar(id);
        aplicar(banner, request);
        auditService.log("STOREFRONT_BANNER_ACTUALIZADO", "STOREFRONT_BANNER", id, null, request, AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return toResponse(banner);
    }

    @Transactional
    public StorefrontBannerResponse actualizarImagen(Long id, MultipartFile file) {
        StorefrontBanner banner = buscar(id);
        banner.setImageUrl(imageUploadService.guardar(file, "storefront-banners"));
        auditService.log("STOREFRONT_BANNER_IMAGEN_ACTUALIZADA", "STOREFRONT_BANNER", id, null, banner.getImageUrl(), AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return toResponse(banner);
    }

    @Transactional
    public StorefrontBannerResponse cambiarEstado(Long id, EstadoGeneral status) {
        StorefrontBanner banner = buscar(id);
        banner.setStatus(status);
        auditService.log("STOREFRONT_BANNER_CAMBIO_ESTADO", "STOREFRONT_BANNER", id, null, status, AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return toResponse(banner);
    }

    private StorefrontBanner buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Banner de tienda", id));
    }

    private void aplicar(StorefrontBanner banner, CrearStorefrontBannerRequest request) {
        if (request.imageUrl() != null) banner.setImageUrl(request.imageUrl().trim());
        banner.setHeadline(blankToNull(request.headline()));
        banner.setDescription(blankToNull(request.description()));
        banner.setCtaLabel(blankToNull(request.ctaLabel()));
        banner.setCtaUrl(validarCtaUrl(request.ctaUrl()));
        banner.setSortOrder(Math.max(0, request.sortOrder() == null ? 0 : request.sortOrder()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String validarCtaUrl(String value) {
        String url = blankToNull(value);
        if (url == null || url.startsWith("/") || url.startsWith("#")) return url;
        try {
            URI uri = URI.create(url);
            if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) return url;
        } catch (IllegalArgumentException ignored) {
            // Se informa como regla de negocio debajo para no filtrar detalles del parser.
        }
        throw new ReglaDeNegocioException("El enlace del banner debe ser una ruta interna o una URL http/https");
    }

    private StorefrontBannerResponse toResponse(StorefrontBanner banner) {
        return new StorefrontBannerResponse(banner.getId(), banner.getImageUrl(), banner.getHeadline(),
                banner.getDescription(), banner.getCtaLabel(), banner.getCtaUrl(), banner.getSortOrder(),
                banner.getStatus(), banner.getCreatedAt(), banner.getUpdatedAt());
    }
}
