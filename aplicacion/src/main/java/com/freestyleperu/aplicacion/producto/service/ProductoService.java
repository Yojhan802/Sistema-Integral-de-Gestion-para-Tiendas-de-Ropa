package com.freestyleperu.aplicacion.producto.service;

import com.freestyleperu.aplicacion.catalogo.domain.Brand;
import com.freestyleperu.aplicacion.catalogo.domain.Category;
import com.freestyleperu.aplicacion.catalogo.domain.Subcategory;
import com.freestyleperu.aplicacion.catalogo.repository.BrandRepository;
import com.freestyleperu.aplicacion.catalogo.repository.CategoryRepository;
import com.freestyleperu.aplicacion.catalogo.repository.SubcategoryRepository;
import com.freestyleperu.aplicacion.producto.domain.Product;
import com.freestyleperu.aplicacion.producto.domain.ProductImage;
import com.freestyleperu.aplicacion.producto.dto.request.ActualizarProductoImagenRequest;
import com.freestyleperu.aplicacion.producto.domain.ProductVariant;
import com.freestyleperu.aplicacion.producto.dto.request.ActualizarProductoRequest;
import com.freestyleperu.aplicacion.producto.dto.request.CrearProductoRequest;
import com.freestyleperu.aplicacion.producto.dto.response.ProductoDetalleResponse;
import com.freestyleperu.aplicacion.producto.dto.response.ProductoImagenResponse;
import com.freestyleperu.aplicacion.producto.dto.response.ProductoResumenResponse;
import com.freestyleperu.aplicacion.producto.mapper.ProductoMapper;
import com.freestyleperu.aplicacion.producto.repository.ProductRepository;
import com.freestyleperu.aplicacion.producto.repository.ProductImageRepository;
import com.freestyleperu.aplicacion.producto.repository.ProductVariantRepository;
import com.freestyleperu.aplicacion.shared.audit.AuditResult;
import com.freestyleperu.aplicacion.shared.audit.AuditService;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.exception.RecursoDuplicadoException;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.exception.ReglaDeNegocioException;
import com.freestyleperu.aplicacion.shared.util.ImageUploadService;
import com.freestyleperu.aplicacion.shared.util.SequenceService;
import com.freestyleperu.aplicacion.shared.util.TextNormalizer;
import com.freestyleperu.aplicacion.tienda.service.StoreCatalogSyncService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class ProductoService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final BrandRepository brandRepository;
    private final SequenceService sequenceService;
    private final ProductoMapper productoMapper;
    private final AuditService auditService;
    private final ImageUploadService imageUploadService;
    private final StoreCatalogSyncService storeCatalogSyncService;

    public ProductoService(ProductRepository productRepository, ProductImageRepository productImageRepository,
            ProductVariantRepository variantRepository,
            CategoryRepository categoryRepository, SubcategoryRepository subcategoryRepository,
            BrandRepository brandRepository, SequenceService sequenceService, ProductoMapper productoMapper,
            AuditService auditService, ImageUploadService imageUploadService,
            StoreCatalogSyncService storeCatalogSyncService) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.variantRepository = variantRepository;
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.brandRepository = brandRepository;
        this.sequenceService = sequenceService;
        this.productoMapper = productoMapper;
        this.auditService = auditService;
        this.imageUploadService = imageUploadService;
        this.storeCatalogSyncService = storeCatalogSyncService;
    }

    public Page<ProductoResumenResponse> listar(String search, Long categoryId, Long subcategoryId, Long brandId,
            EstadoGeneral status, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        Page<Product> page = productRepository.buscar(search, categoryId, subcategoryId, brandId, status, minPrice, maxPrice, pageable);
        return page.map(product -> productoMapper.toResumen(product, variantsDe(product.getId())));
    }

    public ProductoDetalleResponse obtener(Long id) {
        Product product = buscarOFallar(id);
        return productoMapper.toDetalle(product, variantsDe(product.getId()));
    }

    @Transactional
    public ProductoDetalleResponse crear(CrearProductoRequest request) {
        validarPrecios(request.price(), request.promoPrice());
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Categoría", request.categoryId()));
        Subcategory subcategory = resolverSubcategoria(request.subcategoryId(), category);
        Brand brand = resolverMarca(request.brandId());

        Product product = new Product();
        product.setCategory(category);
        product.setSubcategory(subcategory);
        product.setBrand(brand);
        product.setName(request.name());
        product.setDescription(request.description());
        product.setMaterial(request.material());
        product.setFit(request.fit());
        product.setPrice(request.price());
        product.setPromoPrice(request.promoPrice());

        String prefix = TextNormalizer.prefix3(category.getName());
        product.setSku(codigoUnico(request.sku(), () -> sequenceService.next("SKU_" + prefix, prefix, 5), productRepository::existsBySku, "SKU"));
        product.setInternalCode(codigoUnico(request.internalCode(), () -> sequenceService.next("COD_" + prefix, prefix, 4),
                productRepository::existsByInternalCode, "código interno"));

        Product guardado = productRepository.save(product);
        auditService.log("PRODUCTO_CREADO", "PRODUCTO", guardado.getId(), null,
                new Object[] { guardado.getSku(), guardado.getName() }, AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return productoMapper.toDetalle(guardado, List.of());
    }

    @Transactional
    public ProductoDetalleResponse actualizar(Long id, ActualizarProductoRequest request) {
        validarPrecios(request.price(), request.promoPrice());
        Product product = buscarOFallar(id);
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Categoría", request.categoryId()));

        product.setCategory(category);
        product.setSubcategory(resolverSubcategoria(request.subcategoryId(), category));
        product.setBrand(resolverMarca(request.brandId()));
        product.setName(request.name());
        product.setDescription(request.description());
        product.setMaterial(request.material());
        product.setFit(request.fit());
        product.setPrice(request.price());
        product.setPromoPrice(request.promoPrice());
        product.setImageUrl(request.imageUrl());
        if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
            sincronizarImagenPrincipal(product, product.getImageUrl());
        }

        auditService.log("PRODUCTO_ACTUALIZADO", "PRODUCTO", product.getId(), null, request, AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return productoMapper.toDetalle(product, variantsDe(product.getId()));
    }

    @Transactional
    public ProductoResumenResponse cambiarEstado(Long id, EstadoGeneral status) {
        Product product = buscarOFallar(id);
        product.setStatus(status);
        auditService.log("PRODUCTO_CAMBIO_ESTADO", "PRODUCTO", product.getId(), null, status, AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return productoMapper.toResumen(product, variantsDe(product.getId()));
    }

    @Transactional
    public ProductoDetalleResponse actualizarImagen(Long id, MultipartFile file) {
        Product product = buscarOFallar(id);
        product.setImageUrl(imageUploadService.guardar(file, "products"));
        sincronizarImagenPrincipal(product, product.getImageUrl());
        auditService.log("PRODUCTO_IMAGEN_ACTUALIZADA", "PRODUCTO", product.getId(), null, product.getImageUrl(), AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return productoMapper.toDetalle(product, variantsDe(product.getId()));
    }

    public List<ProductoImagenResponse> listarImagenes(Long id) {
        Product product = buscarOFallar(id);
        return productImageRepository.findAllByProductIdOrderBySortOrderAscIdAsc(product.getId()).stream()
                .map(this::toImagenResponse)
                .toList();
    }

    @Transactional
    public ProductoImagenResponse agregarImagen(Long id, MultipartFile file, String altText,
            Integer sortOrder, boolean primary) {
        Product product = buscarOFallar(id);
        String url = imageUploadService.guardar(file, "products");
        boolean first = productImageRepository.countByProductId(id) == 0;
        if (primary || first) {
            quitarPrincipal(id);
        }

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl(url);
        image.setAltText(normalizarAlt(altText));
        image.setSortOrder(Math.max(0, sortOrder == null ? 0 : sortOrder));
        image.setPrimaryImage(primary || first);
        ProductImage saved = productImageRepository.save(image);
        if (saved.isPrimaryImage()) {
            product.setImageUrl(saved.getImageUrl());
        }
        auditService.log("PRODUCTO_GALERIA_IMAGEN_AGREGADA", "PRODUCTO", id, null, saved.getImageUrl(), AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return toImagenResponse(saved);
    }

    @Transactional
    public ProductoImagenResponse marcarImagenPrincipal(Long productId, Long imageId) {
        Product product = buscarOFallar(productId);
        ProductImage image = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Imagen del producto", imageId));
        quitarPrincipal(productId);
        image.setPrimaryImage(true);
        image.setSortOrder(0);
        int siguienteOrden = 1;
        for (ProductImage otra : productImageRepository.findAllByProductIdOrderBySortOrderAscIdAsc(productId)) {
            if (!otra.getId().equals(image.getId())) {
                otra.setSortOrder(siguienteOrden++);
            }
        }
        product.setImageUrl(image.getImageUrl());
        auditService.log("PRODUCTO_GALERIA_IMAGEN_PRINCIPAL", "PRODUCTO", productId, null, image.getImageUrl(), AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return toImagenResponse(image);
    }

    @Transactional
    public ProductoImagenResponse actualizarImagenGaleria(Long productId, Long imageId,
            ActualizarProductoImagenRequest request) {
        buscarOFallar(productId);
        ProductImage image = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Imagen del producto", imageId));
        image.setAltText(normalizarAlt(request.altText()));
        if (request.sortOrder() != null) image.setSortOrder(request.sortOrder());
        auditService.log("PRODUCTO_GALERIA_IMAGEN_ACTUALIZADA", "PRODUCTO", productId, null, imageId, AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return toImagenResponse(image);
    }

    @Transactional
    public void eliminarImagen(Long productId, Long imageId) {
        Product product = buscarOFallar(productId);
        ProductImage image = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Imagen del producto", imageId));
        boolean eraPrincipal = image.isPrimaryImage();
        productImageRepository.delete(image);
        if (eraPrincipal) {
            ProductImage siguiente = productImageRepository.findAllByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                    .findFirst().orElse(null);
            if (siguiente != null) {
                siguiente.setPrimaryImage(true);
                siguiente.setSortOrder(0);
                product.setImageUrl(siguiente.getImageUrl());
            } else {
                product.setImageUrl(null);
            }
        }
        auditService.log("PRODUCTO_GALERIA_IMAGEN_ELIMINADA", "PRODUCTO", productId, image.getImageUrl(), null, AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
    }

    @Transactional
    public ProductoDetalleResponse actualizarGuiaTallas(Long id, MultipartFile file) {
        Product product = buscarOFallar(id);
        product.setSizeGuideImageUrl(imageUploadService.guardar(file, "size-guides"));
        auditService.log("PRODUCTO_GUIA_TALLAS_ACTUALIZADA", "PRODUCTO", product.getId(), null, product.getSizeGuideImageUrl(), AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return productoMapper.toDetalle(product, variantsDe(product.getId()));
    }

    private void validarPrecios(BigDecimal price, BigDecimal promoPrice) {
        if (promoPrice != null && promoPrice.compareTo(price) >= 0) {
            throw new ReglaDeNegocioException("El precio promocional debe ser menor que el precio regular");
        }
    }

    private Subcategory resolverSubcategoria(Long subcategoryId, Category category) {
        if (subcategoryId == null) {
            return null;
        }
        Subcategory subcategory = subcategoryRepository.findById(subcategoryId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Subcategoría", subcategoryId));
        if (!subcategory.getCategory().getId().equals(category.getId())) {
            throw new ReglaDeNegocioException("La subcategoría no pertenece a la categoría seleccionada");
        }
        return subcategory;
    }

    private Brand resolverMarca(Long brandId) {
        if (brandId == null) {
            return null;
        }
        return brandRepository.findById(brandId).orElseThrow(() -> RecursoNoEncontradoException.de("Marca", brandId));
    }

    private String codigoUnico(String provisto, java.util.function.Supplier<String> generador,
            java.util.function.Predicate<String> existe, String etiqueta) {
        if (provisto == null || provisto.isBlank()) {
            return generador.get();
        }
        if (existe.test(provisto)) {
            throw new RecursoDuplicadoException("Ya existe un producto con ese " + etiqueta + ": " + provisto);
        }
        return provisto;
    }

    private List<ProductVariant> variantsDe(Long productId) {
        return variantRepository.findAllByProductId(productId);
    }

    private void sincronizarImagenPrincipal(Product product, String url) {
        ProductImage principal = productImageRepository.findAllByProductIdOrderBySortOrderAscIdAsc(product.getId()).stream()
                .filter(ProductImage::isPrimaryImage)
                .findFirst().orElse(null);
        if (principal == null) {
            principal = new ProductImage();
            principal.setProduct(product);
            principal.setSortOrder(0);
            principal.setPrimaryImage(true);
        }
        principal.setImageUrl(url);
        if (principal.getAltText() == null || principal.getAltText().isBlank()) {
            principal.setAltText(product.getName());
        }
        productImageRepository.save(principal);
    }

    private void quitarPrincipal(Long productId) {
        productImageRepository.findAllByProductIdOrderBySortOrderAscIdAsc(productId)
                .forEach(image -> image.setPrimaryImage(false));
    }

    private String normalizarAlt(String altText) {
        return altText == null || altText.isBlank() ? null : altText.trim();
    }

    private ProductoImagenResponse toImagenResponse(ProductImage image) {
        return new ProductoImagenResponse(image.getId(), image.getProduct().getId(), image.getImageUrl(),
                image.getAltText(), image.getSortOrder(), image.isPrimaryImage());
    }

    private Product buscarOFallar(Long id) {
        return productRepository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Producto", id));
    }
}
