package com.freestyleperu.aplicacion.producto.service;

import com.freestyleperu.aplicacion.catalogo.domain.Attribute;
import com.freestyleperu.aplicacion.catalogo.domain.AttributeValue;
import com.freestyleperu.aplicacion.catalogo.repository.AttributeValueRepository;
import com.freestyleperu.aplicacion.producto.domain.Product;
import com.freestyleperu.aplicacion.producto.domain.ProductAttribute;
import com.freestyleperu.aplicacion.producto.domain.ProductVariant;
import com.freestyleperu.aplicacion.producto.domain.VariantAttributeValue;
import com.freestyleperu.aplicacion.producto.dto.request.ActualizarVarianteRequest;
import com.freestyleperu.aplicacion.producto.dto.request.CrearVarianteRequest;
import com.freestyleperu.aplicacion.producto.dto.request.GenerarVariantesRequest;
import com.freestyleperu.aplicacion.producto.dto.response.VarianteBusquedaResponse;
import com.freestyleperu.aplicacion.producto.dto.response.VarianteResponse;
import com.freestyleperu.aplicacion.producto.mapper.VarianteMapper;
import com.freestyleperu.aplicacion.producto.repository.ProductAttributeRepository;
import com.freestyleperu.aplicacion.producto.repository.ProductRepository;
import com.freestyleperu.aplicacion.producto.repository.ProductVariantRepository;
import com.freestyleperu.aplicacion.shared.audit.AuditResult;
import com.freestyleperu.aplicacion.shared.audit.AuditService;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.exception.RecursoDuplicadoException;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.exception.StockInsuficienteException;
import com.freestyleperu.aplicacion.shared.util.Ean13Generator;
import com.freestyleperu.aplicacion.shared.util.TextNormalizer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VarianteService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final AttributeValueRepository attributeValueRepository;
    private final ProductAttributeRepository productAttributeRepository;
    private final Ean13Generator ean13Generator;
    private final VarianteMapper varianteMapper;
    private final AuditService auditService;
    private final EntityManager entityManager;

    public VarianteService(ProductVariantRepository variantRepository, ProductRepository productRepository,
            AttributeValueRepository attributeValueRepository, ProductAttributeRepository productAttributeRepository,
            Ean13Generator ean13Generator, VarianteMapper varianteMapper, AuditService auditService,
            EntityManager entityManager) {
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.attributeValueRepository = attributeValueRepository;
        this.productAttributeRepository = productAttributeRepository;
        this.ean13Generator = ean13Generator;
        this.varianteMapper = varianteMapper;
        this.entityManager = entityManager;
        this.auditService = auditService;
    }

    public List<VarianteResponse> listarPorProducto(Long productId) {
        Map<Long, Short> posiciones = posicionesDelProducto(productId);
        return variantRepository.findAllByProductId(productId).stream()
                .sorted(Comparator
                        .<ProductVariant, List<Short>>comparing(v -> ordenSortKey(v, posiciones), VarianteService::compararListasOrden)
                        .thenComparing(ProductVariant::getId))
                .map(v -> varianteMapper.toResponse(v, posiciones))
                .toList();
    }

    @Transactional
    public VarianteResponse crear(Long productId, CrearVarianteRequest request) {
        Product product = buscarProductoOFallar(productId);
        List<AttributeValue> valores = buscarValoresOFallar(request.attributeValueIds());

        ProductVariant variant = construirVariante(product, valores, request.sku(), request.barcode(),
                request.generateBarcode(), request.stock(), request.minStock());

        ProductVariant guardado = variantRepository.save(variant);
        auditService.log("VARIANTE_CREADA", "VARIANTE", guardado.getId(), null,
                new Object[] { guardado.getSku(), guardado.getBarcode() }, AuditResult.SUCCESS);
        return varianteMapper.toResponse(guardado, posicionesDelProducto(productId));
    }

    /**
     * Genera el producto cartesiano N-ario de {@code attributeValueIdGroups} para un producto.
     * Las combinaciones que ya existan se omiten sin fallar (semántica idempotente, ver
     * docs/05-api.md §6).
     */
    @Transactional
    public List<VarianteResponse> generarMatriz(Long productId, GenerarVariantesRequest request) {
        Product product = buscarProductoOFallar(productId);
        List<List<AttributeValue>> grupos = request.attributeValueIdGroups().stream()
                .map(this::buscarValoresOFallar)
                .toList();

        int minStock = request.minStock() != null ? request.minStock() : 0;
        List<ProductVariant> creadas = new ArrayList<>();
        for (List<AttributeValue> combinacion : productoCartesiano(grupos)) {
            if (variantRepository.existsByProductIdAndCombinationHash(productId, calcularHash(combinacion))) {
                continue;
            }
            ProductVariant variant = construirVariante(product, combinacion, null, null,
                    request.generateBarcodes(), 0, minStock);
            creadas.add(variantRepository.save(variant));
        }

        auditService.log("VARIANTES_GENERADAS", "PRODUCTO", productId, null, creadas.size(), AuditResult.SUCCESS);
        Map<Long, Short> posiciones = posicionesDelProducto(productId);
        return creadas.stream().map(v -> varianteMapper.toResponse(v, posiciones)).toList();
    }

    @Transactional
    public VarianteResponse actualizar(Long id, ActualizarVarianteRequest request) {
        ProductVariant variant = buscarVarianteOFallar(id);
        variant.setMinStock(request.minStock());
        auditService.log("VARIANTE_ACTUALIZADA", "VARIANTE", variant.getId(), null, request, AuditResult.SUCCESS);
        return varianteMapper.toResponse(variant, posicionesDelProducto(variant.getProduct().getId()));
    }

    @Transactional
    public VarianteResponse cambiarEstado(Long id, EstadoGeneral status) {
        ProductVariant variant = buscarVarianteOFallar(id);
        variant.setStatus(status);
        auditService.log("VARIANTE_CAMBIO_ESTADO", "VARIANTE", variant.getId(), null, status, AuditResult.SUCCESS);
        return varianteMapper.toResponse(variant, posicionesDelProducto(variant.getProduct().getId()));
    }

    @Transactional
    public VarianteResponse asignarCodigoBarras(Long id) {
        ProductVariant variant = buscarVarianteOFallar(id);
        variant.setBarcode(ean13Generator.generar());
        auditService.log("BARCODE_ASIGNADO", "VARIANTE", variant.getId(), null, variant.getBarcode(), AuditResult.SUCCESS);
        return varianteMapper.toResponse(variant, posicionesDelProducto(variant.getProduct().getId()));
    }

    public VarianteBusquedaResponse buscarPorCodigoBarras(String barcode) {
        ProductVariant variant = variantRepository.findByBarcode(barcode)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró ninguna variante con el código " + barcode));
        return varianteMapper.toBusquedaResponse(variant);
    }

    public List<VarianteBusquedaResponse> buscar(String query) {
        return variantRepository.buscar(query).stream().map(varianteMapper::toBusquedaResponse).toList();
    }

    private ProductVariant construirVariante(Product product, List<AttributeValue> valores, String skuProvisto,
            String barcodeProvisto, boolean generarBarcode, Integer stock, Integer minStock) {
        Map<Long, Short> posiciones = asegurarProductAttributes(product, valores);
        List<AttributeValue> ordenados = ordenarPorPosicion(valores, posiciones);
        String combinationHash = calcularHash(valores);
        String variantLabel = calcularLabel(ordenados);

        if (variantRepository.existsByProductIdAndCombinationHash(product.getId(), combinationHash)) {
            throw new RecursoDuplicadoException("Ya existe la variante " + variantLabel + " para este producto");
        }

        String sku = skuProvisto != null && !skuProvisto.isBlank() ? skuProvisto : skuGenerado(product, ordenados);
        if (variantRepository.existsBySku(sku)) {
            throw new RecursoDuplicadoException("Ya existe una variante con el SKU " + sku);
        }

        String barcode = barcodeProvisto;
        if (barcode != null && !barcode.isBlank()) {
            if (variantRepository.existsByBarcode(barcode)) {
                throw new RecursoDuplicadoException("El código de barras " + barcode + " ya está registrado");
            }
        } else if (generarBarcode) {
            barcode = ean13Generator.generar();
        } else {
            barcode = null;
        }

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(sku);
        variant.setBarcode(barcode);
        variant.setStock(stock != null ? stock : 0);
        variant.setMinStock(minStock != null ? minStock : 0);
        variant.setVariantLabel(variantLabel);
        variant.setCombinationHash(combinationHash);
        for (AttributeValue valor : valores) {
            VariantAttributeValue vav = new VariantAttributeValue();
            vav.setVariant(variant);
            vav.setAttributeValue(valor);
            variant.getAttributeValues().add(vav);
        }
        return variant;
    }

    /** Producto cartesiano de N listas — generaliza el doble for (colores × tallas) de antes. */
    private List<List<AttributeValue>> productoCartesiano(List<List<AttributeValue>> grupos) {
        List<List<AttributeValue>> resultado = new ArrayList<>();
        resultado.add(new ArrayList<>());
        for (List<AttributeValue> grupo : grupos) {
            List<List<AttributeValue>> siguiente = new ArrayList<>();
            for (List<AttributeValue> parcial : resultado) {
                for (AttributeValue valor : grupo) {
                    List<AttributeValue> combinacion = new ArrayList<>(parcial);
                    combinacion.add(valor);
                    siguiente.add(combinacion);
                }
            }
            resultado = siguiente;
        }
        return resultado;
    }

    /**
     * Registra en {@code product_attributes} cualquier atributo entre {@code valores} que el
     * producto todavía no tenga configurado (se le asigna la siguiente posición libre) — así el
     * primer alta de variante de un producto nuevo define qué atributos usa, sin necesitar un
     * paso de configuración aparte. Devuelve el mapa {@code attributeId -> position} resultante.
     */
    private Map<Long, Short> asegurarProductAttributes(Product product, List<AttributeValue> valores) {
        List<ProductAttribute> existentes = productAttributeRepository.findAllByProductIdOrderByPositionAsc(product.getId());
        Map<Long, Short> posiciones = new LinkedHashMap<>();
        short maxPosicion = 0;
        for (ProductAttribute pa : existentes) {
            posiciones.put(pa.getAttribute().getId(), pa.getPosition());
            maxPosicion = (short) Math.max(maxPosicion, pa.getPosition());
        }
        LinkedHashSet<Attribute> atributosDelPedido = valores.stream()
                .map(AttributeValue::getAttribute)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Attribute atributo : atributosDelPedido) {
            if (!posiciones.containsKey(atributo.getId())) {
                maxPosicion++;
                ProductAttribute nuevo = new ProductAttribute();
                nuevo.setProduct(product);
                nuevo.setAttribute(atributo);
                nuevo.setPosition(maxPosicion);
                productAttributeRepository.save(nuevo);
                posiciones.put(atributo.getId(), maxPosicion);
            }
        }
        return posiciones;
    }

    private Map<Long, Short> posicionesDelProducto(Long productId) {
        return productAttributeRepository.findAllByProductIdOrderByPositionAsc(productId).stream()
                .collect(Collectors.toMap(pa -> pa.getAttribute().getId(), ProductAttribute::getPosition));
    }

    private List<AttributeValue> ordenarPorPosicion(List<AttributeValue> valores, Map<Long, Short> posiciones) {
        return valores.stream()
                .sorted(Comparator.comparing(v -> posiciones.get(v.getAttribute().getId())))
                .toList();
    }

    /** attribute_value_id ordenados ascendente, unidos por coma — mismo criterio que V56/Fase 0. */
    private String calcularHash(List<AttributeValue> valores) {
        String unido = valores.stream()
                .map(AttributeValue::getId)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(unido.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private String calcularLabel(List<AttributeValue> valoresOrdenadosPorPosicion) {
        return valoresOrdenadosPorPosicion.stream().map(AttributeValue::getValue).collect(Collectors.joining(" / "));
    }

    private String skuGenerado(Product product, List<AttributeValue> valoresOrdenadosPorPosicion) {
        StringBuilder sku = new StringBuilder(product.getSku());
        for (AttributeValue valor : valoresOrdenadosPorPosicion) {
            sku.append('-').append(segmentoSku(valor.getValue()));
        }
        return sku.toString();
    }

    private String segmentoSku(String valor) {
        String limpio = valor.trim();
        return limpio.length() <= 4 ? limpio.toUpperCase() : TextNormalizer.prefix3(limpio).toUpperCase();
    }

    private List<Short> ordenSortKey(ProductVariant variant, Map<Long, Short> posiciones) {
        return variant.getAttributeValues().stream()
                .sorted(Comparator.comparing(vav -> posiciones.get(vav.getAttributeValue().getAttribute().getId())))
                .map(vav -> vav.getAttributeValue().getSortOrder())
                .toList();
    }

    private static int compararListasOrden(List<Short> a, List<Short> b) {
        int n = Math.min(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            int cmp = a.get(i).compareTo(b.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(a.size(), b.size());
    }

    private List<AttributeValue> buscarValoresOFallar(List<Long> ids) {
        List<AttributeValue> valores = attributeValueRepository.findAllById(ids);
        if (valores.size() != ids.size()) {
            throw new RecursoNoEncontradoException("Uno o más valores de atributo no existen");
        }
        return valores;
    }

    private Product buscarProductoOFallar(Long id) {
        return productRepository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Producto", id));
    }

    private ProductVariant buscarVarianteOFallar(Long id) {
        return variantRepository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Variante", id));
    }

    /**
     * Único punto autorizado a escribir {@code product_variants.stock} (RN-05,
     * RN-06). Nunca se expone por un controller: solo {@code InventarioService}
     * lo invoca, siempre junto con el movimiento que justifica el cambio, y
     * dentro de la misma transacción (por eso exige {@code MANDATORY}).
     * El bloqueo pesimista evita que dos ajustes concurrentes sobre la misma
     * variante calculen el mismo "stock antes" a la vez.
     *
     * {@code entityManager.refresh(..., PESSIMISTIC_WRITE)} es intencional en vez de
     * {@code variantRepository.lockById(...)}: si la variante ya estaba cargada en el
     * contexto de persistencia de esta transacción (p. ej. VentaService la lee sin
     * bloqueo antes, para calcular precios), una simple query con @Lock adquiere el
     * lock en la fila pero Hibernate devuelve la instancia ya gestionada tal cual
     * estaba en caché — con el stock desactualizado — sin refrescar sus campos.
     * refresh() fuerza a releer el valor real de la fila ya bloqueada, que es
     * exactamente lo que se necesita para que el chequeo de stock sea correcto bajo
     * concurrencia real (confirmado con una prueba de carrera real: sin refresh(),
     * 2 ventas simultáneas de la última unidad de stock se completaban ambas).
     *
     * El flush() previo es igual de necesario: si esta misma variante ya se ajustó
     * antes dentro de la MISMA transacción (ej. un combo aplicado dos veces sobre el
     * mismo producto, o dos líneas distintas de la misma venta), ese cambio anterior
     * todavía no se volcó a la fila — sin flush(), refresh() lo descartaría y releería
     * el valor viejo de la BD, perdiendo silenciosamente el primer descuento.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AjusteStockResultado ajustarStock(Long variantId, int delta) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Variante", variantId));
        entityManager.flush();
        entityManager.refresh(variant, LockModeType.PESSIMISTIC_WRITE);
        int stockBefore = variant.getStock();
        int stockAfter = stockBefore + delta;
        if (stockAfter < 0) {
            throw new StockInsuficienteException(
                    "Stock insuficiente para " + variant.getProduct().getName() + " " + variant.getVariantLabel()
                            + ". Disponible: " + stockBefore + ", solicitado: " + (-delta));
        }
        variant.setStock(stockAfter);
        return new AjusteStockResultado(variant.getId(), variant.getSku(), variant.getProduct().getName(), stockBefore, stockAfter);
    }

    /** Proxy liviano (sin cargar datos) para que otros módulos enlacen la FK en sus propias entidades. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProductVariant referencia(Long id) {
        return variantRepository.getReferenceById(id);
    }
}
