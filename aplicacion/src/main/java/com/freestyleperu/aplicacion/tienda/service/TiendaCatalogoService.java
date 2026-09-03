package com.freestyleperu.aplicacion.tienda.service;

import com.freestyleperu.aplicacion.catalogo.domain.AttributeInputType;
import com.freestyleperu.aplicacion.catalogo.repository.BrandRepository;
import com.freestyleperu.aplicacion.catalogo.repository.CategoryRepository;
import com.freestyleperu.aplicacion.configuracion.service.ConfiguracionService;
import com.freestyleperu.aplicacion.facturacion.service.BillingConfigurationService;
import com.freestyleperu.aplicacion.pago.repository.PaymentMethodRepository;
import com.freestyleperu.aplicacion.pago.service.PaymentProviderConfigurationService;
import com.freestyleperu.aplicacion.pago.domain.PaymentMethodType;
import com.freestyleperu.aplicacion.producto.domain.Product;
import com.freestyleperu.aplicacion.producto.domain.ProductImage;
import com.freestyleperu.aplicacion.producto.domain.ProductAttribute;
import com.freestyleperu.aplicacion.producto.domain.ProductVariant;
import com.freestyleperu.aplicacion.producto.domain.VariantAttributeValue;
import com.freestyleperu.aplicacion.producto.repository.ProductAttributeRepository;
import com.freestyleperu.aplicacion.producto.repository.ProductRepository;
import com.freestyleperu.aplicacion.producto.repository.ProductImageRepository;
import com.freestyleperu.aplicacion.producto.repository.ProductVariantRepository;
import com.freestyleperu.aplicacion.promocion.service.PromocionService;
import com.freestyleperu.aplicacion.shared.dto.PageResponse;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.validation.RucValidator;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicAttributeValueResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicBillingOptionsResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicCategoriaResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicColorSwatchResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicMarcaResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicMetodoPagoResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicProductoDetalleResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicProductoResumenResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicProductImageResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicSearchSuggestionResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicShippingInfoResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicStorefrontConfigResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicStorefrontBannerResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicVarianteResponse;
import com.freestyleperu.aplicacion.tienda.domain.StorefrontBanner;
import com.freestyleperu.aplicacion.tienda.repository.StorefrontBannerRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectura pública del catálogo — nunca expone SKU/código interno/barcode/stock
 * exacto (ver plan Fase 2). En un pico de tráfico (ej. Black Friday) esta es
 * la ruta más pegada — se cachea unos segundos (`storeCatalog`,
 * `spring.cache.caffeine.spec`) para no golpear la base de datos por cada
 * visita. El checkout (`PedidoService.crear`) nunca lee de esta caché: valida
 * stock siempre contra la base en el momento, así que un dato de "disponible"
 * con unos segundos de retraso nunca permite vender de más, a lo sumo muestra
 * "sin stock" un momento después de lo ideal.
 */
@Service
@Transactional(readOnly = true)
public class TiendaCatalogoService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductAttributeRepository productAttributeRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentProviderConfigurationService paymentProviderConfigurationService;
    private final ConfiguracionService configuracionService;
    private final PromocionService promocionService;
    private final StorefrontBannerRepository storefrontBannerRepository;
    private final BillingConfigurationService billingConfigurationService;

    private static final String DISTRITO_ENVIO_GRATIS = "Huacho";

    public TiendaCatalogoService(ProductRepository productRepository, ProductImageRepository productImageRepository,
            ProductVariantRepository variantRepository,
            ProductAttributeRepository productAttributeRepository, CategoryRepository categoryRepository,
            BrandRepository brandRepository, PaymentMethodRepository paymentMethodRepository,
            PaymentProviderConfigurationService paymentProviderConfigurationService,
            ConfiguracionService configuracionService, PromocionService promocionService,
            StorefrontBannerRepository storefrontBannerRepository,
            BillingConfigurationService billingConfigurationService) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.variantRepository = variantRepository;
        this.productAttributeRepository = productAttributeRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentProviderConfigurationService = paymentProviderConfigurationService;
        this.configuracionService = configuracionService;
        this.promocionService = promocionService;
        this.storefrontBannerRepository = storefrontBannerRepository;
        this.billingConfigurationService = billingConfigurationService;
    }

    public PublicStorefrontConfigResponse obtenerConfiguracionTienda() {
        return configuracionService.obtenerConfiguracionTienda();
    }

    @Cacheable(cacheNames = "storeCatalogProducts", keyGenerator = "tenantAwareKeyGenerator")
    public PageResponse<PublicProductoResumenResponse> listarProductos(
            String search, Long categoryId, Long brandId, Pageable pageable) {
        return PageResponse.of(
                productRepository.buscar(search, categoryId, null, brandId, EstadoGeneral.ACTIVE, null, null, pageable),
                this::toResumen);
    }

    @Cacheable(cacheNames = "storeCatalogProductDetail", keyGenerator = "tenantAwareKeyGenerator")
    public PublicProductoDetalleResponse obtenerProducto(Long id) {
        Product product = productRepository.findById(id)
                .filter(p -> p.getStatus() == EstadoGeneral.ACTIVE)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Producto", id));

        Map<Long, Short> posiciones = posicionesDelProducto(id);
        List<PublicVarianteResponse> variantes = variantRepository.findAllByProductId(id).stream()
                .filter(v -> v.getStatus() == EstadoGeneral.ACTIVE)
                .map(v -> toVariante(v, posiciones))
                .toList();

        return new PublicProductoDetalleResponse(
                product.getId(), product.getName(), product.getDescription(), product.getMaterial(), product.getFit(),
                product.getPrice(), promoPriceParaMostrar(product), product.getImageUrl(), product.getSizeGuideImageUrl(),
                product.getCategory().getName(), product.getBrand() != null ? product.getBrand().getName() : null, variantes,
                imagenesDe(product));
    }

    /**
     * El {@code promoPrice} que se expone al público reutiliza el mismo campo
     * de siempre: si una promoción marcada {@code visibleOnline} deja un
     * precio menor al normal, se muestra esa — si no, el {@code promoPrice}
     * estático del producto (o nulo si tampoco hay). El frontend de la
     * tienda no necesita saber nada de promociones, solo sigue leyendo
     * {@code promoPrice} como ya lo hacía.
     */
    private BigDecimal promoPriceParaMostrar(Product product) {
        BigDecimal efectivo = promocionService.precioEfectivoOnline(product);
        return efectivo.compareTo(product.getPrice()) < 0 ? efectivo : null;
    }

    @Cacheable(cacheNames = "storeCatalogCategories", keyGenerator = "tenantAwareKeyGenerator")
    public List<PublicCategoriaResponse> listarCategorias() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .filter(c -> c.getStatus() == EstadoGeneral.ACTIVE)
                .map(c -> new PublicCategoriaResponse(c.getId(), c.getName(), c.getSlug(), c.getImageUrl()))
                .toList();
    }

    @Cacheable(cacheNames = "storeCatalogBrands", keyGenerator = "tenantAwareKeyGenerator")
    public List<PublicMarcaResponse> listarMarcas() {
        return brandRepository.findAllByOrderByNameAsc().stream()
                .filter(b -> b.getStatus() == EstadoGeneral.ACTIVE)
                .map(b -> new PublicMarcaResponse(b.getId(), b.getName()))
                .toList();
    }

    public List<PublicStorefrontBannerResponse> listarBanners() {
        return storefrontBannerRepository.findAllByStatusOrderBySortOrderAscIdAsc(EstadoGeneral.ACTIVE).stream()
                .filter(b -> b.getImageUrl() != null && !b.getImageUrl().isBlank())
                .map(this::toPublicBanner)
                .toList();
    }

    public List<PublicSearchSuggestionResponse> sugerencias(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) return List.of();

        List<PublicSearchSuggestionResponse> categorias = categoryRepository
                .findTop5ByStatusAndNameContainingIgnoreCaseOrderByNameAsc(EstadoGeneral.ACTIVE, q).stream()
                .map(c -> new PublicSearchSuggestionResponse("CATEGORY", c.getId(), c.getName(), "Categoría", null))
                .toList();
        List<PublicSearchSuggestionResponse> productos = productRepository
                .sugerencias(q, EstadoGeneral.ACTIVE, PageRequest.of(0, 8)).stream()
                .map(p -> new PublicSearchSuggestionResponse("PRODUCT", p.getId(), p.getName(),
                        p.getBrand() != null ? p.getBrand().getName() : p.getCategory().getName(), p.getImageUrl()))
                .toList();
        return java.util.stream.Stream.concat(productos.stream(), categorias.stream()).limit(10).toList();
    }

    @Cacheable(cacheNames = "storeCatalogShipping", keyGenerator = "tenantAwareKeyGenerator")
    public PublicShippingInfoResponse obtenerInfoEnvio() {
        return new PublicShippingInfoResponse(configuracionService.obtenerTarifaEnvio(), DISTRITO_ENVIO_GRATIS);
    }

    /**
     * Publica únicamente si el interruptor del tenant y el proveedor están
     * realmente listos. Nunca expone tokens, claves ni credenciales.
     */
    @Cacheable(cacheNames = "storeCatalogBillingOptions", keyGenerator = "tenantAwareKeyGenerator")
    public PublicBillingOptionsResponse obtenerOpcionesFacturacion() {
        var company = configuracionService.obtener();
        var billing = billingConfigurationService.obtener();
        boolean enabled = company.electronicInvoicingEnabled() && RucValidator.isValid(company.ruc());
        boolean available = enabled && billing.enabled() && billing.configured();
        boolean receiptAvailable = available && billing.receiptSeries() != null && !billing.receiptSeries().isBlank();
        boolean invoiceAvailable = available && billing.invoiceSeries() != null && !billing.invoiceSeries().isBlank();
        return new PublicBillingOptionsResponse(enabled, receiptAvailable || invoiceAvailable,
                receiptAvailable, invoiceAvailable, available ? billing.provider().name() : null);
    }

    @Cacheable(cacheNames = "storeCatalogPaymentMethods", keyGenerator = "tenantAwareKeyGenerator")
    public List<PublicMetodoPagoResponse> listarMetodosPago() {
        // affectsCash = true (ej. EFECTIVO) no tiene sentido en un checkout online sin cajero
        // presente — mismo criterio que ya usa la seña de separaciones (RN-27).
        // La tarjeta representa el cobro online con una pasarela: no se publica si la empresa
        // no activó pagos online o no tiene ningún proveedor configurado y habilitado.
        boolean hayPasarelaOnline = !paymentProviderConfigurationService.listarPublicos().isEmpty();
        return paymentMethodRepository.findAllByOrderBySortOrderAsc().stream()
                .filter(m -> m.getStatus() == EstadoGeneral.ACTIVE && !m.isAffectsCash()
                        && (m.getType() != PaymentMethodType.CARD || hayPasarelaOnline))
                .map(m -> new PublicMetodoPagoResponse(
                        m.getId(), m.getCode(), m.getName(), m.getType(), m.isRequiresReference(),
                        m.getAccountHolder(), m.getAccountNumber(), m.getQrImageUrl()))
                .toList();
    }

    private static final int MAX_SWATCHES = 6;

    private PublicProductoResumenResponse toResumen(Product product) {
        List<ProductVariant> variantesActivas = variantRepository.findAllByProductId(product.getId()).stream()
                .filter(v -> v.getStatus() == EstadoGeneral.ACTIVE)
                .toList();
        boolean inStock = variantesActivas.stream().anyMatch(v -> v.getStock() > 0);

        // Ya no exclusivo de "Color": cualquier atributo SWATCH del producto aparece como swatch
        // en la tarjeta del listado (ver Javadoc de PublicColorSwatchResponse).
        List<PublicColorSwatchResponse> colores = variantesActivas.stream()
                .flatMap(v -> v.getAttributeValues().stream())
                .map(VariantAttributeValue::getAttributeValue)
                .filter(valor -> valor.getAttribute().getInputType() == AttributeInputType.SWATCH)
                .collect(Collectors.toMap(
                        av -> av.getId(), av -> av, (a, b) -> a, LinkedHashMap::new))
                .values().stream()
                .map(valor -> new PublicColorSwatchResponse(valor.getValue(), valor.getHexCode()))
                .limit(MAX_SWATCHES)
                .toList();

        return new PublicProductoResumenResponse(
                product.getId(), product.getName(), product.getPrice(), promoPriceParaMostrar(product), product.getImageUrl(),
                product.getCategory().getName(), product.getBrand() != null ? product.getBrand().getName() : null,
                colores, inStock, imagenesDe(product));
    }

    private List<PublicProductImageResponse> imagenesDe(Product product) {
        List<PublicProductImageResponse> images = productImageRepository
                .findAllByProductIdOrderBySortOrderAscIdAsc(product.getId()).stream()
                .filter(image -> image.getImageUrl() != null && !image.getImageUrl().isBlank())
                .map(this::toPublicImage)
                .toList();
        if (!images.isEmpty() || product.getImageUrl() == null || product.getImageUrl().isBlank()) return images;
        return List.of(new PublicProductImageResponse(null, product.getImageUrl(), product.getName(), 0, true));
    }

    private PublicProductImageResponse toPublicImage(ProductImage image) {
        return new PublicProductImageResponse(image.getId(), image.getImageUrl(), image.getAltText(),
                image.getSortOrder(), image.isPrimaryImage());
    }

    private PublicStorefrontBannerResponse toPublicBanner(StorefrontBanner banner) {
        return new PublicStorefrontBannerResponse(banner.getId(), banner.getImageUrl(), banner.getHeadline(),
                banner.getDescription(), banner.getCtaLabel(), banner.getCtaUrl());
    }

    private Map<Long, Short> posicionesDelProducto(Long productId) {
        return productAttributeRepository.findAllByProductIdOrderByPositionAsc(productId).stream()
                .collect(Collectors.toMap(pa -> pa.getAttribute().getId(), ProductAttribute::getPosition));
    }

    private PublicVarianteResponse toVariante(ProductVariant variant, Map<Long, Short> posiciones) {
        List<PublicAttributeValueResponse> atributos = variant.getAttributeValues().stream()
                .sorted(Comparator.comparing(vav -> posiciones.get(vav.getAttributeValue().getAttribute().getId())))
                .map(vav -> new PublicAttributeValueResponse(
                        vav.getAttributeValue().getAttribute().getId(),
                        vav.getAttributeValue().getAttribute().getName(),
                        vav.getAttributeValue().getAttribute().getInputType(),
                        vav.getAttributeValue().getId(),
                        vav.getAttributeValue().getValue(),
                        vav.getAttributeValue().getHexCode()))
                .toList();
        return new PublicVarianteResponse(variant.getId(), variant.getVariantLabel(), atributos, variant.getStock() > 0);
    }
}
