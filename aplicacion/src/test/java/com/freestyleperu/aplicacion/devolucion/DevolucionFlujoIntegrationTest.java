package com.freestyleperu.aplicacion.devolucion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.freestyleperu.aplicacion.caja.domain.CashRegister;
import com.freestyleperu.aplicacion.caja.dto.request.AbrirCajaRequest;
import com.freestyleperu.aplicacion.caja.dto.response.SesionCajaResponse;
import com.freestyleperu.aplicacion.caja.repository.CashRegisterRepository;
import com.freestyleperu.aplicacion.caja.service.CajaService;
import com.freestyleperu.aplicacion.catalogo.domain.AttributeValue;
import com.freestyleperu.aplicacion.catalogo.domain.Category;
import com.freestyleperu.aplicacion.catalogo.repository.CategoryRepository;
import com.freestyleperu.aplicacion.devolucion.dto.request.CrearDevolucionRequest;
import com.freestyleperu.aplicacion.devolucion.dto.request.ItemDevolucionRequest;
import com.freestyleperu.aplicacion.devolucion.dto.response.DevolucionResponse;
import com.freestyleperu.aplicacion.devolucion.dto.response.ReturnableItemResponse;
import com.freestyleperu.aplicacion.devolucion.service.DevolucionService;
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
import com.freestyleperu.aplicacion.shared.exception.ReglaDeNegocioException;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DevolucionFlujoIntegrationTest {

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
    @Autowired private DevolucionService devolucionService;
    @Autowired private ProductVariantRepository variantRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private SaleDetailRepository saleDetailRepository;
    @Autowired private PaymentRepository paymentRepository;

    @Test
    void devolverUnaVentaSinCajaConReembolsoQueAfectaCajaRechazaConErrorClaroNoNPE() {
        Rol rol = new Rol();
        rol.setCode("TEST_ROL_DEVOLUCION_SINCAJA");
        rol.setName("Rol de prueba devolución sin caja");
        rol.setSystem(false);
        rolRepository.save(rol);

        Usuario usuario = new Usuario();
        usuario.setUsername("staff.devolucionsincaja");
        usuario.setPasswordHash("hash");
        usuario.setFullName("Staff Sin Caja");
        usuario.setStatus(UsuarioEstado.ACTIVE);
        usuario.setRoles(new HashSet<>(List.of(rol)));
        usuarioRepository.save(usuario);

        Category categoria = new Category();
        categoria.setName("Polos-devolucion-sincaja");
        categoria.setSlug("polos-devolucion-sincaja");
        categoryRepository.save(categoria);
        AttributeValue color = atributos.color("Negro-devolucion-sincaja");
        AttributeValue talla = atributos.talla("M-devolucion-sincaja", (short) 1);
        ProductoDetalleResponse producto = productoService.crear(new CrearProductoRequest(
                null, null, "Polo Devolución Sin Caja", categoria.getId(), null, null, null, null, null,
                new BigDecimal("40.00"), null));
        VarianteResponse variante = varianteService.crear(producto.id(),
                new CrearVarianteRequest(List.of(color.getId(), talla.getId()), null, null, 10, 1, false));

        PaymentMethod efectivo = new PaymentMethod();
        efectivo.setCode("EFECTIVO-DEV-SINCAJA");
        efectivo.setName("Efectivo");
        efectivo.setType(PaymentMethodType.CASH);
        efectivo.setAffectsCash(true);
        efectivo.setRequiresReference(false);
        efectivo.setSortOrder((short) 1);
        paymentMethodRepository.save(efectivo);

        // Simula una venta generada por un pedido online confirmado (sin sesión de caja).
        Sale venta = new Sale();
        venta.setSaleNumber("V001-TESTDEVSINCAJA");
        venta.setUser(usuario);
        venta.setSubtotal(new BigDecimal("40.00"));
        venta.setDiscountAmount(BigDecimal.ZERO);
        venta.setTotal(new BigDecimal("40.00"));
        venta.setStatus(SaleStatus.COMPLETED);
        venta.setCreatedAt(LocalDateTime.now());
        Sale ventaGuardada = saleRepository.save(venta);

        SaleDetail detalle = new SaleDetail();
        detalle.setSale(ventaGuardada);
        detalle.setVariant(variantRepository.findById(variante.id()).orElseThrow());
        detalle.setQuantity(1);
        detalle.setUnitPrice(new BigDecimal("40.00"));
        detalle.setDiscountAmount(BigDecimal.ZERO);
        detalle.setSubtotal(new BigDecimal("40.00"));
        detalle.setProductName("Polo Devolución Sin Caja");
        detalle.setVariantSku(variante.sku());
        detalle.setVariantLabel("Negro-devolucion-sincaja / M-devolucion-sincaja");
        SaleDetail detalleGuardado = saleDetailRepository.save(detalle);

        Payment pago = new Payment();
        pago.setSale(ventaGuardada);
        pago.setPaymentMethod(efectivo);
        pago.setAmount(new BigDecimal("40.00"));
        pago.setStatus(PaymentStatus.COMPLETED);
        pago.setCreatedAt(LocalDateTime.now());
        paymentRepository.save(pago);

        assertThatThrownBy(() -> devolucionService.registrar(new CrearDevolucionRequest(
                ventaGuardada.getId(), "Prueba sin caja", efectivo.getId(),
                List.of(new ItemDevolucionRequest(detalleGuardado.getId(), 1, true))), usuario.getId()))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void registraDevolucionParcialConReingresoAStockYReembolsoEnEfectivo() {
        Branch branch = new Branch();
        branch.setCode("SUC-DEV-TEST");
        branch.setName("Sucursal devolución test");
        branchRepository.save(branch);

        Warehouse warehouse = new Warehouse();
        warehouse.setBranch(branch);
        warehouse.setCode("ALM-DEV-TEST");
        warehouse.setName("Almacén devolución test");
        warehouseRepository.save(warehouse);

        CashRegister register = new CashRegister();
        register.setBranch(branch);
        register.setCode("CAJA-DEV-TEST");
        register.setName("Caja devolución test");
        cashRegisterRepository.save(register);

        Rol rol = new Rol();
        rol.setCode("TEST_ROL_DEVOLUCION");
        rol.setName("Rol de prueba devolución");
        rol.setSystem(false);
        rolRepository.save(rol);

        Usuario usuario = new Usuario();
        usuario.setUsername("vendedor.devolucion");
        usuario.setPasswordHash("hash");
        usuario.setFullName("Vendedor Devolución");
        usuario.setStatus(UsuarioEstado.ACTIVE);
        usuario.setRoles(new HashSet<>(List.of(rol)));
        usuarioRepository.save(usuario);
        Long userId = usuario.getId();

        SesionCajaResponse sesion = cajaService.abrirCaja(new AbrirCajaRequest(register.getId(), new BigDecimal("300.00")), userId);

        Category categoria = new Category();
        categoria.setName("Polos-devolucion");
        categoria.setSlug("polos-devolucion");
        categoryRepository.save(categoria);

        AttributeValue color = atributos.color("Negro-devolucion");
        AttributeValue talla = atributos.talla("M-devolucion", (short) 1);

        ProductoDetalleResponse producto = productoService.crear(new CrearProductoRequest(
                null, null, "Polo Devolución", categoria.getId(), null, null, null, null, null, new BigDecimal("40.00"), null));
        VarianteResponse variante = varianteService.crear(producto.id(),
                new CrearVarianteRequest(List.of(color.getId(), talla.getId()), null, null, 10, 1, false));

        PaymentMethod efectivo = new PaymentMethod();
        efectivo.setCode("EFECTIVO-DEV");
        efectivo.setName("Efectivo");
        efectivo.setType(PaymentMethodType.CASH);
        efectivo.setAffectsCash(true);
        efectivo.setRequiresReference(false);
        efectivo.setSortOrder((short) 1);
        paymentMethodRepository.save(efectivo);

        VentaResponse venta = ventaService.registrarVenta(new CrearVentaRequest(
                        null, null, sesion.id(), null, null,
                        List.of(new ItemVentaRequest(variante.id(), 5, null, null, null)),
                        List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("200.00"), null))),
                userId, Set.of());

        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isEqualTo(5); // 10 - 5

        List<ReturnableItemResponse> devolvibles = devolucionService.itemsDevolvibles(venta.id());
        assertThat(devolvibles).hasSize(1);
        assertThat(devolvibles.get(0).quantityReturnable()).isEqualTo(5);
        Long lineaId = devolvibles.get(0).saleDetailId();

        // Devolver 2 de 5, con reingreso a stock.
        DevolucionResponse devolucion = devolucionService.registrar(new CrearDevolucionRequest(
                venta.id(), "Talla incorrecta", efectivo.getId(),
                List.of(new ItemDevolucionRequest(lineaId, 2, true))), userId);

        assertThat(devolucion.totalAmount()).isEqualByComparingTo("80.00"); // 2 * 40.00
        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isEqualTo(7); // 5 + 2

        var resumenCaja = cajaService.obtenerResumenCierre(sesion.id());
        // 300 apertura + 200 venta - 80 reembolso = 420.
        assertThat(resumenCaja.expectedAmount()).isEqualByComparingTo("420.00");

        // La venta queda parcialmente devuelta.
        assertThat(ventaService.obtener(venta.id()).status().name()).isEqualTo("PARTIALLY_RETURNED");

        // No se puede devolver más de lo que queda disponible (quedan 3 de 5).
        assertThatThrownBy(() -> devolucionService.registrar(new CrearDevolucionRequest(
                venta.id(), "Exceso", efectivo.getId(),
                List.of(new ItemDevolucionRequest(lineaId, 4, false))), userId))
                .isInstanceOf(ReglaDeNegocioException.class);

        // Devolver el resto (3) sin reingreso a stock (prenda dañada).
        devolucionService.registrar(new CrearDevolucionRequest(
                venta.id(), "Prenda dañada, no se repone", efectivo.getId(),
                List.of(new ItemDevolucionRequest(lineaId, 3, false))), userId);

        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isEqualTo(7); // sin cambio, restock=false
        assertThat(ventaService.obtener(venta.id()).status().name()).isEqualTo("RETURNED"); // ya se devolvió todo

        var listado = devolucionService.listar(venta.id(), PageRequest.of(0, 10));
        assertThat(listado.totalElements()).isEqualTo(2);
    }
}
