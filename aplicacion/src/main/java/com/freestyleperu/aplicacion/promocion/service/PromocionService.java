package com.freestyleperu.aplicacion.promocion.service;

import com.freestyleperu.aplicacion.catalogo.domain.Category;
import com.freestyleperu.aplicacion.catalogo.repository.CategoryRepository;
import com.freestyleperu.aplicacion.producto.domain.Product;
import com.freestyleperu.aplicacion.producto.domain.ProductVariant;
import com.freestyleperu.aplicacion.producto.repository.ProductRepository;
import com.freestyleperu.aplicacion.producto.repository.ProductVariantRepository;
import com.freestyleperu.aplicacion.promocion.domain.Promocion;
import com.freestyleperu.aplicacion.promocion.domain.PromotionType;
import com.freestyleperu.aplicacion.promocion.dto.request.PromocionRequest;
import com.freestyleperu.aplicacion.promocion.dto.response.PromocionResponse;
import com.freestyleperu.aplicacion.promocion.repository.PromocionRepository;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.exception.ReglaDeNegocioException;
import com.freestyleperu.aplicacion.tienda.service.StoreCatalogSyncService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PromocionService {

    private final PromocionRepository promocionRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final StoreCatalogSyncService storeCatalogSyncService;

    public PromocionService(PromocionRepository promocionRepository, CategoryRepository categoryRepository,
            ProductRepository productRepository, ProductVariantRepository variantRepository,
            StoreCatalogSyncService storeCatalogSyncService) {
        this.promocionRepository = promocionRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.storeCatalogSyncService = storeCatalogSyncService;
    }

    public List<PromocionResponse> listar() {
        return promocionRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    public PromocionResponse obtener(Long id) {
        return toResponse(buscarOFallar(id));
    }

    /** Usado por el POS: promociones vigentes que aplican a la variante que se está por vender. */
    public List<PromocionResponse> listarVigentesParaVariante(Long variantId) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Variante", variantId));
        Product product = variant.getProduct();
        Long categoryId = product.getCategory().getId();
        return promocionRepository.buscarVigentesParaProducto(product.getId(), categoryId, LocalDateTime.now())
                .stream().map(this::toResponse).toList();
    }

    /**
     * Usado por VentaService al aplicar una promoción a una línea de venta —
     * valida que esté activa, vigente por fecha y que su alcance incluya el
     * producto de la variante (nunca se confía en que el frontend ya filtró bien).
     */
    public Promocion obtenerAplicableOFallar(Long id, ProductVariant variant) {
        Promocion promo = buscarOFallar(id);
        if (promo.getStatus() != EstadoGeneral.ACTIVE) {
            throw new ReglaDeNegocioException("La promoción " + promo.getName() + " no está activa");
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (promo.getStartsAt() != null && ahora.isBefore(promo.getStartsAt())) {
            throw new ReglaDeNegocioException("La promoción " + promo.getName() + " todavía no empieza");
        }
        if (promo.getEndsAt() != null && ahora.isAfter(promo.getEndsAt())) {
            throw new ReglaDeNegocioException("La promoción " + promo.getName() + " ya venció");
        }
        Product product = variant.getProduct();
        boolean aplica = switch (promo.getScopeType()) {
            case ALL -> true;
            case CATEGORY -> promo.getScopeCategory().getId().equals(product.getCategory().getId());
            case PRODUCT -> promo.getScopeProduct().getId().equals(product.getId());
        };
        if (!aplica) {
            throw new ReglaDeNegocioException("La promoción " + promo.getName() + " no aplica a " + product.getName());
        }
        return promo;
    }

    /** Descuento resultante de aplicar la promoción a una línea — nunca mayor al importe bruto de esa línea. */
    public BigDecimal calcularDescuento(Promocion promo, BigDecimal brutoLinea) {
        BigDecimal descuento = promo.getDiscountType() == PromotionType.PERCENTAGE
                ? brutoLinea.multiply(promo.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : promo.getDiscountValue();
        return descuento.min(brutoLinea);
    }

    /**
     * Precio final que ve (y paga) el cliente en la tienda online: el precio
     * vigente del producto (o su {@code promoPrice} estático) menos la mejor
     * promoción activa marcada {@code visibleOnline} que aplique — nunca
     * confía en el frontend, se recalcula server-side también al crear el
     * pedido (RN-28). Si ninguna promoción online aplica, devuelve el precio
     * vigente sin cambios.
     */
    public BigDecimal precioEfectivoOnline(Product product) {
        BigDecimal base = product.getPromoPrice() != null ? product.getPromoPrice() : product.getPrice();
        List<Promocion> vigentes = promocionRepository
                .buscarVigentesParaProducto(product.getId(), product.getCategory().getId(), LocalDateTime.now());

        BigDecimal mejor = base;
        for (Promocion promo : vigentes) {
            if (!promo.isVisibleOnline()) {
                continue;
            }
            BigDecimal conDescuento = base.subtract(calcularDescuento(promo, base));
            if (conDescuento.compareTo(mejor) < 0) {
                mejor = conDescuento;
            }
        }
        return mejor;
    }

    @Transactional
    public PromocionResponse crear(PromocionRequest request) {
        if (promocionRepository.existsByCode(request.code())) {
            throw new ReglaDeNegocioException("Ya existe una promoción con el código " + request.code());
        }
        Promocion promo = new Promocion();
        aplicarCampos(promo, request);
        PromocionResponse response = toResponse(promocionRepository.save(promo));
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    @Transactional
    public PromocionResponse actualizar(Long id, PromocionRequest request) {
        Promocion promo = buscarOFallar(id);
        if (!promo.getCode().equals(request.code()) && promocionRepository.existsByCode(request.code())) {
            throw new ReglaDeNegocioException("Ya existe una promoción con el código " + request.code());
        }
        aplicarCampos(promo, request);
        PromocionResponse response = toResponse(promo);
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    @Transactional
    public PromocionResponse cambiarEstado(Long id, EstadoGeneral status) {
        Promocion promo = buscarOFallar(id);
        promo.setStatus(status);
        PromocionResponse response = toResponse(promo);
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    private void aplicarCampos(Promocion promo, PromocionRequest request) {
        if (request.discountType() == PromotionType.PERCENTAGE && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ReglaDeNegocioException("Un descuento porcentual no puede superar 100%");
        }
        if (request.startsAt() != null && request.endsAt() != null && request.startsAt().isAfter(request.endsAt())) {
            throw new ReglaDeNegocioException("La fecha de inicio no puede ser posterior a la de fin");
        }

        Category categoria = null;
        Product producto = null;
        switch (request.scopeType()) {
            case ALL -> { }
            case CATEGORY -> {
                if (request.scopeCategoryId() == null) {
                    throw new ReglaDeNegocioException("Indica la categoría a la que aplica la promoción");
                }
                categoria = categoryRepository.findById(request.scopeCategoryId())
                        .orElseThrow(() -> RecursoNoEncontradoException.de("Categoría", request.scopeCategoryId()));
            }
            case PRODUCT -> {
                if (request.scopeProductId() == null) {
                    throw new ReglaDeNegocioException("Indica el producto al que aplica la promoción");
                }
                producto = productRepository.findById(request.scopeProductId())
                        .orElseThrow(() -> RecursoNoEncontradoException.de("Producto", request.scopeProductId()));
            }
        }

        promo.setCode(request.code());
        promo.setName(request.name());
        promo.setDiscountType(request.discountType());
        promo.setDiscountValue(request.discountValue());
        promo.setScopeType(request.scopeType());
        promo.setScopeCategory(categoria);
        promo.setScopeProduct(producto);
        promo.setStartsAt(request.startsAt());
        promo.setEndsAt(request.endsAt());
        promo.setVisibleOnline(request.visibleOnline());
    }

    private Promocion buscarOFallar(Long id) {
        return promocionRepository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Promoción", id));
    }

    private PromocionResponse toResponse(Promocion promo) {
        return new PromocionResponse(
                promo.getId(), promo.getCode(), promo.getName(), promo.getDiscountType(), promo.getDiscountValue(),
                promo.getScopeType(), promo.getScopeCategory() != null ? promo.getScopeCategory().getId() : null,
                promo.getScopeCategory() != null ? promo.getScopeCategory().getName() : null,
                promo.getScopeProduct() != null ? promo.getScopeProduct().getId() : null,
                promo.getScopeProduct() != null ? promo.getScopeProduct().getName() : null,
                promo.getStartsAt(), promo.getEndsAt(), promo.getStatus(), promo.isVisibleOnline());
    }
}
