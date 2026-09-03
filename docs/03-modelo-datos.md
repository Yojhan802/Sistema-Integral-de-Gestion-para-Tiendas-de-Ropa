# 03 — Modelo de datos

MySQL 8 · InnoDB · `utf8mb4_0900_ai_ci` · zona horaria `America/Lima`

## 1. Mapa de relaciones

```
                          ┌──────────┐
                          │ branches │  (sucursal — 1 registro en Fase 1)
                          └────┬─────┘
                 ┌─────────────┼──────────────┐
                 │                            │
          ┌──────▼──────┐            ┌────────▼────────┐
          │ warehouses  │            │ cash_registers  │
          └──────┬──────┘            └────────┬────────┘
                 │                            │
                 │                   ┌────────▼────────┐
                 │                   │  cash_sessions  │
                 │                   └────────┬────────┘
                 │                            │
                 │                   ┌────────▼────────┐
                 │                   │ cash_movements  │
                 │                   └─────────────────┘
                 │
   ┌─────────────▼─────────────┐
   │   inventory_movements     │◄──────┐
   └─────────────┬─────────────┘       │
                 │                     │ (referencia polimórfica
   ┌─────────────▼─────────────┐       │  reference_type/reference_id)
   │     product_variants      │       │
   └──┬────────┬────────┬──────┘       │
      │        │        │              │
 ┌────▼───┐ ┌──▼───┐ ┌──▼────┐         │
 │products│ │colors│ │ sizes │         │
 └───┬────┘ └──────┘ └───────┘         │
     │                                 │
 ┌───▼──────────┬──────────┐           │
 │ categories   │  brands  │           │
 └───┬──────────┴──────────┘           │
     │                                 │
 ┌───▼───────────┐                     │
 │ subcategories │                     │
 └───────────────┘                     │
                                       │
   ┌───────────┐    ┌──────────────────┴──┐    ┌──────────┐
   │ customers │◄───┤       sales         ├───►│  users   │
   └───────────┘    └───┬────────────┬────┘    └────┬─────┘
                        │            │              │
              ┌─────────▼───┐   ┌────▼─────┐   ┌────▼──────┐
              │sale_details │   │ payments │   │user_roles │
              └─────┬───────┘   └────┬─────┘   └────┬──────┘
                    │                │              │
                    │       ┌────────▼────────┐  ┌──▼────┐
                    │       │ payment_methods │  │ roles │
                    │       └─────────────────┘  └──┬────┘
                    │                               │
         ┌──────────▼──────────┐         ┌──────────▼─────────┐
         │  return_details     │         │ role_permissions   │
         └──────────┬──────────┘         └──────────┬─────────┘
                    │                               │
              ┌─────▼─────┐                   ┌─────▼───────┐
              │  returns  │                   │ permissions │
              └───────────┘                   └─────────────┘

  Independientes:  audit_logs · company_settings · sequences · refresh_tokens
```

## 2. Convenciones

- Nombres de tabla en **inglés, plural, snake_case** (coinciden con el documento §46).
- PK: `id BIGINT UNSIGNED AUTO_INCREMENT`.
- FK: `<tabla_singular>_id`.
- Fechas: `DATETIME(6)`. `created_at` y `updated_at` en todo lo mutable.
- Importes: `DECIMAL(12,2)`. **Nunca** `FLOAT` ni `DOUBLE`.
- Estados: `VARCHAR` + `CHECK`, mapeado a `enum` de Java con `@Enumerated(STRING)`.
  Se evita el tipo `ENUM` de MySQL porque añadir un valor exige `ALTER TABLE`.
- Borrado lógico mediante columna `status`; **no** se borra físicamente nada con
  valor histórico.

---

## 3. Seguridad y acceso

### `users`
| Columna | Tipo | Restricciones |
|---|---|---|
| id | BIGINT UNSIGNED | PK |
| username | VARCHAR(50) | **UNIQUE**, NOT NULL |
| email | VARCHAR(120) | UNIQUE, NULL |
| password_hash | VARCHAR(100) | NOT NULL |
| full_name | VARCHAR(120) | NOT NULL |
| dni | VARCHAR(15) | UNIQUE, NULL |
| phone | VARCHAR(20) | NULL |
| status | VARCHAR(20) | NOT NULL, DEFAULT `ACTIVE` · ACTIVE/INACTIVE/BLOCKED |
| failed_attempts | SMALLINT | NOT NULL DEFAULT 0 |
| locked_until | DATETIME(6) | NULL |
| must_change_password | BOOLEAN | NOT NULL DEFAULT FALSE |
| last_login_at | DATETIME(6) | NULL |
| created_at / updated_at | DATETIME(6) | NOT NULL |

### `roles`
`id` · `code` **UNIQUE** (`ADMINISTRADOR`, `SUPERVISOR`, `VENDEDOR`, `ALMACENERO`) ·
`name` · `description` · `is_system` BOOLEAN — los roles de sistema no se pueden borrar ·
`hierarchy_level` SMALLINT (desde V24, 0-100) — techo de asignación: al crear
un usuario, solo se le pueden dar roles con `hierarchy_level` ≤ el más alto
entre los roles de quien lo crea (ver RN-25). Semilla: Administrador=100,
Supervisor=50, Vendedor/Almacenero=10; un rol nuevo nace en 0.

### `permissions`
`id` · `code` **UNIQUE** (`VENTAS_ANULAR`) · `module` (`VENTAS`) · `description`.
La columna `module` permite agrupar los permisos en la pantalla de roles.

### `role_permissions`
PK compuesta `(role_id, permission_id)`. Ambas FK con `ON DELETE CASCADE`.

### `user_roles`
PK compuesta `(user_id, role_id)`. Un usuario puede acumular varios roles; sus
permisos efectivos son la **unión** de los permisos de todos sus roles.

### `refresh_tokens`
`id` · `user_id` FK · `token_hash` **UNIQUE** · `expires_at` · `revoked_at` ·
`created_at`. Se guarda el **hash** del token, no el token, para que una fuga de
la base de datos no permita suplantar sesiones.

---

## 4. Catálogo

### `categories`
`id` · `name` **UNIQUE** · `slug` UNIQUE · `status` · timestamps.

### `subcategories`
`id` · `category_id` FK → categories · `name` · `slug` · `status`.
**UNIQUE (category_id, name)** — "Manga corta" puede existir en Polos y en Camisas.

### `brands`
`id` · `name` **UNIQUE** · `status`.

### `colors`
`id` · `name` **UNIQUE** · `hex_code` CHAR(7) — permite pintar el color real en la
UI en lugar de mostrar solo texto · `status`.

### `sizes`
`id` · `name` **UNIQUE** · `sort_order` SMALLINT · `status`.
`sort_order` es necesario porque el orden natural de tallas (XS,S,M,L,XL,XXL) no
es alfabético.

---

## 5. Productos y variantes

### `products`
| Columna | Tipo | Restricciones |
|---|---|---|
| id | BIGINT UNSIGNED | PK |
| internal_code | VARCHAR(30) | **UNIQUE**, NOT NULL |
| sku | VARCHAR(40) | **UNIQUE**, NOT NULL |
| name | VARCHAR(150) | NOT NULL, INDEX (búsqueda) |
| category_id | BIGINT | FK NOT NULL |
| subcategory_id | BIGINT | FK NULL |
| brand_id | BIGINT | FK NULL |
| description | TEXT | NULL |
| price | DECIMAL(12,2) | NOT NULL, `CHECK (price >= 0)` |
| promo_price | DECIMAL(12,2) | NULL, `CHECK (promo_price >= 0)` |
| status | VARCHAR(20) | NOT NULL DEFAULT `ACTIVE` |
| image_url | VARCHAR(255) | NULL |
| material / fit | VARCHAR(150) / VARCHAR(100) | NULL (desde V21) — texto libre para la ficha de la tienda online |
| size_guide_image_url | VARCHAR(255) | NULL (desde V21) — imagen de guía de tallas de la tienda online |
| created_at / updated_at | DATETIME(6) | NOT NULL |
| created_by / updated_by | BIGINT | FK → users |

Índices: `idx_products_name(name)`, `idx_products_category(category_id)`,
`idx_products_status(status)`.

### `product_variants`
| Columna | Tipo | Restricciones |
|---|---|---|
| id | BIGINT UNSIGNED | PK |
| product_id | BIGINT | FK NOT NULL |
| color_id | BIGINT | FK NOT NULL |
| size_id | BIGINT | FK NOT NULL |
| sku | VARCHAR(60) | **UNIQUE**, NOT NULL |
| barcode | VARCHAR(20) | **UNIQUE**, NULL |
| stock | INT | NOT NULL DEFAULT 0, `CHECK (stock >= 0)` |
| min_stock | INT | NOT NULL DEFAULT 0, `CHECK (min_stock >= 0)` |
| status | VARCHAR(20) | NOT NULL DEFAULT `ACTIVE` |
| created_at / updated_at | DATETIME(6) | NOT NULL |

**Restricciones clave:**
- `uk_variant_combination UNIQUE (product_id, color_id, size_id)` — impide
  registrar dos veces "Polo Oversize / Negro / M".
- `uk_variant_barcode UNIQUE (barcode)` — el código de barras es único en todo el
  sistema. Es `NULL` mientras no se le asigne (MySQL permite varios `NULL` en un
  índice único, que es justo el comportamiento que se necesita).
- `CHECK (stock >= 0)` — última línea de defensa contra la sobreventa, además de
  la validación en el service.

**SKU vs código de barras** — son campos distintos y nunca intercambiables:

| | SKU | Código de barras |
|---|---|---|
| Para qué | identificador interno legible | lectura con pistola |
| Ejemplo | `POL-00125-M-NEG` | `7750000001255` |
| Formato | definido por la empresa | EAN-13 con dígito verificador |
| Lo usa | personal, reportes | escáner en POS |

---

## 6. Inventario

### `branches` · `warehouses`
`branches`: `id` · `code` UNIQUE · `name` · `address` · `phone` · `status`.
`warehouses`: `id` · `branch_id` FK · `code` UNIQUE · `name` · `status`.

En Fase 1 hay un registro de cada uno (`Tienda Principal` / `Almacén Principal`).
Existen desde el inicio para que los movimientos ya nazcan con `warehouse_id` y
el salto a multisucursal no exija reescribir el histórico.

### `inventory_movements` — **inmutable**
| Columna | Tipo | Restricciones |
|---|---|---|
| id | BIGINT UNSIGNED | PK |
| variant_id | BIGINT | FK NOT NULL |
| warehouse_id | BIGINT | FK NOT NULL |
| type | VARCHAR(20) | NOT NULL · ENTRADA/SALIDA/VENTA/DEVOLUCION/AJUSTE/MERMA |
| quantity | INT | NOT NULL, `CHECK (quantity <> 0)` — **con signo** |
| stock_before | INT | NOT NULL |
| stock_after | INT | NOT NULL |
| reference_type | VARCHAR(20) | NULL · SALE/RETURN/ADJUSTMENT |
| reference_id | BIGINT | NULL |
| reason | VARCHAR(255) | NULL |
| user_id | BIGINT | FK NOT NULL |
| created_at | DATETIME(6) | NOT NULL |

No tiene `updated_at` **a propósito**: un movimiento nunca se modifica. Corregir
un error de inventario se hace con un movimiento `AJUSTE` en sentido contrario,
igual que en contabilidad.

`quantity` lleva signo (`-1` en una venta, `+10` en una entrada). Así se cumple
siempre `stock_after = stock_before + quantity`, y la suma de todos los
movimientos de una variante debe ser igual a su columna `stock`: es una
invariante verificable con una sola consulta.

Índices: `idx_mov_variant(variant_id, created_at)`, `idx_mov_type(type)`,
`idx_mov_reference(reference_type, reference_id)`.

---

## 7. Clientes

### `customers`
`id` · `full_name` NOT NULL · `doc_type` (DNI/RUC/CE/SIN_DOCUMENTO) ·
`doc_number` **UNIQUE NULL** · `phone` · `email` **UNIQUE NULL** (desde Fase 2) ·
`password_hash` VARCHAR(100) NULL (desde Fase 2) · `birth_date` · `status` ·
timestamps.

DNI y email son opcionales según el requisito. El historial de compras y los
totales acumulados **no se guardan como columnas**: se calculan consultando
`sales`, evitando datos redundantes que puedan desincronizarse.

`password_hash` es NULL salvo que el cliente se haya registrado en la tienda
online (§14). Se reutiliza la misma tabla en vez de crear un modelo de cuenta
aparte: si alguien se registra con un correo que ya existe (un cliente que
compró antes en tienda física, sin contraseña), se le asigna la contraseña a
ese mismo registro — así su historial de compras físicas queda ligado a su
cuenta online. `email` pasa a ser **UNIQUE** con la migración V18 para que esa
búsqueda por correo sea inequívoca.

---

## 8. Ventas

### `sales`
| Columna | Tipo | Restricciones |
|---|---|---|
| id | BIGINT UNSIGNED | PK |
| sale_number | VARCHAR(20) | **UNIQUE**, NOT NULL · `V001-00000123` |
| customer_id | BIGINT | FK NULL |
| user_id | BIGINT | FK NOT NULL — vendedor (quien opera la caja, el que figura en el ticket) |
| promoter_id | BIGINT | FK NULL — promotor de piso que ofreció la prenda, si hubo uno (opcional, solo para comisión/reportes; **nunca aparece en el ticket**) |
| cash_session_id | BIGINT | FK NULL (desde V34) — nulo cuando la venta viene de confirmar un pedido online, que nunca pasa por caja física |
| subtotal | DECIMAL(12,2) | NOT NULL |
| discount_amount | DECIMAL(12,2) | NOT NULL DEFAULT 0 |
| shipping_amount | DECIMAL(12,2) | NOT NULL DEFAULT 0 (desde V34) — costo de envío cuando la venta viene de un pedido online; 0 en ventas de POS/separaciones |
| total | DECIMAL(12,2) | NOT NULL, `CHECK (total >= 0)` |
| status | VARCHAR(25) | NOT NULL · COMPLETED/CANCELLED/RETURNED/PARTIALLY_RETURNED |
| notes | VARCHAR(255) | NULL |
| created_at | DATETIME(6) | NOT NULL |
| cancelled_at | DATETIME(6) | NULL |
| cancelled_by | BIGINT | FK NULL |
| cancellation_reason | VARCHAR(255) | NULL |
| authorized_by | BIGINT | FK NULL — quién autorizó la anulación |

`cash_session_id` es obligatorio para una venta de POS o de separación: **no
se puede vender sin caja abierta**, y así el arqueo siempre cuadra. La única
excepción (V34) son las ventas generadas al confirmar el pago de un pedido
online (`PedidoService.confirmarPago`, ver §12) — ese pago nunca pasa por
caja física, así que su `Sale` se crea con `cash_session_id = NULL`. Dos
sitios que asumían una caja siempre presente (`VentaService.anular()` y
`DevolucionService.registrar()`, ambos solo cuando el método de pago
`affects_cash = true`) tienen una guardia explícita que rechaza la
operación con un mensaje claro en vez de fallar con un error interno.

Índices: `idx_sales_created(created_at)`, `idx_sales_user(user_id)`,
`idx_sales_customer(customer_id)`, `idx_sales_status(status)`.

### `sale_details`
`id` · `sale_id` FK · `variant_id` FK · `quantity` `CHECK (> 0)` ·
`unit_price` · `discount_amount` · `subtotal` ·
**snapshot:** `product_name`, `variant_sku`, `color_name`, `size_name`.

El snapshot es deliberado (decisión D-05): si mañana renombran el producto o
cambian su precio, la venta histórica sigue mostrando lo que realmente se vendió
ese día y por cuánto. Sin él, los reportes del pasado cambiarían solos.

### `payment_methods`
| Columna | Uso |
|---|---|
| code **UNIQUE** | EFECTIVO, YAPE, PLIN, TARJETA, TRANSFERENCIA |
| type | CASH / DIGITAL_WALLET / CARD / TRANSFER |
| **affects_cash** | BOOLEAN — solo `true` en efectivo |
| requires_reference | BOOLEAN — pide número de operación |
| account_holder / account_number | datos oficiales de Yape/Plin |
| qr_image_url | QR mostrado en el POS |
| status · sort_order | |

`affects_cash` es la columna que decide qué entra en el arqueo de caja: un cobro
por Yape no aumenta el efectivo del cajón. Los datos de cuenta solo los edita
quien tenga `CONFIGURACION_PAGOS` — nunca desde el POS (regla 8).

### `payments`
`id` · `sale_id` FK · `payment_method_id` FK · `amount` `CHECK (> 0)` ·
`reference` (nº de operación) · `status` (PENDING/COMPLETED/REFUNDED) · `created_at`.

Varias filas por venta ⇒ **pago mixto**. La invariante
`SUM(payments.amount) = sales.total` se valida en el service dentro de la
transacción.

### `promoters`
`id` · `name` · `status` (ACTIVE/INACTIVE) · `created_at` · `updated_at`.

Personal de piso que ofrece la prenda pero no opera la caja. **No es un
usuario del sistema** (sin login, sin contraseña) — es solo un nombre
seleccionable, opcional, al momento de cobrar. Sirve para medir comisión con
el reporte "ventas por promotor" (conteo + total por promotor). Deliberadamente
separado de `users`: crear una cuenta con contraseña para alguien que nunca va
a loguearse no tenía sentido.

---

## 9. Caja

### `cash_registers`
`id` · `branch_id` FK · `code` UNIQUE · `name` · `status`.

### `cash_sessions`
| Columna | Tipo |
|---|---|
| id · cash_register_id FK · opened_by FK | |
| opening_amount | DECIMAL(12,2) NOT NULL |
| opened_at | DATETIME(6) NOT NULL |
| expected_amount / counted_amount / difference | DECIMAL(12,2) NULL |
| closed_by | FK NULL |
| closed_at | DATETIME(6) NULL |
| status | OPEN / CLOSED |
| notes | VARCHAR(255) |

**Una sola sesión abierta por caja** se garantiza en la propia base de datos con
una columna generada más un índice único:

```sql
open_register_id BIGINT UNSIGNED GENERATED ALWAYS AS
    (CASE WHEN status = 'OPEN' THEN cash_register_id END) VIRTUAL,
UNIQUE KEY uk_one_open_session (open_register_id)
```

Al haber múltiples `NULL` permitidos en un índice único, las sesiones cerradas no
estorban, pero dos sesiones abiertas en la misma caja son imposibles incluso ante
una condición de carrera.

`difference = counted_amount − expected_amount`. Se guarda calculada porque es un
dato contable que debe quedar congelado en el momento del cierre.

### `cash_movements` — inmutable
`id` · `cash_session_id` FK · `type` (APERTURA/VENTA/INGRESO/GASTO/RETIRO/DEVOLUCION) ·
`amount` **con signo** · `reference_type` · `reference_id` · `reason` ·
`user_id` FK · `created_at`.

Efectivo esperado al cierre = `opening_amount + SUM(cash_movements.amount)`.

---

## 10. Devoluciones

### `returns`
`id` · `return_number` **UNIQUE** · `sale_id` FK · `user_id` FK ·
`authorized_by` FK NULL · `total_amount` · `refund_method_id` FK ·
`reason` NOT NULL · `status` · `created_at`.

### `return_details`
`id` · `return_id` FK · `sale_detail_id` FK · `variant_id` FK ·
`quantity` `CHECK (> 0)` · `unit_price` · `subtotal` ·
**`restock` BOOLEAN NOT NULL** — decide explícitamente si la prenda vuelve al
stock. Una prenda dañada se devuelve al cliente pero no vuelve a la venta.

`sale_detail_id` permite validar que no se devuelva más cantidad de la vendida
en esa línea concreta.

---

## 11. Auditoría y configuración

### `audit_logs`
`id` · `user_id` FK NULL · `username` (**snapshot**, sobrevive al borrado del
usuario) · `action` · `entity` · `entity_id` · `old_value` JSON · `new_value` JSON ·
`result` (SUCCESS/DENIED/FAILURE) · `ip_address` VARCHAR(45) (cabe IPv6) ·
`user_agent` · `created_at`.

Índices: `idx_audit_user(user_id, created_at)`, `idx_audit_entity(entity, entity_id)`,
`idx_audit_created(created_at)`.

### `company_settings`
Fila única (`id = 1`): `name` · `ruc` · `address` · `phone` · `email` ·
`logo_url` · `currency_code` (PEN) · `currency_symbol` (S/) · `igv_rate` ·
`ticket_footer` · `shipping_flat_rate` (desde V19, §12) ·
`reservation_deposit_amount` / `reservation_expiration_days` (desde V28, §17) ·
`plan` (desde V22, §15) · `updated_at` · `updated_by`.

### `sequences`
`name` PK · `prefix` · `current_value` · `padding`.

Genera `sale_number`, `return_number`, SKU y correlativos de código de barras.
Se lee con `SELECT ... FOR UPDATE` para que dos cajas simultáneas no obtengan el
mismo número.

---

## 12. Tienda online (Fase 2 — catálogo + carrito + pedido)

Todo lo público vive en el paquete `tienda`, separado de `producto`/`cliente`,
para que quede auditable de un vistazo qué endpoints son intencionalmente
públicos. Ver docs/05-api.md §Tienda pública para los endpoints.

### `customer_refresh_tokens`
Espejo de `refresh_tokens`, pero para clientes: `id` · `customer_id` FK
(`ON DELETE CASCADE`) · `token_hash` **UNIQUE** · `expires_at` · `revoked_at` ·
`created_at`. Tabla separada de `refresh_tokens` (que es de staff) — un token
de cliente lleva `ROLE_CUSTOMER` como única autoridad en el JWT y nunca debe
poder confundirse con la sesión de un `Usuario`.

### `orders`
| Columna | Tipo | Restricciones |
|---|---|---|
| id | BIGINT UNSIGNED | PK |
| order_number | VARCHAR(20) | **UNIQUE**, NOT NULL · `PED00000123` |
| customer_id | BIGINT | FK NOT NULL |
| department / province | VARCHAR(100) | NOT NULL (desde V19) — junto con `district`, ubicación completa a nivel de todo el Perú |
| status | VARCHAR(20) | NOT NULL DEFAULT `PENDING_PAYMENT` · PENDING_PAYMENT/CONFIRMED/CANCELLED |
| subtotal | DECIMAL(12,2) | NOT NULL |
| shipping_cost | DECIMAL(12,2) | NOT NULL DEFAULT 0 (desde V19) |
| total | DECIMAL(12,2) | NOT NULL, `CHECK (total >= 0)` · `= subtotal + shipping_cost` |
| payment_method_id | BIGINT | FK NOT NULL |
| payment_reference | VARCHAR(50) | NULL |
| payment_proof_url | VARCHAR(255) | NULL (desde V19) — comprobante subido por el cliente |
| recipient_dni / recipient_first_name / recipient_last_name_paterno / recipient_last_name_materno | VARCHAR | NOT NULL (desde V20) — la courier (Shalom) exige DNI y apellidos separados del destinatario, no un nombre libre |
| phone / address / district | VARCHAR | NOT NULL — datos de entrega |
| notes | VARCHAR(255) | NULL |
| created_at | DATETIME(6) | NOT NULL |
| confirmed_at | DATETIME(6) | NULL |
| confirmed_by | BIGINT | FK → users, NULL |
| cancelled_at | DATETIME(6) | NULL |
| cancellation_reason | VARCHAR(255) | NULL |
| sale_id | BIGINT | FK NULL → `sales` (desde V34) — la venta real generada al confirmar el pago |

**`orders` sigue sin ser `sales`** (son dos tablas con un flujo de estados
distinto — un pedido puede quedar `PENDING_PAYMENT` días antes de que alguien
confirme el pago, una venta no tiene ese estado intermedio), pero desde V34
**confirmar el pago sí genera una `Sale` real** (`PedidoService.confirmarPago`),
para que el pedido aparezca en el historial/reportes de Ventas y pueda
imprimirse el mismo ticket que usa el POS — ver §8 para el detalle de cómo
esa `Sale` se construye sin sesión de caja. `sale_id` queda en `NULL`
mientras el pedido está `PENDING_PAYMENT`; si un pedido ya `CONFIRMED` se
cancela después, la `Sale` enlazada se marca `CANCELLED` directamente (sin
pasar por `VentaService.anular()`, que volvería a reversar stock que el
pedido ya reversó por su cuenta).

**Por qué el stock no se descuenta al crear el pedido:** el pago es manual
(el cliente elige Yape/Plin/transferencia y el staff lo confirma a mano), así
que "pedido creado" no significa "pago recibido". El stock recién se
descuenta cuando el staff confirma el pago (`InventarioService.registrarPorPedido`,
mismo mecanismo que una venta pero con `reference_type = 'ORDER'`), y se
reingresa si un pedido confirmado se cancela después
(`registrarPorCancelacionPedido`). Contrapartida asumida: dos clientes podrían
pedir la última unidad antes de que el staff confirme el primero — aceptable
para un MVP con confirmación manual, ya que el staff revisa cada pedido a
mano de todas formas.

### `order_details`
`id` · `order_id` FK · `variant_id` FK · `quantity` `CHECK (> 0)` ·
`unit_price` · `subtotal` ·
**snapshot:** `product_name`, `variant_sku`, `color_name`, `size_name`.

Mismo patrón de snapshot que `sale_details` (decisión D-05, §8).

`inventory_movements.reference_type` gana el valor `ORDER` (además de
SALE/RETURN/ADJUSTMENT) para que el movimiento de stock al confirmar un
pedido pueda referenciarlo.

### Envío y contraentrega (V19)

`company_settings` gana `shipping_flat_rate` (tarifa única, editable desde
Configuración) — se investigó a Shalom (la courier que usa la empresa) y no
tiene una API pública de tarifas: cobra por peso/tamaño y destino con
cotización manual, así que el monto lo define el staff en vez de calcularse
contra un servicio externo.

Se siembra un método de pago `CONTRAENTREGA` (`type = CASH`) en
`payment_methods`, exclusivo para el distrito de Huacho (sede física de la
empresa): el pedido se paga en efectivo al recibirlo y el envío es gratis
(`shipping_cost = 0`, se asume recojo/entrega local propia del negocio, no un
envío por courier). `PedidoService.crear()` valida el distrito exacto tanto
si el frontend lo permitió como si alguien intenta forzarlo por API
directamente.

Departamento/provincia/distrito se arman en el frontend a partir de un
dataset público de ubigeo (`joseluisq/ubigeos-peru`) embebido como JSON
estático — no se valida contra una tabla en el backend (son strings simples,
igual que `address`), la consistencia la da que el frontend arma el pedido
desde selects en cascada, no texto libre.

### Métodos de pago del checkout online (V34)

`GET /api/store/catalog/payment-methods` (y `PedidoService.crear()` del lado
servidor, como segunda barrera) excluyen cualquier método con
`affects_cash = true` — pagar en efectivo un checkout sin cajero presente no
tiene sentido físico, y además es justo el escenario que dejaría una `Sale`
sin sesión de caja con un pago marcado como "afecta caja" (ver guardias de
§8). `CONTRAENTREGA` sigue disponible porque, pese a su nombre, se sembró
con `affects_cash = false` — el dinero de la contraentrega nunca pasa por
`cash_sessions`.

---

## 13. Redundancias evaluadas y descartadas

| Dato candidato | Decisión | Motivo |
|---|---|---|
| `customers.total_comprado` | **Descartado** | Se calcula desde `sales`; una columna se desincroniza en cuanto haya una anulación |
| `products.stock_total` | **Descartado** | Se suma desde `product_variants` |
| `sales.payment_method` | **Descartado** | Rompe el pago mixto; los métodos están en `payments` |
| `product_variants.stock` | **Conservado** | Redundante frente a los movimientos, pero necesario para la velocidad del POS. Se actualiza en la misma transacción y es verificable |
| Snapshot en `sale_details` | **Conservado** | No es redundancia: es una foto histórica que debe ser inmune a cambios posteriores |

## 14. Orden de creación de las migraciones

```
V1  → esquema de seguridad (users, roles, permissions, tablas puente, refresh_tokens)
V2  → estructura (branches, warehouses, cash_registers, sequences, company_settings)
V3  → catálogo (categories, subcategories, brands, colors, sizes)
V4  → productos y variantes
V5  → inventario (inventory_movements)
V6  → clientes
V7  → caja (cash_sessions, cash_movements)
V8  → ventas (sales, sale_details, payment_methods, payments)
V9  → devoluciones (returns, return_details)
V10 → auditoría (audit_logs)
V11 → datos semilla: permisos, roles, admin inicial, catálogos, métodos de pago
V12 → datos de demostración (perfil dev únicamente)
...
V18 → tienda online: customers.password_hash, customer_refresh_tokens, orders,
      order_details, reference_type ORDER, permisos PEDIDOS_*
V19 → envío: company_settings.shipping_flat_rate, orders.department/province/
      shipping_cost/payment_proof_url, método de pago CONTRAENTREGA
V20 → orders.recipient_dni/recipient_first_name/recipient_last_name_paterno/
      recipient_last_name_materno (reemplaza recipient_name)
V21 → products.material/fit/size_guide_image_url (ficha de tienda online)
V22 → company_settings.plan (plan de suscripción SaaS, §15)
V23 → company_settings.subscription_status/next_payment_due (§16)
V24 → roles.hierarchy_level (techo de asignación, RN-25)
V25 → permiso CONFIGURACION_IDENTIDAD_EDITAR (identidad de empresa separada de lo operativo, RN-26)
V26 → ALMACENERO pierde PRODUCTOS_CREAR/EDITAR/ELIMINAR (no le correspondían)
V27 → SUPERVISOR gana USUARIOS_CONSULTAR/CREAR/EDITAR/BLOQUEAR (seguro gracias al techo de RN-25)
V28 → separaciones: reservations, company_settings.reservation_deposit_amount/
      reservation_expiration_days, reference_type RESERVATION, movement types
      RESERVA/RESERVA_LIBERADA, permisos RESERVAS_* (§17)
V29 → separaciones: reservations.customer_id pasa a NULL + guest_name/guest_phone
      (comprador ocasional sin registrarlo como cliente, §17)
V30 → combos y promociones: combos, combo_items, promotions,
      sale_details.combo_id/promotion_id, permisos COMBOS_*/PROMOCIONES_* (§18)
V31 → combo_items.selector_type: líneas de combo por categoría (+ marca
      opcional), no solo por producto específico (§18)
V32 → promotions.visible_online: promociones que también aplican y se
      muestran en la tienda online (ej. Black Friday), no solo en el POS (§18)
V33 → separaciones pasan de fila plana a cabecera + líneas
      (reservation_details), con combo opcional por línea y combo_group para
      distinguir aplicaciones repetidas del mismo combo (§17)
V34 → sales.cash_session_id pasa a NULL + sales.shipping_amount +
      orders.sale_id: un pedido online confirmado genera una Sale real, sin
      caja, para que aparezca en Ventas y tenga ticket (§8, §12)
```

(Lista ilustrativa de las primeras fases — el detalle exacto de cada migración
vive en `src/main/resources/db/migration/`, que es la fuente de verdad.)

---

## 15. Plan de suscripción (SaaS multi-tenant)

`company_settings.plan` (V22) marca qué nivel de suscripción tiene cada tenant:
`STARTER` < `PROFESIONAL` < `ECOMMERCE` < `IA` (jerarquía por orden ordinal).
Todas las empresas comparten el despliegue, pero cada una tiene su propia fila
de `company_settings`, configuración y datos aislados por `tenant_id`.

**No es editable por el cliente.** `ActualizarCompanySettingsRequest` no
incluye `plan` — a propósito, para que cambiar de plan (y por tanto lo que se
factura) sea siempre una acción del operador de la plataforma, directo en la
base de datos, nunca algo que el propio cliente pueda tocar desde el panel.
`CompanySettingsResponse.plan` sí lo expone (solo lectura) para que el panel
pueda, por ejemplo, ocultar un enlace a una sección que el plan actual no
incluye.

**Aplicación:** `PlanGate` (`@Component("planGate")`) se referencia desde
`@PreAuthorize` exactamente igual que `hasAuthority('PERMISO')`, p. ej.
`@PreAuthorize("hasAuthority('PEDIDOS_CONSULTAR') and @planGate.tienePlan('ECOMMERCE')")`.
Funciona incluso sobre endpoints `permitAll()` (el catálogo público de la
tienda), porque `@PreAuthorize` es una capa de AOP independiente del filtro de
autenticación. Módulos gateados:

| Plan mínimo | Módulos |
|---|---|
| PROFESIONAL | Promotores (`/api/promoters/**`), Auditoría (`/api/audit/**`), Separaciones (`/api/reservations/**`, §17), Combos y promociones (`/api/combos/**`, `/api/promotions/**`, §18) |
| ECOMMERCE | Catálogo público y pedidos de tienda online (`/api/store/**`), pedidos vistos por staff (`/api/orders/**`), notificaciones en tiempo real (`/api/notifications/stream`, `/api/store/notifications/stream`, §22) |

`PlanGate.limiteUsuarios()` además limita `UsuarioService.crear()` a 3
usuarios activos en el plan `STARTER` (los demás planes no tienen límite) —
lanza `OperacionNoPermitidaException` al superarlo. Si no existe fila en
`company_settings` (no debería pasar fuera de tests), `PlanGate` asume
`STARTER` (el plan más restrictivo) como fallback seguro.

---

## 16. Estado de pago de la suscripción

`company_settings` gana (V23) `subscription_status` (`ACTIVA`/`SUSPENDIDA`) y
`next_payment_due` (fecha) — independiente del `plan` (§15): una instalación
puede tener cualquier plan y aun así estar `SUSPENDIDA` por falta de pago.
Tampoco son editables por el cliente vía API, mismo criterio que `plan`.

**Suspensión automática:** `SuscripcionScheduler` corre una vez al día
(`@Scheduled(cron = "0 0 3 * * *")`) y marca `SUSPENDIDA` si hoy pasa
`next_payment_due` + 5 días de margen de gracia. Si `next_payment_due` es
`NULL` (instalación sin ciclo de cobro configurado, ej. este mismo repo en
desarrollo), nunca se suspende sola.

**Aplicación — todo o nada:** a diferencia de `PlanGate` (que gatea módulo
por módulo vía `@PreAuthorize`), `SubscriptionStatusFilter` es un filtro de
Spring Security que corre **antes que cualquier otra cosa** (incluso antes de
parsear el JWT) y bloquea con `402 Payment Required` absolutamente todo —
panel de staff y tienda pública por igual — en cuanto `subscriptionStatus =
SUSPENDIDA`, sin importar plan ni permisos. Rutas exentas (deben seguir
funcionando siempre): `/actuator/health`, `/api/system/info`,
`/api/system/subscription`, `/api/auth/login`, `/api/auth/refresh` — así el
staff puede iniciar sesión y ver *por qué* está bloqueado, el panel de
monitoreo externo puede seguir viendo el estado real de la instalación, y —
crucial — se puede reactivar sin que la propia suspensión bloquee el único
camino para revertirla.

**Reactivación — dos caminos, misma llave que decide quién manda:**
1. **Directo en la base de datos** (igual que el `plan`, §15) — siempre
   disponible, sin dependencias.
2. **`PUT /api/system/subscription`** (ver docs/05-api.md) — autenticado con
   una llave secreta propia de la instalación (`OPS_API_KEY`,
   `OpsApiKeyAuthenticationFilter`), no con login de usuario. Pensado para que
   el panel de monitoreo externo (`panel-monitoreo`, repo aparte) pueda marcar
   pagos/suspensiones con un clic, sin que el operador tenga que entrar a la
   base de datos cada vez. Deliberadamente **no** existe un endpoint
   equivalente para cambiar `plan` — esa decisión es más rara y de mayor
   consecuencia (qué módulos ve el cliente), así que se mantiene solo por
   base de datos.

---

## 17. Separaciones (layaway, plan PROFESIONAL)

**Motivación:** varias tiendas transmiten en vivo por TikTok y cobran una
seña (típicamente S/20 por Yape) para apartar una o varias prendas mostradas
en el live; también hay separaciones en tienda física. Ver
docs/04-reglas-negocio.md para la regla de negocio completa (RN-27).

**Cabecera + líneas (V33):** una separación aparta **varios productos de una
vez** con una sola seña para todo el grupo — antes de V33, una separación
era una fila plana (un producto, una cantidad); ahora `reservations` es la
cabecera (comprador, seña, estado, vencimiento) y `reservation_details`
guarda cada producto apartado, igual patrón que `sales`/`sale_details`.

### `reservations` (cabecera)
| Columna | Tipo | Restricciones |
|---|---|---|
| id | BIGINT UNSIGNED | PK |
| reservation_number | VARCHAR(20) | **UNIQUE**, NOT NULL · `RES-00000001` (`SequenceService`) |
| customer_id | BIGINT | FK NULL (desde V29) — nulo si es un comprador ocasional, ver `guest_name` |
| guest_name | VARCHAR(150) | NULL (desde V29) — nombre del comprador ocasional cuando `customer_id` es nulo |
| guest_phone | VARCHAR(20) | NULL (desde V29) — opcional, para ubicar su comprobante en WhatsApp |
| deposit_amount | DECIMAL(12,2) | NOT NULL — seña efectivamente cobrada, **una sola para todo el grupo de líneas** |
| deposit_payment_method_id | BIGINT | FK NOT NULL → `payment_methods`, debe tener `affects_cash = false` |
| deposit_reference | VARCHAR(50) | NULL |
| promoter_id | BIGINT | FK NULL → `promoters` — quién generó la venta en el live, solo para comisión |
| status | VARCHAR(20) | NOT NULL DEFAULT `RESERVADO` · RESERVADO/COMPLETADO/CANCELADO/VENCIDO |
| expires_at | DATETIME(6) | NOT NULL · `created_at + company_settings.reservation_expiration_days` |
| notes | VARCHAR(255) | NULL |
| created_by | BIGINT | FK NOT NULL → `users` |
| created_at | DATETIME(6) | NOT NULL |
| sale_id | BIGINT | FK NULL → `sales` — se completa al pagar el saldo pendiente |
| completed_at / completed_by | DATETIME(6) / BIGINT | NULL |
| cancelled_at / cancelled_by | DATETIME(6) / BIGINT | NULL |
| cancellation_reason | VARCHAR(255) | NULL |

### `reservation_details` (líneas, V33)
| Columna | Tipo | Restricciones |
|---|---|---|
| id | BIGINT UNSIGNED | PK |
| reservation_id | BIGINT UNSIGNED | FK NOT NULL → `reservations` |
| variant_id | BIGINT UNSIGNED | FK NOT NULL → `product_variants` |
| quantity | INT | NOT NULL, `CHECK (quantity > 0)` |
| unit_price | DECIMAL(12,2) | NOT NULL — precio (o promo) vigente al crear la separación |
| discount_amount | DECIMAL(12,2) | NOT NULL DEFAULT 0 — descuento de esta línea si viene de un combo |
| subtotal | DECIMAL(12,2) | NOT NULL — `unit_price × quantity − discount_amount` |
| combo_id | BIGINT UNSIGNED | FK NULL → `combos` — no nulo cuando la línea viene de aplicar un combo |
| combo_group | INT | NULL — distingue **aplicaciones repetidas del mismo combo** (ver abajo) |

**Combo elegido explícitamente al crear (botón "+ Agregar combo"):** al
armar el carrito de la nueva separación, el cajero puede agregar productos
sueltos (buscador + cantidad) o, con el botón "+ Agregar combo", elegir un
combo activo y llenar cada "slot" con una variante concreta — mismo patrón
ya usado en el POS (`abrirSelectorCombo` → `abrirFormularioComboItems`,
docs/03 §18). `ReservaService.crear()` reutiliza el motor de
`ComboService.consumirCandidatos` (mismo algoritmo greedy PRODUCT-antes-que-
CATEGORY, mismo chequeo estricto de consumo exacto que `VentaService`) para
calcular el descuento de cada línea del combo y repartirlo
proporcionalmente — igual técnica que ya usaba `completarVarias` (última
línea absorbe el redondeo). El combo queda **fijado desde la creación**: no
se vuelve a recalcular al completar el pago.

**`combo_group` — múltiplos del mismo combo:** el caso "8 polos = 2 combos
de 4×100 = S/200" requiere aplicar el **mismo** combo dos veces dentro de
una sola separación. El frontend le asigna a cada aplicación un contador
incremental (0, 1, 2…) que viaja en el request (`ReservaItemRequest.comboGroup`)
junto al `comboId`; el backend agrupa las líneas por el par
`(comboId, comboGroup)` — no solo por `comboId` — para no mezclar dos
aplicaciones en un solo grupo que ya no calzaría con la definición del
combo (que solo pide 4, no 8). Verificado en vivo: dos aplicaciones de un
combo "4 polos x 100" en una misma separación dan `total = 200.00` exacto.

**Comprador ocasional (V29):** los compradores de un live no siempre están
registrados como `Customer` — solo lo están los que ya compran por la tienda
online. Forzar un registro de cliente completo (nombre, DNI, teléfono) por
cada separación de un live habría sido fricción pura para el vendedor, así
que `customer_id` es nullable y `CHECK (customer_id IS NOT NULL OR guest_name
IS NOT NULL)` exige exactamente uno de los dos caminos. El formulario del
panel sigue permitiendo buscar y vincular un cliente ya registrado si el
vendedor lo prefiere — ambos caminos conviven.

**El stock se retira de inmediato al crear la separación** — a diferencia de
un pedido online (`orders`, §12), donde el stock recién se descuenta al
confirmar el pago. Aquí la prenda queda físicamente apartada desde el
momento de la seña, así que el stock debe reflejar eso de inmediato para que
el resto del sistema (POS, tienda online, otro pedido) no la vuelva a
ofrecer. Se implementa reutilizando el mecanismo de Kardex existente
(`InventarioService.registrarPorReserva` / `registrarPorLiberacionReserva`)
en vez de una columna paralela `reserved_stock`: como `product_variants.stock`
ya es la única fuente de verdad que todo el sistema consulta, cada
verificación de disponibilidad existente respeta las separaciones activas
sin ningún cambio adicional en otro módulo.

`inventory_movements` gana los tipos `RESERVA` (retira stock, `-quantity`,
al crear) y `RESERVA_LIBERADA` (devuelve stock, `+quantity`, al cancelar o
vencer), y `reference_type` gana `RESERVATION`.

**Completar reutiliza el modelo de `Sale`/`SaleDetail`/`Payment`**, no una
estructura de reportes paralela: al pagar el saldo pendiente,
`ReservaService.completar()` crea una `Sale` normal (con `promoter_id`
heredado de la separación) y su `SaleDetail`/`Payment` correspondientes —
así la venta completada entra automáticamente en los mismos reportes de
comisión por promotor que ya existían, sin construir un segundo camino de
reportes. El `Payment` de la seña se registra con
`created_at = reservations.created_at` (la fecha real en que se cobró) y
**nunca pasa por caja** (`CajaService.registrarPorVenta` no se llama para
ese pago); solo el/los pago(s) del saldo final pasan por caja, y solo si su
método de pago tiene `affects_cash = true`. El detalle de la venta **no**
vuelve a descontar stock — ya salió al crear la separación.

**La seña nunca puede ser en efectivo** — `ReservaService.crear()` rechaza
cualquier `deposit_payment_method_id` con `affects_cash = true`
(`ReglaDeNegocioException`), porque una seña de un live remoto por
definición no puede cobrarse en efectivo de caja; forzaría un ingreso de
caja sin una sesión de caja real detrás.

**Vencimiento:** `ReservaScheduler` corre cada hora (`@Scheduled(cron = "0 0
* * * *")`, más seguido que `SuscripcionScheduler` porque una separación
vencida bloquea stock real, no solo información) y llama a
`ReservaService.vencerSeparacionesPendientes()`, que libera el stock de toda
separación `RESERVADO` con `expires_at` pasado y la marca `VENCIDO`. **La
seña ya pagada se pierde — no hay reversión ni reembolso automático**
(decisión de negocio explícita, RN-27). El movimiento de liberación de stock
y el registro de auditoría quedan a nombre de quien creó la separación (no
existe un concepto de "usuario sistema" en este modelo).

`company_settings` gana (V28) `reservation_deposit_amount` (seña por
defecto, editable en Configuración, el cajero puede ajustarla por
separación) y `reservation_expiration_days` (plazo de vencimiento,
igualmente configurable — deliberadamente no hardcodeado).

**Cobro conjunto con detección automática de combo (entre separaciones
distintas):** además del combo elegido explícitamente al crear, sigue
existiendo un segundo camino, pensado para cuando el cajero *no* usó el
botón "+ Agregar combo" al momento de apartar (ej. varias separaciones
sueltas hechas en distintos momentos del mismo live). `ReservaService.completarVarias()`
permite seleccionar varias separaciones `RESERVADO` del mismo comprador
(mismo `customer_id`, o mismo `guest_name` sin distinguir mayúsculas) y
cobrarlas de una sola vez. Al hacerlo, junta **todas las líneas** de las
separaciones seleccionadas y las separa en dos grupos:
- **Líneas ya con combo fijado** (`combo_id` no nulo, puestas ahí desde la
  creación) — se cobran tal cual quedaron, **no se vuelven a evaluar**.
- **Líneas sueltas** (`combo_id` nulo) — se arma un candidato por línea
  (variante, producto, categoría, marca, cantidad) y se reutiliza el mismo
  motor de emparejamiento de combos que la venta normal (§18): si el
  conjunto completo de líneas sueltas calza exacto con un combo activo, se
  aplica automáticamente sobre esas líneas; si no, cada una se cobra a su
  precio normal.

Esto permite que una separación con líneas mixtas (algunas ya en combo,
otras sueltas) participe correctamente: lo ya fijado no se toca, lo suelto
sigue siendo candidato a un combo detectado entre varias separaciones.
Genera **una sola `Sale`** para todas las separaciones incluidas, con el
descuento total repartido proporcionalmente entre las líneas que corresponda.
`ReservaService.previsualizarCompletarVarias()` expone el mismo cálculo en
modo solo-lectura para que el cajero vea el monto correcto antes de
confirmar el cobro.

**Búsqueda por comprador:** el listado de separaciones acepta un filtro
`buyerName` que busca por coincidencia parcial tanto en el cliente
registrado (`customer.full_name`) como en el comprador ocasional
(`guest_name`) — pensado para que, en un recojo presencial, el cajero ubique
rápido todas las separaciones pendientes de esa persona sin importar si
está registrada como cliente o no.

---

## 18. Combos y promociones (plan PROFESIONAL)

**Motivación:** además de las separaciones de un live, la tienda maneja
combos ("casaca + pantalón a un precio fijo") y promociones (% o monto fijo,
por tiempo limitado) tanto en tienda física como en vivo. Ver
docs/04-reglas-negocio.md RN-28.

### `combos` · `combo_items`
| Columna | Tipo | Restricciones |
|---|---|---|
| `combos.code` | VARCHAR(30) | **UNIQUE**, NOT NULL |
| `combos.name` | VARCHAR(150) | NOT NULL |
| `combos.price` | DECIMAL(12,2) | NOT NULL, `CHECK (price > 0)` — precio total fijo del combo |
| `combos.status` | VARCHAR(20) | NOT NULL DEFAULT `ACTIVE` |
| `combo_items.combo_id` | BIGINT | FK NOT NULL |
| `combo_items.selector_type` | VARCHAR(20) | NOT NULL DEFAULT `PRODUCT` · `PRODUCT` / `CATEGORY` (desde V31) |
| `combo_items.product_id` | BIGINT | FK NULL — lleno cuando `selector_type = PRODUCT` |
| `combo_items.category_id` | BIGINT | FK NULL — lleno cuando `selector_type = CATEGORY` |
| `combo_items.brand_id` | BIGINT | FK NULL — opcional, acota una línea `CATEGORY` a una marca |
| `combo_items.quantity` | INT | NOT NULL, `CHECK (quantity > 0)` |

`CHECK` de negocio: exactamente uno de `product_id`/`category_id` según
`selector_type`. Una línea de combo puede ser (V31):
- **`PRODUCT`** — un producto específico ("esta casaca exacta").
- **`CATEGORY`** — cualquier producto de una categoría, opcionalmente acotado
  a una marca ("4 polos de esta marca", "una prenda de esta categoría").

Un combo puede mezclar líneas de ambos tipos (ej. "esta casaca específica +
cualquier accesorio de esta categoría"). El cajero elige color/talla — y,
en una línea `CATEGORY`, también el producto concreto — recién al vender el
combo en el POS.

**Precio del combo vs. suma de productos:** `ComboService` valida al
crear/editar que `price` sea menor a la suma de los precios vigentes de sus
productos, pero **solo cuando todas las líneas son `PRODUCT`** — con una
línea `CATEGORY` no hay forma de saber de antemano qué productos concretos
se van a elegir, así que ese chequeo se aplaza al momento de la venta
(`VentaService`, con los productos ya elegidos) y `ComboResponse.normalTotal`/`savings`
quedan en `null` para esos combos.

### `promotions`
| Columna | Tipo | Restricciones |
|---|---|---|
| code / name | VARCHAR | **UNIQUE** / NOT NULL |
| discount_type | VARCHAR(20) | NOT NULL · `PERCENTAGE` / `FIXED_AMOUNT`, `CHECK` |
| discount_value | DECIMAL(12,2) | NOT NULL, `CHECK (> 0)`, y `CHECK (discount_type <> 'PERCENTAGE' OR discount_value <= 100)` |
| scope_type | VARCHAR(20) | NOT NULL DEFAULT `ALL` · `ALL` / `CATEGORY` / `PRODUCT`, `CHECK` |
| scope_category_id / scope_product_id | BIGINT | FK NULL — solo uno aplica, según `scope_type` |
| starts_at / ends_at | DATETIME(6) | NULL — nulo de un lado = sin límite por ese lado |
| status | VARCHAR(20) | NOT NULL DEFAULT `ACTIVE` |
| visible_online | TINYINT(1) | NOT NULL DEFAULT `0` (desde V32) — también aplica en la tienda online |

**Promociones visibles en la tienda online (V32):** por defecto una
promoción solo la aplica el cajero manualmente en el POS. Marcarla
`visible_online = true` la hace también aplicar en `/api/store/catalog/**`
(pensado para ofertas de todo el catálogo tipo Black Friday, sin tocar cada
producto uno por uno): `TiendaCatalogoService` calcula el mejor precio
vigente entre las promociones `visible_online` aplicables y lo devuelve en
el campo público ya existente `promoPrice` (reutilizado — cero campos
nuevos en las respuestas del catálogo público, el frontend de la tienda ya
sabía mostrar un `promoPrice != null`). `PedidoService.crear()` recalcula
ese mismo precio **server-side** al confirmar el pedido, nunca confía en el
precio mostrado por el frontend.

### Venta con combo o promoción — reutiliza `Sale`/`SaleDetail`, no una estructura paralela

`sale_details` gana (V30) `combo_id` y `promotion_id`, ambos FK NULL — solo
trazabilidad para reportes ("combos vendidos", "ventas por promoción"), el
mismo criterio D-05 ya usado para separaciones y pedidos.

- **Combo:** `VentaService` agrupa las líneas de la venta que comparten un
  mismo `comboId` y las reparte entre las líneas del combo con un algoritmo
  greedy: primero satisface las líneas `PRODUCT` (más restrictivas, para que
  un producto específico no se lo "robe" una línea `CATEGORY` que también lo
  incluiría), y con lo que sobra intenta cubrir cada línea `CATEGORY` por
  categoría (y marca, si la línea la exige). Si algo del combo queda sin
  cubrir, o sobran productos que no encajan en ninguna línea, la venta se
  rechaza. Ya con las líneas resueltas, **calcula el descuento de cada línea
  en el backend** (nunca confía en un `discountAmount` del cliente para esas
  líneas): el descuento total (`Σ precio normal − precio fijo del combo`) se
  reparte proporcionalmente al precio normal de cada línea, con la última
  línea absorbiendo el redondeo para que la suma cuadre exacto con el precio
  fijo.
- **Promoción:** el cajero la elige por línea de venta (`ItemVentaRequest.promotionId`)
  entre las promociones vigentes que apliquen a esa variante
  (`GET /api/promotions/applicable?variantId=`). El backend siempre
  revalida vigencia y alcance server-side antes de aplicar el descuento —
  nunca confía en que el frontend ya filtró bien.
- Un `ItemVentaRequest` solo puede traer **uno** de `discountAmount` (manual),
  `comboId` o `promotionId` — nunca dos a la vez.
