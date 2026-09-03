# 04 — Reglas de negocio

Cada regla indica **dónde se aplica** y **qué error devuelve**. El backend es
siempre la autoridad final: aunque el frontend valide, el servidor vuelve a
validar.

---

## RN-01 · No se vende sin stock suficiente

**Dónde:** `VentaService.registrarVenta()`
**Cómo:** antes de descontar, se bloquean las variantes con `PESSIMISTIC_WRITE`
(ordenadas por id ascendente para evitar interbloqueos) y se comprueba
`stock >= cantidad`.
**Defensa adicional:** `CHECK (stock >= 0)` en la tabla.
**Error:** `409 INSUFFICIENT_STOCK` — *"Stock insuficiente para Polo Oversize Negro M. Disponible: 2, solicitado: 3"*

> El bloqueo pesimista es necesario: dos cajeros vendiendo la última unidad al
> mismo tiempo es un caso real en una tienda con varias cajas.

## RN-02 · Códigos de barras únicos

**Dónde:** `VarianteService` + restricción `uk_variant_barcode`.
**Cómo:** se comprueba antes de insertar y se captura además la violación de
integridad, por si dos peticiones concurrentes pasan la comprobación a la vez.
**Error:** `409 DUPLICATE_RESOURCE` — *"El código de barras 7750000001255 ya está registrado"*

## RN-03 · SKU únicos

**Política:** el SKU de producto es único globalmente; el SKU de variante también.
**Formato:** producto `POL-00125`; variante `POL-00125-M-NEG`
(`<sku_producto>-<talla>-<color_3_letras>`).
**Generación:** automática desde la tabla `sequences`, editable manualmente si el
resultado sigue siendo único.
**Error:** `409 DUPLICATE_RESOURCE`

## RN-04 · Una variante no se repite dentro del producto

**Dónde:** `uk_variant_combination UNIQUE (product_id, color_id, size_id)`.
**Error:** `409 DUPLICATE_RESOURCE` — *"Ya existe la variante Negro / M para este producto"*

## RN-05 · El stock nunca cambia sin movimiento

**Dónde:** `InventarioService` es el **único** componente autorizado a escribir la
columna `stock`. Ningún otro service la toca.
**Cómo:** todo cambio pasa por `registrarMovimiento()`, que en una sola operación
lee el stock actual, calcula el nuevo, guarda el movimiento con
`stock_before`/`stock_after` y actualiza la variante.
**Invariante verificable:**
```sql
SELECT v.id FROM product_variants v
JOIN (SELECT variant_id, SUM(quantity) s FROM inventory_movements GROUP BY variant_id) m
  ON m.variant_id = v.id
WHERE v.stock <> m.s;   -- debe devolver 0 filas siempre
```

## RN-06 · Los movimientos de inventario son inmutables

No existen endpoints `PUT` ni `DELETE` sobre `/api/inventory/movements`. La
entidad tiene los campos `@Column(updatable = false)`. Un error se corrige con un
movimiento `AJUSTE` en sentido contrario, con motivo obligatorio.

## RN-07 · La suma de pagos debe igualar el total

**Dónde:** `VentaService`, antes de confirmar.
**Cómo:** `sum(pagos) == total` comparando `BigDecimal` con `compareTo` y escala 2.
**No se implementa venta a crédito** en esta fase: un pago parcial es un error.
**Error:** `409 BUSINESS_RULE_VIOLATION` — *"La suma de pagos (S/70.00) no coincide con el total (S/80.00)"*

Ejemplo válido de pago mixto: total S/80 = efectivo S/50 + Yape S/30.

## RN-08 · No se vende sin caja abierta

**Dónde:** `VentaService`. La venta exige una `cash_session` en estado `OPEN`
para la caja del vendedor.
**Error:** `409 BUSINESS_RULE_VIOLATION` — *"No hay una sesión de caja abierta"*

**Excepción (V34):** las ventas que genera `PedidoService.confirmarPago()`
al confirmar un pedido online no tienen caja — un pago online no pasa por
un cajero físico (ver RN-21). `sales.cash_session_id` es `NULL` solo para
esas; anular una de esas ventas, o registrarle una devolución, con un
método de pago que afecta caja se rechaza explícitamente en vez de fallar
con un error interno.

## RN-09 · Solo una sesión abierta por caja

**Dónde:** garantizado por la base de datos con la columna generada
`open_register_id` y su índice único (ver [03-modelo-datos.md](03-modelo-datos.md) §9).
**Error:** `409 BUSINESS_RULE_VIOLATION` — *"La caja #01 ya tiene una sesión abierta"*

## RN-10 · Solo el efectivo afecta al arqueo

**Dónde:** `CajaService`. Se registra movimiento de caja únicamente cuando
`payment_method.affects_cash = true`.
**Cálculo del cierre:**
```
efectivo esperado = monto_apertura + Σ(movimientos de caja)
diferencia        = efectivo contado − efectivo esperado
```
La diferencia (sobrante o faltante) siempre queda registrada, nunca se descarta.

## RN-11 · Una caja cerrada no admite movimientos

Cerrada la sesión, no se aceptan ventas ni movimientos contra ella. Cualquier
corrección posterior exige una autorización explícita y queda auditada.
**Error:** `409 BUSINESS_RULE_VIOLATION` — *"La sesión de caja está cerrada"*

## RN-12 · Las ventas no se eliminan

No existe `DELETE /api/sales/{id}`. Una venta errónea se **anula**, conservando el
registro.

## RN-13 · Anular exige permiso, motivo y autorización

**Flujo:**
```
solicitar anulación → motivo obligatorio → permiso VENTAS_ANULAR →
marcar CANCELLED → devolver stock (movimiento DEVOLUCION) →
revertir efectivo en caja si lo hubo → registrar auditoría
```
Una venta ya `CANCELLED` no se anula dos veces. Si la sesión de caja de la venta
ya está cerrada, la reversión del efectivo se registra contra la sesión abierta
actual, dejando constancia de la sesión original.
**Error sin permiso:** `403 ACCESS_DENIED` + registro de auditoría con `result = DENIED`.

## RN-14 · Una devolución siempre pertenece a una venta

**Validaciones:**
- La venta existe y no está anulada.
- La cantidad devuelta ≤ cantidad vendida − ya devuelta en esa línea.
- Motivo obligatorio.
- `restock` se indica explícitamente por línea.

**Efectos:** si `restock = true` se genera un movimiento `DEVOLUCION` que suma
stock; si el reembolso es en efectivo, se genera un movimiento de caja negativo.
El estado de la venta pasa a `RETURNED` o `PARTIALLY_RETURNED` según se haya
devuelto todo o parte.
**Error:** `409 BUSINESS_RULE_VIOLATION` — *"No se puede devolver 3 unidades: solo se vendieron 2"*

## RN-15 · Los datos oficiales de pago no se editan desde el POS

Titular, número y QR de Yape/Plin requieren `CONFIGURACION_PAGOS`, que ningún
vendedor tiene. El POS los muestra en **solo lectura**, etiquetados como
*"YAPE — CUENTA OFICIAL"*.

## RN-16 · Los descuentos requieren permiso

Aplicar descuento exige `VENTAS_DESCUENTO`. Si además supera el porcentaje
máximo configurado, requiere autorización de un supervisor.
**Error:** `403 ACCESS_DENIED` — *"No tienes permisos para aplicar descuentos"*

## RN-17 · Toda operación sensible se audita

Se registran, como mínimo: login (correcto y fallido), creación y anulación de
ventas, devoluciones, ajustes de inventario, apertura y cierre de caja, cambios
de precio, gestión de usuarios y roles, cambios de configuración, y **todo
intento denegado por falta de permisos**.

La auditoría se escribe con `REQUIRES_NEW`: **persiste aunque la transacción
principal se revierta**, que es precisamente cuando más interesa saber qué se
intentó hacer.

## RN-18 · Contraseñas y bloqueo de cuenta

- BCrypt con factor 12; jamás texto plano ni en logs.
- Mínimo 8 caracteres, con al menos una letra y un número.
- 5 intentos fallidos ⇒ bloqueo de 15 minutos.
- Un login correcto reinicia el contador.
- Un usuario `BLOCKED` o `INACTIVE` no puede autenticarse.

## RN-19 · Los precios y los importes

- `precio > 0`; `precio_promocional`, si existe, debe ser **menor** que el precio.
- Todos los cálculos con `BigDecimal`, escala 2, redondeo `HALF_UP`.
- `subtotal_línea = (precio_unitario × cantidad) − descuento_línea`
- `total_venta = Σ subtotales − descuento_global`
- El descuento nunca puede dejar el total en negativo.

## RN-20 · Autorización por permiso, no por rol

Ningún endpoint comprueba el nombre del rol. Todos usan
`@PreAuthorize("hasAuthority('PERMISO')")`. Así, cambiar qué puede hacer un rol
es modificar datos, no recompilar la aplicación.

## RN-21 · Un pedido online no descuenta stock hasta que se confirma el pago

Crear un pedido (`POST /api/store/orders`) solo valida que haya stock
suficiente en ese momento — no lo reserva ni lo descuenta. El descuento real
ocurre recién en `POST /api/orders/{id}/confirm`, cuando el staff confirma
manualmente que el pago (Yape/Plin/transferencia) llegó. Si un pedido
confirmado se anula después, el stock se reingresa. Motivo: el pago es
manual (Fase 2 no integra pasarela), así que "pedido creado" no equivale a
"pago recibido" — descontar antes sería mostrar como vendido algo que todavía
podría no pagarse nunca.

**Confirmar el pago también genera una `Sale` real** (V34): el pedido deja
de ser un flujo paralelo invisible para el módulo de Ventas — pasa a
aparecer en el historial/reportes y puede imprimirse el mismo ticket que el
POS, reusando `imprimirTicket` sin construir nada nuevo. Esa `Sale` se crea
**sin sesión de caja** (ver excepción a RN-08) y su pago nunca afecta caja,
sin importar el método elegido — un pago online no tiene un cajero físico
detrás. El checkout online (`GET /api/store/catalog/payment-methods`,
`PedidoService.crear()`) por eso solo ofrece/acepta métodos con
`affects_cash = false`.

## RN-22 · Envío: tarifa plana, salvo contraentrega exclusiva de Huacho

`total = subtotal + shippingCost`. `shippingCost` es la tarifa plana
configurada en Configuración (Shalom, la courier que usa la empresa, no tiene
API pública de tarifas — el monto lo define el staff), salvo que el método de
pago sea `CONTRAENTREGA`, en cuyo caso `shippingCost = 0` y el pedido **debe**
tener `district = "Huacho"` exactamente (sede física de la empresa) — el
backend rechaza cualquier otro distrito con `CONTRAENTREGA`, sin importar lo
que haya permitido el frontend.

---

## RN-23 · El plan de suscripción gatea módulos, no solo el rol del usuario

Cada instalación (un despliegue por cliente, ver docs/03-modelo-datos.md §15)
tiene un `company_settings.plan` (`STARTER < PROFESIONAL < ECOMMERCE < IA`).
Un usuario puede tener el permiso de un módulo y aun así no poder usarlo si el
plan de su instalación no lo incluye — el permiso de rol y el plan son dos
verificaciones independientes, ambas deben pasar
(`hasAuthority('PERMISO') and @planGate.tienePlan('...')`). El plan **no lo
edita el cliente**: solo lo cambia el operador de la plataforma directo en la
base de datos. El plan `STARTER` además limita a 3 usuarios activos
(`UsuarioService.crear()` lo rechaza con `OperacionNoPermitidaException` al
superarlo).

---

## RN-24 · Suscripción vencida suspende todo el sistema, sin excepción

Si `company_settings.subscription_status = SUSPENDIDA` (por falta de pago,
ver docs/03-modelo-datos.md §16), **todo** el sistema queda bloqueado con
`402 Payment Required` — panel de staff y tienda pública por igual —
independientemente del plan contratado o de los permisos del usuario. Solo
quedan accesibles `/actuator/health`, `/api/system/info`, `/api/auth/login`
y `/api/auth/refresh`. La suspensión la dispara sola un job diario si se pasa
la fecha de próximo pago más 5 días de margen de gracia; la reactivación
siempre es manual, por el operador de la plataforma.

---

## RN-25 · Un rol solo puede asignar roles hasta su propio techo de asignación

Cada rol tiene un `hierarchyLevel` (0-100). Al crear un usuario, el sistema
suma el nivel más alto entre los roles de **quien lo crea** y exige que
**todos** los roles que se le quieran asignar al usuario nuevo tengan
`hierarchyLevel` igual o menor — si no, `403 OPERATION_NOT_ALLOWED`. Así, un
rol con techo 40 ("Jefe de Tienda") puede crear cajeros/vendedores (nivel 10)
y hasta otro Jefe de Tienda a su mismo nivel (40), pero nunca un
Administrador (nivel 100). Niveles de la semilla inicial: Administrador=100,
Supervisor=50, Vendedor/Almacenero=10; un rol nuevo nace en 0 si no se le
asigna un nivel explícito (fail closed — no puede asignar casi nada hasta que
el operador le suba el techo). Esto es independiente del permiso
`USUARIOS_CREAR`: tener el permiso solo habilita el endpoint, el techo decide
qué roles concretos se le pueden dar al usuario nuevo.

---

## RN-26 · La identidad de la empresa está separada de sus datos operativos

`PUT /api/settings/company` (moneda, IGV, tarifa de envío, pie de ticket) y
`PUT /api/settings/company/identity` + `POST /api/settings/company/logo`
(razón social, RUC, dirección, contacto, logo) son dos permisos distintos:
`CONFIGURACION_EDITAR` y `CONFIGURACION_IDENTIDAD_EDITAR` respectivamente. El
sistema sirve a varias empresas, así que la identidad de cada una la define
el operador de la plataforma — la semilla solo se la da a `ADMINISTRADOR`, y
la convención es reservar ese rol para la cuenta del propio operador, nunca
asignarlo al personal del cliente (coherente con RN-25: un "Jefe de Tienda"
vive por debajo, con `CONFIGURACION_EDITAR` si se le concede, pero sin
`CONFIGURACION_IDENTIDAD_EDITAR`). El frontend de Configuración → Empresa
oculta por completo la sección de identidad si el usuario no tiene ese
permiso — no la muestra en modo solo lectura, directamente no aparece.

---

## RN-27 · Separaciones: seña sin efectivo, stock retirado de inmediato, vencimiento sin reversión

Una separación (layaway — apartar una prenda con una seña, típico de lives
de TikTok) exige plan `PROFESIONAL` además del permiso
(`RESERVAS_CONSULTAR`/`RESERVAS_CREAR`/`RESERVAS_GESTIONAR`, mismo criterio
de RN-23). Tres reglas de negocio explícitas, decididas con el usuario antes
de escribir la migración (irreversible en producción):

1. **La seña nunca se paga en efectivo** — solo Yape, Plin, transferencia o
   tarjeta (`affects_cash = false`). Rechazado con `ReglaDeNegocioException`
   si se intenta con un método que afecta caja.
2. **El stock se retira al crear la separación, no al completarla** — a
   diferencia de un pedido online (RN-21, que retira stock recién al
   confirmar el pago). Aquí la prenda queda físicamente apartada desde la
   seña, así que el resto del sistema (POS, tienda online, otra separación)
   debe verla como no disponible de inmediato.
3. **El monto de la seña por defecto y el plazo de vencimiento son
   configurables** (`company_settings.reservation_deposit_amount` /
   `reservation_expiration_days`, editables en Configuración → Datos
   operativos), no hardcodeados — el cajero puede además ajustar la seña por
   separación concreta al crearla.
4. **Al vencer el plazo, la seña ya pagada se pierde — no hay devolución ni
   reversión automática.** Un job por hora (`ReservaScheduler`) libera el
   stock de toda separación vencida y la marca `VENCIDO`; el dinero de la
   seña simplemente queda cobrado, sin ningún flujo de reembolso.
5. **El comprador no necesita estar registrado como cliente** (V29) — un
   comprador ocasional de un live solo requiere su nombre (y, opcionalmente,
   su teléfono para ubicar más rápido su comprobante en WhatsApp). Obligar a
   registrar cada comprador ocasional como `Customer` habría sido fricción
   pura para el vendedor durante un live; el formulario sigue permitiendo
   buscar y vincular un cliente ya registrado si aplica (ej. alguien que ya
   compra por la tienda online).
6. **Una separación puede apartar varios productos de una vez, con una sola
   seña para todo el grupo** (V33) — antes, una separación era un solo
   producto; el cajero arma un carrito (buscador + cantidad) igual que en el
   POS. Si esos productos arman un combo activo, el cajero lo elige
   explícitamente con el botón **"+ Agregar combo"** (mismo patrón de
   "elegir combo → llenar cada línea con una variante concreta" ya usado en
   el POS) — decisión explícita del usuario para evitar la ambigüedad de
   detectar combos automáticamente sobre categorías/marcas/múltiplos. El
   combo queda fijado desde la creación, con su descuento ya repartido
   proporcionalmente entre sus líneas. El mismo combo se puede aplicar **más
   de una vez** en una sola separación (ej. "8 polos" = 2 aplicaciones de un
   combo de 4 unidades) — cada aplicación es un grupo independiente, así que
   8 polos a "4 x S/100" cobran S/200, no se mezclan en un solo grupo de 8
   que ya no calzaría con la definición del combo.
7. **Varias separaciones del mismo comprador se pueden cobrar juntas**, con
   detección automática de combo (`POST /api/reservations/complete-batch`) —
   pensado para cuando el cajero *no* usó el botón "+ Agregar combo" al
   crear (ej. varias separaciones sueltas de un mismo live). Solo participan
   de esta detección las líneas que **todavía no** tienen un combo fijado;
   las que ya lo traen desde la creación se cobran tal cual, sin
   re-evaluarse. El backend exige que todas las separaciones seleccionadas
   sean del mismo comprador (mismo cliente registrado, o mismo `guest_name`)
   y genera **una sola `Sale`** con el descuento total repartido
   proporcionalmente. El listado de separaciones admite además buscar por
   nombre de comprador (`buyerName`, registrado u ocasional) para ubicar
   rápido las pendientes de una persona en un recojo presencial.

Ver docs/03-modelo-datos.md §17 para el modelo de datos completo.

---

## RN-28 · Combos y promociones: precio fijo garantizado, descuento nunca automático

Exige plan `PROFESIONAL` (mismo criterio de RN-23). Decisiones explícitas,
confirmadas con el usuario antes de escribir la migración:

1. **Un combo es un conjunto fijo de líneas a un precio fijo total.** Cada
   línea (`combo_items`, V31) es de un tipo: `PRODUCT` (un producto
   específico) o `CATEGORY` (cualquier producto de una categoría,
   opcionalmente acotado a una marca — ej. "4 polos de esta marca", "una
   prenda de esta categoría"), y un mismo combo puede mezclar líneas de
   ambos tipos. El cajero elige color/talla de cada producto — y, en una
   línea `CATEGORY`, también qué producto concreto — recién al venderlo; el
   combo mismo solo fija el tipo de cada línea y su cantidad.
2. **El precio de un combo debe ser menor a la suma de sus productos** —
   `ComboService` lo rechaza al crear/editar si no, tanto con los precios
   vigentes en ese momento como (por seguridad) otra vez al momento de
   vender, por si los precios cambiaron después. Esta validación al
   crear/editar **se omite** si el combo tiene alguna línea `CATEGORY` (no
   hay un conjunto fijo de productos con el que comparar de antemano); en
   ese caso solo queda el chequeo del momento de la venta.
3. **El descuento de un combo lo calcula siempre el backend**, nunca el
   cliente: se reparte proporcionalmente entre las líneas vendidas para que
   la suma cuadre exacto con el precio fijo del combo.
4. **Una promoción nunca se aplica sola — el cajero la elige por línea de
   venta.** Esto fue una decisión explícita para resolver el caso de
   ofertas exclusivas de una plataforma (ej. "solo para el live de TikTok,
   no en tienda física"): en vez de agregar un concepto nuevo de "canal de
   venta" al sistema, la promoción simplemente vive en el catálogo con su
   vigencia por fechas, y es **criterio del vendedor** decidir si corresponde
   aplicarla a una venta concreta — igual que ya es criterio suyo decidir a
   quién le vende con qué descuento manual (`VENTAS_DESCUENTO`).
5. **Aplicar una promoción usa un permiso más acotado que el descuento
   libre** — `PROMOCIONES_APLICAR`, no `VENTAS_DESCUENTO`. Así, la gerencia
   puede darle a un vendedor la capacidad de aplicar promociones ya
   aprobadas (con tope y alcance definidos de antemano) sin darle la
   capacidad de aplicar cualquier descuento arbitrario.
6. Una línea de venta admite **como máximo uno** de: descuento manual, combo
   o promoción — nunca se combinan entre sí en la misma línea.
7. **Una promoción solo aplica en la tienda online si se marca explícitamente
   `visible_online`** (V32, default `false`) — pensado para ofertas
   storewide tipo Black Friday, no para descuentos puntuales del POS que no
   deberían filtrarse al ecommerce. Cuando está marcada, el precio rebajado
   se muestra en el catálogo público y se recalcula server-side otra vez al
   confirmar el pedido — el mismo criterio de "nunca confiar en el precio
   que manda el cliente" que ya rige el resto del checkout online.

Ver docs/03-modelo-datos.md §18 para el modelo de datos completo.

---

## Matriz rol → permisos (semilla inicial)

| Permiso | ADMIN | SUPERVISOR | VENDEDOR | ALMACENERO |
|---|:---:|:---:|:---:|:---:|
| `DASHBOARD_VER` | ✔ | ✔ | ✔ | ✔ |
| `PRODUCTOS_CONSULTAR` | ✔ | ✔ | ✔ | ✔ |
| `PRODUCTOS_CREAR` | ✔ | ✔ | | |
| `PRODUCTOS_EDITAR` | ✔ | ✔ | | |
| `PRODUCTOS_ELIMINAR` | ✔ | | | |
| `VARIANTES_GESTIONAR` | ✔ | ✔ | | ✔ |
| `BARCODE_GENERAR` | ✔ | ✔ | | ✔ |
| `INVENTARIO_CONSULTAR` | ✔ | ✔ | ✔ | ✔ |
| `INVENTARIO_ENTRADA` | ✔ | ✔ | | ✔ |
| `INVENTARIO_SALIDA` | ✔ | ✔ | | ✔ |
| `INVENTARIO_AJUSTAR` | ✔ | ✔ | | ✔ |
| `VENTAS_CONSULTAR` | ✔ | ✔ | propias | |
| `VENTAS_CREAR` | ✔ | ✔ | ✔ | |
| `VENTAS_ANULAR` | ✔ | ✔ | | |
| `VENTAS_DESCUENTO` | ✔ | ✔ | | |
| `VENTAS_DEVOLVER` | ✔ | ✔ | | |
| `PROMOTORES_CONSULTAR` | ✔ | ✔ | ✔ | |
| `PROMOTORES_GESTIONAR` | ✔ | ✔ | | |
| `CLIENTES_CONSULTAR` | ✔ | ✔ | ✔ | |
| `CLIENTES_CREAR` | ✔ | ✔ | ✔ | |
| `CLIENTES_EDITAR` | ✔ | ✔ | | |
| `PEDIDOS_CONSULTAR` | ✔ | ✔ | ✔ | |
| `PEDIDOS_GESTIONAR` | ✔ | ✔ | | |
| `RESERVAS_CONSULTAR` | ✔ | ✔ | ✔ | |
| `RESERVAS_CREAR` | ✔ | ✔ | ✔ | |
| `RESERVAS_GESTIONAR` | ✔ | ✔ | | |
| `COMBOS_CONSULTAR` | ✔ | ✔ | ✔ | |
| `COMBOS_GESTIONAR` | ✔ | ✔ | | |
| `PROMOCIONES_CONSULTAR` | ✔ | ✔ | ✔ | |
| `PROMOCIONES_GESTIONAR` | ✔ | ✔ | | |
| `PROMOCIONES_APLICAR` | ✔ | ✔ | ✔ | |
| `CAJA_ABRIR` | ✔ | ✔ | ✔ | |
| `CAJA_CERRAR` | ✔ | ✔ | | |
| `CAJA_CONSULTAR` | ✔ | ✔ | propia | |
| `CAJA_MOVIMIENTO` | ✔ | ✔ | | |
| `REPORTES_CONSULTAR` | ✔ | ✔ | | |
| `REPORTES_EXPORTAR` | ✔ | ✔ | | |
| `AUDITORIA_CONSULTAR` | ✔ | | | |
| `USUARIOS_CONSULTAR` | ✔ | ✔ | | |
| `USUARIOS_CREAR` | ✔ | ✔ | | |
| `USUARIOS_EDITAR` | ✔ | ✔ | | |
| `USUARIOS_BLOQUEAR` | ✔ | ✔ | | |
| `USUARIOS_CAMBIAR_CONTRASENA` | ✔ | ✔ | ✔ | ✔ |
| `USUARIOS_RESETEAR_CONTRASENA` | ✔ | | | |
| `ROLES_GESTIONAR` | ✔ | | | |
| `CONFIGURACION_VER` | ✔ | ✔ | | |
| `CONFIGURACION_EDITAR` | ✔ | | | |
| `CONFIGURACION_PAGOS` | ✔ | | | |
| `CONFIGURACION_IDENTIDAD_EDITAR` (RN-26, reservado al operador) | ✔ | | | |

*"propias" / "propia"*: el permiso se concede, pero el service filtra los
resultados al usuario autenticado.

`SUPERVISOR` tiene los cuatro permisos `USUARIOS_*` (desde V27) — antes de
RN-25 esto habría sido peligroso (podría haber creado un Administrador), pero
con el techo de asignación por nivel ya no lo es: solo puede crear/editar
personal de su mismo nivel (50) o menor. `ALMACENERO` deliberadamente **no**
tiene `PRODUCTOS_CREAR/EDITAR/ELIMINAR` (V26) — gestiona stock, variantes y
códigos de barras, no el catálogo de productos en sí; eso quedó fuera de la
semilla original y se había colado por edición manual en una instalación,
V26 lo corrige.

**Ejemplo real de rol a medida** — "Jefe de Tienda" (nivel 40, entre
Supervisor y Vendedor/Almacenero): todo lo operativo de Supervisor menos
`PRODUCTOS_ELIMINAR`, más `USUARIOS_CREAR`/`USUARIOS_EDITAR` (para dar de
alta a su propio personal, limitado por su techo de 40) y
`CONFIGURACION_VER`/`CONFIGURACION_PAGOS` (ve el plan y administra los
métodos de pago de su tienda) — pero nunca `CONFIGURACION_IDENTIDAD_EDITAR`
ni `CONFIGURACION_EDITAR`. No es un rol de sistema: se crea y ajusta desde
Roles y permisos, no vive en ninguna migración.
