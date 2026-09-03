package com.freestyleperu.aplicacion.reserva;

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
import com.freestyleperu.aplicacion.cliente.domain.Customer;
import com.freestyleperu.aplicacion.cliente.repository.CustomerRepository;
import com.freestyleperu.aplicacion.combo.domain.ComboSelectorType;
import com.freestyleperu.aplicacion.combo.dto.request.ComboItemRequest;
import com.freestyleperu.aplicacion.combo.dto.request.ComboRequest;
import com.freestyleperu.aplicacion.combo.dto.response.ComboResponse;
import com.freestyleperu.aplicacion.combo.service.ComboService;
import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
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
import com.freestyleperu.aplicacion.producto.dto.response.VarianteResponse;
import com.freestyleperu.aplicacion.producto.repository.ProductVariantRepository;
import com.freestyleperu.aplicacion.producto.service.ProductoService;
import com.freestyleperu.aplicacion.producto.service.VarianteService;
import com.freestyleperu.aplicacion.promotor.dto.request.PromoterRequest;
import com.freestyleperu.aplicacion.promotor.dto.response.PromoterResponse;
import com.freestyleperu.aplicacion.promotor.service.PromoterService;
import com.freestyleperu.aplicacion.reserva.domain.ReservaStatus;
import com.freestyleperu.aplicacion.reserva.dto.request.CancelarReservaRequest;
import com.freestyleperu.aplicacion.reserva.dto.request.CompletarReservaRequest;
import com.freestyleperu.aplicacion.reserva.dto.request.CompletarVariasReservasRequest;
import com.freestyleperu.aplicacion.reserva.dto.request.CrearReservaRequest;
import com.freestyleperu.aplicacion.reserva.dto.request.ReservaItemRequest;
import com.freestyleperu.aplicacion.reserva.dto.response.ReservaResponse;
import com.freestyleperu.aplicacion.reserva.repository.ReservaRepository;
import com.freestyleperu.aplicacion.reserva.service.ReservaService;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.exception.ReglaDeNegocioException;
import com.freestyleperu.aplicacion.usuario.domain.Rol;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import com.freestyleperu.aplicacion.usuario.domain.UsuarioEstado;
import com.freestyleperu.aplicacion.usuario.repository.RolRepository;
import com.freestyleperu.aplicacion.usuario.repository.UsuarioRepository;
import com.freestyleperu.aplicacion.venta.dto.request.PagoVentaRequest;
import com.freestyleperu.aplicacion.venta.repository.SaleDetailRepository;
import com.freestyleperu.aplicacion.venta.repository.SaleRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReservaFlujoIntegrationTest {

    @Autowired private BranchRepository branchRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private CashRegisterRepository cashRegisterRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AtributoTestFixture atributos;
    @Autowired private PaymentMethodRepository paymentMethodRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CompanySettingsRepository companySettingsRepository;
    @Autowired private ProductoService productoService;
    @Autowired private VarianteService varianteService;
    @Autowired private CajaService cajaService;
    @Autowired private PromoterService promoterService;
    @Autowired private ComboService comboService;
    @Autowired private ReservaService reservaService;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private ProductVariantRepository variantRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private SaleDetailRepository saleDetailRepository;

    @Test
    void crearReservaRetiraStockDeInmediatoYRechazaSenaEnEfectivoOMayorAlTotal() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.reserva").getId();
        abrirCaja(userId);
        VarianteResponse variante = crearVarianteConStock("Casaca Denim", "Negro", "L", new BigDecimal("120.00"), 10);
        Customer cliente = nuevoCliente("cliente.reserva@test.com");
        PaymentMethod yape = metodoPago("YAPE");
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        // La seña no puede pagarse en efectivo.
        assertThatThrownBy(() -> reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null, unItem(variante.id(), 1), new BigDecimal("20.00"), efectivo.getId(), null, null, null),
                userId)).isInstanceOf(ReglaDeNegocioException.class);

        // La seña no puede superar el total (120.00).
        assertThatThrownBy(() -> reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null, unItem(variante.id(), 1), new BigDecimal("200.00"), yape.getId(), null, null, null),
                userId)).isInstanceOf(ReglaDeNegocioException.class);

        ReservaResponse reserva = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null, unItem(variante.id(), 2), new BigDecimal("20.00"), yape.getId(), "OP-999", null,
                "Aparta para el sábado"), userId);

        assertThat(reserva.status()).isEqualTo(ReservaStatus.RESERVADO);
        assertThat(reserva.total()).isEqualByComparingTo("240.00");
        assertThat(reserva.reservationNumber()).startsWith("RES-");

        // El stock baja de inmediato (10 -> 8), a diferencia de un pedido online.
        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isEqualTo(8);
    }

    @Test
    void crearReservaParaCompradorOcasionalNoExigeClienteRegistrado() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.ocasional").getId();
        abrirCaja(userId);
        VarianteResponse variante = crearVarianteConStock("Casaca Live TikTok", "Negro", "M", new BigDecimal("80.00"), 5);
        PaymentMethod yape = metodoPago("YAPE");

        ReservaResponse reserva = reservaService.crear(new CrearReservaRequest(
                null, "Comprador del live", "987654321", unItem(variante.id(), 1), new BigDecimal("20.00"), yape.getId(), null, null,
                null), userId);

        assertThat(reserva.customerId()).isNull();
        assertThat(reserva.guest()).isTrue();
        assertThat(reserva.customerName()).isEqualTo("Comprador del live");
        assertThat(reserva.guestPhone()).isEqualTo("987654321");

        // El stock se retira igual que con un cliente registrado.
        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isEqualTo(4);
    }

    @Test
    void crearReservaSinClienteNiNombreDeCompradorEsRechazada() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.sindatos").getId();
        abrirCaja(userId);
        VarianteResponse variante = crearVarianteConStock("Gorra Live", "Azul", "Única", new BigDecimal("40.00"), 5);
        PaymentMethod yape = metodoPago("YAPE");

        assertThatThrownBy(() -> reservaService.crear(new CrearReservaRequest(
                null, null, null, unItem(variante.id(), 1), new BigDecimal("20.00"), yape.getId(), null, null, null), userId))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void crearReservaUsaMontoDeSenaPorDefectoCuandoNoSeEspecifica() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.senadefecto").getId();
        abrirCaja(userId);
        VarianteResponse variante = crearVarianteConStock("Polo Basico", "Blanco", "M", new BigDecimal("50.00"), 5);
        Customer cliente = nuevoCliente("cliente.senadefecto@test.com");
        PaymentMethod yape = metodoPago("YAPE");

        ReservaResponse reserva = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null, unItem(variante.id(), 1), null, yape.getId(), null, null, null), userId);

        assertThat(reserva.depositAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void crearReservaConVariasLineasSueltasSumaCadaUnaAPrecioNormal() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.multilineas").getId();
        abrirCaja(userId);
        VarianteResponse polo = crearVarianteConStock("PoloMultilinea", "Blanco", "M", new BigDecimal("40.00"), 5);
        VarianteResponse casaca = crearVarianteConStock("CasacaMultilinea", "Negro", "L", new BigDecimal("120.00"), 5);
        Customer cliente = nuevoCliente("cliente.multilineas@test.com");
        PaymentMethod yape = metodoPago("YAPE");

        ReservaResponse reserva = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null,
                List.of(new ReservaItemRequest(polo.id(), 3, null, null), new ReservaItemRequest(casaca.id(), 1, null, null)),
                new BigDecimal("20.00"), yape.getId(), null, null, "3 polos + 1 casaca del live"), userId);

        // 3 x 40 + 1 x 120 = 240.
        assertThat(reserva.total()).isEqualByComparingTo("240.00");
        assertThat(reserva.items()).hasSize(2);
        assertThat(variantRepository.findById(polo.id()).orElseThrow().getStock()).isEqualTo(2);
        assertThat(variantRepository.findById(casaca.id()).orElseThrow().getStock()).isEqualTo(4);
    }

    @Test
    void crearReservaConComboAplicaPrecioFijoYRepartoProporcional() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.combocreacion").getId();
        abrirCaja(userId);
        VarianteResponse casaca = crearVarianteConStock("CasacaCombo", "Negro", "L", new BigDecimal("100.00"), 5);
        VarianteResponse pantalon = crearVarianteConStock("PantalonCombo", "Azul", "32", new BigDecimal("80.00"), 5);
        Customer cliente = nuevoCliente("cliente.combocreacion@test.com");
        PaymentMethod yape = metodoPago("YAPE");

        ComboResponse combo = comboService.crear(new ComboRequest(
                "COMBO-CREACION", "Casaca + Pantalón", new BigDecimal("150.00"),
                List.of(
                        new ComboItemRequest(ComboSelectorType.PRODUCT, casaca.productId(), null, null, 1),
                        new ComboItemRequest(ComboSelectorType.PRODUCT, pantalon.productId(), null, null, 1))));

        ReservaResponse reserva = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null,
                List.of(
                        new ReservaItemRequest(casaca.id(), 1, combo.id(), 0),
                        new ReservaItemRequest(pantalon.id(), 1, combo.id(), 0)),
                new BigDecimal("20.00"), yape.getId(), null, null, "Combo elegido en el live"), userId);

        // Normal 180, combo 150 -> descuento 30 repartido proporcionalmente (100/180 y 80/180).
        assertThat(reserva.total()).isEqualByComparingTo("150.00");
        assertThat(reserva.items()).hasSize(2);
        assertThat(reserva.items()).allSatisfy(i -> assertThat(i.comboId()).isEqualTo(combo.id()));
        BigDecimal descuentoTotal = reserva.items().stream().map(i -> i.discountAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(descuentoTotal).isEqualByComparingTo("30.00");
    }

    @Test
    void crearReservaConComboIncompletoEsRechazada() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.comboincompleto").getId();
        abrirCaja(userId);
        VarianteResponse casaca = crearVarianteConStock("CasacaIncompleto", "Negro", "L", new BigDecimal("100.00"), 5);
        VarianteResponse pantalon = crearVarianteConStock("PantalonIncomp", "Azul", "32", new BigDecimal("80.00"), 5);
        Customer cliente = nuevoCliente("cliente.comboincompleto@test.com");
        PaymentMethod yape = metodoPago("YAPE");

        ComboResponse combo = comboService.crear(new ComboRequest(
                "COMBO-INCOMPLETO", "Casaca + Pantalón incompleto", new BigDecimal("150.00"),
                List.of(
                        new ComboItemRequest(ComboSelectorType.PRODUCT, casaca.productId(), null, null, 1),
                        new ComboItemRequest(ComboSelectorType.PRODUCT, pantalon.productId(), null, null, 1))));

        // Solo se elige la casaca, falta el pantalón para completar el combo.
        assertThatThrownBy(() -> reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null, List.of(new ReservaItemRequest(casaca.id(), 1, combo.id(), 0)),
                new BigDecimal("20.00"), yape.getId(), null, null, null), userId))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("Faltan productos");
    }

    @Test
    void crearReservaConDosAplicacionesDelMismoComboMultiplicaElPrecio() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.combomultiple").getId();
        abrirCaja(userId);
        VarianteResponse polo = crearVarianteConStock("PoloComboMultiple", "Blanco", "M", new BigDecimal("30.00"), 20);
        Customer cliente = nuevoCliente("cliente.combomultiple@test.com");
        PaymentMethod yape = metodoPago("YAPE");

        ComboResponse combo = comboService.crear(new ComboRequest(
                "COMBO-4-POLOS", "4 polos x 100", new BigDecimal("100.00"),
                List.of(new ComboItemRequest(ComboSelectorType.PRODUCT, polo.productId(), null, null, 4))));

        // 8 polos = 2 aplicaciones del combo de 4 (comboGroup 0 y 1) -> 2 x 100 = 200.
        ReservaResponse reserva = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null,
                List.of(
                        new ReservaItemRequest(polo.id(), 4, combo.id(), 0),
                        new ReservaItemRequest(polo.id(), 4, combo.id(), 1)),
                new BigDecimal("20.00"), yape.getId(), null, null, "8 polos del live"), userId);

        assertThat(reserva.total()).isEqualByComparingTo("200.00");
        assertThat(reserva.items()).hasSize(2);
        assertThat(variantRepository.findById(polo.id()).orElseThrow().getStock()).isEqualTo(12);
    }

    @Test
    void completarReservaGeneraVentaConPromotorYSoloAfectaCajaConElPagoFinalEnEfectivo() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.completar").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        VarianteResponse variante = crearVarianteConStock("Casaca Cuero", "Marrón", "M", new BigDecimal("300.00"), 5);
        Customer cliente = nuevoCliente("cliente.completar@test.com");
        PaymentMethod yape = metodoPago("YAPE");
        PaymentMethod efectivo = metodoPago("EFECTIVO");
        PromoterResponse promotor = promoterService.crear(new PromoterRequest("Promotor Live TikTok"));

        ReservaResponse reserva = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null, unItem(variante.id(), 1), new BigDecimal("50.00"), yape.getId(), "OP-1", promotor.id(),
                null), userId);

        // El saldo pendiente (250.00) debe coincidir exactamente con la suma de pagos.
        assertThatThrownBy(() -> reservaService.completar(reserva.id(), new CompletarReservaRequest(
                sesion.id(), List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("100.00"), null))), userId))
                .isInstanceOf(ReglaDeNegocioException.class);

        ReservaResponse completada = reservaService.completar(reserva.id(), new CompletarReservaRequest(
                sesion.id(), List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("250.00"), null))), userId);

        assertThat(completada.status()).isEqualTo(ReservaStatus.COMPLETADO);
        assertThat(completada.saleId()).isNotNull();
        assertThat(completada.promoterId()).isEqualTo(promotor.id());

        // El stock no se vuelve a descontar al completar (ya bajó al crear la reserva: 5 -> 4).
        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isEqualTo(4);

        // Solo el pago final en efectivo (250) afecta la caja, la seña por Yape (50) no.
        var resumen = cajaService.obtenerResumenCierre(sesion.id());
        assertThat(resumen.expectedAmount()).isEqualByComparingTo("550.00"); // 300 apertura + 250 efectivo

        // No se puede completar una separación dos veces.
        assertThatThrownBy(() -> reservaService.completar(reserva.id(), new CompletarReservaRequest(
                sesion.id(), List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("250.00"), null))), userId))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void completarReservaConLineaDeComboYLineaSueltaGeneraUnSaleDetailPorLinea() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.completarmixto").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        VarianteResponse casaca = crearVarianteConStock("CasacaCompMixto", "Negro", "L", new BigDecimal("100.00"), 5);
        VarianteResponse pantalon = crearVarianteConStock("PantalonMixto", "Azul", "32", new BigDecimal("80.00"), 5);
        VarianteResponse gorra = crearVarianteConStock("GorraMixto", "Blanco", "Única", new BigDecimal("25.00"), 5);
        Customer cliente = nuevoCliente("cliente.completarmixto@test.com");
        PaymentMethod yape = metodoPago("YAPE");
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        ComboResponse combo = comboService.crear(new ComboRequest(
                "COMBO-COMPLETAR-MIXTO", "Casaca + Pantalón mixto", new BigDecimal("150.00"),
                List.of(
                        new ComboItemRequest(ComboSelectorType.PRODUCT, casaca.productId(), null, null, 1),
                        new ComboItemRequest(ComboSelectorType.PRODUCT, pantalon.productId(), null, null, 1))));

        ReservaResponse reserva = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null,
                List.of(
                        new ReservaItemRequest(casaca.id(), 1, combo.id(), 0),
                        new ReservaItemRequest(pantalon.id(), 1, combo.id(), 0),
                        new ReservaItemRequest(gorra.id(), 1, null, null)),
                new BigDecimal("20.00"), yape.getId(), null, null, null), userId);

        // Total: 150 (combo) + 25 (gorra suelta) = 175. Saldo: 175 - 20 = 155.
        assertThat(reserva.total()).isEqualByComparingTo("175.00");

        ReservaResponse completada = reservaService.completar(reserva.id(), new CompletarReservaRequest(
                sesion.id(), List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("155.00"), null))), userId);

        var detalles = saleDetailRepository.findAllBySaleId(completada.saleId());
        assertThat(detalles).hasSize(3);
        assertThat(detalles).filteredOn(d -> d.getCombo() != null).hasSize(2)
                .allSatisfy(d -> assertThat(d.getCombo().getId()).isEqualTo(combo.id()));
        assertThat(detalles).filteredOn(d -> d.getCombo() == null).hasSize(1)
                .allSatisfy(d -> assertThat(d.getSubtotal()).isEqualByComparingTo("25.00"));
    }

    @Test
    void completarReservaRequiereSesionDeCajaAbierta() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.sincaja").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        VarianteResponse variante = crearVarianteConStock("Zapatilla Urbana", "Blanco", "42", new BigDecimal("180.00"), 3);
        Customer cliente = nuevoCliente("cliente.sincaja@test.com");
        PaymentMethod yape = metodoPago("YAPE");
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        ReservaResponse reserva = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null, unItem(variante.id(), 1), new BigDecimal("20.00"), yape.getId(), null, null, null),
                userId);

        cajaService.cerrarCaja(sesion.id(), new com.freestyleperu.aplicacion.caja.dto.request.CerrarCajaRequest(
                new BigDecimal("300.00"), null), userId);

        assertThatThrownBy(() -> reservaService.completar(reserva.id(), new CompletarReservaRequest(
                sesion.id(), List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("160.00"), null))), userId))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void cancelarReservaLiberaStockYNoPermiteCompletarNiCancelarDeNuevo() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.cancelar").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        VarianteResponse variante = crearVarianteConStock("Pantalon Jogger", "Gris", "L", new BigDecimal("90.00"), 4);
        Customer cliente = nuevoCliente("cliente.cancelar@test.com");
        PaymentMethod yape = metodoPago("YAPE");
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        ReservaResponse reserva = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null, unItem(variante.id(), 1), new BigDecimal("20.00"), yape.getId(), null, null, null),
                userId);
        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isEqualTo(3);

        ReservaResponse cancelada = reservaService.cancelar(reserva.id(),
                new CancelarReservaRequest("El cliente ya no la quiere"), userId);

        assertThat(cancelada.status()).isEqualTo(ReservaStatus.CANCELADO);
        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isEqualTo(4);

        assertThatThrownBy(() -> reservaService.cancelar(reserva.id(),
                new CancelarReservaRequest("Otra vez"), userId))
                .isInstanceOf(ReglaDeNegocioException.class);

        assertThatThrownBy(() -> reservaService.completar(reserva.id(), new CompletarReservaRequest(
                sesion.id(), List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("70.00"), null))), userId))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void cancelarReservaConVariasLineasLiberaStockDeTodasSusLineas() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.cancelarmultilineas").getId();
        abrirCaja(userId);
        VarianteResponse polo = crearVarianteConStock("PoloCancelMulti", "Blanco", "M", new BigDecimal("40.00"), 5);
        VarianteResponse casaca = crearVarianteConStock("CasacaCancelMulti", "Negro", "L", new BigDecimal("120.00"), 5);
        Customer cliente = nuevoCliente("cliente.cancelarmultilineas@test.com");
        PaymentMethod yape = metodoPago("YAPE");

        ReservaResponse reserva = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null,
                List.of(new ReservaItemRequest(polo.id(), 2, null, null), new ReservaItemRequest(casaca.id(), 1, null, null)),
                new BigDecimal("20.00"), yape.getId(), null, null, null), userId);

        assertThat(variantRepository.findById(polo.id()).orElseThrow().getStock()).isEqualTo(3);
        assertThat(variantRepository.findById(casaca.id()).orElseThrow().getStock()).isEqualTo(4);

        reservaService.cancelar(reserva.id(), new CancelarReservaRequest("Ya no lo quiere"), userId);

        assertThat(variantRepository.findById(polo.id()).orElseThrow().getStock()).isEqualTo(5);
        assertThat(variantRepository.findById(casaca.id()).orElseThrow().getStock()).isEqualTo(5);
    }

    @Test
    void vencerSeparacionesPendientesLiberaSoloLasVencidasYDejaElRestoIntacto() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.vencimiento").getId();
        abrirCaja(userId);
        VarianteResponse variante = crearVarianteConStock("Buzo Oversize", "Negro", "XL", new BigDecimal("140.00"), 6);
        Customer cliente = nuevoCliente("cliente.vencimiento@test.com");
        PaymentMethod yape = metodoPago("YAPE");

        ReservaResponse vigente = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null, unItem(variante.id(), 1), new BigDecimal("20.00"), yape.getId(), null, null, null),
                userId);
        ReservaResponse porVencer = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null, unItem(variante.id(), 1), new BigDecimal("20.00"), yape.getId(), null, null, null),
                userId);

        // Forzamos el vencimiento de la segunda reserva retrocediendo su expires_at.
        var entidadPorVencer = reservaRepository.findById(porVencer.id()).orElseThrow();
        entidadPorVencer.setExpiresAt(LocalDateTime.now().minusDays(1));
        reservaRepository.save(entidadPorVencer);

        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isEqualTo(4);

        int vencidas = reservaService.vencerSeparacionesPendientes();

        assertThat(vencidas).isEqualTo(1);
        assertThat(reservaService.obtener(porVencer.id()).status()).isEqualTo(ReservaStatus.VENCIDO);
        assertThat(reservaService.obtener(vigente.id()).status()).isEqualTo(ReservaStatus.RESERVADO);

        // Solo se libera el stock de la vencida (4 -> 5), la vigente sigue reteniendo el suyo.
        assertThat(variantRepository.findById(variante.id()).orElseThrow().getStock()).isEqualTo(5);
    }

    @Test
    void completarVariasDetectaComboAutomaticamenteCuandoLasCantidadesCalzan() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.combolive").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        Customer cliente = nuevoCliente("cliente.combolive@test.com");
        PaymentMethod yape = metodoPago("YAPE");
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        VarianteResponse casaca = crearVarianteConStock("CasacaLive", "Negro", "L", new BigDecimal("100.00"), 5);
        VarianteResponse pantalon = crearVarianteConStock("PantalonLive", "Azul", "32", new BigDecimal("80.00"), 5);

        comboService.crear(new ComboRequest(
                "COMBO-LIVE", "Casaca + Pantalón del live", new BigDecimal("150.00"),
                List.of(
                        new ComboItemRequest(ComboSelectorType.PRODUCT, casaca.productId(), null, null, 1),
                        new ComboItemRequest(ComboSelectorType.PRODUCT, pantalon.productId(), null, null, 1))));

        // Dos separaciones hechas en momentos distintos del live, para el mismo cliente.
        ReservaResponse reservaCasaca = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null, unItem(casaca.id(), 1), new BigDecimal("20.00"), yape.getId(), null, null, null), userId);
        ReservaResponse reservaPantalon = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null, unItem(pantalon.id(), 1), new BigDecimal("20.00"), yape.getId(), null, null, null),
                userId);

        // Normal: 100 + 80 = 180. Seña total: 40. Con combo (150): saldo = 110.
        var preview = reservaService.previsualizarCompletarVarias(List.of(reservaCasaca.id(), reservaPantalon.id()));
        assertThat(preview.totalNormal()).isEqualByComparingTo("180.00");
        assertThat(preview.totalFinal()).isEqualByComparingTo("150.00");
        assertThat(preview.comboNombre()).isEqualTo("Casaca + Pantalón del live");
        assertThat(preview.saldoPendiente()).isEqualByComparingTo("110.00");

        List<ReservaResponse> completadas = reservaService.completarVarias(new CompletarVariasReservasRequest(
                List.of(reservaCasaca.id(), reservaPantalon.id()), sesion.id(),
                List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("110.00"), null))), userId);

        assertThat(completadas).allSatisfy(r -> assertThat(r.status()).isEqualTo(ReservaStatus.COMPLETADO));
        assertThat(completadas).allSatisfy(r -> assertThat(r.saleId()).isEqualTo(completadas.get(0).saleId()));

        var venta = ventaRepositoryObtenerPorId(completadas.get(0).saleId());
        assertThat(venta.getTotal()).isEqualByComparingTo("150.00");
        assertThat(venta.getDiscountAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void completarVariasSinComboQueCalceCobraCadaUnaAPrecioNormal() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.sincombolive").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        Customer cliente = nuevoCliente("cliente.sincombolive@test.com");
        PaymentMethod yape = metodoPago("YAPE");
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        VarianteResponse polo = crearVarianteConStock("PoloSueltoLive", "Blanco", "M", new BigDecimal("40.00"), 5);
        VarianteResponse gorra = crearVarianteConStock("GorraSuelta", "Negro", "Única", new BigDecimal("25.00"), 5);

        ReservaResponse reservaPolo = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null, unItem(polo.id(), 1), new BigDecimal("10.00"), yape.getId(), null, null, null), userId);
        ReservaResponse reservaGorra = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null, unItem(gorra.id(), 1), new BigDecimal("10.00"), yape.getId(), null, null, null), userId);

        List<ReservaResponse> completadas = reservaService.completarVarias(new CompletarVariasReservasRequest(
                List.of(reservaPolo.id(), reservaGorra.id()), sesion.id(),
                List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("45.00"), null))), userId);

        var venta = ventaRepositoryObtenerPorId(completadas.get(0).saleId());
        // Sin combo aplicable: 40 + 25 = 65 normal, menos 20 de señas ya pagadas = 45.
        assertThat(venta.getTotal()).isEqualByComparingTo("65.00");
        assertThat(venta.getDiscountAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void completarVariasConUnaReservaYaConComboFijoNoLoReDetectaYLasLineasLibresSiParticipanDeLaDeteccionCruzada() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.combomixtobatch").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        Customer cliente = nuevoCliente("cliente.combomixtobatch@test.com");
        PaymentMethod yape = metodoPago("YAPE");
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        VarianteResponse gorra = crearVarianteConStock("GorraFija", "Negro", "Única", new BigDecimal("30.00"), 5);
        VarianteResponse casaca = crearVarianteConStock("CasacaCruzada", "Negro", "L", new BigDecimal("100.00"), 5);
        VarianteResponse pantalon = crearVarianteConStock("PantalonCruzado", "Azul", "32", new BigDecimal("80.00"), 5);

        ComboResponse comboFijo = comboService.crear(new ComboRequest(
                "COMBO-FIJO-BATCH", "Gorra en oferta fija", new BigDecimal("25.00"),
                List.of(new ComboItemRequest(ComboSelectorType.PRODUCT, gorra.productId(), null, null, 1))));
        ComboResponse comboCruzado = comboService.crear(new ComboRequest(
                "COMBO-CRUZADO-BATCH", "Casaca + Pantalón cruzado", new BigDecimal("150.00"),
                List.of(
                        new ComboItemRequest(ComboSelectorType.PRODUCT, casaca.productId(), null, null, 1),
                        new ComboItemRequest(ComboSelectorType.PRODUCT, pantalon.productId(), null, null, 1))));

        // Una separación ya trae la gorra con su combo fijado desde la creación.
        ReservaResponse reservaFija = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null, List.of(new ReservaItemRequest(gorra.id(), 1, comboFijo.id(), 0)),
                new BigDecimal("10.00"), yape.getId(), null, null, null), userId);
        // La otra trae casaca y pantalón sueltos, hechas en momentos distintos del live.
        ReservaResponse reservaCasaca = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null, unItem(casaca.id(), 1), new BigDecimal("10.00"), yape.getId(), null, null, null), userId);
        ReservaResponse reservaPantalon = reservaService.crear(new CrearReservaRequest(
                cliente.getId(), null, null, unItem(pantalon.id(), 1), new BigDecimal("10.00"), yape.getId(), null, null, null),
                userId);

        // Total: 25 (gorra ya fija) + 150 (casaca+pantalón recién detectado) = 175.
        var preview = reservaService.previsualizarCompletarVarias(
                List.of(reservaFija.id(), reservaCasaca.id(), reservaPantalon.id()));
        assertThat(preview.totalFinal()).isEqualByComparingTo("175.00");
        assertThat(preview.comboNombre()).isEqualTo("Casaca + Pantalón cruzado");

        List<ReservaResponse> completadas = reservaService.completarVarias(new CompletarVariasReservasRequest(
                List.of(reservaFija.id(), reservaCasaca.id(), reservaPantalon.id()), sesion.id(),
                List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("145.00"), null))), userId);

        var detalles = saleDetailRepository.findAllBySaleId(completadas.get(0).saleId());
        assertThat(detalles).hasSize(3);
        assertThat(detalles).filteredOn(d -> d.getVariant().getId().equals(gorra.id())).hasSize(1)
                .allSatisfy(d -> {
                    assertThat(d.getCombo().getId()).isEqualTo(comboFijo.id());
                    assertThat(d.getSubtotal()).isEqualByComparingTo("25.00");
                });
        assertThat(detalles).filteredOn(d -> !d.getVariant().getId().equals(gorra.id())).hasSize(2)
                .allSatisfy(d -> assertThat(d.getCombo().getId()).isEqualTo(comboCruzado.id()));
    }

    @Test
    void completarVariasRechazaSeparacionesDeCompradoresDistintos() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.compradoresdistintos").getId();
        SesionCajaResponse sesion = abrirCaja(userId);
        Customer clienteA = nuevoCliente("cliente.a@test.com");
        Customer clienteB = nuevoCliente("cliente.b@test.com");
        PaymentMethod yape = metodoPago("YAPE");
        PaymentMethod efectivo = metodoPago("EFECTIVO");

        VarianteResponse variante1 = crearVarianteConStock("ProductoA", "Negro", "M", new BigDecimal("30.00"), 5);
        VarianteResponse variante2 = crearVarianteConStock("ProductoB", "Negro", "M", new BigDecimal("30.00"), 5);

        ReservaResponse reservaA = reservaService.crear(new CrearReservaRequest(
                clienteA.getId(), null, null, unItem(variante1.id(), 1), new BigDecimal("10.00"), yape.getId(), null, null, null),
                userId);
        ReservaResponse reservaB = reservaService.crear(new CrearReservaRequest(
                clienteB.getId(), null, null, unItem(variante2.id(), 1), new BigDecimal("10.00"), yape.getId(), null, null, null),
                userId);

        CompletarVariasReservasRequest request = new CompletarVariasReservasRequest(
                List.of(reservaA.id(), reservaB.id()), sesion.id(),
                List.of(new PagoVentaRequest(efectivo.getId(), new BigDecimal("40.00"), null)));

        assertThatThrownBy(() -> reservaService.completarVarias(request, userId))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void listarBuscaPorNombreDeCompradorRegistradoYOcasional() {
        sembrarConfiguracion();
        Long userId = nuevoUsuario("cajero.buscar").getId();
        abrirCaja(userId);
        Customer registrado = nuevoCliente("cliente.buscar@test.com");
        VarianteResponse variante = crearVarianteConStock("ProductoBuscar", "Negro", "M", new BigDecimal("30.00"), 5);
        PaymentMethod yape = metodoPago("YAPE");

        reservaService.crear(new CrearReservaRequest(
                registrado.getId(), null, null, unItem(variante.id(), 1), new BigDecimal("10.00"), yape.getId(), null, null, null),
                userId);
        reservaService.crear(new CrearReservaRequest(
                null, "Comprador Ocasional Buscable", "999888777", unItem(variante.id(), 1), new BigDecimal("10.00"), yape.getId(),
                null, null, null), userId);

        var porRegistrado = reservaService.listar(null, null, registrado.getFullName(), org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(porRegistrado.content()).hasSize(1);

        var porOcasional = reservaService.listar(null, null, "Ocasional Buscable", org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(porOcasional.content()).hasSize(1);
    }

    private List<ReservaItemRequest> unItem(Long variantId, int quantity) {
        return List.of(new ReservaItemRequest(variantId, quantity, null, null));
    }

    private com.freestyleperu.aplicacion.venta.domain.Sale ventaRepositoryObtenerPorId(Long saleId) {
        return saleRepository.findById(saleId).orElseThrow();
    }

    private void sembrarConfiguracion() {
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
        settings.setPlan(Plan.PROFESIONAL);
        settings.setSubscriptionStatus(SubscriptionStatus.ACTIVA);
        settings.setUpdatedAt(LocalDateTime.now());
        companySettingsRepository.save(settings);
    }

    private SesionCajaResponse abrirCaja(Long userId) {
        Branch branch = new Branch();
        branch.setCode("SUC-RESERVA-TEST");
        branch.setName("Sucursal reserva test");
        branchRepository.save(branch);

        Warehouse warehouse = new Warehouse();
        warehouse.setBranch(branch);
        warehouse.setCode("ALM-RESERVA-TEST");
        warehouse.setName("Almacén reserva test");
        warehouseRepository.save(warehouse);

        CashRegister register = new CashRegister();
        register.setBranch(branch);
        register.setCode("CAJA-RESERVA-TEST");
        register.setName("Caja reserva test");
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

    private Customer nuevoCliente(String email) {
        Customer customer = new Customer();
        customer.setFullName("Cliente de prueba");
        customer.setEmail(email);
        customer.setStatus(EstadoGeneral.ACTIVE);
        return customerRepository.save(customer);
    }

    private Usuario nuevoUsuario(String username) {
        Rol rol = new Rol();
        rol.setCode("TEST_ROL_RESERVA_" + username.hashCode());
        rol.setName("Rol de prueba reserva");
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
