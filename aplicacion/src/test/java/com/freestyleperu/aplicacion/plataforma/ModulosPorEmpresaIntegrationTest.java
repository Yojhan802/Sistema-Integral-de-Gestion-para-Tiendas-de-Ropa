package com.freestyleperu.aplicacion.plataforma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.freestyleperu.aplicacion.configuracion.domain.BusinessVertical;
import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.plataforma.domain.ModuloSistema;
import com.freestyleperu.aplicacion.plataforma.dto.request.ActualizarModulosRequest;
import com.freestyleperu.aplicacion.plataforma.dto.request.CrearTenantRequest;
import com.freestyleperu.aplicacion.plataforma.dto.response.CambioPaqueteResponse;
import com.freestyleperu.aplicacion.plataforma.dto.response.CrearTenantResponse;
import com.freestyleperu.aplicacion.plataforma.dto.request.ActualizarModulosRequest.ModuloSeleccionado;
import com.freestyleperu.aplicacion.plataforma.dto.response.ModuloResponse;
import com.freestyleperu.aplicacion.plataforma.dto.response.ModulosTenantResponse;
import com.freestyleperu.aplicacion.plataforma.dto.response.PagoSuscripcionResponse;
import com.freestyleperu.aplicacion.plataforma.service.ModuloGate;
import com.freestyleperu.aplicacion.plataforma.service.PlatformModuleService;
import com.freestyleperu.aplicacion.plataforma.service.PlatformTenantService;
import com.freestyleperu.aplicacion.plataforma.service.SubscriptionRenewalService;
import com.freestyleperu.aplicacion.shared.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los módulos contratados sustituyen a la escalera de planes como criterio de acceso.
 * Lo que se fija aquí es que el paquete se pueda recortar al presupuesto del cliente
 * sin dejar nunca una empresa en un estado que reviente en runtime.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ModulosPorEmpresaIntegrationTest {

    private static final Long TENANT = 1L;

    @Autowired private PlatformModuleService moduleService;
    @Autowired private ModuloGate moduloGate;
    @Autowired private PlatformTenantService tenantService;
    @Autowired private SubscriptionRenewalService renewalService;
    @Autowired private CompanySettingsRepository companySettingsRepository;
    @PersistenceContext private EntityManager entityManager;

    @AfterEach
    void limpiarCache() {
        // La resolución de módulos se cachea por tenant; sin esto una prueba vería el
        // conjunto que dejó la anterior.
        moduloGate.invalidar(TENANT);
    }

    /**
     * El gate resuelve la empresa por id 1. La fila se inserta con id autogenerado y en H2
     * el contador avanza aunque la prueba anterior haya hecho rollback, así que se fija el
     * id para que la clase corra aislada.
     */
    private void aseguraEmpresa(Plan plan) {
        CompanySettings settings = companySettingsRepository.findById(TENANT).orElse(null);
        if (settings == null) {
            settings = new CompanySettings();
            settings.setSlug("default");
            settings.setName("Empresa de prueba");
            settings.setCurrencyCode("PEN");
            settings.setCurrencySymbol("S/");
            settings.setIgvRate(new BigDecimal("0.18"));
            settings.setShippingFlatRate(new BigDecimal("15.00"));
            settings.setReservationDepositAmount(new BigDecimal("20.00"));
            settings.setReservationExpirationDays(3);
            settings.setSubscriptionStatus(SubscriptionStatus.ACTIVA);
            settings.setPlan(plan);
            settings.setUpdatedAt(LocalDateTime.now());
            Long generado = companySettingsRepository.saveAndFlush(settings).getId();
            if (!TENANT.equals(generado)) {
                entityManager.createNativeQuery("UPDATE company_settings SET id = 1 WHERE id = :id")
                        .setParameter("id", generado).executeUpdate();
                entityManager.clear();
            }
            settings = companySettingsRepository.findById(TENANT).orElseThrow();
        }
        settings.setPlan(plan);
        settings.setUpdatedAt(LocalDateTime.now());
        companySettingsRepository.saveAndFlush(settings);
        moduloGate.invalidar(TENANT);
    }

    private ModulosTenantResponse contratar(List<ModuloSeleccionado> seleccion) {
        return moduleService.actualizar(TENANT, new ActualizarModulosRequest(seleccion));
    }

    /** El slug y el usuario dueño son únicos globales; un sufijo corto colisiona. */
    private static String unico(String prefijo) {
        return prefijo + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static ModuloSeleccionado con(ModuloSistema modulo, String precio) {
        return new ModuloSeleccionado(modulo, new BigDecimal(precio));
    }

    private static ModuloResponse buscar(ModulosTenantResponse respuesta, ModuloSistema modulo) {
        return respuesta.modulos().stream().filter(m -> m.code() == modulo).findFirst().orElseThrow();
    }

    /**
     * El caso del cliente que ya tiene su sistema de gestión y solo quiere vender por
     * internet: se le cobra la tienda, y el catálogo, el stock y los clientes entran como
     * infraestructura, sin POS ni caja, que es lo que abarata el paquete.
     */
    @Test
    void soloTiendaVirtualArrastraLoImprescindibleYDejaFueraElRestoDelMostrador() {
        aseguraEmpresa(Plan.ECOMMERCE);

        ModulosTenantResponse resultado = contratar(List.of(con(ModuloSistema.TIENDA, "25.00")));

        Set<ModuloSistema> activos = moduloGate.modulosDe(TENANT);
        assertThat(activos).contains(ModuloSistema.TIENDA, ModuloSistema.PRODUCTOS,
                ModuloSistema.INVENTARIO, ModuloSistema.CLIENTES);
        assertThat(activos).doesNotContain(ModuloSistema.POS, ModuloSistema.CAJA,
                ModuloSistema.COMBOS, ModuloSistema.SEPARACIONES, ModuloSistema.PROMOCIONES);

        // Solo se cobra lo que el cliente pidió; lo que se activó por dependencia va a cero.
        assertThat(resultado.totalMensual()).isEqualByComparingTo("25.00");
        assertThat(buscar(resultado, ModuloSistema.INVENTARIO).incluidoPorDependencia()).isTrue();
        assertThat(buscar(resultado, ModuloSistema.INVENTARIO).precioMensual()).isEqualByComparingTo("0.00");
    }

    /**
     * Recortar el paquete tiene que abaratarlo. Si un módulo que antes se cobraba pasa a
     * entrar solo como dependencia, deja de facturarse: conservar su precio anterior
     * inflaba el total justo cuando el operador intenta ajustarlo al presupuesto.
     */
    @Test
    void recortarElPaqueteDejaDeCobrarLoQueSoloQuedaComoDependencia() {
        aseguraEmpresa(Plan.ECOMMERCE);
        contratar(List.of(con(ModuloSistema.PRODUCTOS, "15.00"), con(ModuloSistema.INVENTARIO, "12.00"),
                con(ModuloSistema.POS, "20.00"), con(ModuloSistema.CAJA, "10.00")));

        moduloGate.invalidar(TENANT);
        ModulosTenantResponse recortado = contratar(List.of(con(ModuloSistema.TIENDA, "25.00")));

        assertThat(recortado.totalMensual()).isEqualByComparingTo("25.00");
        assertThat(buscar(recortado, ModuloSistema.PRODUCTOS).precioMensual()).isEqualByComparingTo("0.00");
        assertThat(buscar(recortado, ModuloSistema.INVENTARIO).precioMensual()).isEqualByComparingTo("0.00");
    }

    @Test
    void venderPorInternetObligaAPublicarElLibroDeReclamaciones() {
        aseguraEmpresa(Plan.ECOMMERCE);

        ModulosTenantResponse resultado = contratar(List.of(con(ModuloSistema.TIENDA, "25.00")));

        // Es una obligación del proveedor (D.S. 011-2011-PCM), no un extra facturable.
        assertThat(moduloGate.modulosDe(TENANT)).contains(ModuloSistema.RECLAMOS);
        ModuloResponse reclamos = buscar(resultado, ModuloSistema.RECLAMOS);
        assertThat(reclamos.bloqueado()).isTrue();
        assertThat(reclamos.motivoBloqueo()).contains("Obligatorio por ley");
        assertThat(reclamos.precioMensual()).isEqualByComparingTo("0.00");
    }

    @Test
    void elPosNoSePuedeVenderSinCajaPorqueLaVentaLaNecesita() {
        aseguraEmpresa(Plan.STARTER);

        ModulosTenantResponse resultado = contratar(List.of(con(ModuloSistema.POS, "20.00")));

        // VentaService abre y consulta sesiones de caja: sin CAJA reventaría al vender.
        assertThat(moduloGate.modulosDe(TENANT)).contains(ModuloSistema.CAJA, ModuloSistema.INVENTARIO);
        ModuloResponse caja = buscar(resultado, ModuloSistema.CAJA);
        assertThat(caja.bloqueado()).isTrue();
        assertThat(caja.motivoBloqueo()).contains("POS");
    }

    @Test
    void unModuloDelQueDependenOtrosNoSePuedeSoltar() {
        aseguraEmpresa(Plan.ECOMMERCE);
        ModulosTenantResponse resultado = contratar(List.of(
                con(ModuloSistema.TIENDA, "25.00"), con(ModuloSistema.COMBOS, "6.00")));

        ModuloResponse productos = buscar(resultado, ModuloSistema.PRODUCTOS);
        assertThat(productos.bloqueado()).isTrue();
        // PRODUCTOS es núcleo: el sistema no existe sin catálogo.
        assertThat(productos.motivoBloqueo()).isNotBlank();

        ModuloResponse inventario = buscar(resultado, ModuloSistema.INVENTARIO);
        assertThat(inventario.motivoBloqueo()).contains("Tienda virtual");
    }

    @Test
    void quitarLaTiendaLibraLoQueSoloEllaSosteniaYBajaElTotal() {
        aseguraEmpresa(Plan.ECOMMERCE);
        contratar(List.of(con(ModuloSistema.TIENDA, "25.00")));
        assertThat(moduloGate.modulosDe(TENANT)).contains(ModuloSistema.CLIENTES, ModuloSistema.RECLAMOS);

        moduloGate.invalidar(TENANT);
        ModulosTenantResponse sinTienda = contratar(List.of(con(ModuloSistema.COMBOS, "6.00")));

        Set<ModuloSistema> activos = moduloGate.modulosDe(TENANT);
        assertThat(activos).contains(ModuloSistema.COMBOS, ModuloSistema.PRODUCTOS);
        assertThat(activos).doesNotContain(ModuloSistema.TIENDA, ModuloSistema.CLIENTES, ModuloSistema.RECLAMOS);
        assertThat(sinTienda.totalMensual()).isEqualByComparingTo("6.00");
    }

    @Test
    void elTotalSumaSoloLoContratadoYRespetaElPrecioPactado() {
        aseguraEmpresa(Plan.PROFESIONAL);

        // Precio negociado por debajo del de lista para entrar en el presupuesto.
        ModulosTenantResponse resultado = contratar(List.of(
                con(ModuloSistema.POS, "15.00"), con(ModuloSistema.CAJA, "8.00")));

        assertThat(resultado.totalMensual()).isEqualByComparingTo("23.00");
        assertThat(buscar(resultado, ModuloSistema.POS).precioMensual()).isEqualByComparingTo("15.00");
        assertThat(buscar(resultado, ModuloSistema.POS).precioLista()).isEqualByComparingTo("20.00");
    }

    @Test
    void sinConfiguracionPropiaMandaElPlanParaNoDejarAcefalaAUnaEmpresaNueva() {
        aseguraEmpresa(Plan.PROFESIONAL);

        // Ninguna fila en tenant_modules: el plan sigue siendo el punto de partida.
        Set<ModuloSistema> activos = moduloGate.modulosDe(TENANT);

        assertThat(activos).containsAll(ModuloSistema.delPlan(Plan.PROFESIONAL));
        assertThat(activos).doesNotContain(ModuloSistema.TIENDA, ModuloSistema.IA);
    }

    @Test
    void elGateRespondeSobreElTenantDeLaPeticion() {
        aseguraEmpresa(Plan.STARTER);
        contratar(List.of(con(ModuloSistema.TIENDA, "25.00")));
        TenantContext.set(TENANT);
        try {
            assertThat(moduloGate.activo("TIENDA")).isTrue();
            assertThat(moduloGate.activo("COMBOS")).isFalse();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void unCodigoDeModuloInexistenteFallaEnVezDeConcederAcceso() {
        aseguraEmpresa(Plan.IA);
        TenantContext.set(TENANT);
        try {
            assertThatThrownBy(() -> moduloGate.activo("MODULO_QUE_NO_EXISTE"))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * El alta siembra el paquete: sin filas propias el acceso caería al plan, y la gracia
     * de vender por módulos es poder recortarlo desde el primer día.
     */
    @Test
    void elAltaConPaqueteAMedidaDejaLaEmpresaConSoloEsosModulos() {
        aseguraEmpresa(Plan.STARTER);
        CrearTenantResponse alta = tenantService.crear(new CrearTenantRequest(
                "Tienda del Barrio", unico("tienda-barrio"), null, null, null, null,
                BusinessVertical.CLOTHING, Plan.ECOMMERCE, null, unico("duenio"),
                null, "Dueño de Prueba",
                List.of(new ModuloSeleccionado(ModuloSistema.TIENDA, new BigDecimal("25.00"))), null, null), null);

        Long nuevoTenant = alta.tenant().id();
        ModulosTenantResponse paquete = moduleService.obtener(nuevoTenant);

        assertThat(paquete.totalMensual()).isEqualByComparingTo("25.00");
        assertThat(moduloGate.modulosDe(nuevoTenant))
                .contains(ModuloSistema.TIENDA, ModuloSistema.PRODUCTOS, ModuloSistema.RECLAMOS)
                .doesNotContain(ModuloSistema.POS, ModuloSistema.CAJA, ModuloSistema.COMBOS);
        // El importe viaja en el listado para poder ver los ingresos por empresa.
        assertThat(alta.tenant().monthlyTotal()).isNotNull();
        moduloGate.invalidar(nuevoTenant);
    }

    @Test
    void elAltaSinPaqueteExplicitoSiembraLosModulosDelPlanAPrecioDeLista() {
        aseguraEmpresa(Plan.STARTER);
        CrearTenantResponse alta = tenantService.crear(new CrearTenantRequest(
                "Bodega Central", unico("bodega"), null, null, null, null,
                BusinessVertical.GENERAL, Plan.STARTER, null, unico("bodeguero"),
                null, "Dueño Bodega", List.of(), null, null), null);

        Long nuevoTenant = alta.tenant().id();
        ModulosTenantResponse paquete = moduleService.obtener(nuevoTenant);

        assertThat(paquete.modulos().stream().filter(ModuloResponse::contratado).map(ModuloResponse::code))
                .containsExactlyInAnyOrderElementsOf(ModuloSistema.delPlan(Plan.STARTER));
        assertThat(paquete.totalMensual()).isGreaterThan(BigDecimal.ZERO);
        moduloGate.invalidar(nuevoTenant);
    }

    /**
     * El costo de implementación cubre el primer mes. Si el alta no fijara vencimiento, la
     * empresa nacería sin fecha y nada la suspendería nunca aunque dejara de pagar —
     * exactamente lo que pasaba antes.
     */
    @Test
    void elAltaDejaCubiertoElPrimerMesYLoRegistraComoPago() {
        aseguraEmpresa(Plan.STARTER);
        CrearTenantResponse alta = tenantService.crear(new CrearTenantRequest(
                "Bodega Nueva", unico("bodega-nueva"), null, null, null, null,
                BusinessVertical.GENERAL, Plan.STARTER, null, unico("duenio"), null, "Dueño Nuevo",
                List.of(new ModuloSeleccionado(ModuloSistema.POS, new BigDecimal("20.00"))),
                new BigDecimal("350.00"), null), null, "operador");

        Long nuevoTenant = alta.tenant().id();
        assertThat(alta.tenant().nextPaymentDue()).isEqualTo(LocalDate.now().plusMonths(1));

        List<PagoSuscripcionResponse> pagos = renewalService.historial(nuevoTenant);
        assertThat(pagos).hasSize(1);
        assertThat(pagos.get(0).monto()).isEqualByComparingTo("350.00");
        assertThat(pagos.get(0).metodo()).isEqualTo("IMPLEMENTACION");
        assertThat(pagos.get(0).periodoInicio()).isEqualTo(LocalDate.now());
        assertThat(pagos.get(0).periodoFin()).isEqualTo(LocalDate.now().plusMonths(1));
        moduloGate.invalidar(nuevoTenant);
    }

    /** Sin costo explícito se cobra el paquete: es la mensualidad que va a pagar después. */
    @Test
    void sinCostoDeImplementacionSeRegistraElTotalDelPaquete() {
        aseguraEmpresa(Plan.STARTER);
        CrearTenantResponse alta = tenantService.crear(new CrearTenantRequest(
                "Bodega Simple", unico("bodega-simple"), null, null, null, null,
                BusinessVertical.GENERAL, Plan.STARTER, null, unico("duenio"), null, "Dueño Simple",
                List.of(), null, null), null, "operador");

        List<PagoSuscripcionResponse> pagos = renewalService.historial(alta.tenant().id());
        assertThat(pagos).hasSize(1);
        assertThat(pagos.get(0).monto()).isEqualByComparingTo(alta.tenant().monthlyTotal());
        moduloGate.invalidar(alta.tenant().id());
    }

    /**
     * La tienda propia y el demo funcionan igual que cualquier empresa, pero nadie paga por
     * ellas: si contaran, el ingreso mensual saldría inflado y el promedio por empresa
     * dejaría de significar nada.
     */
    @Test
    void unaEmpresaNoFacturableSeCreaIgualPeroQuedaMarcada() {
        aseguraEmpresa(Plan.STARTER);
        CrearTenantResponse demo = tenantService.crear(new CrearTenantRequest(
                "Demo Qynex", unico("demo"), null, null, null, null,
                BusinessVertical.CLOTHING, Plan.IA, null, unico("duenio"), null, "Dueño Demo",
                List.of(), null, false), null, "operador");

        assertThat(demo.tenant().billable()).isFalse();
        // Sigue siendo una empresa completa: el paquete se siembra igual.
        assertThat(moduloGate.modulosDe(demo.tenant().id())).contains(ModuloSistema.TIENDA, ModuloSistema.IA);
        moduloGate.invalidar(demo.tenant().id());
    }

    @Test
    void porOmisionUnaEmpresaNuevaSiFactura() {
        aseguraEmpresa(Plan.STARTER);
        CrearTenantResponse cliente = tenantService.crear(new CrearTenantRequest(
                "Cliente Normal", unico("cliente"), null, null, null, null,
                BusinessVertical.GENERAL, Plan.STARTER, null, unico("duenio"), null, "Dueño Cliente",
                List.of(), null, null), null, "operador");

        assertThat(cliente.tenant().billable()).isTrue();
        moduloGate.invalidar(cliente.tenant().id());
    }

    @Test
    void elCatalogoParaElAltaLlegaSinNadaContratadoYConSusPrecios() {
        var catalogo = moduleService.catalogo();

        assertThat(catalogo.modulos()).isNotEmpty().allMatch(m -> !m.contratado());
        assertThat(catalogo.modulos()).anyMatch(m -> m.code() == ModuloSistema.TIENDA
                && m.precioLista().compareTo(BigDecimal.ZERO) > 0);
        assertThat(catalogo.presets()).containsKeys(Plan.STARTER, Plan.ECOMMERCE);
    }

    /**
     * Sin historial, cuando un cliente discute su factura no hay forma de saber quién le
     * cambió el paquete ni cuándo: `tenant_modules` solo guarda el estado actual.
     */
    @Test
    void cadaCambioDePaqueteQuedaRegistradoConSuAutorYSuImporte() {
        aseguraEmpresa(Plan.ECOMMERCE);
        moduleService.actualizar(TENANT, new ActualizarModulosRequest(List.of(
                con(ModuloSistema.POS, "20.00"), con(ModuloSistema.CAJA, "10.00"))), 7L, "operador.uno");
        moduloGate.invalidar(TENANT);
        moduleService.actualizar(TENANT, new ActualizarModulosRequest(List.of(
                con(ModuloSistema.TIENDA, "25.00"))), 9L, "operador.dos");

        List<CambioPaqueteResponse> historial = moduleService.historial(TENANT);

        assertThat(historial).hasSize(2);
        CambioPaqueteResponse ultimo = historial.get(0);
        assertThat(ultimo.usuario()).isEqualTo("operador.dos");
        assertThat(ultimo.totalAnterior()).isEqualByComparingTo("30.00");
        assertThat(ultimo.totalNuevo()).isEqualByComparingTo("25.00");
        assertThat(ultimo.agregados()).contains("Tienda virtual", "Libro de Reclamaciones");
        assertThat(ultimo.quitados()).contains("POS / Ventas", "Caja");
        // Más reciente primero, para leerlo como una conversación con el cliente.
        assertThat(historial.get(1).usuario()).isEqualTo("operador.uno");
    }

    /** Abrir y guardar sin tocar nada no debe ensuciar el historial. */
    @Test
    void guardarElMismoPaqueteNoDejaRastro() {
        aseguraEmpresa(Plan.STARTER);
        moduleService.actualizar(TENANT, new ActualizarModulosRequest(List.of(
                con(ModuloSistema.POS, "20.00"), con(ModuloSistema.CAJA, "10.00"))), 7L, "operador.uno");
        moduloGate.invalidar(TENANT);
        int tras_primer_cambio = moduleService.historial(TENANT).size();

        // Mismos importes con otra escala: es lo que llega desde el panel, porque el precio
        // vuelve de la base como 20.00 y el formulario lo manda como 20.0. Comparar con
        // BigDecimal.equals los daba por distintos y ensuciaba el historial.
        moduleService.actualizar(TENANT, new ActualizarModulosRequest(List.of(
                con(ModuloSistema.POS, "20.0"), con(ModuloSistema.CAJA, "10"))), 7L, "operador.uno");

        assertThat(moduleService.historial(TENANT)).hasSize(tras_primer_cambio);
    }

    @Test
    void losPresetsDeCadaPlanVienenCerradosYListosParaAplicar() {
        aseguraEmpresa(Plan.STARTER);

        ModulosTenantResponse resultado = moduleService.obtener(TENANT);

        assertThat(resultado.presets().get(Plan.ECOMMERCE))
                .contains(ModuloSistema.TIENDA, ModuloSistema.RECLAMOS, ModuloSistema.CLIENTES);
        assertThat(resultado.presets().get(Plan.STARTER))
                .doesNotContain(ModuloSistema.TIENDA, ModuloSistema.IA);
    }
}
