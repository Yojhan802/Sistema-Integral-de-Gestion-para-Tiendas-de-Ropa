package com.freestyleperu.aplicacion.catalogo.service;

import com.freestyleperu.aplicacion.catalogo.domain.Category;
import com.freestyleperu.aplicacion.catalogo.domain.Subcategory;
import com.freestyleperu.aplicacion.catalogo.dto.request.SubcategoryRequest;
import com.freestyleperu.aplicacion.catalogo.dto.response.SubcategoryResponse;
import com.freestyleperu.aplicacion.catalogo.repository.CategoryRepository;
import com.freestyleperu.aplicacion.catalogo.repository.SubcategoryRepository;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.exception.RecursoDuplicadoException;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.util.TextNormalizer;
import com.freestyleperu.aplicacion.tienda.service.StoreCatalogSyncService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SubcategoryService {

    private final SubcategoryRepository subcategoryRepository;
    private final CategoryRepository categoryRepository;
    private final StoreCatalogSyncService storeCatalogSyncService;

    public SubcategoryService(SubcategoryRepository subcategoryRepository, CategoryRepository categoryRepository,
            StoreCatalogSyncService storeCatalogSyncService) {
        this.subcategoryRepository = subcategoryRepository;
        this.categoryRepository = categoryRepository;
        this.storeCatalogSyncService = storeCatalogSyncService;
    }

    public List<SubcategoryResponse> listar(Long categoryId) {
        List<Subcategory> subcategories = categoryId == null
                ? subcategoryRepository.findAllByOrderByNameAsc()
                : subcategoryRepository.findAllByCategoryIdOrderByNameAsc(categoryId);
        return subcategories.stream().map(this::toResponse).toList();
    }

    public SubcategoryResponse obtener(Long id) {
        return toResponse(buscarOFallar(id));
    }

    @Transactional
    public SubcategoryResponse crear(SubcategoryRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Categoría", request.categoryId()));
        if (subcategoryRepository.existsByCategoryIdAndNameIgnoreCase(category.getId(), request.name())) {
            throw new RecursoDuplicadoException("Ya existe la subcategoría " + request.name() + " en " + category.getName());
        }
        Subcategory subcategory = new Subcategory();
        subcategory.setCategory(category);
        subcategory.setName(request.name());
        subcategory.setSlug(TextNormalizer.slugify(category.getName() + "-" + request.name()));
        SubcategoryResponse response = toResponse(subcategoryRepository.save(subcategory));
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    @Transactional
    public SubcategoryResponse actualizar(Long id, SubcategoryRequest request) {
        Subcategory subcategory = buscarOFallar(id);
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Categoría", request.categoryId()));

        boolean cambia = !subcategory.getName().equalsIgnoreCase(request.name())
                || !subcategory.getCategory().getId().equals(category.getId());
        if (cambia && subcategoryRepository.existsByCategoryIdAndNameIgnoreCase(category.getId(), request.name())) {
            throw new RecursoDuplicadoException("Ya existe la subcategoría " + request.name() + " en " + category.getName());
        }

        subcategory.setCategory(category);
        subcategory.setName(request.name());
        subcategory.setSlug(TextNormalizer.slugify(category.getName() + "-" + request.name()));
        SubcategoryResponse response = toResponse(subcategory);
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    @Transactional
    public SubcategoryResponse cambiarEstado(Long id, EstadoGeneral status) {
        Subcategory subcategory = buscarOFallar(id);
        subcategory.setStatus(status);
        SubcategoryResponse response = toResponse(subcategory);
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    private Subcategory buscarOFallar(Long id) {
        return subcategoryRepository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Subcategoría", id));
    }

    private SubcategoryResponse toResponse(Subcategory subcategory) {
        return new SubcategoryResponse(
                subcategory.getId(),
                subcategory.getCategory().getId(),
                subcategory.getCategory().getName(),
                subcategory.getName(),
                subcategory.getSlug(),
                subcategory.getStatus());
    }
}
