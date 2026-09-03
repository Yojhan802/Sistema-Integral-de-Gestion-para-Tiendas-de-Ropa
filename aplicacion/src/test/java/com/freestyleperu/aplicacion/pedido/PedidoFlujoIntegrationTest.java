package com.freestyleperu.aplicacion.pedido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.freestyleperu.aplicacion.catalogo.domain.Category;
import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.catalogo.domain.AttributeValue;
import com.freestyleperu.aplicacion.catalogo.repository.CategoryRepository;
import com.freestyleperu.aplicacion.cliente.domain.Customer;
import com.freestyleperu.aplicacion.cliente.repository.CustomerRepository;
import com.freestyleperu.aplicacion.inventario.domain.Branch;
import com.freestyleperu.aplicacion.inventario.domain.Warehouse;
import com.freestyleperu.aplicacion.inventario.repository.BranchRepository;
import com.freestyleperu.aplicacion.inventario.repository.WarehouseRepository;
import com.freestyleperu.aplicacion.notificacion.service.NotificacionService;
import com.freestyleperu.aplicacion.pago.domain.PaymentMethod;
import com.freestyleperu.aplicacion.pago.domain.PaymentMethodType;
import com.freestyleperu.aplicacion.pago.repository.PaymentMethodRepository;
import com.freestyleperu.aplicacion.pedido.domain.PedidoStatus;
import com.freestyleperu.aplicacion.pedido.dto.request.CancelarPedidoRequest;
import com.freestyleperu.aplicacion.pedido.dto.request.CrearPedidoRequest;
import com.freestyleperu.aplicacion.pedido.dto.request.ItemPedidoRequest;
import com.freestyleperu.aplicacion.pedido.dto.response.PedidoResponse;
import com.freestyleperu.aplicacion.pedido.service.PedidoService;
import com.freestyleperu.aplicacion.producto.AtributoTestFixture;
import com.freestyleperu.aplicacion.producto.dto.request.CrearProductoRequest;
import com.freestyleperu.aplicacion.producto.dto.request.CrearVarianteRequest;
import com.freestyleperu.aplicacion.producto.dto.response.ProductoDetalleResponse;
import com.freestyleperu.aplicacion.producto.dto.response.VarianteResponse;
import com.freestyleperu.aplicacion.producto.repository.ProductVariantRepository;
import com.freestyleperu.aplicacion.producto.service.ProductoService;
import com.freestyleperu.aplicacion.producto.service.VarianteService;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.exception.ReglaDeNegocioException;
import com.freestyleperu.aplicacion.shared.exception.StockInsuficienteException;
import com.freestyleperu.aplicacion.usuario.domain.Rol;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import com.freestyleperu.aplicacion.usuario.domain.UsuarioEstado;
import com.freestyleperu.aplicacion.usuario.repository.RolRepository;
import com.freestyleperu.aplicacion.usuario.repository.UsuarioRepository;
import com.freestyleperu.aplicacion.venta.domain.Sale;
import com.freestyleperu.aplicacion.venta.domain.SaleStatus;
import com.freestyleperu.aplicacion.venta.repository.PaymentRepository;
import com.freestyleperu.aplicacion.venta.repository.SaleDetailRepository;
import com.freestyleperu.aplicacion.venta.repository.SaleRepository;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PedidoFlujoIntegrationTest {

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AtributoTestFixture atributos;
    @Autowired private PaymentMethodRepository paymentMethodRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProductoService productoService;
    @Autowired private VarianteService varianteService;
    @Autowired private ProductVariantRepository variantRepository;
    @Autowired private PedidoService pedidoService;
    @Autowired private BranchRepository branchRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private CompanySettingsRepository companySettingsRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private SaleDetailRepository saleDetailRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private NotificacionService notificacionService;
    @Autowired private jakarta.validation.Validator validator;

    /** PedidoService.resolverCostoEnvio exige una tarifa plana configurada (salvo contraentrega). */
    private void aseguraConfiguracionEmpresa() {
        if (companySettingsRepository.existsById(1L)) {
            return;
        }
        CompanySettings settings = new CompanySettings();
        settings.setSlug("default");
        settings.setName("Freestyle Perú (semilla test)");
        settings.setCurrencyCode("PEN");
        settings.setCurrencySymbol("S/");
        settings.setIgvRate(new BigDecimal("0.18"));
        settings.setShippingFlatRate(new BigDecimal("15.00"));
        settings.setReservationDepositAmount(new BigDecimal("20.00"));
        settings.setReservationExpirationDays(3);
        settings.setPlan(Plan.ECOMMERCE);
        settings.setSubscriptionStatus(SubscriptionStatus.ACTIVA);
        settings.setUpdatedAt(java.time.LocalDateTime.now());
        companySettingsRepository.save(settings);
    }

    /**
     * PedidoService.crear() retiene stock a nombre del usuario técnico "sistema_tienda"
     * (ver migración V37) — en producción lo siembra Flyway, pero los tests corren contra H2
     * con Flyway deshabilitado (ver application-test.yml), así que hay que sembrarlo a mano.
     */
    private void aseguraUsuarioSistema() {
        if (usuarioRepository.findByUsername("sistema_tienda").isPresent()) {
            return;
        }
        Usuario sistema = new Usuario();
        sistema.setUsername("sistema_tienda");
        sistema.setPasswordHash("hash");
        sistema.setFullName("Sistema (Tienda Online)");
        sistema.setStatus(UsuarioEstado.INACTIVE);
        usuarioRepository.save(sistema);
    }

    /** InventarioService.registrar exige un almacén activo — confirmarPago/cancelar pasan por ahí. */
    private void aseguraAlmacenActivo(String sufijo) {
        Branch branch = new Branch();
        branch.setCode("SUC-PEDIDO-" + sufijo);
        branch.setName("Sucursal pedido test " + sufijo);
        branchRepository.save(branch);

        Warehouse warehouse = new Warehouse();
        warehouse.setBranch(branch);
        warehouse.setCode("ALM-PEDIDO-" + sufijo);
        warehouse.setName("Almacén pedido test " + sufijo);
        warehouseRepository.save(warehouse);
    }

    @Test
    void creaPedidoRetieneStockDeInmediatoYConfirmarPagoNoLoVuelveATocar() throws Exception {
        aseguraConfiguracionEmpresa();
        aseguraAlmacenActivo("1");
        aseguraUsuarioSistema();
        VarianteResponse variante = crearVarianteConStock("Polo Piqué", "Blanco", "M", new BigDecimal("80.00"), 5);
        PaymentMethod yape = metodoPago("YAPE");
        Long customerId = nuevoCliente("cliente1@test.com").getId();
        Long staffUserId = nuevoStaff("staff.pedidos1").getId();

        // Notificaciones en tiempo real: crear() avisa a staff, confirmarPago() avisa al cliente dueño del pedido.
        CapturingEmitter staffEmitter = new CapturingEmitter();
        inyectarStaffEmitter(staffEmitter);
        CapturingEmitter clienteEmitter = new CapturingEmitter();
        inyectarClienteEmitter(customerId, clienteEmitter);

        PedidoResponse pedido = pedidoService.crear(
                new CrearPedidoRequest(
                        List.of(new ItemPedidoRequest(variante.id(), 2)),
                        yape.getId(), "OP-999", "45678912", "Juan", "Pérez", "García", "999888777", "Av. Siempre Viva 123",
                        "Lima", "Lima", "San Isidro", null),
                customerId);

        assertThat(pedido.status()).isEqualTo(PedidoStatus.PENDING_PAYMENT);
        // 160.00 (2 x 80.00) + 15.00 de envío (tarifa plana sembrada por defecto).
        assertThat(pedido.shippingCost()).isEqualByComparingTo("15.00");
        assertThat(pedido.total()).isEqualByComparingTo("175.00");
        assertThat(pedido.items()).hasSize(1);
        // El stock se retiene de inmediato al crear el pedido (corrección ALTA PED-07) — ya no
        // hace falta esperar a que el staff confirme el pago para que deje de estar disponible.
        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isEqualTo(3);
        assertThat(staffEmitter.eventosRecibidos).isEqualTo(1);
        assertThat(clienteEmitter.eventosRecibidos).isEqualTo(0);

        PedidoResponse confirmado = pedidoService.confirmarPago(pedido.id(), staffUserId);
        assertThat(clienteEmitter.eventosRecibidos).isEqualTo(1);
        assertThat(confirmado.status()).isEqualTo(PedidoStatus.CONFIRMED);
        assertThat(confirmado.confirmedAt()).isNotNull();
        assertThat(confirmado.confirmedByUsername()).isEqualTo("staff.pedidos1");
        // Confirmar el pago no vuelve a tocar el stock — ya estaba retenido desde crear().
        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isEqualTo(3);

        // Confirmar el pago genera una Sale real (sin caja) para que el pedido aparezca en Ventas y tenga ticket.
        assertThat(confirmado.saleId()).isNotNull();
        Sale venta = saleRepository.findById(confirmado.saleId()).orElseThrow();
        assertThat(venta.getCashSession()).isNull();
        assertThat(venta.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(venta.getSubtotal()).isEqualByComparingTo("160.00");
        assertThat(venta.getShippingAmount()).isEqualByComparingTo("15.00");
        assertThat(venta.getTotal()).isEqualByComparingTo("175.00");
        assertThat(saleDetailRepository.findAllBySaleId(venta.getId())).hasSize(1);
        assertThat(paymentRepository.findAllBySaleId(venta.getId())).hasSize(1);

        // No se puede confirmar dos veces.
        assertThatThrownBy(() -> pedidoService.confirmarPago(pedido.id(), staffUserId))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    /**
     * La contratación a distancia exige el consentimiento informado del comprador: el
     * pedido guarda cuándo aceptó y qué versión del texto regía, y el borde HTTP no
     * admite un cuerpo que no acepte (validado sobre el DTO, no sobre el servicio).
     */
    @Test
    void elPedidoSellaLaAceptacionDeTerminosYElBordeRechazaAQuienNoAcepta() {
        aseguraConfiguracionEmpresa();
        aseguraAlmacenActivo("terminos");
        aseguraUsuarioSistema();
        VarianteResponse variante = crearVarianteConStock("Polo Básico", "Negro", "S", new BigDecimal("49.90"), 3);
        PaymentMethod yape = metodoPago("YAPE");
        Long customerId = nuevoCliente("cliente.terminos@test.com").getId();

        PedidoResponse pedido = pedidoService.crear(
                new CrearPedidoRequest(
                        List.of(new ItemPedidoRequest(variante.id(), 1)),
                        yape.getId(), "OP-100", "45678914", "Lucía", "Ramos", "Vega", "922333444",
                        "Av. Bolognesi 890", "Lima", "Lima", "Surco", null, null, null, null,
                        Boolean.TRUE, "2026-09"),
                customerId);

        assertThat(pedido.termsAcceptedAt()).isNotNull();
        assertThat(pedido.termsVersion()).isEqualTo("2026-09");

        CrearPedidoRequest sinAceptar = new CrearPedidoRequest(
                List.of(new ItemPedidoRequest(variante.id(), 1)),
                yape.getId(), "OP-101", "45678915", "Diego", "Salas", "Ruiz", "933444555",
                "Av. Bolognesi 891", "Lima", "Lima", "Surco", null, null, null, null,
                Boolean.FALSE, "2026-09");
        assertThat(validator.validate(sinAceptar))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("acceptedTerms"));
    }

    @Test
    void cancelarUnPedidoConfirmadoReingresaStockYUnoPendienteLiberaLaReserva() {
        aseguraConfiguracionEmpresa();
        aseguraAlmacenActivo("2");
        aseguraUsuarioSistema();
        VarianteResponse variante = crearVarianteConStock("Casaca Jean", "Azul", "L", new BigDecimal("150.00"), 4);
        PaymentMethod yape = metodoPago("YAPE");
        Long customerId = nuevoCliente("cliente2@test.com").getId();
        Long staffUserId = nuevoStaff("staff.pedidos2").getId();

        PedidoResponse confirmado = pedidoService.crear(
                new CrearPedidoRequest(
                        List.of(new ItemPedidoRequest(variante.id(), 2)),
                        yape.getId(), null, "45678913", "Ana", "Torres", "López", "911222333", "Jr. Las Flores 45",
                        "Lima", "Lima", "Miraflores", "Tocar timbre"),
                customerId);
        confirmado = pedidoService.confirmarPago(confirmado.id(), staffUserId);
        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isEqualTo(2);

        PedidoResponse cancelado = pedidoService.cancelar(
                confirmado.id(), new CancelarPedidoRequest("Cliente cambió de opinión"), staffUserId);
        assertThat(cancelado.status()).isEqualTo(PedidoStatus.CANCELLED);
        assertThat(cancelado.cancellationReason()).isEqualTo("Cliente cambió de opinión");
        // El stock vuelve a como estaba antes de confirmar — UNA sola vez, no duplicado por la venta enlazada.
        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isEqualTo(4);

        // La venta generada al confirmar también queda anulada, sin volver a tocar stock/caja.
        Sale venta = saleRepository.findById(confirmado.saleId()).orElseThrow();
        assertThat(venta.getStatus()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(venta.getCancellationReason()).isEqualTo("Cliente cambió de opinión");

        assertThatThrownBy(() -> pedidoService.cancelar(cancelado.id(), new CancelarPedidoRequest("de nuevo"), staffUserId))
                .isInstanceOf(ReglaDeNegocioException.class);

        // Un pedido nunca confirmado SÍ retiene stock al crearse (corrección ALTA PED-07) y lo
        // libera al cancelarse — no debe quedar "perdido" ni tampoco duplicarse.
        PedidoResponse pendiente = pedidoService.crear(
                new CrearPedidoRequest(
                        List.of(new ItemPedidoRequest(variante.id(), 1)),
                        yape.getId(), null, "45678914", "Luis", "Ramos", "Díaz", "955666777", "Calle Sol 1",
                        "Lima", "Lima", "Surco", null),
                customerId);
        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isEqualTo(3);
        pedidoService.cancelar(pendiente.id(), new CancelarPedidoRequest("No completó el pago"), staffUserId);
        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isEqualTo(4);
    }

    @Test
    void rechazaCrearPedidoSinStockYElSegundoPedidoPorLaMismaUnidadYaNoPuedeCrearse() {
        aseguraConfiguracionEmpresa();
        aseguraAlmacenActivo("3");
        aseguraUsuarioSistema();
        VarianteResponse variante = crearVarianteConStock("Gorra Trucker", "Negro", "Única", new BigDecimal("45.00"), 1);
        PaymentMethod yape = metodoPago("YAPE");
        Long customerId = nuevoCliente("cliente3@test.com").getId();
        Long staffUserId = nuevoStaff("staff.pedidos3").getId();

        assertThatThrownBy(() -> pedidoService.crear(
                new CrearPedidoRequest(
                        List.of(new ItemPedidoRequest(variante.id(), 2)),
                        yape.getId(), null, "45000001", "Cliente", "Sin", "Stock", "900000000", "Dirección 1",
                        "Lima", "Lima", "Distrito", null),
                customerId))
                .isInstanceOf(StockInsuficienteException.class);

        // Dos pedidos piden la última unidad: el primero la retiene al crearse (corrección ALTA
        // PED-07); el segundo ya no puede ni siquiera crearse — antes del fix, ambos se creaban
        // como PENDING_PAYMENT y el conflicto solo se detectaba al confirmar el segundo, cuando
        // el cliente ya creía haber comprado con éxito.
        PedidoResponse pedidoA = pedidoService.crear(
                new CrearPedidoRequest(
                        List.of(new ItemPedidoRequest(variante.id(), 1)),
                        yape.getId(), null, "45000002", "Cliente", "A", "Prueba", "900000001", "Dirección 1",
                        "Lima", "Lima", "Distrito", null),
                customerId);
        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isZero();

        assertThatThrownBy(() -> pedidoService.crear(
                new CrearPedidoRequest(
                        List.of(new ItemPedidoRequest(variante.id(), 1)),
                        yape.getId(), null, "45000003", "Cliente", "B", "Prueba", "900000002", "Dirección 2",
                        "Lima", "Lima", "Distrito", null),
                customerId))
                .isInstanceOf(StockInsuficienteException.class);

        // El primer pedido, que sí retuvo el stock a tiempo, se confirma sin problema.
        PedidoResponse confirmado = pedidoService.confirmarPago(pedidoA.id(), staffUserId);
        assertThat(confirmado.status()).isEqualTo(PedidoStatus.CONFIRMED);
        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isZero();
    }

    @Test
    void contraentregaEsGratisSoloEnHuachoYSeRechazaEnCualquierOtroDistrito() {
        aseguraAlmacenActivo("5");
        aseguraUsuarioSistema();
        VarianteResponse variante = crearVarianteConStock("Buzo Canguro", "Gris", "L", new BigDecimal("90.00"), 3);
        PaymentMethod contraentrega = metodoPago("CONTRAENTREGA");
        Long customerId = nuevoCliente("cliente.huacho@test.com").getId();

        PedidoResponse pedidoHuacho = pedidoService.crear(
                new CrearPedidoRequest(
                        List.of(new ItemPedidoRequest(variante.id(), 1)),
                        contraentrega.getId(), null, "45000004", "Cliente", "Huacho", "Prueba", "900111222", "Jr. Comercio 200",
                        "Lima", "Huaura", "Huacho", null),
                customerId);
        assertThat(pedidoHuacho.shippingCost()).isEqualByComparingTo("0.00");
        assertThat(pedidoHuacho.total()).isEqualByComparingTo("90.00");

        assertThatThrownBy(() -> pedidoService.crear(
                new CrearPedidoRequest(
                        List.of(new ItemPedidoRequest(variante.id(), 1)),
                        contraentrega.getId(), null, "45000005", "Cliente", "Otro", "Distrito", "900333444", "Av. Larco 500",
                        "Lima", "Lima", "Miraflores", null),
                customerId))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void comprobanteDePagoSoloLoSubeElDueñoYSoloMientrasEstaPendiente() {
        aseguraConfiguracionEmpresa();
        aseguraAlmacenActivo("4");
        aseguraUsuarioSistema();
        VarianteResponse variante = crearVarianteConStock("Camisa Lino", "Blanco", "M", new BigDecimal("70.00"), 2);
        PaymentMethod yape = metodoPago("YAPE");
        Long customerId = nuevoCliente("cliente.comprobante@test.com").getId();
        Long otroClienteId = nuevoCliente("otro.cliente@test.com").getId();
        Long staffUserId = nuevoStaff("staff.comprobante").getId();

        PedidoResponse pedido = pedidoService.crear(
                new CrearPedidoRequest(
                        List.of(new ItemPedidoRequest(variante.id(), 1)),
                        yape.getId(), "OP-777", "45000006", "Cliente", "Comprobante", "Prueba", "900555666", "Calle Uno 10",
                        "Lima", "Lima", "Surco", null),
                customerId);
        assertThat(pedido.paymentProofUrl()).isNull();

        MockMultipartFile archivo = new MockMultipartFile("file", "comprobante.jpg", "image/jpeg", new byte[] { 1, 2, 3 });

        // Un cliente que no es el dueño no puede subir el comprobante.
        assertThatThrownBy(() -> pedidoService.subirComprobante(pedido.id(), otroClienteId, archivo))
                .isInstanceOf(RecursoNoEncontradoException.class);

        PedidoResponse conComprobante = pedidoService.subirComprobante(pedido.id(), customerId, archivo);
        assertThat(conComprobante.paymentProofUrl()).isNotBlank();

        // Una vez confirmado el pedido, ya no se puede volver a adjuntar comprobante.
        pedidoService.confirmarPago(pedido.id(), staffUserId);
        assertThatThrownBy(() -> pedidoService.subirComprobante(pedido.id(), customerId, archivo))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void rechazaCrearPedidoConMetodoDePagoQueAfectaCaja() {
        aseguraConfiguracionEmpresa();
        VarianteResponse variante = crearVarianteConStock("Short Deportivo", "Negro", "M", new BigDecimal("35.00"), 3);
        PaymentMethod efectivo = paymentMethodRepository.findAllByOrderBySortOrderAsc().stream()
                .filter(m -> m.getCode().equals("EFECTIVO"))
                .findFirst()
                .orElseGet(() -> {
                    PaymentMethod method = new PaymentMethod();
                    method.setCode("EFECTIVO");
                    method.setName("Efectivo");
                    method.setType(PaymentMethodType.CASH);
                    method.setAffectsCash(true);
                    method.setRequiresReference(false);
                    method.setSortOrder((short) 1);
                    return paymentMethodRepository.save(method);
                });
        Long customerId = nuevoCliente("cliente.efectivo@test.com").getId();

        // No tiene sentido pagar en efectivo un checkout online sin cajero presente.
        assertThatThrownBy(() -> pedidoService.crear(
                new CrearPedidoRequest(
                        List.of(new ItemPedidoRequest(variante.id(), 1)),
                        efectivo.getId(), null, "45000007", "Cliente", "Efectivo", "Prueba", "900777888", "Calle Dos 20",
                        "Lima", "Lima", "Surco", null),
                customerId))
                .isInstanceOf(ReglaDeNegocioException.class);
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
                    method.setType(PaymentMethodType.DIGITAL_WALLET);
                    method.setAffectsCash(false);
                    method.setRequiresReference(true);
                    method.setSortOrder((short) 1);
                    return paymentMethodRepository.save(method);
                });
    }

    /** NotificacionService no expone quién está suscrito — se inyecta un emitter de prueba por reflexión (ver NotificacionServiceTest). */
    @SuppressWarnings("unchecked")
    private void inyectarStaffEmitter(SseEmitter emitter) throws Exception {
        Field field = NotificacionService.class.getDeclaredField("staffEmitters");
        field.setAccessible(true);
        ((List<SseEmitter>) field.get(notificacionService)).add(emitter);
    }

    @SuppressWarnings("unchecked")
    private void inyectarClienteEmitter(Long customerId, SseEmitter emitter) throws Exception {
        Field field = NotificacionService.class.getDeclaredField("emittersPorCliente");
        field.setAccessible(true);
        Map<Long, List<SseEmitter>> map = (Map<Long, List<SseEmitter>>) field.get(notificacionService);
        map.computeIfAbsent(customerId, k -> new CopyOnWriteArrayList<>()).add(emitter);
    }

    private static class CapturingEmitter extends SseEmitter {
        int eventosRecibidos = 0;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            eventosRecibidos++;
        }
    }

    private Customer nuevoCliente(String email) {
        Customer customer = new Customer();
        customer.setFullName("Cliente de prueba");
        customer.setEmail(email);
        customer.setStatus(EstadoGeneral.ACTIVE);
        return customerRepository.save(customer);
    }

    private Usuario nuevoStaff(String username) {
        Rol rol = new Rol();
        rol.setCode("TEST_ROL_PEDIDO_" + username.hashCode());
        rol.setName("Rol de prueba pedidos");
        rol.setSystem(false);
        rolRepository.save(rol);

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPasswordHash("hash");
        usuario.setFullName("Staff de Prueba");
        usuario.setStatus(UsuarioEstado.ACTIVE);
        usuario.setRoles(new HashSet<>(List.of(rol)));
        return usuarioRepository.save(usuario);
    }
}
