package com.freestyleperu.aplicacion.tienda.web;

import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import com.freestyleperu.aplicacion.tienda.dto.request.CrearStorefrontBannerRequest;
import com.freestyleperu.aplicacion.tienda.dto.response.StorefrontBannerResponse;
import com.freestyleperu.aplicacion.tienda.service.StorefrontBannerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "') and @modulos.activo('TIENDA')")
public class StorefrontBannerController {

    private final StorefrontBannerService service;

    public StorefrontBannerController(StorefrontBannerService service) {
        this.service = service;
    }

    @GetMapping("/api/storefront/banners")
    public List<StorefrontBannerResponse> listar() { return service.listar(); }

    @PostMapping("/api/storefront/banners")
    public StorefrontBannerResponse crear(@Valid @RequestBody CrearStorefrontBannerRequest request) {
        return service.crear(request);
    }

    @PutMapping("/api/storefront/banners/{id}")
    public StorefrontBannerResponse actualizar(@PathVariable Long id,
            @Valid @RequestBody CrearStorefrontBannerRequest request) {
        return service.actualizar(id, request);
    }

    @PostMapping("/api/storefront/banners/{id}/image")
    public StorefrontBannerResponse actualizarImagen(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return service.actualizarImagen(id, file);
    }

    @PatchMapping("/api/storefront/banners/{id}/status")
    public StorefrontBannerResponse cambiarEstado(@PathVariable Long id, @RequestParam EstadoGeneral status) {
        return service.cambiarEstado(id, status);
    }
}
