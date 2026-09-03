package com.freestyleperu.aplicacion.promocion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.freestyleperu.aplicacion.caja.domain.CashRegister;
import com.freestyleperu.aplicacion.caja.dto.request.AbrirCajaRequest;
import com.freestyleperu.aplicacion.caja.dto.response.SesionCajaResponse;
import com.freestyleperu.aplicacion.caja.repository.CashRegisterRepository;
import com.freestyleperu.aplicacion.caja.service.CajaService;
import com.freestyleperu.aplicacion.catalogo.domain.Category;
import com.freestyleperu.aplicacion.catalogo.domain.AttributeValue;
import com.freestyleperu.aplicacion.catalogo.repository.CategoryRepository;
import com.freestyleperu.aplicacion.inventario.domain.Branch;
import com.freestyleperu.aplicacion.inventario.domain.Warehouse;
import com.freestyleperu.aplicacion.inventario.repository.BranchRepository;
import com.freestyleperu.aplicacion.inventario.repository.WarehouseRepository;
import com.freestyleperu.aplicacion.pago.domain.PaymentMethod;
import com.freestyleperu.aplicacion.pago.domain.PaymentMethodType;
import com.freestyleperu.aplicacion.pago.repository.PaymentMethodRepository;
import com.freestyleperu.aplicacion.producto.AtributoTestFixture;
import com.freestyleperu.aplicacion.producto.dto.request.CrearProductoRequest;
import com.freestyleperu.aplicacion.producto.dto.request.CrearVarianteRequest;
import com.freestyleperu.aplicacion.producto.dto.response.ProductoDetalleResponse;
import com.freestyleperu.aplicacion.producto.domain.Product;
import com.freestyleperu.aplicacion.producto.dto.response.VarianteResponse;
import com.freestyleperu.aplicacion.producto.repository.ProductRepository;
import com.freestyleperu.aplicacion.producto.service.ProductoService;
import com.freestyleperu.aplicacion.producto.service.VarianteService;
import com.freestyleperu.aplicacion.promocion.domain.PromotionScope;
import com.freestyleperu.aplicacion.promocion.domain.PromotionType;
import com.freestyleperu.aplicacion.promocion.dto.request.PromocionRequest;
import com.freestyleperu.aplicacion.promocion.dto.response.PromocionResponse;
import com.freestyleperu.aplicacion.promocion.service.PromocionService;
import com.freestyleperu.aplicacion.shared.exception.OperacionNoPermitidaException;
import com.freestyleperu.aplicacion.shared.exception.ReglaDeNegocioException;
import com.freestyleperu.aplicacion.shared.security.Permisos;
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
import java.time.LocalDateTime;
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
class PromocionFlujoIntegrationTest {

    @Autowired private BranchRepository branchRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private CashRegisterRepository cashRegisterRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AtributoTestFixture atributos;
    @Autowired private PaymentMethodRepository paymentMethodRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductoService productoService;
    @Autowired private VarianteService varianteService;
    @Autowired private CajaService cajaService;
    @Autowired private PromocionService promocionService;
    @Autowired private VentaService ventaService;

    private static final Set<String> AUTORIDADES_SIN_DESCUENTO = Set.of();
    private static final Set<String> AUTORIDADES_CON_PROMOCION = Set.of(Permisos.PROMOCIONES_APLICAR);

    @Test
    void crearPromocionPorcentualMayorA100EsRechazada() {
        assertThatThrownBy(() -> promocionService.crear(new PromocionRequest(
                "PROMO-INVALIDA", "Promo inválida", PromotionType.PERCENTAGE, new BigDecimal("150.00"),
                PromotionScope.ALL, null, null, null, null, false)))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void aplicarPromocionSinElPermisoPromocionesAplicarEsRechazado() {
        Long userId = nuevoUsuario("cajero.promosinpermiso").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        VarianteResponse variante = crearVarianteConStock("Polo Promo1", "Blanco", "M", new BigDecimal("50.00"), 5);
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        PromocionResponse promo = promocionService.crear(new PromocionRequest(
                "PROMO-20", "20% de descuento", PromotionType.PERCENTAGE, new BigDecimal("20.00"),
                PromotionScope.ALL, null, null, null, null, false));

        CrearVentaRequest request = new CrearVentaRequest(
                null, null, sesion.id(), null, null,
                List.of(new ItemVentaRequest(variante.id(), 1, null, null, promo.id())),
                List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("40.00"), null)));

        assertThatThrownBy(() -> ventaService.registrarVenta(request, userId, AUTORIDADES_SIN_DESCUENTO))
                .isInstanceOf(OperacionNoPermitidaException.class);
    }

    @Test
    void aplicarPromocionPorcentualYFijaCalculaElDescuentoCorrectamente() {
        Long userId = nuevoUsuario("cajero.promook").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        VarianteResponse polo = crearVarianteConStock("Polo Promo2", "Blanco", "M", new BigDecimal("50.00"), 5);
        VarianteResponse gorra = crearVarianteConStock("Gorra Promo2", "Negro", "Única", new BigDecimal("30.00"), 5);
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        PromocionResponse promoPorcentaje = promocionService.crear(new PromocionRequest(
                "PROMO-20B", "20% de descuento", PromotionType.PERCENTAGE, new BigDecimal("20.00"),
                PromotionScope.ALL, null, null, null, null, false));
        PromocionResponse promoFija = promocionService.crear(new PromocionRequest(
                "PROMO-FIJA-10", "S/10 de descuento", PromotionType.FIXED_AMOUNT, new BigDecimal("10.00"),
                PromotionScope.ALL, null, null, null, null, false));

        CrearVentaRequest request = new CrearVentaRequest(
                null, null, sesion.id(), null, null,
                List.of(
                        new ItemVentaRequest(polo.id(), 1, null, null, promoPorcentaje.id()),
                        new ItemVentaRequest(gorra.id(), 1, null, null, promoFija.id())),
                List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("60.00"), null)));

        VentaResponse venta = ventaService.registrarVenta(request, userId, AUTORIDADES_CON_PROMOCION);

        // Polo: 50.00 - 20% = 40.00. Gorra: 30.00 - 10.00 = 20.00. Total = 60.00.
        assertThat(venta.total()).isEqualByComparingTo("60.00");
        assertThat(venta.items().get(0).discountAmount()).isEqualByComparingTo("10.00");
        assertThat(venta.items().get(0).promotionId()).isEqualTo(promoPorcentaje.id());
        assertThat(venta.items().get(1).discountAmount()).isEqualByComparingTo("10.00");
        assertThat(venta.items().get(1).promotionId()).isEqualTo(promoFija.id());
    }

    @Test
    void aplicarPromocionFueraDeVigenciaEsRechazada() {
        Long userId = nuevoUsuario("cajero.promovencida").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        VarianteResponse variante = crearVarianteConStock("Polo Promo3", "Blanco", "M", new BigDecimal("50.00"), 5);
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        PromocionResponse promoVencida = promocionService.crear(new PromocionRequest(
                "PROMO-VENCIDA", "Promo ya vencida", PromotionType.PERCENTAGE, new BigDecimal("20.00"),
                PromotionScope.ALL, null, null,
                LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1), false));

        CrearVentaRequest request = new CrearVentaRequest(
                null, null, sesion.id(), null, null,
                List.of(new ItemVentaRequest(variante.id(), 1, null, null, promoVencida.id())),
                List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("40.00"), null)));

        assertThatThrownBy(() -> ventaService.registrarVenta(request, userId, AUTORIDADES_CON_PROMOCION))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void aplicarPromocionQueNoAplicaAlProductoPorAlcanceEsRechazada() {
        Long userId = nuevoUsuario("cajero.promoalcance").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        VarianteResponse polo = crearVarianteConStock("Polo Promo4", "Blanco", "M", new BigDecimal("50.00"), 5);
        VarianteResponse otroProducto = crearVarianteConStock("Zapatilla Promo4", "Negro", "42", new BigDecimal("120.00"), 5);
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        // Promoción exclusiva del producto "polo", nunca de "otroProducto".
        PromocionResponse promoProducto = promocionService.crear(new PromocionRequest(
                "PROMO-SOLO-POLO", "Solo para este polo", PromotionType.FIXED_AMOUNT, new BigDecimal("5.00"),
                PromotionScope.PRODUCT, null, polo.productId(), null, null, false));

        CrearVentaRequest request = new CrearVentaRequest(
                null, null, sesion.id(), null, null,
                List.of(new ItemVentaRequest(otroProducto.id(), 1, null, null, promoProducto.id())),
                List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("115.00"), null)));

        assertThatThrownBy(() -> ventaService.registrarVenta(request, userId, AUTORIDADES_CON_PROMOCION))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void listarVigentesParaVarianteSoloDevuelveLasQueAplican() {
        VarianteResponse polo = crearVarianteConStock("Polo Promo5", "Blanco", "M", new BigDecimal("50.00"), 5);
        VarianteResponse otroProducto = crearVarianteConStock("Zapatilla Promo5", "Negro", "42", new BigDecimal("120.00"), 5);

        PromocionResponse promoGeneral = promocionService.crear(new PromocionRequest(
                "PROMO-GENERAL5", "Para todos", PromotionType.PERCENTAGE, new BigDecimal("10.00"),
                PromotionScope.ALL, null, null, null, null, false));
        promocionService.crear(new PromocionRequest(
                "PROMO-OTRO5", "Solo para otro producto", PromotionType.FIXED_AMOUNT, new BigDecimal("5.00"),
                PromotionScope.PRODUCT, null, otroProducto.productId(), null, null, false));

        List<PromocionResponse> vigentes = promocionService.listarVigentesParaVariante(polo.id());

        assertThat(vigentes).extracting(PromocionResponse::id).containsExactly(promoGeneral.id());
    }

    @Test
    void precioEfectivoOnlineSoloConsideraPromocionesMarcadasVisibleOnline() {
        VarianteResponse variante = crearVarianteConStock("Polo Promo6", "Blanco", "M", new BigDecimal("50.00"), 5);
        Product producto = productRepository.findById(variante.productId()).orElseThrow();

        // Promoción solo para POS (visibleOnline=false) — no debe afectar el precio online.
        promocionService.crear(new PromocionRequest(
                "PROMO-POS6", "Solo POS", PromotionType.PERCENTAGE, new BigDecimal("50.00"),
                PromotionScope.ALL, null, null, null, null, false));
        assertThat(promocionService.precioEfectivoOnline(producto)).isEqualByComparingTo("50.00");

        // Promoción tipo Black Friday, visible en la tienda online.
        promocionService.crear(new PromocionRequest(
                "PROMO-ONLINE6", "Black Friday", PromotionType.PERCENTAGE, new BigDecimal("20.00"),
                PromotionScope.ALL, null, null, null, null, true));
        assertThat(promocionService.precioEfectivoOnline(producto)).isEqualByComparingTo("40.00");
    }

    private SesionCajaResponse abrirCaja(Long userId) {
        Branch branch = new Branch();
        branch.setCode("SUC-PROMO-TEST");
        branch.setName("Sucursal promo test");
        branchRepository.save(branch);

        Warehouse warehouse = new Warehouse();
        warehouse.setBranch(branch);
        warehouse.setCode("ALM-PROMO-TEST");
        warehouse.setName("Almacén promo test");
        warehouseRepository.save(warehouse);

        CashRegister register = new CashRegister();
        register.setBranch(branch);
        register.setCode("CAJA-PROMO-TEST");
        register.setName("Caja promo test");
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
        rol.setCode("TEST_ROL_PROMO_" + username.hashCode());
        rol.setName("Rol de prueba promocion");
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
