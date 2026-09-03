package com.freestyleperu.aplicacion.busqueda;

import static org.assertj.core.api.Assertions.assertThat;

import com.freestyleperu.aplicacion.busqueda.dto.response.SearchResponse;
import com.freestyleperu.aplicacion.busqueda.service.BusquedaService;
import com.freestyleperu.aplicacion.caja.domain.CashRegister;
import com.freestyleperu.aplicacion.caja.dto.request.AbrirCajaRequest;
import com.freestyleperu.aplicacion.caja.dto.response.SesionCajaResponse;
import com.freestyleperu.aplicacion.caja.repository.CashRegisterRepository;
import com.freestyleperu.aplicacion.caja.service.CajaService;
import com.freestyleperu.aplicacion.catalogo.domain.AttributeValue;
import com.freestyleperu.aplicacion.catalogo.domain.Category;
import com.freestyleperu.aplicacion.catalogo.repository.CategoryRepository;
import com.freestyleperu.aplicacion.producto.AtributoTestFixture;
import com.freestyleperu.aplicacion.cliente.domain.Customer;
import com.freestyleperu.aplicacion.cliente.domain.TipoDocumento;
import com.freestyleperu.aplicacion.cliente.repository.CustomerRepository;
import com.freestyleperu.aplicacion.inventario.domain.Branch;
import com.freestyleperu.aplicacion.inventario.domain.Warehouse;
import com.freestyleperu.aplicacion.inventario.repository.BranchRepository;
import com.freestyleperu.aplicacion.inventario.repository.WarehouseRepository;
import com.freestyleperu.aplicacion.pago.domain.PaymentMethod;
import com.freestyleperu.aplicacion.pago.repository.PaymentMethodRepository;
import com.freestyleperu.aplicacion.producto.dto.request.CrearProductoRequest;
import com.freestyleperu.aplicacion.producto.dto.request.CrearVarianteRequest;
import com.freestyleperu.aplicacion.producto.dto.response.ProductoDetalleResponse;
import com.freestyleperu.aplicacion.producto.dto.response.VarianteResponse;
import com.freestyleperu.aplicacion.producto.service.ProductoService;
import com.freestyleperu.aplicacion.producto.service.VarianteService;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.security.AuthenticatedUser;
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
class BusquedaFlujoIntegrationTest {

    @Autowired private BusquedaService busquedaService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AtributoTestFixture atributos;
    @Autowired private PaymentMethodRepository paymentMethodRepository;
    @Autowired private ProductoService productoService;
    @Autowired private VarianteService varianteService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private CashRegisterRepository cashRegisterRepository;
    @Autowired private CajaService cajaService;
    @Autowired private VentaService ventaService;

    private static final Set<String> TODOS_LOS_PERMISOS = Set.of(
            Permisos.PRODUCTOS_CONSULTAR, Permisos.CLIENTES_CONSULTAR, Permisos.VENTAS_CONSULTAR, Permisos.USUARIOS_CONSULTAR);

    @Test
    void encuentraCadaTipoDeEntidadPorSuCampoDeBusqueda() {
        Long staffId = nuevoStaff("busqueda.staff").getId();
        SesionCajaResponse sesion = abrirCaja(staffId);
        VarianteResponse variante = crearVarianteConStock("Casaca Búsqueda", "Negro", "M", new BigDecimal("100.00"), 5);
        PaymentMethod efectivo = metodoPago("EFECTIVO");
        Customer cliente = nuevoCliente("Roberto Fujimori", "45123456");

        CrearVentaRequest ventaRequest = new CrearVentaRequest(
                cliente.getId(), null, sesion.id(), null, null,
                List.of(new ItemVentaRequest(variante.id(), 1, null, null, null)),
                List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("100.00"), null)));
        VentaResponse venta = ventaService.registrarVenta(ventaRequest, staffId, Set.of());

        AuthenticatedUser usuarioConTodo = new AuthenticatedUser(staffId, "busqueda.staff", TODOS_LOS_PERMISOS, 1L);

        // Producto: por nombre.
        SearchResponse porNombre = busquedaService.buscar("Casaca Búsqueda", usuarioConTodo);
        assertThat(porNombre.products()).extracting("id").contains(variante.productId());

        // Producto: por código de barras de la variante (no del producto).
        SearchResponse porBarcode = busquedaService.buscar(variante.sku(), usuarioConTodo);
        assertThat(porBarcode.products()).extracting("id").contains(variante.productId());

        // Cliente: por documento.
        SearchResponse porDocumento = busquedaService.buscar("45123456", usuarioConTodo);
        assertThat(porDocumento.customers()).extracting("title").contains("Roberto Fujimori");

        // Venta: por número de venta.
        SearchResponse porNumeroVenta = busquedaService.buscar(venta.saleNumber(), usuarioConTodo);
        assertThat(porNumeroVenta.sales()).extracting("id").contains(venta.id());

        // Usuario: por username.
        SearchResponse porUsername = busquedaService.buscar("busqueda.staff", usuarioConTodo);
        assertThat(porUsername.users()).extracting("subtitle").contains("busqueda.staff");
    }

    @Test
    void cadaCategoriaSoloApareceSiElUsuarioTieneElPermisoDeConsultarla() {
        nuevoCliente("Cliente Sin Permiso", "78912345");
        AuthenticatedUser sinPermisos = new AuthenticatedUser(999L, "sin.permisos", Set.of(), 1L);

        SearchResponse resultado = busquedaService.buscar("Cliente Sin Permiso", sinPermisos);

        assertThat(resultado.customers()).isEmpty();
        assertThat(resultado.products()).isEmpty();
        assertThat(resultado.sales()).isEmpty();
        assertThat(resultado.users()).isEmpty();

        AuthenticatedUser soloClientes = new AuthenticatedUser(999L, "solo.clientes", Set.of(Permisos.CLIENTES_CONSULTAR), 1L);
        SearchResponse conPermiso = busquedaService.buscar("Cliente Sin Permiso", soloClientes);
        assertThat(conPermiso.customers()).extracting("title").contains("Cliente Sin Permiso");
    }

    private SesionCajaResponse abrirCaja(Long userId) {
        Branch branch = new Branch();
        branch.setCode("SUC-BUSQUEDA-TEST");
        branch.setName("Sucursal búsqueda test");
        branchRepository.save(branch);

        Warehouse warehouse = new Warehouse();
        warehouse.setBranch(branch);
        warehouse.setCode("ALM-BUSQUEDA-TEST");
        warehouse.setName("Almacén búsqueda test");
        warehouseRepository.save(warehouse);

        CashRegister register = new CashRegister();
        register.setBranch(branch);
        register.setCode("CAJA-BUSQUEDA-TEST");
        register.setName("Caja búsqueda test");
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
                .orElseGet(() -> {
                    PaymentMethod method = new PaymentMethod();
                    method.setCode(code);
                    method.setName(code);
                    method.setType(com.freestyleperu.aplicacion.pago.domain.PaymentMethodType.CASH);
                    method.setAffectsCash(true);
                    method.setRequiresReference(false);
                    method.setSortOrder((short) 1);
                    return paymentMethodRepository.save(method);
                });
    }

    private Customer nuevoCliente(String nombre, String docNumber) {
        Customer customer = new Customer();
        customer.setFullName(nombre);
        customer.setDocType(TipoDocumento.DNI);
        customer.setDocNumber(docNumber);
        customer.setStatus(EstadoGeneral.ACTIVE);
        return customerRepository.save(customer);
    }

    private Usuario nuevoStaff(String username) {
        Rol rol = new Rol();
        rol.setCode("TEST_ROL_BUSQUEDA_" + username.hashCode());
        rol.setName("Rol de prueba búsqueda");
        rol.setSystem(false);
        rolRepository.save(rol);

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPasswordHash("hash");
        usuario.setFullName("Staff de Prueba Búsqueda");
        usuario.setStatus(UsuarioEstado.ACTIVE);
        usuario.setRoles(new HashSet<>(List.of(rol)));
        return usuarioRepository.save(usuario);
    }
}
