package com.freestyleperu.aplicacion.venta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.freestyleperu.aplicacion.caja.domain.CashRegister;
import com.freestyleperu.aplicacion.caja.dto.request.AbrirCajaRequest;
import com.freestyleperu.aplicacion.caja.dto.request.CerrarCajaRequest;
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
import com.freestyleperu.aplicacion.pago.repository.PaymentMethodRepository;
import com.freestyleperu.aplicacion.producto.AtributoTestFixture;
import com.freestyleperu.aplicacion.producto.dto.request.CrearProductoRequest;
import com.freestyleperu.aplicacion.producto.dto.request.CrearVarianteRequest;
import com.freestyleperu.aplicacion.producto.dto.response.ProductoDetalleResponse;
import com.freestyleperu.aplicacion.producto.dto.response.VarianteResponse;
import com.freestyleperu.aplicacion.producto.repository.ProductVariantRepository;
import com.freestyleperu.aplicacion.producto.service.ProductoService;
import com.freestyleperu.aplicacion.producto.service.VarianteService;
import com.freestyleperu.aplicacion.shared.exception.OperacionNoPermitidaException;
import com.freestyleperu.aplicacion.shared.exception.ReglaDeNegocioException;
import com.freestyleperu.aplicacion.shared.exception.StockInsuficienteException;
import com.freestyleperu.aplicacion.usuario.domain.Rol;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import com.freestyleperu.aplicacion.usuario.domain.UsuarioEstado;
import com.freestyleperu.aplicacion.usuario.repository.RolRepository;
import com.freestyleperu.aplicacion.usuario.repository.UsuarioRepository;
import com.freestyleperu.aplicacion.venta.domain.Payment;
import com.freestyleperu.aplicacion.venta.domain.PaymentStatus;
import com.freestyleperu.aplicacion.venta.domain.Sale;
import com.freestyleperu.aplicacion.venta.domain.SaleDetail;
import com.freestyleperu.aplicacion.venta.domain.SaleStatus;
import com.freestyleperu.aplicacion.venta.dto.request.AnularVentaRequest;
import com.freestyleperu.aplicacion.venta.dto.request.CrearVentaRequest;
import com.freestyleperu.aplicacion.venta.dto.request.ItemVentaRequest;
import com.freestyleperu.aplicacion.venta.dto.request.PagoVentaRequest;
import com.freestyleperu.aplicacion.venta.dto.response.VentaResponse;
import com.freestyleperu.aplicacion.venta.repository.PaymentRepository;
import com.freestyleperu.aplicacion.venta.repository.SaleDetailRepository;
import com.freestyleperu.aplicacion.venta.repository.SaleRepository;
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
class VentaFlujoIntegrationTest {

    @Autowired private BranchRepository branchRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private CashRegisterRepository cashRegisterRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AtributoTestFixture atributos;
    @Autowired private PaymentMethodRepository paymentMethodRepository;
    @Autowired private ProductoService productoService;
    @Autowired private VarianteService varianteService;
    @Autowired private CajaService cajaService;
    @Autowired private VentaService ventaService;
    @Autowired private ProductVariantRepository variantRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private SaleDetailRepository saleDetailRepository;
    @Autowired private PaymentRepository paymentRepository;

    private static final Set<String> AUTORIDADES_SIN_DESCUENTO = Set.of();

    @Test
    void registraVentaConPagoMixtoDescuentaInventarioYAfectaSoloElEfectivoEnCaja() {
        Long userId = nuevoUsuario("vendedor.test").getId();
        SesionCajaResponse sesion = abrirCaja(userId);

        VarianteResponse variante = crearVarianteConStock("Casaca Denim", "Negro", "L", new BigDecimal("120.00"), 10);
        PaymentMethod efectivo = metodoPago("EFECTIVO");
        PaymentMethod yape = metodoPago("YAPE");

        CrearVentaRequest request = new CrearVentaRequest(
                null, null, sesion.id(), null, null,
                List.of(new ItemVentaRequest(variante.id(), 2, null, null, null)),
                List.of(
                        new PagoVentaRequest(efectivo.getId(), new BigDecimal("100.00"), null),
                        new PagoVentaRequest(yape.getId(), new BigDecimal("140.00"), "OP-123")));

        VentaResponse venta = ventaService.registrarVenta(request, userId, AUTORIDADES_SIN_DESCUENTO);

        assertThat(venta.total()).isEqualByComparingTo("240.00");
        assertThat(venta.status().name()).isEqualTo("COMPLETED");
        assertThat(venta.items()).hasSize(1);
        assertThat(venta.payments()).hasSize(2);

        // El stock bajó de 10 a 8.
        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isEqualTo(8);

        // Solo el efectivo (100) debe reflejarse en el arqueo, no el Yape (140).
        var resumen = cajaService.obtenerResumenCierre(sesion.id());
        assertThat(resumen.expectedAmount()).isEqualByComparingTo("400.00"); // 300 apertura + 100 efectivo

        // No se puede vender más de lo disponible (999 * 120.00 = 119880.00, pago exacto para llegar al chequeo de stock).
        assertThatThrownBy(() -> ventaService.registrarVenta(
                new CrearVentaRequest(null, null, sesion.id(), null, null,
                        List.of(new ItemVentaRequest(variante.id(), 999, null, null, null)),
                        List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("119880.00"), null))),
                userId, AUTORIDADES_SIN_DESCUENTO))
                .isInstanceOf(StockInsuficienteException.class);

        // El descuento sin permiso se rechaza.
        assertThatThrownBy(() -> ventaService.registrarVenta(
                new CrearVentaRequest(null, null, sesion.id(), new BigDecimal("10.00"), null,
                        List.of(new ItemVentaRequest(variante.id(), 1, null, null, null)),
                        List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("110.00"), null))),
                userId, AUTORIDADES_SIN_DESCUENTO))
                .isInstanceOf(OperacionNoPermitidaException.class);

        // Pagos que no cuadran con el total se rechazan.
        assertThatThrownBy(() -> ventaService.registrarVenta(
                new CrearVentaRequest(null, null, sesion.id(), null, null,
                        List.of(new ItemVentaRequest(variante.id(), 1, null, null, null)),
                        List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("50.00"), null))),
                userId, AUTORIDADES_SIN_DESCUENTO))
                .isInstanceOf(ReglaDeNegocioException.class);

        // Anular revierte stock (8 -> 9, ya que la venta de anulación fue de 1 unidad más arriba) y efectivo.
        // Anulamos la venta original de 2 unidades: stock debe volver a 8 + 2 = 10.
        ventaService.anular(venta.id(), new AnularVentaRequest("Cliente se arrepintió"), userId);
        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isEqualTo(10);

        var resumenTrasAnular = cajaService.obtenerResumenCierre(sesion.id());
        // 400 (con la venta) - 100 (reversión del efectivo de esa venta) = 300, vuelve a la apertura.
        assertThat(resumenTrasAnular.expectedAmount()).isEqualByComparingTo("300.00");

        // No se puede anular dos veces.
        assertThatThrownBy(() -> ventaService.anular(venta.id(), new AnularVentaRequest("de nuevo"), userId))
                .isInstanceOf(ReglaDeNegocioException.class);

        // Un pago que supera el total también se rechaza (no solo el que queda corto).
        assertThatThrownBy(() -> ventaService.registrarVenta(
                new CrearVentaRequest(null, null, sesion.id(), null, null,
                        List.of(new ItemVentaRequest(variante.id(), 1, null, null, null)),
                        List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("200.00"), null))),
                userId, AUTORIDADES_SIN_DESCUENTO))
                .isInstanceOf(ReglaDeNegocioException.class);

        // No se puede vender con la caja cerrada.
        cajaService.cerrarCaja(sesion.id(), new CerrarCajaRequest(new BigDecimal("300.00"), "Cierre de turno"), userId);
        assertThatThrownBy(() -> ventaService.registrarVenta(
                new CrearVentaRequest(null, null, sesion.id(), null, null,
                        List.of(new ItemVentaRequest(variante.id(), 1, null, null, null)),
                        List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("120.00"), null))),
                userId, AUTORIDADES_SIN_DESCUENTO))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void rechazaVenderStockCeroYCodigoDeBarrasDuplicadoEnVariantesDelMismoProducto() {
        Long userId = nuevoUsuario("vendedor.stock0").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        VarianteResponse sinStock = crearVarianteConStock("Gorra Snapback", "Azul", "Única", new BigDecimal("60.00"), 0);

        assertThatThrownBy(() -> ventaService.registrarVenta(
                new CrearVentaRequest(null, null, sesion.id(), null, null,
                        List.of(new ItemVentaRequest(sinStock.id(), 1, null, null, null)),
                        List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("60.00"), null))),
                userId, AUTORIDADES_SIN_DESCUENTO))
                .isInstanceOf(StockInsuficienteException.class);
    }

    @Test
    void anularUnaVentaSinCajaConPagoQueAfectaCajaRechazaConErrorClaroNoNPE() {
        Long userId = nuevoUsuario("vendedor.sincajapedido").getId();
        VarianteResponse variante = crearVarianteConStock("Zapatilla Running", "Blanco", "40", new BigDecimal("200.00"), 5);
        PaymentMethod efectivo = metodoPago("EFECTIVO");
        Usuario vendedor = usuarioRepository.findById(userId).orElseThrow();

        // Simula una venta generada por un pedido online confirmado (sin sesión de caja).
        Sale venta = new Sale();
        venta.setSaleNumber("V001-TESTSINCAJA");
        venta.setUser(vendedor);
        venta.setSubtotal(new BigDecimal("200.00"));
        venta.setDiscountAmount(BigDecimal.ZERO);
        venta.setTotal(new BigDecimal("200.00"));
        venta.setStatus(SaleStatus.COMPLETED);
        venta.setCreatedAt(LocalDateTime.now());
        Sale ventaGuardada = saleRepository.save(venta);

        SaleDetail detalle = new SaleDetail();
        detalle.setSale(ventaGuardada);
        detalle.setVariant(variantRepository.findById(variante.id()).orElseThrow());
        detalle.setQuantity(1);
        detalle.setUnitPrice(new BigDecimal("200.00"));
        detalle.setDiscountAmount(BigDecimal.ZERO);
        detalle.setSubtotal(new BigDecimal("200.00"));
        detalle.setProductName("Zapatilla Running");
        detalle.setVariantSku(variante.sku());
        detalle.setVariantLabel("Blanco / 40");
        saleDetailRepository.save(detalle);

        Payment pago = new Payment();
        pago.setSale(ventaGuardada);
        pago.setPaymentMethod(efectivo);
        pago.setAmount(new BigDecimal("200.00"));
        pago.setStatus(PaymentStatus.COMPLETED);
        pago.setCreatedAt(LocalDateTime.now());
        paymentRepository.save(pago);

        assertThat(ventaGuardada.getCashSession()).isNull();

        // Anular con un pago que afecta caja debe rechazarse con un mensaje claro, no un NullPointerException.
        assertThatThrownBy(() -> ventaService.anular(ventaGuardada.getId(), new AnularVentaRequest("prueba"), userId))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    private SesionCajaResponse abrirCaja(Long userId) {
        Branch branch = new Branch();
        branch.setCode("SUC-VENTA-TEST");
        branch.setName("Sucursal venta test");
        branchRepository.save(branch);

        Warehouse warehouse = new Warehouse();
        warehouse.setBranch(branch);
        warehouse.setCode("ALM-VENTA-TEST");
        warehouse.setName("Almacén venta test");
        warehouseRepository.save(warehouse);

        CashRegister register = new CashRegister();
        register.setBranch(branch);
        register.setCode("CAJA-VENTA-TEST");
        register.setName("Caja venta test");
        cashRegisterRepository.save(register);

        return cajaService.abrirCaja(new AbrirCajaRequest(register.getId(), new BigDecimal("300.00")), userId);
    }

    private VarianteResponse crearVarianteConStock(String producto, String color, String talla, BigDecimal precio, int stock) {
        Category categoria = new Category();
        categoria.setName(producto + "-cat");
        categoria.setSlug((producto + "-cat").toLowerCase());
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
                .orElseGet(() -> crearMetodoPago(code));
    }

    private PaymentMethod crearMetodoPago(String code) {
        PaymentMethod method = new PaymentMethod();
        method.setCode(code);
        method.setName(code);
        boolean efectivo = code.equals("EFECTIVO");
        method.setType(efectivo ? com.freestyleperu.aplicacion.pago.domain.PaymentMethodType.CASH
                : com.freestyleperu.aplicacion.pago.domain.PaymentMethodType.DIGITAL_WALLET);
        method.setAffectsCash(efectivo);
        method.setRequiresReference(!efectivo);
        method.setSortOrder((short) 1);
        return paymentMethodRepository.save(method);
    }

    private Usuario nuevoUsuario(String username) {
        Rol rol = new Rol();
        rol.setCode("TEST_ROL_VENTA_" + username.hashCode());
        rol.setName("Rol de prueba venta");
        rol.setSystem(false);
        rolRepository.save(rol);

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPasswordHash("hash");
        usuario.setFullName("Vendedor de Prueba");
        usuario.setStatus(UsuarioEstado.ACTIVE);
        usuario.setRoles(new HashSet<>(List.of(rol)));
        return usuarioRepository.save(usuario);
    }
}
