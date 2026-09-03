package com.freestyleperu.aplicacion.combo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.freestyleperu.aplicacion.caja.domain.CashRegister;
import com.freestyleperu.aplicacion.caja.dto.request.AbrirCajaRequest;
import com.freestyleperu.aplicacion.caja.dto.response.SesionCajaResponse;
import com.freestyleperu.aplicacion.caja.repository.CashRegisterRepository;
import com.freestyleperu.aplicacion.caja.service.CajaService;
import com.freestyleperu.aplicacion.catalogo.domain.AttributeValue;
import com.freestyleperu.aplicacion.catalogo.domain.Brand;
import com.freestyleperu.aplicacion.catalogo.domain.Category;
import com.freestyleperu.aplicacion.catalogo.repository.BrandRepository;
import com.freestyleperu.aplicacion.catalogo.repository.CategoryRepository;
import com.freestyleperu.aplicacion.combo.domain.ComboSelectorType;
import com.freestyleperu.aplicacion.combo.dto.request.ComboItemRequest;
import com.freestyleperu.aplicacion.combo.dto.request.ComboRequest;
import com.freestyleperu.aplicacion.combo.dto.response.ComboResponse;
import com.freestyleperu.aplicacion.combo.service.ComboService;
import com.freestyleperu.aplicacion.inventario.domain.Branch;
import com.freestyleperu.aplicacion.inventario.domain.Warehouse;
import com.freestyleperu.aplicacion.inventario.repository.BranchRepository;
import com.freestyleperu.aplicacion.inventario.repository.WarehouseRepository;
import com.freestyleperu.aplicacion.pago.domain.PaymentMethod;
import com.freestyleperu.aplicacion.pago.domain.PaymentMethodType;
import com.freestyleperu.aplicacion.pago.repository.PaymentMethodRepository;
import com.freestyleperu.aplicacion.producto.dto.request.CrearProductoRequest;
import com.freestyleperu.aplicacion.producto.dto.request.CrearVarianteRequest;
import com.freestyleperu.aplicacion.producto.AtributoTestFixture;
import com.freestyleperu.aplicacion.producto.dto.response.ProductoDetalleResponse;
import com.freestyleperu.aplicacion.producto.dto.response.VarianteResponse;
import com.freestyleperu.aplicacion.producto.repository.ProductVariantRepository;
import com.freestyleperu.aplicacion.producto.service.ProductoService;
import com.freestyleperu.aplicacion.producto.service.VarianteService;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.exception.ReglaDeNegocioException;
import com.freestyleperu.aplicacion.usuario.domain.Rol;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import com.freestyleperu.aplicacion.usuario.domain.UsuarioEstado;
import com.freestyleperu.aplicacion.usuario.repository.RolRepository;
import com.freestyleperu.aplicacion.usuario.repository.UsuarioRepository;
import com.freestyleperu.aplicacion.venta.dto.request.CrearVentaRequest;
import com.freestyleperu.aplicacion.venta.dto.request.ItemVentaRequest;
import com.freestyleperu.aplicacion.venta.dto.request.PagoVentaRequest;
import com.freestyleperu.aplicacion.venta.dto.response.VentaResponse;
import com.freestyleperu.aplicacion.venta.service.VentaService;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ComboFlujoIntegrationTest {

    @Autowired private BranchRepository branchRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private CashRegisterRepository cashRegisterRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private AtributoTestFixture atributos;
    @Autowired private PaymentMethodRepository paymentMethodRepository;
    @Autowired private ProductoService productoService;
    @Autowired private VarianteService varianteService;
    @Autowired private CajaService cajaService;
    @Autowired private ComboService comboService;
    @Autowired private VentaService ventaService;
    @Autowired private ProductVariantRepository variantRepository;

    private static final Set<String> AUTORIDADES_SIN_DESCUENTO = Set.of();

    @Test
    void crearComboRechazaSiElPrecioNoEsMenorALaSumaDeSusProductos() {
        VarianteResponse casaca = crearVarianteConStock("CasacaCombo1", "Negro", "L", new BigDecimal("100.00"), 5);
        VarianteResponse pantalon = crearVarianteConStock("PantalonCombo1", "Azul", "32", new BigDecimal("80.00"), 5);

        assertThatThrownBy(() -> comboService.crear(new ComboRequest(
                "COMBO-CARO", "Combo caro", new BigDecimal("180.00"),
                List.of(new ComboItemRequest(ComboSelectorType.PRODUCT, casaca.productId(), null, null, 1), new ComboItemRequest(ComboSelectorType.PRODUCT, pantalon.productId(), null, null, 1)))))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void venderComboReparteElDescuentoProporcionalmenteYCuadraConElPrecioFijo() {
        Long userId = nuevoUsuario("cajero.combo").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        VarianteResponse casaca = crearVarianteConStock("CasacaCombo2", "Negro", "L", new BigDecimal("100.00"), 5);
        VarianteResponse pantalon = crearVarianteConStock("PantalonCombo2", "Azul", "32", new BigDecimal("80.00"), 5);
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        ComboResponse combo = comboService.crear(new ComboRequest(
                "COMBO-CASACA-PANTALON", "Casaca + Pantalón", new BigDecimal("150.00"),
                List.of(new ComboItemRequest(ComboSelectorType.PRODUCT, casaca.productId(), null, null, 1), new ComboItemRequest(ComboSelectorType.PRODUCT, pantalon.productId(), null, null, 1))));

        assertThat(combo.normalTotal()).isEqualByComparingTo("180.00");
        assertThat(combo.savings()).isEqualByComparingTo("30.00");

        CrearVentaRequest request = new CrearVentaRequest(
                null, null, sesion.id(), null, null,
                List.of(
                        new ItemVentaRequest(casaca.id(), 1, null, combo.id(), null),
                        new ItemVentaRequest(pantalon.id(), 1, null, combo.id(), null)),
                List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("150.00"), null)));

        VentaResponse venta = ventaService.registrarVenta(request, userId, AUTORIDADES_SIN_DESCUENTO);

        assertThat(venta.total()).isEqualByComparingTo("150.00");
        assertThat(venta.items()).hasSize(2);
        assertThat(venta.items().get(0).comboId()).isEqualTo(combo.id());
        assertThat(venta.items().get(1).comboId()).isEqualTo(combo.id());
        // La suma de los subtotales de línea debe cuadrar exacto con el precio fijo del combo.
        BigDecimal sumaSubtotales = venta.items().stream().map(i -> i.subtotal()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumaSubtotales).isEqualByComparingTo("150.00");

        // El stock de ambos productos del combo bajó.
        assertThat(variantRepository.findById(casaca.id()).orElseThrow().getStock()).isEqualTo(4);
        assertThat(variantRepository.findById(pantalon.id()).orElseThrow().getStock()).isEqualTo(4);
    }

    @Test
    void venderComboConProductosQueNoCoincidenConSuDefinicionEsRechazado() {
        Long userId = nuevoUsuario("cajero.combomal").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        VarianteResponse casaca = crearVarianteConStock("CasacaCombo3", "Negro", "L", new BigDecimal("100.00"), 5);
        VarianteResponse pantalon = crearVarianteConStock("PantalonCombo3", "Azul", "32", new BigDecimal("80.00"), 5);
        VarianteResponse otro = crearVarianteConStock("GorraCombo3", "Blanco", "Única", new BigDecimal("40.00"), 5);
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        ComboResponse combo = comboService.crear(new ComboRequest(
                "COMBO-MAL-DEF", "Casaca + Pantalón v2", new BigDecimal("150.00"),
                List.of(new ComboItemRequest(ComboSelectorType.PRODUCT, casaca.productId(), null, null, 1), new ComboItemRequest(ComboSelectorType.PRODUCT, pantalon.productId(), null, null, 1))));

        // Se manda la gorra en vez del pantalón — no coincide con la definición del combo.
        CrearVentaRequest request = new CrearVentaRequest(
                null, null, sesion.id(), null, null,
                List.of(
                        new ItemVentaRequest(casaca.id(), 1, null, combo.id(), null),
                        new ItemVentaRequest(otro.id(), 1, null, combo.id(), null)),
                List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("150.00"), null)));

        assertThatThrownBy(() -> ventaService.registrarVenta(request, userId, AUTORIDADES_SIN_DESCUENTO))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void comboInactivoNoSePuedeVender() {
        Long userId = nuevoUsuario("cajero.comboinactivo").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        VarianteResponse casaca = crearVarianteConStock("CasacaCombo4", "Negro", "L", new BigDecimal("100.00"), 5);
        VarianteResponse pantalon = crearVarianteConStock("PantalonCombo4", "Azul", "32", new BigDecimal("80.00"), 5);
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        ComboResponse combo = comboService.crear(new ComboRequest(
                "COMBO-INACTIVO", "Combo a desactivar", new BigDecimal("150.00"),
                List.of(new ComboItemRequest(ComboSelectorType.PRODUCT, casaca.productId(), null, null, 1), new ComboItemRequest(ComboSelectorType.PRODUCT, pantalon.productId(), null, null, 1))));
        comboService.cambiarEstado(combo.id(), EstadoGeneral.INACTIVE);

        CrearVentaRequest request = new CrearVentaRequest(
                null, null, sesion.id(), null, null,
                List.of(
                        new ItemVentaRequest(casaca.id(), 1, null, combo.id(), null),
                        new ItemVentaRequest(pantalon.id(), 1, null, combo.id(), null)),
                List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("150.00"), null)));

        assertThatThrownBy(() -> ventaService.registrarVenta(request, userId, AUTORIDADES_SIN_DESCUENTO))
                .isInstanceOf(Exception.class);
    }

    @Test
    void venderComboConLineaDeCategoriaAceptaCualquierProductoDeEsaCategoriaYMarca() {
        Long userId = nuevoUsuario("cajero.combocategoria").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        Category categoriaPolos = new Category();
        categoriaPolos.setName("Polos Combo Cat");
        categoriaPolos.setSlug("polos-combo-cat");
        categoryRepository.save(categoriaPolos);

        Brand marca = new Brand();
        marca.setName("Marca Combo Cat");
        brandRepository.save(marca);

        // Cuatro polos distintos (colores/tallas distintas), todos de la misma categoría y marca — "4 polos por S/100".
        VarianteResponse polo1 = crearVarianteEnCategoria(categoriaPolos.getId(), marca.getId(), "PoloA", "Negro", "M", new BigDecimal("30.00"), 5);
        VarianteResponse polo2 = crearVarianteEnCategoria(categoriaPolos.getId(), marca.getId(), "PoloB", "Blanco", "L", new BigDecimal("35.00"), 5);
        VarianteResponse polo3 = crearVarianteEnCategoria(categoriaPolos.getId(), marca.getId(), "PoloC", "Azul", "S", new BigDecimal("28.00"), 5);
        VarianteResponse polo4 = crearVarianteEnCategoria(categoriaPolos.getId(), marca.getId(), "PoloD", "Rojo", "XL", new BigDecimal("32.00"), 5);

        ComboResponse combo = comboService.crear(new ComboRequest(
                "COMBO-4-POLOS", "4 polos por S/100", new BigDecimal("100.00"),
                List.of(new ComboItemRequest(ComboSelectorType.CATEGORY, null, categoriaPolos.getId(), marca.getId(), 4))));

        // No se puede calcular ahorro/precio normal de antemano: depende de qué polos concretos se elijan.
        assertThat(combo.normalTotal()).isNull();
        assertThat(combo.savings()).isNull();

        CrearVentaRequest request = new CrearVentaRequest(
                null, null, sesion.id(), null, null,
                List.of(
                        new ItemVentaRequest(polo1.id(), 1, null, combo.id(), null),
                        new ItemVentaRequest(polo2.id(), 1, null, combo.id(), null),
                        new ItemVentaRequest(polo3.id(), 1, null, combo.id(), null),
                        new ItemVentaRequest(polo4.id(), 1, null, combo.id(), null)),
                List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("100.00"), null)));

        VentaResponse venta = ventaService.registrarVenta(request, userId, AUTORIDADES_SIN_DESCUENTO);

        assertThat(venta.total()).isEqualByComparingTo("100.00");
        BigDecimal sumaSubtotales = venta.items().stream().map(i -> i.subtotal()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumaSubtotales).isEqualByComparingTo("100.00");
    }

    @Test
    void venderComboConLineaDeCategoriaYMarcaRechazaProductoDeOtraMarca() {
        Long userId = nuevoUsuario("cajero.combomarca").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        Category categoria = new Category();
        categoria.setName("Polos Combo Marca");
        categoria.setSlug("polos-combo-marca");
        categoryRepository.save(categoria);

        Brand marcaExigida = new Brand();
        marcaExigida.setName("Marca Exigida");
        brandRepository.save(marcaExigida);

        Brand otraMarca = new Brand();
        otraMarca.setName("Otra Marca");
        brandRepository.save(otraMarca);

        VarianteResponse deOtraMarca = crearVarianteEnCategoria(categoria.getId(), otraMarca.getId(), "PoloOtraMarca", "Negro", "M", new BigDecimal("30.00"), 5);

        ComboResponse combo = comboService.crear(new ComboRequest(
                "COMBO-1-POLO-MARCA", "1 polo de la marca exigida", new BigDecimal("25.00"),
                List.of(new ComboItemRequest(ComboSelectorType.CATEGORY, null, categoria.getId(), marcaExigida.getId(), 1))));

        CrearVentaRequest request = new CrearVentaRequest(
                null, null, sesion.id(), null, null,
                List.of(new ItemVentaRequest(deOtraMarca.id(), 1, null, combo.id(), null)),
                List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("25.00"), null)));

        assertThatThrownBy(() -> ventaService.registrarVenta(request, userId, AUTORIDADES_SIN_DESCUENTO))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void venderComboConLineaDeProductoEspecificoYLineaDeCategoriaAsignaCadaLineaAlSlotCorrecto() {
        Long userId = nuevoUsuario("cajero.combomixto").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        Category categoriaAccesorios = new Category();
        categoriaAccesorios.setName("Accesorios Combo Mixto");
        categoriaAccesorios.setSlug("accesorios-combo-mixto");
        categoryRepository.save(categoriaAccesorios);

        VarianteResponse casaca = crearVarianteConStock("CasacaComboMixto", "Negro", "L", new BigDecimal("100.00"), 5);
        VarianteResponse gorra = crearVarianteEnCategoria(categoriaAccesorios.getId(), null, "GorraCM", "Blanco", "Única", new BigDecimal("20.00"), 5);

        ComboResponse combo = comboService.crear(new ComboRequest(
                "COMBO-MIXTO", "Casaca + accesorio", new BigDecimal("110.00"),
                List.of(
                        new ComboItemRequest(ComboSelectorType.PRODUCT, casaca.productId(), null, null, 1),
                        new ComboItemRequest(ComboSelectorType.CATEGORY, null, categoriaAccesorios.getId(), null, 1))));

        CrearVentaRequest request = new CrearVentaRequest(
                null, null, sesion.id(), null, null,
                List.of(
                        new ItemVentaRequest(casaca.id(), 1, null, combo.id(), null),
                        new ItemVentaRequest(gorra.id(), 1, null, combo.id(), null)),
                List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("110.00"), null)));

        VentaResponse venta = ventaService.registrarVenta(request, userId, AUTORIDADES_SIN_DESCUENTO);

        assertThat(venta.total()).isEqualByComparingTo("110.00");
    }

    private SesionCajaResponse abrirCaja(Long userId) {
        Branch branch = new Branch();
        branch.setCode("SUC-COMBO-TEST");
        branch.setName("Sucursal combo test");
        branchRepository.save(branch);

        Warehouse warehouse = new Warehouse();
        warehouse.setBranch(branch);
        warehouse.setCode("ALM-COMBO-TEST");
        warehouse.setName("Almacén combo test");
        warehouseRepository.save(warehouse);

        CashRegister register = new CashRegister();
        register.setBranch(branch);
        register.setCode("CAJA-COMBO-TEST");
        register.setName("Caja combo test");
        cashRegisterRepository.save(register);

        return cajaService.abrirCaja(new AbrirCajaRequest(register.getId(), new BigDecimal("300.00")), userId);
    }

    private VarianteResponse crearVarianteConStock(String producto, String color, String talla, BigDecimal precio, int stock) {
        Category categoria = new Category();
        categoria.setName(producto + "-cat");
        categoria.setSlug((producto + "-cat" + producto.hashCode()).toLowerCase());
        categoryRepository.save(categoria);

        AttributeValue colorEntity = atributos.color(color + "-" + producto);
        AttributeValue sizeEntity = atributos.talla(talla + "-" + producto, (short) 1);

        ProductoDetalleResponse productoCreado = productoService.crear(new CrearProductoRequest(
                null, null, producto, categoria.getId(), null, null, null, null, null, precio, null));
        return varianteService.crear(productoCreado.id(),
                new CrearVarianteRequest(List.of(colorEntity.getId(), sizeEntity.getId()), null, null, stock, 1, false));
    }

    /** A diferencia de crearVarianteConStock, reutiliza una categoría (y opcionalmente una marca) ya existente. */
    private VarianteResponse crearVarianteEnCategoria(
            Long categoryId, Long brandId, String producto, String color, String talla, BigDecimal precio, int stock) {
        AttributeValue colorEntity = atributos.color(color + "-" + producto);
        AttributeValue sizeEntity = atributos.talla(talla + "-" + producto, (short) 1);

        ProductoDetalleResponse productoCreado = productoService.crear(new CrearProductoRequest(
                null, null, producto, categoryId, null, brandId, null, null, null, precio, null));
        return varianteService.crear(productoCreado.id(),
                new CrearVarianteRequest(List.of(colorEntity.getId(), sizeEntity.getId()), null, null, stock, 1, false));
    }

    private PaymentMethod metodoPago(String code) {
        return paymentMethodRepository.findAllByOrderBySortOrderAsc().stream()
                .filter(m -> m.getCode().equals(code))
                .findFirst()
                .orElseGet(() -> {
                    PaymentMethod method = new PaymentMethod();
                    method.setCode(code);
                    method.setName(code);
                    boolean efectivo = code.equals("EFECTIVO");
                    method.setType(efectivo ? PaymentMethodType.CASH : PaymentMethodType.DIGITAL_WALLET);
                    method.setAffectsCash(efectivo);
                    method.setRequiresReference(!efectivo);
                    method.setSortOrder((short) 1);
                    return paymentMethodRepository.save(method);
                });
    }

    private Usuario nuevoUsuario(String username) {
        Rol rol = new Rol();
        rol.setCode("TEST_ROL_COMBO_" + username.hashCode());
        rol.setName("Rol de prueba combo");
        rol.setSystem(false);
        rolRepository.save(rol);

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPasswordHash("hash");
        usuario.setFullName("Cajero de Prueba");
        usuario.setStatus(UsuarioEstado.ACTIVE);
        usuario.setRoles(new HashSet<>(List.of(rol)));
        return usuarioRepository.save(usuario);
    }
}
