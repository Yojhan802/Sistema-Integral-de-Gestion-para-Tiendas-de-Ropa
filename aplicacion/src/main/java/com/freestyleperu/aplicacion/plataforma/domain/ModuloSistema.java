package com.freestyleperu.aplicacion.plataforma.domain;

import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Catálogo de módulos vendibles.
 *
 * <p>Las dependencias viven aquí y no en base de datos a propósito: no son una decisión
 * comercial sino un hecho del código (por ejemplo, {@code PedidoService} retiene stock a
 * través de {@code InventarioService}, así que una tienda online sin inventario reventaría
 * en runtime). Dejarlas configurables permitiría dejar una empresa en un estado imposible.
 *
 * <p>El precio de aquí es solo el de lista: el pactado con cada empresa se guarda en
 * {@code tenant_modules}, porque el objetivo del módulo es ajustarse al presupuesto real
 * de cada cliente.
 */
public enum ModuloSistema {

    /** Sin catálogo no hay nada que vender, cobrar ni mostrar. */
    PRODUCTOS("Productos", "Catálogo, variantes y atributos", "15.00", Tipo.NUCLEO),

    INVENTARIO("Inventario", "Stock por almacén y movimientos", "12.00", Tipo.OPCIONAL, PRODUCTOS),

    CLIENTES("Clientes", "Registro de clientes y sus cuentas", "8.00", Tipo.OPCIONAL),

    /** {@code VentaService} abre y consulta sesiones de caja; no funciona sin ellas. */
    CAJA("Caja", "Apertura, cierre y arqueo de caja", "10.00", Tipo.OPCIONAL),

    POS("POS / Ventas", "Venta presencial en mostrador", "20.00", Tipo.OPCIONAL,
            PRODUCTOS, INVENTARIO, CAJA),

    /**
     * La tienda online genera su propia {@code Sale} sin sesión de caja, así que no
     * arrastra CAJA ni POS: se puede vender sola, que es el caso del cliente que ya
     * tiene su sistema de gestión y solo necesita vender por internet.
     */
    TIENDA("Tienda virtual", "Catálogo público, carrito y checkout", "25.00", Tipo.OPCIONAL,
            PRODUCTOS, INVENTARIO, CLIENTES),

    RECLAMOS("Libro de Reclamaciones", "Obligatorio si vendes por internet (D.S. 011-2011-PCM)",
            "0.00", Tipo.LEGAL),

    SEPARACIONES("Separaciones", "Reservas con adelanto", "8.00", Tipo.OPCIONAL,
            PRODUCTOS, CLIENTES, CAJA),

    COMBOS("Combos", "Paquetes de productos a precio especial", "6.00", Tipo.OPCIONAL, PRODUCTOS),

    PROMOCIONES("Promociones", "Descuentos por producto, categoría o catálogo", "6.00", Tipo.OPCIONAL,
            PRODUCTOS),

    REPORTES("Reportes", "Ventas, inventario y exportación", "10.00", Tipo.OPCIONAL),

    /** Personal de piso que ofrece producto sin operar caja; solo tiene sentido si se vende. */
    PROMOTORES("Promotores", "Personal de piso y su seguimiento", "6.00", Tipo.OPCIONAL, PRODUCTOS),

    AUDITORIA("Auditoría", "Trazabilidad de quién hizo qué y cuándo", "8.00", Tipo.OPCIONAL),

    FACTURACION("Facturación electrónica", "Boletas y facturas ante SUNAT", "18.00", Tipo.OPCIONAL,
            PRODUCTOS),

    IA("Asistente IA", "Descripciones de producto y asistente de tienda", "25.00", Tipo.OPCIONAL,
            PRODUCTOS);

    /** NUCLEO no se puede desactivar; LEGAL se fuerza cuando corresponde. */
    public enum Tipo { NUCLEO, OPCIONAL, LEGAL }

    private final String nombre;
    private final String descripcion;
    private final BigDecimal precioLista;
    private final Tipo tipo;
    private final List<ModuloSistema> requiere;

    ModuloSistema(String nombre, String descripcion, String precioLista, Tipo tipo, ModuloSistema... requiere) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precioLista = new BigDecimal(precioLista);
        this.tipo = tipo;
        this.requiere = List.of(requiere);
    }

    public String getNombre() { return nombre; }

    public String getDescripcion() { return descripcion; }

    public BigDecimal getPrecioLista() { return precioLista; }

    public Tipo getTipo() { return tipo; }

    public List<ModuloSistema> getRequiere() { return requiere; }

    /**
     * Vender la tienda online obliga a publicar el Libro de Reclamaciones: es una
     * obligación legal del proveedor, no un extra facturable, y por eso su precio de
     * lista es cero y no se puede desmarcar mientras TIENDA esté activa.
     */
    public static Set<ModuloSistema> forzadosPor(Set<ModuloSistema> activos) {
        return activos.contains(TIENDA) ? EnumSet.of(RECLAMOS) : EnumSet.noneOf(ModuloSistema.class);
    }

    /**
     * Añade todo lo que los módulos elegidos necesitan para funcionar, de forma
     * transitiva, más lo que la ley obliga a incluir.
     */
    public static Set<ModuloSistema> cerrarDependencias(Set<ModuloSistema> elegidos) {
        Set<ModuloSistema> resultado = EnumSet.noneOf(ModuloSistema.class);
        Arrays.stream(values()).filter(m -> m.tipo == Tipo.NUCLEO).forEach(resultado::add);
        elegidos.forEach(modulo -> agregarCon(modulo, resultado));
        forzadosPor(resultado).forEach(modulo -> agregarCon(modulo, resultado));
        return resultado;
    }

    private static void agregarCon(ModuloSistema modulo, Set<ModuloSistema> acumulado) {
        if (!acumulado.add(modulo)) {
            return;
        }
        modulo.requiere.forEach(dependencia -> agregarCon(dependencia, acumulado));
    }

    /**
     * Módulos que quedarían huérfanos si se desactivara {@code modulo}: sirve para que el
     * panel bloquee la casilla en vez de dejar guardar algo que no puede funcionar.
     */
    public static Set<ModuloSistema> dependientesActivos(ModuloSistema modulo, Set<ModuloSistema> activos) {
        Set<ModuloSistema> dependientes = EnumSet.noneOf(ModuloSistema.class);
        for (ModuloSistema candidato : activos) {
            if (candidato != modulo && cerrarDependencias(EnumSet.of(candidato)).contains(modulo)) {
                dependientes.add(candidato);
            }
        }
        return dependientes;
    }

    /**
     * Conjunto que otorga cada plan, para seguir usándolos como preset comercial. Un plan
     * ya no controla el acceso: solo siembra los módulos, que después se ajustan a mano.
     */
    public static Set<ModuloSistema> delPlan(Plan plan) {
        Set<ModuloSistema> base = EnumSet.of(PRODUCTOS, INVENTARIO, CLIENTES, CAJA, POS, REPORTES);
        Set<ModuloSistema> profesional = EnumSet.of(SEPARACIONES, COMBOS, PROMOCIONES, PROMOTORES, AUDITORIA);
        Set<ModuloSistema> elegidos = switch (plan) {
            case STARTER -> base;
            case PROFESIONAL -> concat(base, profesional);
            case ECOMMERCE -> concat(concat(base, profesional), EnumSet.of(TIENDA, FACTURACION));
            case IA -> concat(concat(base, profesional), EnumSet.of(TIENDA, FACTURACION, IA));
        };
        return cerrarDependencias(elegidos);
    }

    private static Set<ModuloSistema> concat(Set<ModuloSistema> a, Set<ModuloSistema> b) {
        Set<ModuloSistema> union = EnumSet.copyOf(a);
        union.addAll(b);
        return union;
    }
}
