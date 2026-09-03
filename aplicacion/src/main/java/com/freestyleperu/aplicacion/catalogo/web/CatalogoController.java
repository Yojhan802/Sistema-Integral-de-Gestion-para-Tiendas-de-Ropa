package com.freestyleperu.aplicacion.catalogo.web;

import com.freestyleperu.aplicacion.catalogo.dto.request.AttributeRequest;
import com.freestyleperu.aplicacion.catalogo.dto.request.AttributeValueRequest;
import com.freestyleperu.aplicacion.catalogo.dto.request.BrandRequest;
import com.freestyleperu.aplicacion.catalogo.dto.request.CategoryRequest;
import com.freestyleperu.aplicacion.catalogo.dto.request.SubcategoryRequest;
import com.freestyleperu.aplicacion.catalogo.dto.response.AttributeResponse;
import com.freestyleperu.aplicacion.catalogo.dto.response.AttributeValueResponse;
import com.freestyleperu.aplicacion.catalogo.dto.response.BrandResponse;
import com.freestyleperu.aplicacion.catalogo.dto.response.CategoryResponse;
import com.freestyleperu.aplicacion.catalogo.dto.response.SubcategoryResponse;
import com.freestyleperu.aplicacion.catalogo.service.AtributoService;
import com.freestyleperu.aplicacion.catalogo.service.BrandService;
import com.freestyleperu.aplicacion.catalogo.service.CategoryService;
import com.freestyleperu.aplicacion.catalogo.service.SubcategoryService;
import com.freestyleperu.aplicacion.shared.dto.CambiarEstadoRequest;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.multipart.MultipartFile;

/**
 * Agrupa categorías, subcategorías, marcas y atributos genéricos (color, talla, u otros — ver
 * plan aprobado de atributos genéricos, reemplaza los antiguos catálogos fijos de colores y
 * tallas): mismo patrón CRUD (ver docs/05-api.md §4). Un único controller evita archivos casi
 * idénticos.
 */
@RestController
public class CatalogoController {

    private final CategoryService categoryService;
    private final SubcategoryService subcategoryService;
    private final BrandService brandService;
    private final AtributoService atributoService;

    public CatalogoController(CategoryService categoryService, SubcategoryService subcategoryService,
            BrandService brandService, AtributoService atributoService) {
        this.categoryService = categoryService;
        this.subcategoryService = subcategoryService;
        this.brandService = brandService;
        this.atributoService = atributoService;
    }

    // ---------- Categorías ----------

    @GetMapping("/api/categories")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_CONSULTAR + "')")
    public List<CategoryResponse> listarCategorias() {
        return categoryService.listar();
    }

    @GetMapping("/api/categories/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_CONSULTAR + "')")
    public CategoryResponse obtenerCategoria(@PathVariable Long id) {
        return categoryService.obtener(id);
    }

    @PostMapping("/api/categories")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "')")
    public ResponseEntity<CategoryResponse> crearCategoria(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse creado = categoryService.crear(request);
        return ResponseEntity.created(URI.create("/api/categories/" + creado.id())).body(creado);
    }

    @PutMapping("/api/categories/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "')")
    public CategoryResponse actualizarCategoria(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.actualizar(id, request);
    }

    @PatchMapping("/api/categories/{id}/status")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "')")
    public CategoryResponse cambiarEstadoCategoria(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest request) {
        return categoryService.cambiarEstado(id, request.status());
    }

    @PostMapping("/api/categories/{id}/image")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "')")
    public CategoryResponse actualizarImagenCategoria(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return categoryService.actualizarImagen(id, file);
    }

    @DeleteMapping("/api/categories/{id}/image")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "')")
    public CategoryResponse eliminarImagenCategoria(@PathVariable Long id) {
        return categoryService.eliminarImagen(id);
    }

    // ---------- Subcategorías ----------

    @GetMapping("/api/subcategories")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_CONSULTAR + "')")
    public List<SubcategoryResponse> listarSubcategorias(@RequestParam(required = false) Long categoryId) {
        return subcategoryService.listar(categoryId);
    }

    @GetMapping("/api/subcategories/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_CONSULTAR + "')")
    public SubcategoryResponse obtenerSubcategoria(@PathVariable Long id) {
        return subcategoryService.obtener(id);
    }

    @PostMapping("/api/subcategories")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "')")
    public ResponseEntity<SubcategoryResponse> crearSubcategoria(@Valid @RequestBody SubcategoryRequest request) {
        SubcategoryResponse creado = subcategoryService.crear(request);
        return ResponseEntity.created(URI.create("/api/subcategories/" + creado.id())).body(creado);
    }

    @PutMapping("/api/subcategories/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "')")
    public SubcategoryResponse actualizarSubcategoria(@PathVariable Long id, @Valid @RequestBody SubcategoryRequest request) {
        return subcategoryService.actualizar(id, request);
    }

    @PatchMapping("/api/subcategories/{id}/status")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "')")
    public SubcategoryResponse cambiarEstadoSubcategoria(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest request) {
        return subcategoryService.cambiarEstado(id, request.status());
    }

    // ---------- Marcas ----------

    @GetMapping("/api/brands")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_CONSULTAR + "')")
    public List<BrandResponse> listarMarcas() {
        return brandService.listar();
    }

    @GetMapping("/api/brands/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_CONSULTAR + "')")
    public BrandResponse obtenerMarca(@PathVariable Long id) {
        return brandService.obtener(id);
    }

    @PostMapping("/api/brands")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "')")
    public ResponseEntity<BrandResponse> crearMarca(@Valid @RequestBody BrandRequest request) {
        BrandResponse creado = brandService.crear(request);
        return ResponseEntity.created(URI.create("/api/brands/" + creado.id())).body(creado);
    }

    @PutMapping("/api/brands/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "')")
    public BrandResponse actualizarMarca(@PathVariable Long id, @Valid @RequestBody BrandRequest request) {
        return brandService.actualizar(id, request);
    }

    @PatchMapping("/api/brands/{id}/status")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "')")
    public BrandResponse cambiarEstadoMarca(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest request) {
        return brandService.cambiarEstado(id, request.status());
    }

    // ---------- Atributos genéricos (Color, Talla, u otros — reemplazan color/talla fijos, ver plan aprobado) ----------

    @GetMapping("/api/attributes")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_CONSULTAR + "')")
    public List<AttributeResponse> listarAtributos() {
        return atributoService.listar();
    }

    @GetMapping("/api/attributes/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.PRODUCTOS_CONSULTAR + "')")
    public AttributeResponse obtenerAtributo(@PathVariable Long id) {
        return atributoService.obtener(id);
    }

    @PostMapping("/api/attributes")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "')")
    public ResponseEntity<AttributeResponse> crearAtributo(@Valid @RequestBody AttributeRequest request) {
        AttributeResponse creado = atributoService.crear(request);
        return ResponseEntity.created(URI.create("/api/attributes/" + creado.id())).body(creado);
    }

    @PutMapping("/api/attributes/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "')")
    public AttributeResponse actualizarAtributo(@PathVariable Long id, @Valid @RequestBody AttributeRequest request) {
        return atributoService.actualizar(id, request);
    }

    @PatchMapping("/api/attributes/{id}/status")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "')")
    public AttributeResponse cambiarEstadoAtributo(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest request) {
        return atributoService.cambiarEstado(id, request.status());
    }

    @PostMapping("/api/attributes/{id}/values")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "')")
    public ResponseEntity<AttributeValueResponse> crearValorAtributo(@PathVariable Long id,
            @Valid @RequestBody AttributeValueRequest request) {
        AttributeValueResponse creado = atributoService.crearValor(id, request);
        return ResponseEntity.created(URI.create("/api/attributes/" + id + "/values/" + creado.id())).body(creado);
    }

    @PutMapping("/api/attributes/values/{valueId}")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "')")
    public AttributeValueResponse actualizarValorAtributo(@PathVariable Long valueId,
            @Valid @RequestBody AttributeValueRequest request) {
        return atributoService.actualizarValor(valueId, request);
    }

    @PatchMapping("/api/attributes/values/{valueId}/status")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_EDITAR + "')")
    public AttributeValueResponse cambiarEstadoValorAtributo(@PathVariable Long valueId,
            @Valid @RequestBody CambiarEstadoRequest request) {
        return atributoService.cambiarEstadoValor(valueId, request.status());
    }
}
