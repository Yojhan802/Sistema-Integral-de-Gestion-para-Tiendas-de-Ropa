# 05 — API REST

**Base:** `/api` · **Formato:** JSON · **Auth:** `Authorization: Bearer <token>`
**Fechas:** ISO-8601 con zona (`2026-08-19T18:32:11-05:00`)
**Importes:** número con 2 decimales (`49.90`)

---

## 1. Convenciones

### Códigos de estado

| Código | Uso |
|---|---|
| 200 | Consulta o actualización correcta |
| 201 | Recurso creado (incluye `Location`) |
| 204 | Operación sin contenido de respuesta |
| 400 | Petición malformada o validación fallida |
| 401 | Sin autenticar o token expirado |
| 403 | Autenticado pero sin permiso (o plan de suscripción insuficiente, RN-23) |
| 404 | Recurso inexistente |
| 409 | Conflicto: duplicado o regla de negocio violada |
| 422 | Semánticamente incorrecto |
| 402 | Suscripción suspendida por falta de pago — bloquea todo el sistema (RN-24) |
| 500 | Error interno |

### Respuesta de error (uniforme)

```json
{
  "timestamp": "2026-08-19T18:32:11-05:00",
  "status": 409,
  "error": "DUPLICATE_RESOURCE",
  "message": "El código de barras 7750000001255 ya está registrado",
  "path": "/api/variants",
  "fieldErrors": [
    { "field": "barcode", "message": "ya está registrado" }
  ]
}
```

### Paginación

Petición: `?page=0&size=20&sort=createdAt,desc`

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 143,
  "totalPages": 8,
  "first": true,
  "last": false
}
```

---

## 2. Autenticación — `/api/auth`

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| POST | `/login` | público | Devuelve access + refresh token |
| POST | `/refresh` | público | Renueva el access token |
| POST | `/logout` | autenticado | Revoca el refresh token |
| GET | `/me` | autenticado | Usuario actual con sus permisos |
| POST | `/change-password` | `USUARIOS_CAMBIAR_CONTRASENA` | Cambio de contraseña propia |
| POST | `/complete-password-change` | autenticado | Completa un cambio obligatorio por contraseña temporal |

**POST `/api/auth/login`**
```json
{ "username": "admin", "password": "..." }
```
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "9f2c...",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "user": {
    "id": 1,
    "username": "admin",
    "fullName": "Administrador",
    "mustChangePassword": false,
    "roles": ["ADMINISTRADOR"],
    "permissions": ["VENTAS_CREAR", "VENTAS_ANULAR", "..."]
  }
}
```

El frontend usa `permissions` para ocultar acciones, pero **el backend vuelve a
comprobarlas siempre** (ocultar no es proteger).

`POST /api/auth/change-password` solo cambia la contraseña del usuario autenticado;
no permite modificar la de otro usuario. El permiso
`USUARIOS_CAMBIAR_CONTRASENA` se entrega por defecto a todos los roles.
`POST /api/auth/complete-password-change` es la ruta controlada para el primer
ingreso después de un alta o reseteo: solo funciona mientras la cuenta tenga
`mustChangePassword=true`, y únicamente permite definir la propia contraseña.
El administrador puede restablecer la contraseña de otros usuarios mediante
`/api/users/{id}/reset-password`, operación separada que genera una contraseña
temporal y exige `USUARIOS_RESETEAR_CONTRASENA`.

---

## 3. Empresas de la plataforma — `/api/platform/tenants`

Estas rutas están reservadas al usuario interno marcado como operador de la
plataforma. No dependen del subdominio del tenant actual y no se bloquean si una
empresa está suspendida.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/platform/tenants` | Lista empresas; acepta `search` y `status` |
| POST | `/api/platform/tenants` | Crea empresa, roles, usuario administrador, sucursal, almacén, caja y correlativos |
| PUT | `/api/platform/tenants/{tenantId}` | Actualiza identidad básica, plan y estado de suscripción |

`POST` genera una contraseña temporal y la devuelve una sola vez en la
respuesta. El administrador de la nueva empresa debe cambiarla al iniciar
sesión. El `slug` queda reservado como subdominio y no se cambia desde la
edición normal para no romper los enlaces del negocio.

El usuario operador recibe la autoridad sintética
`PLATAFORMA_EMPRESAS_GESTIONAR`; esta no es un permiso asignable desde los
roles de una empresa.

El alta recibe `name`, `slug`, `businessVertical` (`CLOTHING` o `GENERAL`),
`plan`, los datos de contacto y los datos del administrador inicial. La edición
permite actualizar también el rubro, plan, estado de suscripción y fecha del
próximo pago.

## 4. Usuarios, roles y permisos

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/users` | `USUARIOS_CONSULTAR` |
| GET | `/api/users/{id}` | `USUARIOS_CONSULTAR` |
| POST | `/api/users` | `USUARIOS_CREAR` |
| PUT | `/api/users/{id}` | `USUARIOS_EDITAR` |
| PATCH | `/api/users/{id}/status` | `USUARIOS_BLOQUEAR` |
| POST | `/api/users/{id}/reset-password` | `USUARIOS_RESETEAR_CONTRASENA` |
| GET | `/api/roles` | `ROLES_GESTIONAR`, `USUARIOS_CREAR` o `USUARIOS_EDITAR` |
| GET | `/api/roles/{id}` | `ROLES_GESTIONAR`, `USUARIOS_CREAR` o `USUARIOS_EDITAR` |
| POST | `/api/roles` | `ROLES_GESTIONAR` |
| PUT | `/api/roles/{id}` | `ROLES_GESTIONAR` |
| PUT | `/api/roles/{id}/permissions` | `ROLES_GESTIONAR` |
| GET | `/api/permissions` | `ROLES_GESTIONAR` |

Los `GET` de `/api/roles` son deliberadamente más abiertos que el resto:
crear o editar un usuario exige poder listar los roles para armar el
selector (`usuario-form.js`), aunque quien crea usuarios no tenga permiso
para administrar roles en sí — `ROLES_GESTIONAR` sigue siendo solo de
Administrador.

`hierarchyLevel` (0-100, desde V24) va en `CrearRolRequest`/`ActualizarRolRequest`
(opcional — un rol nuevo nace en 0 si se omite) y en `RolResponse`/`RolResumenResponse`.
Es el techo de asignación de RN-25: `POST /api/users` responde `403
OPERATION_NOT_ALLOWED` si alguno de los `roleIds` pedidos supera el nivel más
alto entre los roles de quien hace la petición.

No existe `DELETE /api/users/{id}`: un usuario se desactiva, porque sus ventas y
movimientos deben seguir siendo atribuibles.

---

## 4. Catálogo

Mismo patrón para `/api/categories`, `/api/subcategories`, `/api/brands`,
`/api/colors`, `/api/sizes`:

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/{recurso}` | `PRODUCTOS_CONSULTAR` |
| POST | `/api/{recurso}` | `CONFIGURACION_EDITAR` |
| PUT | `/api/{recurso}/{id}` | `CONFIGURACION_EDITAR` |
| PATCH | `/api/{recurso}/{id}/status` | `CONFIGURACION_EDITAR` |

`GET /api/subcategories?categoryId=3` filtra por categoría.

---

## 5. Productos — `/api/products`

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `/api/products` | `PRODUCTOS_CONSULTAR` | Listado paginado con filtros |
| GET | `/api/products/{id}` | `PRODUCTOS_CONSULTAR` | Detalle con variantes |
| POST | `/api/products` | `PRODUCTOS_CREAR` | |
| PUT | `/api/products/{id}` | `PRODUCTOS_EDITAR` | |
| PATCH | `/api/products/{id}/status` | `PRODUCTOS_EDITAR` | Activar / desactivar |
| POST | `/api/products/{id}/image` | `PRODUCTOS_EDITAR` | Subir imagen |
| GET | `/api/products/{id}/images` | `PRODUCTOS_CONSULTAR` | Listar galería del producto |
| POST | `/api/products/{id}/images` | `PRODUCTOS_EDITAR` | Agregar imagen (`file`, `altText`, `sortOrder`, `primary`) |
| PATCH | `/api/products/{id}/images/{imageId}/primary` | `PRODUCTOS_EDITAR` | Marcar portada |
| PATCH | `/api/products/{id}/images/{imageId}` | `PRODUCTOS_EDITAR` | Actualizar texto alternativo y orden |
| DELETE | `/api/products/{id}/images/{imageId}` | `PRODUCTOS_EDITAR` | Retirar imagen |
| POST | `/api/products/{id}/size-guide` | `PRODUCTOS_EDITAR` | Subir imagen de guía de tallas (tienda online) |

`material` y `fit` (texto libre, ej. "100% Algodón" / "True to size") viajan
en `CrearProductoRequest`/`ActualizarProductoRequest` y se muestran en la
ficha pública del producto.

**Filtros:** `?search=polo&categoryId=1&subcategoryId=4&brandId=2&status=ACTIVE&minPrice=20&maxPrice=100`

**POST `/api/products`**
```json
{
  "internalCode": "POL-0012",
  "sku": "POL-00125",
  "name": "Polo Oversize",
  "categoryId": 1,
  "subcategoryId": 3,
  "brandId": 2,
  "description": "Polo oversize de algodón peruano 100%",
  "price": 49.90,
  "promoPrice": 39.90
}
```

Si se omite `sku`, el backend lo genera desde la tabla de secuencias.

---

## 6. Variantes — `/api/variants`

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `/api/products/{id}/variants` | `PRODUCTOS_CONSULTAR` | Variantes del producto |
| POST | `/api/products/{id}/variants` | `VARIANTES_GESTIONAR` | Crear una |
| POST | `/api/products/{id}/variants/bulk` | `VARIANTES_GESTIONAR` | **Generar la matriz color × talla** |
| PUT | `/api/variants/{id}` | `VARIANTES_GESTIONAR` | |
| PATCH | `/api/variants/{id}/status` | `VARIANTES_GESTIONAR` | |
| GET | `/api/variants/barcode/{barcode}` | `PRODUCTOS_CONSULTAR` | **Búsqueda por escaneo** |
| GET | `/api/variants/search?q=` | `PRODUCTOS_CONSULTAR` | Búsqueda para POS |

**`bulk`** resuelve el caso real: dar de alta un polo en 2 colores × 6 tallas son
12 variantes que nadie quiere crear a mano.
```json
{ "colorIds": [1, 2], "sizeIds": [1,2,3,4,5,6], "minStock": 3, "generateBarcodes": true }
```
Crea las combinaciones que falten y omite las que ya existan, sin fallar.

**`GET /api/variants/barcode/{barcode}`** es el endpoint más crítico del POS.
Responde en una sola consulta todo lo que necesita la pantalla de venta:
```json
{
  "variantId": 145,
  "productName": "Polo Oversize",
  "colorName": "Negro",
  "sizeName": "M",
  "sku": "POL-00125-M-NEG",
  "barcode": "7750000001255",
  "price": 49.90,
  "promoPrice": 39.90,
  "effectivePrice": 39.90,
  "stock": 12,
  "status": "ACTIVE"
}
```

---

## 7. Códigos de barras — `/api/barcodes`

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| POST | `/api/barcodes/generate` | `BARCODE_GENERAR` | Genera EAN-13 sin asignar |
| POST | `/api/variants/{id}/barcode` | `BARCODE_GENERAR` | Asigna o regenera |
| GET | `/api/barcodes/{code}/validate` | `PRODUCTOS_CONSULTAR` | Comprueba disponibilidad |
| GET | `/api/variants/{id}/label` | `BARCODE_GENERAR` | Etiqueta imprimible (PNG/PDF) |
| POST | `/api/barcodes/labels` | `BARCODE_GENERAR` | Lote de etiquetas |

Los códigos generados son **EAN-13 válidos** con prefijo interno `775` y dígito
verificador calculado, para que cualquier lector estándar los acepte.

---

## 8. Inventario — `/api/inventory`

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `/api/inventory` | `INVENTARIO_CONSULTAR` | Stock por variante |
| GET | `/api/inventory/low-stock` | `INVENTARIO_CONSULTAR` | `stock <= min_stock` |
| GET | `/api/inventory/out-of-stock` | `INVENTARIO_CONSULTAR` | `stock = 0` |
| GET | `/api/inventory/movements` | `INVENTARIO_CONSULTAR` | Historial con filtros |
| GET | `/api/inventory/movements?variantId=` | `INVENTARIO_CONSULTAR` | Historial de una variante |
| POST | `/api/inventory/entry` | `INVENTARIO_ENTRADA` | Entrada |
| POST | `/api/inventory/exit` | `INVENTARIO_SALIDA` | Salida |
| POST | `/api/inventory/adjustment` | `INVENTARIO_AJUSTAR` | Ajuste con motivo obligatorio |

**No hay `PUT` ni `DELETE` sobre movimientos: son inmutables (RN-06).**

```json
// POST /api/inventory/adjustment
{ "variantId": 145, "newStock": 18, "reason": "Recuento físico del 19/08" }
```
El backend calcula la diferencia y crea un movimiento `AJUSTE` con esa cantidad.

---

## 9. Clientes — `/api/customers`

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/customers` | `CLIENTES_CONSULTAR` |
| GET | `/api/customers/{id}` | `CLIENTES_CONSULTAR` |
| GET | `/api/customers/search?q=` | `CLIENTES_CONSULTAR` |
| GET | `/api/customers/{id}/purchases` | `CLIENTES_CONSULTAR` |
| POST | `/api/customers` | `CLIENTES_CREAR` |
| PUT | `/api/customers/{id}` | `CLIENTES_EDITAR` |

`/purchases` devuelve el historial más los agregados (total comprado, número de
compras, última compra, productos comprados) calculados en consulta.

---

## 10. Ventas — `/api/sales`

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `/api/sales` | `VENTAS_CONSULTAR` | Listado con filtros |
| GET | `/api/sales/{id}` | `VENTAS_CONSULTAR` | Detalle completo |
| GET | `/api/sales/{id}/ticket` | `VENTAS_CONSULTAR` | Ticket imprimible |
| POST | `/api/sales` | `VENTAS_CREAR` | **Registrar venta** |
| POST | `/api/sales/{id}/cancel` | `VENTAS_ANULAR` | Anular con motivo; si existe un CPE aceptado, genera y exige aceptar una nota de crédito |

Un vendedor sin `VENTAS_CONSULTAR` global solo ve sus propias ventas: el filtro
lo aplica el service, no el cliente.

**POST `/api/sales`** — operación transaccional completa:
```json
{
  "customerId": 12,
  "promoterId": null,
  "cashSessionId": 4,
  "discountAmount": 0.00,
  "notes": null,
  "items": [
    { "variantId": 145, "quantity": 2, "discountAmount": 0.00, "comboId": null, "promotionId": null },
    { "variantId": 201, "quantity": 1, "discountAmount": 10.00, "comboId": null, "promotionId": null }
  ],
  "payments": [
    { "paymentMethodId": 1, "amount": 100.00, "reference": null },
    { "paymentMethodId": 2, "amount": 59.80, "reference": "OP-88213" }
  ]
}
```
El precio unitario nunca viaja en la petición — el backend siempre lo
resuelve del precio vigente del producto. `discountAmount`, `comboId` y
`promotionId` son mutuamente excluyentes: a lo sumo uno por línea (RN-28,
ver §21 para vender con combo o promoción).

`shippingAmount` (V34) sale en `0.00` para toda venta creada por este
endpoint — solo es distinto de cero en las ventas que genera
`PedidoService.confirmarPago()` al confirmar un pedido online (§18), que
tampoco pasan por este endpoint (no hay sesión de caja detrás de un pago
online, así que esas ventas no se crean con `POST /api/sales`).

`201 Created`:
```json
{
  "id": 1523,
  "saleNumber": "V001-00001523",
  "subtotal": 169.70,
  "discountAmount": 10.00,
  "shippingAmount": 0.00,
  "total": 159.80,
  "status": "COMPLETED",
  "createdAt": "2026-08-19T18:32:11-05:00",
  "seller": { "id": 3, "fullName": "Carlos Ramírez" },
  "customer": { "id": 12, "fullName": "María Quispe" },
  "items": [],
  "payments": []
}
```

Errores posibles: `409 INSUFFICIENT_STOCK`, `409 BUSINESS_RULE_VIOLATION`
(pagos ≠ total, sin caja abierta), `403 ACCESS_DENIED` (descuento sin permiso).

### Promotores — `/api/promoters`

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `/api/promoters` | `PROMOTORES_CONSULTAR` | Listado (para el select opcional del POS) |
| POST | `/api/promoters` | `PROMOTORES_GESTIONAR` | Crear |
| PUT | `/api/promoters/{id}` | `PROMOTORES_GESTIONAR` | Editar nombre |
| PATCH | `/api/promoters/{id}/status` | `PROMOTORES_GESTIONAR` | Activar/desactivar |

Requiere además plan `PROFESIONAL` o superior (RN-23).

Un promotor **no es un usuario del sistema**: sin login, sin contraseña, solo
un nombre. `promoterId` en `POST /api/sales` es opcional — se deja `null`
cuando nadie de piso ofreció la prenda. Nunca se imprime en el ticket, solo
sirve para el reporte de comisión (`/api/reports/sales/by-promoter`).

---

## 11. Pagos y métodos — `/api/payment-methods`

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/payment-methods` | autenticado |
| GET | `/api/payment-methods/{id}` | autenticado |
| PUT | `/api/payment-methods/{id}` | `CONFIGURACION_PAGOS` |
| POST | `/api/payment-methods/{id}/qr` | `CONFIGURACION_PAGOS` |

El POS consume `GET` y muestra los datos oficiales en **solo lectura** (RN-15).
Los pagos no tienen endpoint propio: se crean como parte de la venta, para que no
puedan existir sueltos ni descuadrados.

---

## 12. Caja — `/api/cash-registers`

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `/api/cash-registers` | `CAJA_CONSULTAR` | Cajas físicas |
| GET | `/api/cash-registers/sessions` | `CAJA_CONSULTAR` | Historial de sesiones |
| GET | `/api/cash-registers/sessions/current` | `CAJA_CONSULTAR` | Sesión abierta del usuario |
| GET | `/api/cash-registers/sessions/{id}` | `CAJA_CONSULTAR` | Detalle con movimientos |
| POST | `/api/cash-registers/sessions` | `CAJA_ABRIR` | Abrir caja |
| POST | `/api/cash-registers/sessions/{id}/close` | `CAJA_CERRAR` | Cerrar con arqueo |
| GET | `/api/cash-registers/sessions/{id}/summary` | `CAJA_CONSULTAR` | Previsualizar el arqueo |
| POST | `/api/cash-registers/movements` | `CAJA_MOVIMIENTO` | Ingreso / gasto / retiro |

**Cierre** — el cajero envía solo lo que ha contado; el sistema calcula el resto:
```json
{ "countedAmount": 980.00, "notes": "Faltante detectado en el turno tarde" }
```
```json
{
  "expectedAmount": 1000.00,
  "countedAmount": 980.00,
  "difference": -20.00,
  "status": "CLOSED",
  "closedAt": "2026-08-19T21:05:00-05:00"
}
```

---

## 13. Devoluciones — `/api/returns`

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/returns` | `VENTAS_CONSULTAR` |
| GET | `/api/returns/{id}` | `VENTAS_CONSULTAR` |
| POST | `/api/returns` | `VENTAS_DEVOLVER` |
| GET | `/api/sales/{id}/returnable-items` | `VENTAS_DEVOLVER` |

`returnable-items` devuelve cuánto queda por devolver de cada línea, para que la
interfaz no permita superar lo vendido.

```json
// POST /api/returns
{
  "saleId": 1523,
  "reason": "Talla incorrecta",
  "refundMethodId": 1,
  "items": [
    { "saleDetailId": 3011, "quantity": 1, "restock": true }
  ]
}
```

---

## 14. Reportes — `/api/reports`

Todos aceptan `?from=2026-08-01&to=2026-08-19` y requieren `REPORTES_CONSULTAR`.

| Ruta | Contenido |
|---|---|
| `/api/reports/dashboard` | Métricas del panel principal |
| `/api/reports/sales/summary` | Totales, ticket medio, nº de ventas |
| `/api/reports/sales/by-day` | Serie temporal |
| `/api/reports/sales/by-category` | Ventas por categoría |
| `/api/reports/sales/by-seller` | Ventas por vendedor |
| `/api/reports/sales/by-promoter` | Ventas por promotor (conteo + total, para comisión) |
| `/api/reports/sales/by-payment-method` | Distribución de cobros |
| `/api/reports/payments/online` | Pagos online agrupados por proveedor y estado, con cantidad y monto |
| `/api/reports/billing/documents` | Comprobantes electrónicos agrupados por proveedor y estado, con cantidad y monto |
| `/api/reports/products/top-selling` | Más vendidos |
| `/api/reports/products/no-movement` | Sin rotación |
| `/api/reports/inventory/valuation` | Valorización del stock |
| `/api/reports/inventory/by-size` | Stock por talla |
| `/api/reports/inventory/by-color` | Stock por color |
| `/api/reports/cash/sessions` | Aperturas, cierres y diferencias |

`GET /api/reports/dashboard`:
```json
{
  "salesToday": { "count": 23, "total": 1847.50 },
  "salesMonth": { "count": 412, "total": 32180.00 },
  "productsSoldToday": 41,
  "lowStockCount": 7,
  "outOfStockCount": 2,
  "paymentBreakdown": [
    { "method": "EFECTIVO", "total": 820.00, "percentage": 44.4 },
    { "method": "YAPE", "total": 640.50, "percentage": 34.7 }
  ],
  "salesByDay": [],
  "topProducts": []
}
```

Exportación (`REPORTES_EXPORTAR`): `?format=xlsx|pdf|csv`.

---

## 15. Auditoría — `/api/audit`

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/audit` | `AUDITORIA_CONSULTAR` |
| GET | `/api/audit/{id}` | `AUDITORIA_CONSULTAR` |

Filtros: `?userId=&action=&entity=&result=&from=&to=` (`action`/`entity` con
coincidencia parcial). `GET /api/audit/{id}` agrega `oldValue`/`newValue`
(el JSON crudo guardado por `AuditService.log`) e `ipAddress`/`userAgent`,
que el listado omite por ser pesados de mostrar en una tabla.
**Solo lectura**: no existe forma de escribir ni borrar auditoría por API.

Requiere además plan `PROFESIONAL` o superior (RN-23) — `STARTER` recibe 403
aunque el usuario tenga `AUDITORIA_CONSULTAR`.

---

## 16. Configuración — `/api/settings`

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/settings/company` | `CONFIGURACION_VER` |
| PUT | `/api/settings/company` | `CONFIGURACION_EDITAR` (solo moneda/IGV/envío/pie de ticket) |
| PUT | `/api/settings/company/identity` | `CONFIGURACION_IDENTIDAD_EDITAR` (razón social/RUC/dirección/contacto) |
| POST | `/api/settings/company/logo` | `CONFIGURACION_IDENTIDAD_EDITAR` |

`PUT /api/settings/company` y `PUT /api/settings/company/identity` son dos
endpoints separados a propósito (RN-26) — el primero es operativo y se le
puede conceder a un rol de cliente (ej. "Jefe de Tienda"); el segundo cambia
quién es la empresa ante el sistema y está reservado al operador de la
plataforma, semillado solo en `ADMINISTRADOR`.

`shippingFlatRate` (desde Fase 2) es la tarifa de envío que se cobra en todo
pedido online salvo contraentrega en Huacho — se edita aquí, no hay un
servicio externo del que leerla (ver docs/03 §12).

`plan` (`STARTER`/`PROFESIONAL`/`ECOMMERCE`/`IA`), `subscriptionStatus`
(`ACTIVA`/`SUSPENDIDA`) y `nextPaymentDue` vienen en la respuesta del `GET`
pero **no** son campos de `PUT /api/settings/company` — no se pueden cambiar
desde la API por diseño; solo los cambia el operador de la plataforma directo
en la base de datos (ver docs/03 §15-16, RN-23, RN-24).

### Ficha pública de la instalación — `GET /api/system/info`

Sin autenticación, a propósito — es lo único que un panel externo de
monitoreo (fuera de este sistema, ver docs/03 §15) puede preguntarle a cada
instalación cliente sin credenciales. Es una de las pocas rutas que **siguen
respondiendo incluso con la suscripción suspendida** (RN-24), justamente para
que el panel externo pueda ver ese estado:

```json
{
  "name": "Freestyle Perú",
  "plan": "ECOMMERCE",
  "version": "0.0.1-SNAPSHOT",
  "subscriptionStatus": "ACTIVA",
  "nextPaymentDue": "2026-09-01"
}
```

Deliberadamente mínimo: nada de RUC, dirección ni datos de contacto (eso
sigue detrás de login en `/api/settings/company`). `version` viene de
`spring-boot-maven-plugin` (goal `build-info`, generado en el empaquetado);
si el bean `BuildProperties` no existe (ej. corriendo con `spring-boot:run`
sin pasar por `package`), cae a `"dev"`.

### Actualizar suscripción — `PUT /api/system/subscription`

**No usa login de usuario.** Se autentica con el header `X-Ops-Key`, que debe
coincidir con `OPS_API_KEY` (variable de entorno de esta instalación,
`OpsApiKeyAuthenticationFilter`) — si esa variable no está configurada, el
endpoint queda inalcanzable para cualquiera (fail closed). Pensado para que
el panel de monitoreo externo (`panel-monitoreo`, repo aparte) marque pagos
o suspensiones con un clic. Es una de las rutas exentas del bloqueo por
suspensión (RN-24): funciona incluso con la suscripción ya `SUSPENDIDA`, si
no nunca se podría reactivar por esta vía.

```
PUT /api/system/subscription
X-Ops-Key: <OPS_API_KEY>

{ "subscriptionStatus": "ACTIVA", "nextPaymentDue": "2026-09-30" }
```

`nextPaymentDue` es opcional — si se omite, se deja la fecha actual sin
tocar (útil para suspender manualmente sin cambiarla). Responde el mismo
`SystemInfoResponse` de `GET /api/system/info`. Header ausente o incorrecto
→ `403 ACCESS_DENIED`. **No existe** un endpoint equivalente para cambiar
`plan` — esa decisión sigue siendo solo por base de datos (ver docs/03 §16).

---

## 17. Búsqueda global — `/api/search`

`GET /api/search?q=polo` (autenticado, sin un único permiso que bloquee todo
el endpoint) — devuelve resultados agrupados por tipo. `q` con menos de 2
caracteres devuelve todos los grupos vacíos.

```json
{
  "products": [{ "type": "PRODUCTO", "id": 12, "title": "Polo Oversize", "subtitle": "POL-00125", "url": "producto-detalle.html?id=12" }],
  "customers": [{ "type": "CLIENTE", "id": 3, "title": "María Quispe", "subtitle": "45678912", "url": "clientes.html" }],
  "sales": [{ "type": "VENTA", "id": 1523, "title": "V001-00001523", "subtitle": "María Quispe", "url": "pos.html?ventaId=1523" }],
  "users": [{ "type": "USUARIO", "id": 5, "title": "Carlos Ramírez", "subtitle": "carlos.ramirez", "url": "usuarios.html" }]
}
```

Busca producto por nombre/SKU/código interno **y** por SKU/código de barras
de variante (unificado en un solo resultado de producto). Cada grupo
(`products`/`customers`/`sales`/`users`) solo se llena si el usuario
autenticado tiene el permiso `*_CONSULTAR` correspondiente — la
autorización se aplica por categoría dentro del service, no bloqueando todo
el endpoint (RN-20). `pos.html?ventaId=` abre directo el detalle de esa
venta al cargar la página.

---

## 18. Pedidos (staff) — `/api/orders`

Vista de staff sobre los pedidos hechos desde la tienda online. Ver §19 para el
lado del cliente (`/api/store/orders`). Requiere además plan `ECOMMERCE` o
superior (RN-23).

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `/api/orders` | `PEDIDOS_CONSULTAR` | Listado con filtros `status`, `from`, `to` |
| GET | `/api/orders/{id}` | `PEDIDOS_CONSULTAR` | Detalle completo con items |
| POST | `/api/orders/{id}/confirm` | `PEDIDOS_GESTIONAR` | Confirma el pago — **descuenta stock**, genera una `Sale` y pasa a `CONFIRMED` |
| POST | `/api/orders/{id}/cancel` | `PEDIDOS_GESTIONAR` | Anula — si estaba `CONFIRMED`, **reingresa stock** y cancela la venta enlazada |

```json
// POST /api/orders/{id}/cancel
{ "reason": "El cliente no completó el pago a tiempo" }
```

Errores posibles: `409 BUSINESS_RULE_VIOLATION` (confirmar un pedido que no
está `PENDING_PAYMENT`, o anular uno ya `CANCELLED`), `409 INSUFFICIENT_STOCK`
(al confirmar, si el stock ya no alcanza — puede pasar si otro pedido o venta
se adelantó).

### Confirmar pago genera una venta real (V34)

Al confirmar (`POST /api/orders/{id}/confirm`), además de descontar stock,
`PedidoService.confirmarPago()` crea una `Sale`/`SaleDetail`/`Payment` reales
(ver docs/03-modelo-datos.md §8 y §12) — **sin sesión de caja**, porque un
pago online nunca pasa por caja física. La respuesta (`PedidoResponse`) gana
`saleId` (nulo mientras el pedido está `PENDING_PAYMENT`):
```json
{
  "id": 12,
  "orderNumber": "PED-00000012",
  "status": "CONFIRMED",
  "saleId": 72,
  "...": "..."
}
```
Con ese `saleId`, el panel pide `GET /api/sales/{saleId}` (§10) — que ya
devuelve el `VentaResponse` completo con `items`/`payments` — y reusa el
mismo componente `imprimirTicket` que el POS, sin construir ningún endpoint
ni formato de ticket nuevo. `front/js/pages/pedidos.js` lo dispara
automáticamente apenas se confirma el pago, y deja además un botón
"Imprimir ticket" en el detalle del pedido para reimprimir después.

Si un pedido `CONFIRMED` se cancela, su `Sale` enlazada se marca
`CANCELLED` directamente (mismo motivo de cancelación) — **sin** pasar por
`POST /api/sales/{id}/cancel` (§10), que volvería a reversar stock que el
pedido ya reversó por su cuenta.

---

## 19. Tienda pública — `/api/store/**`

Todo bajo `/api/store` es intencionalmente distinto del resto de la API: el
catálogo es público (sin token) y la autenticación de clientes es un sistema
paralelo al de staff — un JWT de cliente lleva `ROLE_CUSTOMER` como única
autoridad y nunca sirve para pasar un `hasAuthority('VENTAS_...')` de staff,
ni viceversa. Ver docs/03-modelo-datos.md §12. Todo `/api/store/**` requiere
además plan `ECOMMERCE` o superior (RN-23) — en una instalación con un plan
menor, incluso el catálogo público responde 403.

### Catálogo — `/api/store/catalog` (público, sin autenticación)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/store/catalog/products` | Paginado; filtros `search`, `categoryId`, `brandId`. Cada item incluye `colors: [{name, hexCode}]` (colores distintos con stock, máx. 6) para los swatches del listado |
| GET | `/api/store/catalog/products/{id}` | Detalle + variantes (color/talla) con `inStock` |
| GET | `/api/store/catalog/categories` | Solo categorías activas |
| GET | `/api/store/catalog/brands` | Solo marcas activas |
| GET | `/api/store/catalog/payment-methods` | Métodos activos que **no** afecten caja (`affectsCash = false`), con QR/cuenta para pagar. Incluye `code` (ej. `CONTRAENTREGA`) para que el checkout decida cuándo mostrar cada opción |
| GET | `/api/store/catalog/payment-providers` | Pasarelas online activas y listas para el tenant actual; no expone credenciales privadas |
| GET | `/api/store/catalog/shipping-info` | `{ flatRate, freeShippingDistrict }` — tarifa de envío vigente y el distrito con envío gratis (Huacho) |

Las respuestas usan DTOs propios (`Public*Response`) que **nunca** exponen
`internalCode`, SKU/barcode interno, ni `stock`/`minStock` exactos — solo
`inStock` booleano. Esto es deliberado: son datos operativos internos, no
información de cara al público.

```json
// GET /api/store/catalog/products/{id}
{
  "id": 1,
  "name": "Polo Oversize",
  "description": "Polo oversize de algodón peruano 100%",
  "material": "100% Algodón",
  "fit": "True to size",
  "price": 49.90,
  "promoPrice": 39.90,
  "imageUrl": "/uploads/products/....jpg",
  "sizeGuideImageUrl": "/uploads/size-guides/....jpg",
  "categoryName": "Polos",
  "brandName": null,
  "variants": [
    { "variantId": 145, "colorId": 3, "colorName": "Negro", "colorHex": "#000000", "sizeId": 2, "sizeName": "M", "inStock": true }
  ]
}
```

### Autenticación de clientes — `/api/store/auth`

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| POST | `/api/store/auth/register` | público | Crea cuenta o reclama un `Customer` existente sin contraseña por email |
| POST | `/api/store/auth/login` | público | |
| POST | `/api/store/auth/refresh` | público | Mismo formato que `/api/auth/refresh` |
| GET | `/api/store/auth/me` | `ROLE_CUSTOMER` | Datos del cliente autenticado |

```json
// POST /api/store/auth/register
{ "email": "maria@example.com", "password": "Maria2026", "fullName": "María Quispe", "phone": "987654321" }
```
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "9f2c...",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "customer": { "id": 12, "fullName": "María Quispe", "email": "maria@example.com", "phone": "987654321" }
}
```

Si ya existía un `Customer` con ese email (de una compra en tienda física,
sin contraseña), el registro **reclama ese mismo registro** en vez de crear
uno duplicado — así el historial de compras físicas queda ligado a la cuenta
online. `409 DUPLICATE_RESOURCE` si el email ya tiene contraseña.

### Pedidos del cliente — `/api/store/orders` (`ROLE_CUSTOMER`)

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/store/orders` | Crea el pedido — el precio se resuelve server-side, nunca viaja del cliente |
| GET | `/api/store/orders` | Pedidos propios, paginado |
| GET | `/api/store/orders/{id}` | Detalle propio (404 si no es del cliente autenticado) |
| POST | `/api/store/orders/{id}/payment-proof` | Multipart; sube/reemplaza el comprobante de pago (opcional, solo si `PENDING_PAYMENT`) |

```json
// POST /api/store/orders
{
  "items": [{ "variantId": 145, "quantity": 2 }],
  "paymentMethodId": 2,
  "paymentReference": null,
  "recipientDni": "45678912",
  "recipientFirstName": "María",
  "recipientLastNamePaterno": "Quispe",
  "recipientLastNameMaterno": "Ramos",
  "phone": "987654321",
  "address": "Av. Siempre Viva 123",
  "department": "Lima",
  "province": "Lima",
  "district": "San Isidro",
  "notes": null
}
```

`201 Created`, status inicial `PENDING_PAYMENT`. `total = subtotal + shippingCost`:
el envío es la tarifa plana vigente (`/api/store/catalog/shipping-info`),
salvo que el método de pago sea `CONTRAENTREGA` (solo permitido si
`district = "Huacho"`, validado también en el backend), en cuyo caso
`shippingCost = 0`. `paymentMethodId` debe apuntar a un método con
`affectsCash = false` (V34) — validado también server-side, no solo porque
`/api/store/catalog/payment-methods` ya no ofrece los que afectan caja
(`409 BUSINESS_RULE_VIOLATION` si se fuerza uno por API directamente). El
stock **no** se descuenta en este paso — recién al confirmar el pago desde
`/api/orders/{id}/confirm` (§18). El endpoint sí
valida que haya stock suficiente en el momento de crear el pedido (chequeo
informativo, no una reserva).

```json
// POST /api/store/orders/{id}/payment-proof (multipart, campo "file")
```
Devuelve el pedido completo con `paymentProofUrl` actualizado. El staff lo ve
en `GET /api/orders/{id}` (§18) al revisar el pedido antes de confirmar.

---

### Pasarelas y transacciones online

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/store/catalog/payment-providers` | Lista solo proveedores activados, configurados y listos para la empresa |
| POST | `/api/store/orders/{orderId}/payment-transactions` | Crea un intento idempotente; el monto y tenant se resuelven en servidor |
| GET | `/api/store/payment-transactions/{id}` | Consulta un intento propio |
| GET | `/api/store/payment-transactions/{id}/checkout` | Inicializa el checkout desacoplado del proveedor |
| POST | `/api/store/payment-transactions/{id}/charge` | Procesa token/cargo server-side cuando el proveedor lo requiere |

El frontend nunca puede aprobar una transacción. Niubiz y Culqi pueden
resolver el cargo en la respuesta backend; Izipay queda `PENDING` hasta su
IPN firmado.

Configuración staff:

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/settings/payment-providers` | `CONFIGURACION_PAGOS` |
| PUT | `/api/settings/payment-providers/{provider}` | `CONFIGURACION_PAGOS` |

Las credenciales se guardan cifradas y nunca se devuelven completas.

### Facturación electrónica

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `/api/sales/{saleId}/electronic-documents` | `VENTAS_CONSULTAR` | Lista comprobantes de una venta |
| POST | `/api/sales/{saleId}/electronic-documents` | `VENTAS_CREAR` | Crea borrador idempotente de boleta, factura, nota de crédito o nota de débito |
| POST | `/api/electronic-documents/{id}/submit` | `VENTAS_CREAR` | Envía al proveedor configurado por el tenant (Verifac o NubeFact) |
| GET | `/api/electronic-documents/{id}/status` | `VENTAS_CONSULTAR` | Consulta estado en el proveedor del comprobante |
| POST | `/api/electronic-documents/{id}/retry` | `VENTAS_CREAR` | Reintenta un documento rechazado o con error |
| GET | `/api/electronic-documents/{id}/pdf` | `VENTAS_CONSULTAR` | Descarga PDF |
| GET | `/api/electronic-documents/{id}/xml` | `VENTAS_CONSULTAR` | Descarga XML |
| GET | `/api/electronic-documents/{id}/cdr` | `VENTAS_CONSULTAR` | Descarga CDR |

Para una nota, el cuerpo incluye:

```json
{
  "documentType": "NOTA_CREDITO",
  "sourceDocumentId": 10,
  "reasonCode": "06",
  "reasonDescription": "Devolución total",
  "items": null
}
```

El comprobante de origen debe pertenecer a la misma venta y tenant, estar
aceptado por el proveedor configurado y ser una boleta o factura. Para una devolución parcial
(`reasonCode = "07"`), `items` contiene objetos `{ "variantId": 12,
"quantity": 1 }`; las cantidades se validan contra la venta original y se
recalculan en el servidor.

---

## 20. Separaciones — `/api/reservations`

Layaway: apartar una prenda con una seña (típico de lives de TikTok). Ver
docs/03-modelo-datos.md §17 y docs/04-reglas-negocio.md RN-27. Requiere
además plan `PROFESIONAL` o superior (RN-23) — cada método combina el
permiso y el plan en un mismo `@PreAuthorize`.

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `/api/reservations` | `RESERVAS_CONSULTAR` | Listado paginado, filtros `status`, `customerId`, `buyerName` |
| GET | `/api/reservations/{id}` | `RESERVAS_CONSULTAR` | Detalle completo |
| POST | `/api/reservations` | `RESERVAS_CREAR` | Crea la separación — **retira stock de inmediato** |
| POST | `/api/reservations/{id}/complete` | `RESERVAS_GESTIONAR` | Paga el saldo pendiente — genera una `Sale` |
| GET | `/api/reservations/complete-batch/preview` | `RESERVAS_GESTIONAR` | Previsualiza el cobro conjunto de varias separaciones |
| POST | `/api/reservations/complete-batch` | `RESERVAS_GESTIONAR` | Cobra varias separaciones juntas, detectando combo automáticamente |
| POST | `/api/reservations/{id}/cancel` | `RESERVAS_GESTIONAR` | Cancela — libera el stock apartado |

`buyerName` busca por coincidencia parcial (case-insensitive) tanto en el
nombre del cliente registrado (`customer.fullName`) como en el nombre del
comprador ocasional (`guestName`) — pensado para que el cajero ubique
rápido, al momento del recojo presencial, todas las separaciones pendientes
de un comprador del live, sin importar si está registrado como cliente o no.

```json
// POST /api/reservations
{
  "customerId": null,
  "guestName": "Comprador del live",
  "guestPhone": "987654321",
  "items": [
    { "variantId": 88, "quantity": 1, "comboId": null, "comboGroup": null },
    { "variantId": 145, "quantity": 1, "comboId": 7, "comboGroup": 0 },
    { "variantId": 146, "quantity": 1, "comboId": 7, "comboGroup": 0 }
  ],
  "depositAmount": 20.00,
  "depositPaymentMethodId": 2,
  "depositReference": "OP-123456",
  "promoterId": null,
  "notes": "Separado en el live del sábado"
}
```
Exactamente uno de `customerId` (cliente ya registrado) o `guestName`
(comprador ocasional, RN-27) debe venir — `guestPhone` es siempre opcional.
`items` (V33) es una lista — una separación aparta **varios productos de
una vez**, no uno solo. `depositAmount` es opcional — si se omite, se usa
`company_settings.reservation_deposit_amount`; es **una sola seña para todo
el grupo**, sin importar cuántas líneas tenga. `depositPaymentMethodId` debe
apuntar a un método con `affectsCash = false` (nunca efectivo). `201
Created`, status inicial `RESERVADO`, `expiresAt = ahora + reservation_expiration_days`.

Cada `items[]` es un producto suelto (`comboId`/`comboGroup` en `null`) o
una línea de un combo elegido con el botón "+ Agregar combo" del panel
(mismo patrón que el POS — ver §21): `comboId` identifica el combo,
`comboGroup` distingue **aplicaciones repetidas del mismo combo** dentro de
la misma separación (0, 1, 2… — necesario para el caso "8 polos = 2
aplicaciones de un combo de 4 unidades"; sin `comboGroup` el backend
mezclaría las 8 líneas en un solo grupo que ya no calzaría con la
definición del combo, que solo pide 4). El backend valida cada grupo
`(comboId, comboGroup)` con el mismo motor estricto de `VentaService`
(consumo exacto, mismos mensajes de error — "Faltan productos para
completar el combo X", "Los productos elegidos no coinciden con la
definición del combo X") y reparte su descuento proporcionalmente entre sus
líneas; el combo queda **fijado desde la creación**, no se vuelve a
calcular al completar el pago. `total` (respuesta) es la suma de los
`subtotal` de todas las líneas.

```json
// Respuesta (201) — combo casaca+pantalón fijado + una gorra suelta
{
  "id": 12,
  "reservationNumber": "RES-00000011",
  "customerName": "Comprador del live",
  "guest": true,
  "items": [
    { "variantId": 145, "productName": "Casaca...", "quantity": 1, "unitPrice": 100.00,
      "discountAmount": 16.67, "subtotal": 83.33, "comboId": 7, "comboName": "Casaca + Pantalón", "comboGroup": 0 },
    { "variantId": 146, "productName": "Pantalón...", "quantity": 1, "unitPrice": 80.00,
      "discountAmount": 13.33, "subtotal": 66.67, "comboId": 7, "comboName": "Casaca + Pantalón", "comboGroup": 0 },
    { "variantId": 88, "productName": "Gorra...", "quantity": 1, "unitPrice": 25.00,
      "discountAmount": 0.00, "subtotal": 25.00, "comboId": null, "comboName": null, "comboGroup": null }
  ],
  "total": 175.00,
  "depositAmount": 20.00,
  "status": "RESERVADO"
}
```

```json
// POST /api/reservations/{id}/complete
{
  "cashSessionId": 7,
  "payments": [{ "paymentMethodId": 1, "amount": 100.00, "reference": null }]
}
```
`payments` debe sumar **exactamente** el saldo pendiente (`total -
depositAmount`), igual que el cobro mixto de una venta (§10). Exige una
sesión de caja abierta. La seña ya pagada se registra como un `Payment` que
nunca pasa por caja; solo estos pagos del saldo final afectan caja, y solo
si su método de pago tiene `affectsCash = true`. El stock no vuelve a
descontarse — ya salió al crear la separación.

```json
// POST /api/reservations/{id}/cancel
{ "reason": "El cliente ya no la quiere" }
```
Libera el stock apartado. La seña ya pagada **no se devuelve** (mismo
criterio que el vencimiento automático, RN-27).

### Cobro conjunto de varias separaciones (`complete-batch`)

Pensado para cuando el cajero **no** usó el botón "+ Agregar combo" al
crear (ej. varias separaciones sueltas de un mismo live, cada una con un
solo producto): el cajero selecciona en la pantalla de Separaciones varias
reservas `RESERVADO` **del mismo comprador** (cliente registrado o el mismo
`guestName`, comparado sin distinguir mayúsculas) y las cobra de una sola
vez. Junta todas las líneas de las reservas seleccionadas y las separa en
dos grupos: las que **ya traen un combo fijado** desde la creación se
cobran tal cual (no se re-evalúan); entre las **líneas sueltas**
(`comboId` nulo), si sus cantidades calzan con las líneas de un combo
activo (ver §21, incluye líneas por categoría), el combo se detecta y
aplica **automáticamente** sobre ellas — sin que el cajero tenga que
elegirlo a mano.

```
GET /api/reservations/complete-batch/preview?ids=101,102,103
```
```json
{
  "totalNormal": 120.00,
  "totalFinal": 100.00,
  "comboNombre": "4 polos x 100",
  "totalDeposito": 20.00,
  "saldoPendiente": 80.00
}
```
Solo consulta — no cobra ni modifica nada. `comboNombre` es `null` si
ninguna combinación de las separaciones seleccionadas calza con un combo
activo (en ese caso `totalFinal = totalNormal`, cada línea a precio
normal). El frontend usa este preview para mostrar el monto correcto en el
modal de pago **antes** de que el cajero confirme.

```json
// POST /api/reservations/complete-batch
{
  "reservationIds": [101, 102, 103],
  "cashSessionId": 7,
  "payments": [{ "paymentMethodId": 1, "amount": 80.00, "reference": null }]
}
```
`payments` debe sumar **exactamente** `saldoPendiente` (igual que el
`complete` individual). Genera **una sola `Sale`** para todas las
separaciones incluidas: `subtotal = totalNormal`, `discountAmount =
totalNormal - totalFinal`, `total = totalFinal`; el descuento del combo (si
aplica) se reparte proporcionalmente entre las líneas, con la última línea
absorbiendo el redondeo. Cada seña ya pagada se preserva como su propio
`Payment` (con su método/referencia/fecha originales); los pagos del
request solo afectan caja según su propio `affectsCash`. Todas las
reservas pasan a `COMPLETADO` enlazadas a la misma `Sale`. El stock no
vuelve a descontarse.

Errores posibles: `409 BUSINESS_RULE_VIOLATION` (alguna reserva no está
`RESERVADO`, las reservas seleccionadas no son del mismo comprador, suma de
pagos que no coincide con el saldo pendiente, caja cerrada),
`403 FORBIDDEN` (plan por debajo de `PROFESIONAL`, aunque el usuario tenga
el permiso).

Errores posibles (endpoints individuales): `409 BUSINESS_RULE_VIOLATION`
(seña en efectivo, seña mayor al total, completar/cancelar una separación
que no está `RESERVADO`, suma de pagos que no coincide con el saldo
pendiente, caja cerrada), `403 FORBIDDEN` (plan por debajo de
`PROFESIONAL`, aunque el usuario tenga el permiso).

---

## 21. Combos y promociones

Ver docs/03-modelo-datos.md §18 y docs/04-reglas-negocio.md RN-28. Requieren
plan `PROFESIONAL` o superior (RN-23), igual que separaciones.

### Combos — `/api/combos`

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `/api/combos` | `COMBOS_CONSULTAR` | Listado completo, con sus productos |
| GET | `/api/combos/{id}` | `COMBOS_CONSULTAR` | Detalle |
| POST | `/api/combos` | `COMBOS_GESTIONAR` | Crea un combo |
| PUT | `/api/combos/{id}` | `COMBOS_GESTIONAR` | Reemplaza nombre/precio/productos |
| PATCH | `/api/combos/{id}/status` | `COMBOS_GESTIONAR` | Activa/desactiva |

```json
// POST /api/combos
{
  "code": "COMBO-4-POLOS",
  "name": "4 polos x 100",
  "price": 100.00,
  "items": [
    { "selectorType": "CATEGORY", "categoryId": 3, "brandId": 5, "quantity": 4 }
  ]
}
```
```json
// POST /api/combos — línea mixta (producto específico + categoría)
{
  "code": "COMBO-CASACA-ACC",
  "name": "Casaca + accesorio",
  "price": 150.00,
  "items": [
    { "selectorType": "PRODUCT", "productId": 12, "quantity": 1 },
    { "selectorType": "CATEGORY", "categoryId": 8, "brandId": null, "quantity": 1 }
  ]
}
```
Cada línea (`ComboItem`) es de un tipo:
- `PRODUCT` — un **producto** específico (no variante) — el cajero elige
  color/talla al vender el combo en el POS o al crearlo dentro de una
  separación (§20). Requiere `productId`, `categoryId`/`brandId` deben ir
  `null`.
- `CATEGORY` — cualquier producto de una **categoría**, opcionalmente
  acotado a una **marca** (`brandId` es opcional dentro de este tipo).
  Requiere `categoryId`, `productId` debe ir `null`.

Un combo puede combinar líneas de ambos tipos. Al vender (§10) o al cobrar
varias separaciones juntas (§20 `complete-batch`), el emparejamiento usa un
algoritmo *greedy*: primero se resuelven las líneas `PRODUCT` (más
restrictivas, reclaman su producto exacto primero) y luego las `CATEGORY`,
para que una línea de producto específico nunca "pierda" su producto frente
a una línea de categoría más amplia que también calzaría con él.

`409 BUSINESS_RULE_VIOLATION` si `price` no es menor a la suma de los
precios vigentes de los productos — esta validación **se omite** si el
combo tiene alguna línea `CATEGORY` (no hay un conjunto fijo de productos
con el que comparar); en ese caso la validez del precio se confía a la
regla de negocio general y `normalTotal`/`savings` en la respuesta salen
`null`.

### Promociones — `/api/promotions`

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `/api/promotions` | `PROMOCIONES_CONSULTAR` | Listado completo |
| GET | `/api/promotions/applicable?variantId=` | `PROMOCIONES_CONSULTAR` | Promociones vigentes y aplicables a esa variante — para el POS |
| GET | `/api/promotions/{id}` | `PROMOCIONES_CONSULTAR` | Detalle |
| POST | `/api/promotions` | `PROMOCIONES_GESTIONAR` | Crea una promoción |
| PUT | `/api/promotions/{id}` | `PROMOCIONES_GESTIONAR` | Reemplaza |
| PATCH | `/api/promotions/{id}/status` | `PROMOCIONES_GESTIONAR` | Activa/desactiva |

```json
// POST /api/promotions
{
  "code": "VERANO25",
  "name": "20% en polos",
  "discountType": "PERCENTAGE",
  "discountValue": 20.00,
  "scopeType": "CATEGORY",
  "scopeCategoryId": 3,
  "scopeProductId": null,
  "startsAt": "2026-01-01T00:00:00",
  "endsAt": "2026-02-28T23:59:59",
  "visibleOnline": false
}
```
`discountType` es `PERCENTAGE` (máx. 100) o `FIXED_AMOUNT`. `scopeType` es
`ALL` (todo el catálogo), `CATEGORY` (requiere `scopeCategoryId`) o
`PRODUCT` (requiere `scopeProductId`) — el campo que no corresponda debe ir
`null`. `startsAt`/`endsAt` son opcionales; nulo de un lado = sin límite por
ese lado.

`visibleOnline` (default `false`) controla si la promoción también aplica
en la **tienda online**, pensado para ofertas de todo el catálogo tipo
Black Friday. En `false` (el caso normal), la promoción solo la puede
aplicar el cajero en el POS con `PROMOCIONES_APLICAR`. En `true`:
- `GET /api/store/catalog/**` (§19) muestra el precio ya rebajado en el
  campo existente `promoPrice` (reutilizado — no hay campos nuevos en las
  respuestas públicas: si no hay ninguna promoción `visibleOnline` vigente
  y aplicable, `promoPrice` sale `null` como hoy).
- El checkout online (`POST /api/store/orders`, §19) recalcula y aplica el
  mismo precio rebajado **server-side** al crear el pedido — nunca confía
  en el precio que mandó el frontend.
- Si varias promociones `visibleOnline` vigentes aplican al mismo producto,
  se usa la que dé el precio más bajo.

### Vender con combo o promoción — `POST /api/sales` (§10)

Un combo se vende como varias líneas normales que comparten `comboId`
(una por variante elegida — el conjunto de productos/cantidades debe
coincidir exacto con la definición del combo); el backend calcula y reparte
el descuento, un `discountAmount` en esas líneas es rechazado. Una promoción
se aplica con `promotionId` en una sola línea (requiere `PROMOCIONES_APLICAR`,
no `VENTAS_DESCUENTO`); el backend revalida vigencia y alcance server-side.

```json
{
  "cashSessionId": 4,
  "items": [
    { "variantId": 145, "quantity": 1, "comboId": 7 },
    { "variantId": 201, "quantity": 1, "comboId": 7 },
    { "variantId": 88, "quantity": 1, "promotionId": 3 }
  ],
  "payments": [{ "paymentMethodId": 1, "amount": 183.91, "reference": null }]
}
```
`sale.items[].comboId` / `.promotionId` en la respuesta permiten al frontend
mostrar de qué combo/promoción viene cada línea. Errores posibles:
`409 BUSINESS_RULE_VIOLATION` (productos del combo no coinciden con su
definición, precio del combo ya no válido frente al precio actual de sus
productos, promoción fuera de vigencia o de alcance, más de un modificador
en la misma línea), `403 ACCESS_DENIED` (sin `PROMOCIONES_APLICAR` al usar
`promotionId`).

---

## 22. Notificaciones en tiempo real (SSE) — `/api/notifications`, `/api/store/notifications`

Empuja eventos de pedidos al navegador sin que este tenga que refrescar ni
sondear (`setInterval`/polling): el staff se entera al instante de un pedido
nuevo, y el cliente ve cuando el suyo cambia de estado. Implementado con
Server-Sent Events (`SseEmitter`, ya incluido en `spring-boot-starter-webmvc`
— no se agregó ninguna dependencia nueva) porque el flujo es siempre
servidor → navegador, nunca al revés.

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `/api/notifications/stream?token=` | `PEDIDOS_CONSULTAR` + plan `ECOMMERCE` | Stream de staff — recibe `pedido-nuevo` por cada pedido que entra |
| GET | `/api/store/notifications/stream?token=` | `ROLE_CUSTOMER` | Stream del cliente — recibe `pedido-actualizado` solo de sus propios pedidos |

**El token va por query param, no por header.** El navegador (`EventSource`)
no puede mandar headers propios en la conexión inicial, así que estas dos
rutas son la única excepción en toda la API: `JwtAuthenticationFilter` cae al
parámetro `?token=` cuando el header `Authorization` está ausente, pero
**solo** para estas dos URIs exactas — el resto de la API sigue exigiendo el
header como siempre. Es el mismo access token de 30 minutos de todos los
demás endpoints (no hay un sistema aparte de tickets de un solo uso); si
expira mientras el stream está abierto, el cliente reconecta con un token
refrescado (`front/js/core/live-stream.js`).

Eventos emitidos, ambos con el `PedidoResponse` completo (§18) como `data`:
```
event: pedido-nuevo
data: { "id": 15, "orderNumber": "PED-00000015", "status": "PENDING_PAYMENT", ... }

event: pedido-actualizado
data: { "id": 15, "orderNumber": "PED-00000015", "status": "CONFIRMED", ... }
```
`pedido-nuevo` se dispara al final de `PedidoService.crear()` (a todo el
staff conectado); `pedido-actualizado` al final de `confirmarPago()` y de
`cancelar()` (solo a los streams del cliente dueño del pedido). Ambos son
disparos genéricos a propósito — agregar el envío automático de
WhatsApp/boleta al confirmar (pendiente, fuera de esta tanda) será sumar
otro suscriptor a estos mismos eventos, no tocar `PedidoService` de nuevo.

Un emitter que falla al escribir (cliente desconectado) se descarta en
silencio — nunca tumba la transacción que originó el evento. No hay cola ni
estado persistente: las listas de suscriptores viven en memoria del proceso,
así que un restart del backend simplemente cierra las conexiones abiertas
(el frontend reconecta solo).

**nginx**: estas rutas necesitan `proxy_buffering off` y un `proxy_read_timeout`
largo (ver `deploy/nginx-freestyleperu.conf` y docs/08-despliegue.md) — sin
eso, nginx acumula la respuesta completa antes de mandarla y nada llega "en
vivo" en producción aunque funcione perfecto en local.
