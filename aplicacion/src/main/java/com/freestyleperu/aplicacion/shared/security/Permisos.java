package com.freestyleperu.aplicacion.shared.security;

/**
 * Códigos de permiso usados en {@code @PreAuthorize}. Deben coincidir
 * exactamente con la columna {@code code} de la tabla {@code permissions}
 * (ver docs/04-reglas-negocio.md, matriz rol → permisos).
 */
public final class Permisos {

    private Permisos() {
    }

    public static final String DASHBOARD_VER = "DASHBOARD_VER";

    public static final String PRODUCTOS_CONSULTAR = "PRODUCTOS_CONSULTAR";
    public static final String PRODUCTOS_CREAR = "PRODUCTOS_CREAR";
    public static final String PRODUCTOS_EDITAR = "PRODUCTOS_EDITAR";
    public static final String PRODUCTOS_ELIMINAR = "PRODUCTOS_ELIMINAR";

    public static final String VARIANTES_GESTIONAR = "VARIANTES_GESTIONAR";
    public static final String BARCODE_GENERAR = "BARCODE_GENERAR";

    public static final String INVENTARIO_CONSULTAR = "INVENTARIO_CONSULTAR";
    public static final String INVENTARIO_ENTRADA = "INVENTARIO_ENTRADA";
    public static final String INVENTARIO_SALIDA = "INVENTARIO_SALIDA";
    public static final String INVENTARIO_AJUSTAR = "INVENTARIO_AJUSTAR";

    public static final String VENTAS_CONSULTAR = "VENTAS_CONSULTAR";
    public static final String VENTAS_CONSULTAR_TODAS = "VENTAS_CONSULTAR_TODAS";
    public static final String VENTAS_CREAR = "VENTAS_CREAR";
    public static final String VENTAS_ANULAR = "VENTAS_ANULAR";
    public static final String VENTAS_DESCUENTO = "VENTAS_DESCUENTO";
    public static final String VENTAS_DEVOLVER = "VENTAS_DEVOLVER";

    public static final String CLIENTES_CONSULTAR = "CLIENTES_CONSULTAR";
    public static final String CLIENTES_CREAR = "CLIENTES_CREAR";
    public static final String CLIENTES_EDITAR = "CLIENTES_EDITAR";

    public static final String CAJA_ABRIR = "CAJA_ABRIR";
    public static final String CAJA_CERRAR = "CAJA_CERRAR";
    public static final String CAJA_CONSULTAR = "CAJA_CONSULTAR";
    public static final String CAJA_MOVIMIENTO = "CAJA_MOVIMIENTO";

    public static final String REPORTES_CONSULTAR = "REPORTES_CONSULTAR";
    public static final String REPORTES_EXPORTAR = "REPORTES_EXPORTAR";

    public static final String AUDITORIA_CONSULTAR = "AUDITORIA_CONSULTAR";

    public static final String USUARIOS_CONSULTAR = "USUARIOS_CONSULTAR";
    public static final String USUARIOS_CREAR = "USUARIOS_CREAR";
    public static final String USUARIOS_EDITAR = "USUARIOS_EDITAR";
    public static final String USUARIOS_BLOQUEAR = "USUARIOS_BLOQUEAR";
    public static final String USUARIOS_CAMBIAR_CONTRASENA = "USUARIOS_CAMBIAR_CONTRASENA";
    public static final String USUARIOS_RESETEAR_CONTRASENA = "USUARIOS_RESETEAR_CONTRASENA";
    public static final String ROLES_GESTIONAR = "ROLES_GESTIONAR";

    public static final String CONFIGURACION_VER = "CONFIGURACION_VER";
    public static final String CONFIGURACION_EDITAR = "CONFIGURACION_EDITAR";
    public static final String CONFIGURACION_PAGOS = "CONFIGURACION_PAGOS";
    /** Razón social, RUC, dirección, contacto y logo — reservado para el operador de la plataforma, nunca para el cliente. Ver RN-26. */
    public static final String CONFIGURACION_IDENTIDAD_EDITAR = "CONFIGURACION_IDENTIDAD_EDITAR";

    /** Autoridad sintética otorgada solo a usuarios marcados como operadores de plataforma. */
    public static final String PLATAFORMA_EMPRESAS_GESTIONAR = "PLATAFORMA_EMPRESAS_GESTIONAR";

    public static final String PROMOTORES_CONSULTAR = "PROMOTORES_CONSULTAR";
    public static final String PROMOTORES_GESTIONAR = "PROMOTORES_GESTIONAR";

    public static final String PEDIDOS_CONSULTAR = "PEDIDOS_CONSULTAR";
    public static final String PEDIDOS_GESTIONAR = "PEDIDOS_GESTIONAR";

    public static final String RECLAMOS_CONSULTAR = "RECLAMOS_CONSULTAR";
    public static final String RECLAMOS_RESPONDER = "RECLAMOS_RESPONDER";

    public static final String RESERVAS_CONSULTAR = "RESERVAS_CONSULTAR";
    public static final String RESERVAS_CREAR = "RESERVAS_CREAR";
    public static final String RESERVAS_GESTIONAR = "RESERVAS_GESTIONAR";

    public static final String COMBOS_CONSULTAR = "COMBOS_CONSULTAR";
    public static final String COMBOS_GESTIONAR = "COMBOS_GESTIONAR";

    public static final String PROMOCIONES_CONSULTAR = "PROMOCIONES_CONSULTAR";
    public static final String PROMOCIONES_GESTIONAR = "PROMOCIONES_GESTIONAR";
    /** Aplicar una promoción ya definida a una línea de venta — más acotado que VENTAS_DESCUENTO (RN-28). */
    public static final String PROMOCIONES_APLICAR = "PROMOCIONES_APLICAR";

    /** Autoridad que llevan los JWT de clientes de la tienda (no es un permiso de la tabla `permissions`). */
    public static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
}
