package com.freestyleperu.aplicacion.tienda.web;

import com.freestyleperu.aplicacion.shared.dto.PageResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicCategoriaResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicMarcaResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicMetodoPagoResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicProductoDetalleResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicProductoResumenResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicSearchSuggestionResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicShippingInfoResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicStorefrontBannerResponse;
import com.freestyleperu.aplicacion.tienda.service.TiendaCatalogoService;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo público de la tienda online — todo bajo /api/store/catalog, sin
 * autenticación. Gateado por plan de suscripción (no todos los planes
 * incluyen tienda online) — {@code @PreAuthorize} funciona igual sobre un
 * endpoint público: no exige estar autenticado, solo evalúa la expresión.
 */
@RestController
@PreAuthorize("@modulos.activo('TIENDA')")
public class TiendaCatalogoController {

    private final TiendaCatalogoService catalogoService;

    public TiendaCatalogoController(TiendaCatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/api/store/catalog/products")
    public PageResponse<PublicProductoResumenResponse> listarProductos(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            Pageable pageable) {
        return catalogoService.listarProductos(search, categoryId, brandId, pageable);
    }

    @GetMapping("/api/store/catalog/products/{id}")
    public PublicProductoDetalleResponse obtenerProducto(@PathVariable Long id) {
        return catalogoService.obtenerProducto(id);
    }

    @GetMapping("/api/store/catalog/categories")
    public List<PublicCategoriaResponse> listarCategorias() {
        return catalogoService.listarCategorias();
    }

    @GetMapping("/api/store/catalog/brands")
    public List<PublicMarcaResponse> listarMarcas() {
        return catalogoService.listarMarcas();
    }

    @GetMapping("/api/store/catalog/payment-methods")
    public List<PublicMetodoPagoResponse> listarMetodosPago() {
        return catalogoService.listarMetodosPago();
    }

    @GetMapping("/api/store/catalog/shipping-info")
    public PublicShippingInfoResponse obtenerInfoEnvio() {
        return catalogoService.obtenerInfoEnvio();
    }

    @GetMapping("/api/store/catalog/billing-options")
    public com.freestyleperu.aplicacion.tienda.dto.response.PublicBillingOptionsResponse obtenerOpcionesFacturacion() {
        return catalogoService.obtenerOpcionesFacturacion();
    }

    @GetMapping("/api/store/catalog/config")
    public com.freestyleperu.aplicacion.tienda.dto.response.PublicStorefrontConfigResponse obtenerConfiguracion() {
        return catalogoService.obtenerConfiguracionTienda();
    }

    @GetMapping("/api/store/catalog/banners")
    public List<PublicStorefrontBannerResponse> listarBanners() {
        return catalogoService.listarBanners();
    }

    @GetMapping("/api/store/catalog/search-suggestions")
    public List<PublicSearchSuggestionResponse> sugerencias(@RequestParam("q") String query) {
        return catalogoService.sugerencias(query);
    }
}
