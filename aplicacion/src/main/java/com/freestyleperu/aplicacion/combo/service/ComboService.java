package com.freestyleperu.aplicacion.combo.service;

import com.freestyleperu.aplicacion.catalogo.domain.Brand;
import com.freestyleperu.aplicacion.catalogo.domain.Category;
import com.freestyleperu.aplicacion.catalogo.repository.BrandRepository;
import com.freestyleperu.aplicacion.catalogo.repository.CategoryRepository;
import com.freestyleperu.aplicacion.combo.domain.Combo;
import com.freestyleperu.aplicacion.combo.domain.ComboItem;
import com.freestyleperu.aplicacion.combo.domain.ComboSelectorType;
import com.freestyleperu.aplicacion.combo.dto.request.ComboItemRequest;
import com.freestyleperu.aplicacion.combo.dto.request.ComboRequest;
import com.freestyleperu.aplicacion.combo.dto.response.ComboItemResponse;
import com.freestyleperu.aplicacion.combo.dto.response.ComboResponse;
import com.freestyleperu.aplicacion.combo.repository.ComboRepository;
import com.freestyleperu.aplicacion.producto.domain.Product;
import com.freestyleperu.aplicacion.producto.repository.ProductRepository;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.exception.ReglaDeNegocioException;
import com.freestyleperu.aplicacion.tienda.service.StoreCatalogSyncService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ComboService {

    private final ComboRepository comboRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final StoreCatalogSyncService storeCatalogSyncService;

    public ComboService(ComboRepository comboRepository, ProductRepository productRepository,
            CategoryRepository categoryRepository, BrandRepository brandRepository,
            StoreCatalogSyncService storeCatalogSyncService) {
        this.comboRepository = comboRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.storeCatalogSyncService = storeCatalogSyncService;
    }

    public List<ComboResponse> listar() {
        return comboRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    public ComboResponse obtener(Long id) {
        return toResponse(buscarOFallar(id));
    }

    /**
     * Candidato para armar un combo: una línea de venta ya resuelta a su
     * producto/categoría/marca. {@code index} referencia la posición del
     * candidato en la lista de entrada, para que quien llama pueda mapear el
     * resultado de vuelta a sus propias líneas.
     */
    public record CandidatoCombo(int index, Long variantId, Long productId, Long categoryId, Long brandId, int cantidad) {
    }

    /** Usado por ReservaService al completar varias separaciones juntas — necesita revisar todos los combos activos. */
    public List<Combo> listarActivos() {
        return comboRepository.findAllByOrderByNameAsc().stream().filter(c -> c.getStatus() == EstadoGeneral.ACTIVE).toList();
    }

    /**
     * Reparte los candidatos entre las líneas del combo con el mismo
     * algoritmo greedy en ambos sentidos de uso (venta y detección
     * automática, RN-28): primero las líneas `PRODUCT` (más restrictivas),
     * luego las `CATEGORY`. Devuelve cuánto se consumió de cada candidato
     * (mismo orden que la entrada) si el combo se pudo completar, o vacío si
     * falta algo — el llamador decide si tolera que sobren candidatos sin
     * consumir (detección automática) o no (venta, que exige cuadre exacto).
     */
    public Optional<int[]> consumirCandidatos(Combo combo, List<CandidatoCombo> candidatos) {
        int[] restante = candidatos.stream().mapToInt(CandidatoCombo::cantidad).toArray();
        List<ComboItem> itemsOrdenados = combo.getItems().stream()
                .sorted(Comparator.comparing(ci -> ci.getSelectorType() == ComboSelectorType.PRODUCT ? 0 : 1))
                .toList();
        for (ComboItem comboItem : itemsOrdenados) {
            int necesario = comboItem.getQuantity();
            for (int i = 0; i < candidatos.size() && necesario > 0; i++) {
                if (restante[i] <= 0 || !coincideConComboItem(candidatos.get(i), comboItem)) {
                    continue;
                }
                int tomar = Math.min(necesario, restante[i]);
                restante[i] -= tomar;
                necesario -= tomar;
            }
            if (necesario > 0) {
                return Optional.empty();
            }
        }
        int[] consumido = new int[candidatos.size()];
        for (int i = 0; i < candidatos.size(); i++) {
            consumido[i] = candidatos.get(i).cantidad() - restante[i];
        }
        return Optional.of(consumido);
    }

    private boolean coincideConComboItem(CandidatoCombo candidato, ComboItem comboItem) {
        return switch (comboItem.getSelectorType()) {
            case PRODUCT -> candidato.productId().equals(comboItem.getProduct().getId());
            case CATEGORY -> candidato.categoryId().equals(comboItem.getCategory().getId())
                    && (comboItem.getBrand() == null || comboItem.getBrand().getId().equals(candidato.brandId()));
        };
    }

    /** Usado por VentaService al vender un combo — solo combos activos se pueden vender. */
    public Combo obtenerActivoOFallar(Long id) {
        Combo combo = buscarOFallar(id);
        if (combo.getStatus() != EstadoGeneral.ACTIVE) {
            throw new RecursoNoEncontradoException("El combo " + combo.getName() + " no está disponible");
        }
        return combo;
    }

    @Transactional
    public ComboResponse crear(ComboRequest request) {
        if (comboRepository.existsByCode(request.code())) {
            throw new ReglaDeNegocioException("Ya existe un combo con el código " + request.code());
        }
        Combo combo = new Combo();
        combo.setCode(request.code());
        combo.setName(request.name());
        combo.setPrice(request.price());
        combo.setItems(construirItems(combo, request.items()));
        validarPrecio(combo);
        ComboResponse response = toResponse(comboRepository.save(combo));
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    @Transactional
    public ComboResponse actualizar(Long id, ComboRequest request) {
        Combo combo = buscarOFallar(id);
        if (!combo.getCode().equals(request.code()) && comboRepository.existsByCode(request.code())) {
            throw new ReglaDeNegocioException("Ya existe un combo con el código " + request.code());
        }
        combo.setCode(request.code());
        combo.setName(request.name());
        combo.setPrice(request.price());
        combo.getItems().clear();
        combo.getItems().addAll(construirItems(combo, request.items()));
        validarPrecio(combo);
        ComboResponse response = toResponse(combo);
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    @Transactional
    public ComboResponse cambiarEstado(Long id, EstadoGeneral status) {
        Combo combo = buscarOFallar(id);
        combo.setStatus(status);
        ComboResponse response = toResponse(combo);
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    private List<ComboItem> construirItems(Combo combo, List<ComboItemRequest> items) {
        List<ComboItem> resultado = new ArrayList<>();
        for (ComboItemRequest itemRequest : items) {
            ComboItem item = new ComboItem();
            item.setCombo(combo);
            item.setSelectorType(itemRequest.selectorType());
            item.setQuantity(itemRequest.quantity());
            switch (itemRequest.selectorType()) {
                case PRODUCT -> {
                    if (itemRequest.productId() == null) {
                        throw new ReglaDeNegocioException("Indica el producto de esta línea del combo");
                    }
                    Product product = productRepository.findById(itemRequest.productId())
                            .orElseThrow(() -> RecursoNoEncontradoException.de("Producto", itemRequest.productId()));
                    item.setProduct(product);
                }
                case CATEGORY -> {
                    if (itemRequest.categoryId() == null) {
                        throw new ReglaDeNegocioException("Indica la categoría de esta línea del combo");
                    }
                    Category category = categoryRepository.findById(itemRequest.categoryId())
                            .orElseThrow(() -> RecursoNoEncontradoException.de("Categoría", itemRequest.categoryId()));
                    item.setCategory(category);
                    if (itemRequest.brandId() != null) {
                        Brand brand = brandRepository.findById(itemRequest.brandId())
                                .orElseThrow(() -> RecursoNoEncontradoException.de("Marca", itemRequest.brandId()));
                        item.setBrand(brand);
                    }
                }
            }
            resultado.add(item);
        }
        return resultado;
    }

    /**
     * El combo debe costar menos que comprar cada producto por separado. Solo
     * se puede verificar con exactitud cuando todas las líneas son de producto
     * específico — una línea por categoría ("4 polos de esta marca") no fija
     * qué productos concretos se van a elegir, así que ese chequeo se deja al
     * momento de la venta ({@code VentaService}), donde sí se sabe exactamente
     * qué se vendió.
     */
    private void validarPrecio(Combo combo) {
        boolean todosPorProducto = combo.getItems().stream().allMatch(i -> i.getSelectorType() == ComboSelectorType.PRODUCT);
        if (!todosPorProducto) {
            return;
        }
        BigDecimal normalTotal = calcularTotalNormal(combo);
        if (combo.getPrice().compareTo(normalTotal) >= 0) {
            throw new ReglaDeNegocioException(
                    "El precio del combo (" + combo.getPrice() + ") debe ser menor a la suma de sus productos (" + normalTotal + ")");
        }
    }

    private BigDecimal calcularTotalNormal(Combo combo) {
        BigDecimal total = BigDecimal.ZERO;
        for (ComboItem item : combo.getItems()) {
            if (item.getSelectorType() != ComboSelectorType.PRODUCT) {
                return null;
            }
            BigDecimal precioUnitario = precioVigente(item.getProduct());
            total = total.add(precioUnitario.multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        return total;
    }

    private BigDecimal precioVigente(Product product) {
        return product.getPromoPrice() != null ? product.getPromoPrice() : product.getPrice();
    }

    private Combo buscarOFallar(Long id) {
        return comboRepository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Combo", id));
    }

    private ComboResponse toResponse(Combo combo) {
        BigDecimal normalTotal = calcularTotalNormal(combo);
        List<ComboItemResponse> items = combo.getItems().stream().map(this::toItemResponse).toList();
        return new ComboResponse(
                combo.getId(), combo.getCode(), combo.getName(), combo.getPrice(),
                normalTotal, normalTotal != null ? normalTotal.subtract(combo.getPrice()) : null, combo.getStatus(), items);
    }

    private ComboItemResponse toItemResponse(ComboItem item) {
        return new ComboItemResponse(
                item.getSelectorType(),
                item.getProduct() != null ? item.getProduct().getId() : null,
                item.getProduct() != null ? item.getProduct().getName() : null,
                item.getCategory() != null ? item.getCategory().getId() : null,
                item.getCategory() != null ? item.getCategory().getName() : null,
                item.getBrand() != null ? item.getBrand().getId() : null,
                item.getBrand() != null ? item.getBrand().getName() : null,
                item.getQuantity());
    }
}
