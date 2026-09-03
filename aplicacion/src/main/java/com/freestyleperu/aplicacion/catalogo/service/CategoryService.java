package com.freestyleperu.aplicacion.catalogo.service;

import com.freestyleperu.aplicacion.catalogo.domain.Category;
import com.freestyleperu.aplicacion.catalogo.dto.request.CategoryRequest;
import com.freestyleperu.aplicacion.catalogo.dto.response.CategoryResponse;
import com.freestyleperu.aplicacion.catalogo.repository.CategoryRepository;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.exception.RecursoDuplicadoException;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.util.TextNormalizer;
import com.freestyleperu.aplicacion.shared.util.ImageUploadService;
import com.freestyleperu.aplicacion.tienda.service.StoreCatalogSyncService;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ImageUploadService imageUploadService;
    private final StoreCatalogSyncService storeCatalogSyncService;

    public CategoryService(CategoryRepository categoryRepository, ImageUploadService imageUploadService,
            StoreCatalogSyncService storeCatalogSyncService) {
        this.categoryRepository = categoryRepository;
        this.imageUploadService = imageUploadService;
        this.storeCatalogSyncService = storeCatalogSyncService;
    }

    public List<CategoryResponse> listar() {
        return categoryRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    public CategoryResponse obtener(Long id) {
        return toResponse(buscarOFallar(id));
    }

    @Transactional
    @CacheEvict(cacheNames = "storeCatalogCategories", keyGenerator = "tenantAwareKeyGenerator")
    public CategoryResponse crear(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new RecursoDuplicadoException("Ya existe una categoría llamada " + request.name());
        }
        Category category = new Category();
        category.setName(request.name());
        category.setSlug(slugUnico(request.name()));
        CategoryResponse response = toResponse(categoryRepository.save(category));
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    @Transactional
    @CacheEvict(cacheNames = "storeCatalogCategories", keyGenerator = "tenantAwareKeyGenerator")
    public CategoryResponse actualizar(Long id, CategoryRequest request) {
        Category category = buscarOFallar(id);
        if (!category.getName().equalsIgnoreCase(request.name()) && categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new RecursoDuplicadoException("Ya existe una categoría llamada " + request.name());
        }
        category.setName(request.name());
        CategoryResponse response = toResponse(category);
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    @Transactional
    @CacheEvict(cacheNames = "storeCatalogCategories", keyGenerator = "tenantAwareKeyGenerator")
    public CategoryResponse cambiarEstado(Long id, EstadoGeneral status) {
        Category category = buscarOFallar(id);
        category.setStatus(status);
        CategoryResponse response = toResponse(category);
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    @Transactional
    @CacheEvict(cacheNames = "storeCatalogCategories", keyGenerator = "tenantAwareKeyGenerator")
    public CategoryResponse actualizarImagen(Long id, MultipartFile file) {
        Category category = buscarOFallar(id);
        category.setImageUrl(imageUploadService.guardar(file, "categories"));
        CategoryResponse response = toResponse(category);
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    @Transactional
    @CacheEvict(cacheNames = "storeCatalogCategories", keyGenerator = "tenantAwareKeyGenerator")
    public CategoryResponse eliminarImagen(Long id) {
        Category category = buscarOFallar(id);
        category.setImageUrl(null);
        CategoryResponse response = toResponse(category);
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    private String slugUnico(String name) {
        String base = TextNormalizer.slugify(name);
        String slug = base;
        int suffix = 2;
        while (categoryRepository.existsBySlug(slug)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }

    private Category buscarOFallar(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Categoría", id));
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug(), category.getImageUrl(), category.getStatus());
    }
}
