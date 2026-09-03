package com.freestyleperu.aplicacion.producto.web;

import com.freestyleperu.aplicacion.producto.dto.request.ActualizarProductoRequest;
import com.freestyleperu.aplicacion.producto.dto.request.ActualizarProductoImagenRequest;
import com.freestyleperu.aplicacion.producto.dto.request.CrearProductoRequest;
import com.freestyleperu.aplicacion.producto.dto.request.GenerarDescripcionRequest;
import com.freestyleperu.aplicacion.producto.dto.response.GenerarDescripcionResponse;
import com.freestyleperu.aplicacion.producto.dto.response.ProductoDetalleResponse;
import com.freestyleperu.aplicacion.producto.dto.response.ProductoImagenResponse;
import com.freestyleperu.aplicacion.producto.dto.response.ProductoResumenResponse;
import com.freestyleperu.aplicacion.producto.service.ProductoAsistenteService;
import com.freestyleperu.aplicacion.producto.service.ProductoService;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.dto.CambiarEstadoRequest;
import com.freestyleperu.aplicacion.shared.dto.PageResponse;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ProductoController {

    private final ProductoService productoService;
    private final ProductoAsistenteService productoAsistenteService;

    public ProductoController(ProductoService productoService, ProductoAsistenteService productoAsistenteService) {
        this.productoService = productoService;
        this.productoAsistenteService = productoAsistenteService;
    }

    @GetMapping("/api/products")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_CONSULTAR + "')")
    public PageResponse<ProductoResumenResponse> listar(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subcategoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) EstadoGeneral status,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            Pageable pageable) {
        return PageResponse.of(
                productoService.listar(search, categoryId, subcategoryId, brandId, status, minPrice, maxPrice, pageable),
                it -> it);
    }

    @GetMapping("/api/products/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_CONSULTAR + "')")
    public ProductoDetalleResponse obtener(@PathVariable Long id) {
        return productoService.obtener(id);
    }

    @PostMapping("/api/products")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_CREAR + "')")
    public ResponseEntity<ProductoDetalleResponse> crear(@Valid @RequestBody CrearProductoRequest request) {
        ProductoDetalleResponse creado = productoService.crear(request);
        return ResponseEntity.created(URI.create("/api/products/" + creado.id())).body(creado);
    }

    @PutMapping("/api/products/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_EDITAR + "')")
    public ProductoDetalleResponse actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarProductoRequest request) {
        return productoService.actualizar(id, request);
    }

    @PatchMapping("/api/products/{id}/status")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_EDITAR + "')")
    public ProductoResumenResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest request) {
        return productoService.cambiarEstado(id, request.status());
    }

    @PostMapping("/api/products/{id}/image")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_EDITAR + "')")
    public ProductoDetalleResponse actualizarImagen(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return productoService.actualizarImagen(id, file);
    }

    @GetMapping("/api/products/{id}/images")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_CONSULTAR + "')")
    public List<ProductoImagenResponse> listarImagenes(@PathVariable Long id) {
        return productoService.listarImagenes(id);
    }

    @PostMapping("/api/products/{id}/images")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_EDITAR + "')")
    public ProductoImagenResponse agregarImagen(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String altText,
            @RequestParam(defaultValue = "0") Integer sortOrder,
            @RequestParam(defaultValue = "false") boolean primary) {
        return productoService.agregarImagen(id, file, altText, sortOrder, primary);
    }

    @PatchMapping("/api/products/{id}/images/{imageId}/primary")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_EDITAR + "')")
    public ProductoImagenResponse marcarImagenPrincipal(@PathVariable Long id, @PathVariable Long imageId) {
        return productoService.marcarImagenPrincipal(id, imageId);
    }

    @PatchMapping("/api/products/{id}/images/{imageId}")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_EDITAR + "')")
    public ProductoImagenResponse actualizarImagenGaleria(
            @PathVariable Long id,
            @PathVariable Long imageId,
            @Valid @RequestBody ActualizarProductoImagenRequest request) {
        return productoService.actualizarImagenGaleria(id, imageId, request);
    }

    @DeleteMapping("/api/products/{id}/images/{imageId}")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_EDITAR + "')")
    public void eliminarImagen(@PathVariable Long id, @PathVariable Long imageId) {
        productoService.eliminarImagen(id, imageId);
    }

    @PostMapping("/api/products/{id}/size-guide")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_EDITAR + "')")
    public ProductoDetalleResponse actualizarGuiaTallas(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return productoService.actualizarGuiaTallas(id, file);
    }

    /** "Generar con IA" en el formulario de producto (plan IA) — funciona tanto al crear como al editar. */
    @PostMapping("/api/products/assistant/description")
    @PreAuthorize("hasAnyAuthority('" + Permisos.PRODUCTOS_CREAR + "', '" + Permisos.PRODUCTOS_EDITAR + "') and @modulos.activo('IA')")
    public GenerarDescripcionResponse generarDescripcion(@Valid @RequestBody GenerarDescripcionRequest request) {
        return new GenerarDescripcionResponse(productoAsistenteService.generarDescripcion(request));
    }
}
