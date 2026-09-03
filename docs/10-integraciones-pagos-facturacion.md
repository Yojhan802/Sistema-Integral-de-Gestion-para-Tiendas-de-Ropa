# Integraciones de pagos y facturación

Este documento describe la implementación SaaS por empresa de pagos online con
Niubiz, Culqi e Izipay y de facturación electrónica con Verifac y NubeFact.

## Configuración por empresa

La empresa controla dos interruptores independientes en configuración:

- `onlinePaymentsEnabled`: habilita o deshabilita el catálogo de pasarelas para
  la tienda.
- `electronicInvoicingEnabled`: habilita o deshabilita la emisión electrónica.

Además, cada pasarela tiene un registro propio en
`payment_provider_configurations` y el proveedor seleccionado tiene un registro
propio en `billing_configurations`. Las credenciales privadas se cifran antes de
guardarse. Las respuestas públicas solo contienen datos necesarios para montar
el SDK, nunca llaves privadas.

Una empresa que no utilice una pasarela o facturación puede mantener la
integración desactivada y sin credenciales. El resto de empresas no se ve
afectado.

## Alta de una empresa

El operador interno usa el módulo **Empresas** para crear un tenant. El alta
provisiona el administrador inicial, roles del sistema, métodos de pago,
sucursal, almacén, caja y correlativos independientes. La contraseña temporal
se muestra únicamente al terminar el alta y debe entregarse por un canal
seguro.

El operador puede seleccionar el rubro `CLOTHING` (ropa) o `GENERAL` (otro tipo
de negocio). Esto configura el contexto inicial del asistente, pero la empresa
puede completar el resto de su identidad desde su propia configuración.

El nuevo negocio comienza con pagos online y facturación electrónica
desactivados. Cada empresa puede activar sus propios interruptores y registrar
sus credenciales más adelante; no se usan variables globales para las
credenciales de los tenants.

## Pagos online

El flujo común es:

1. La tienda crea un `payment_transaction` idempotente usando
   `Idempotency-Key`.
2. El backend valida empresa, pedido, monto, moneda, cliente y pasarela.
3. El backend inicializa el checkout desacoplado.
4. El navegador muestra el formulario hospedado por el proveedor.
5. El backend procesa el resultado o espera el webhook/IPN del proveedor.
6. Solo un resultado aprobado confirma el pedido y materializa la venta.

Los estados internos son `CREATED`, `PENDING`, `PROCESSING`, `APPROVED`,
`DECLINED`, `FAILED`, `CANCELLED` y `REFUNDED`. El frontend nunca decide que
un pago está aprobado.

### Niubiz

Se utiliza Pago Web desacoplado: el backend obtiene el token de seguridad y la
sesión; el navegador carga el `checkout.js` de Niubiz y recibe únicamente el
token efímero de transacción. La autorización final se realiza en el backend.

Credenciales privadas esperadas: `username` y `password`. Se aceptan URLs
específicas opcionales (`securityUrl`, `sessionUrl`, `authorizationUrl`) para
cuentas o ambientes con rutas diferentes.

### Culqi

El navegador carga el Checkout JS oficial y obtiene un token efímero. El
backend crea el cargo con la llave privada. Las notificaciones se verifican
consultando el evento autenticado en Culqi antes de aprobar la transacción.

Credenciales: la llave pública se guarda en `publicKey`; la privada puede
guardarse como `secretKey`, `privateKey` o `apiKey`.

### Izipay

Se utiliza Punto Web desacoplado. El backend genera el token de sesión y crea
un `transactionId` externo de entre 5 y 40 caracteres (`FP` más el ID interno
de la transacción). El navegador carga el SDK oficial con `authorization` y
`keyRSA`. El callback del navegador solo sirve para cerrar el formulario: la
confirmación confiable es el IPN firmado.

Credenciales y valores esperados:

- `merchantCode`: código de comercio.
- `publicKey`: clave pública RSA.
- `newPaymentButtonApiKey`: clave de API del nuevo botón de pagos.
- `hashKey`: clave hash para verificar el IPN.
- `sessionTokenUrl`: URL completa opcional del endpoint Generate Token. Si no
  se indica, se usa `/security/v1/Token/Generate` sobre el ambiente configurado.
- `ipnUrl`: URL pública del webhook, opcional; puede contener
  `{transactionId}`.
- `apiKeyPrefix` y `apiKeyHeader`: opcionales para cuentas cuyo esquema de
  autenticación difiera del valor predeterminado.

El endpoint público de IPN es
`POST /api/webhooks/izipay/{transactionId}` y exige que el identificador de la
ruta, el header y el cuerpo coincidan. La firma se valida con HMAC-SHA256 y la
clave hash configurada.

## Facturación Verifac y NubeFact

Una venta completada puede generar un borrador idempotente de boleta o factura.
La emisión es una decisión posterior a la venta: el operador elige un único tipo
para esa venta y luego confirma el envío al proveedor. La factura exige cliente
con RUC válido de 11 dígitos y dígito verificador correcto. La boleta puede ser
para consumidor final sin documento; si el cliente lo solicita o el total supera
S/ 700, el sistema exige identificarlo con DNI, RUC o CE válido. El correlativo
no se inventa localmente: lo asigna Verifac al procesar la serie configurada.

El adaptador de Verifac usa la API con `X-API-Key` y soporta:

- emisión de boletas y facturas;
- emisión de notas de crédito y débito con referencia a un comprobante aceptado;
- consulta de estado;
- reenvío de documentos con error o rechazo;
- descarga de PDF, XML y CDR.

La empresa puede seleccionar Verifac o NubeFact desde la configuración de su
tenant. NubeFact usa una ruta completa por cuenta y el header
`Authorization: Token token=...`; todas sus operaciones se envían mediante
`POST` con el campo `operacion`. Su API exige que Qynex genere el correlativo,
por lo que el borrador NubeFact reserva el siguiente número local por serie y
tipo. La API devuelve PDF, XML y CDR como ZIP en base64; el adaptador los
descarga y descomprime al solicitar el recurso.

NubeFact soporta emisión y consulta de facturas, boletas, notas de crédito y
notas de débito. Para notas utiliza la serie del comprobante de origen. La
ruta, el token y el ambiente deben pertenecer a la misma cuenta/RUC del tenant.

Los estados son `DRAFT`, `PENDING`, `SENT`, `ACCEPTED`, `REJECTED`, `ERROR` y
`CANCELLED`. El envío al proveedor puede responder de inmediato o dejar el
documento en cola; mientras esté `PENDING`/`SENT`, la interfaz consulta su estado
sin crear otro documento. Solo `ACCEPTED` habilita PDF/XML/CDR y sirve como
origen de una nota fiscal. Un documento aceptado por Verifac o NubeFact no se
confunde con un pago aprobado: son procesos independientes.

Si la empresa no tiene activada la facturación electrónica, no se muestran las
acciones de comprobante ni se llama a ningún proveedor: la venta conserva su
ticket interno imprimible. Activar la opción exige además proveedor, credenciales
y series configuradas.

Las notas se crean desde una venta que ya tiene una boleta o factura aceptada.
El backend exige `sourceDocumentId`, `reasonCode` y `reasonDescription`, valida
los códigos SUNAT de los catálogos 09 y 10, y envía a Verifac
`tipoComprobanteReferencia`, `serieNumeroReferencia`, `codigoMotivo` y
`descripcionMotivo`. Una venta puede tener varias notas; la clave de
idempotencia evita repetir la misma operación.

## Idempotencia, errores y seguridad

- Las claves de idempotencia impiden pagos y comprobantes duplicados.
- Los montos se recalculan desde el pedido o venta del servidor.
- Los tokens de sesión y de tarjeta no se persisten.
- Los callbacks se validan contra la empresa, transacción, orden, monto y
  moneda.
- Las credenciales se guardan cifradas y no se incluyen en respuestas de
  configuración.
- Cada transición importante queda auditada.

Al anular una venta desde el POS, si existe una boleta o factura aceptada, el
backend genera una nota de crédito con motivo `01` (anulación de la operación)
y espera su aceptación antes de marcar la venta como `CANCELLED`, revertir
inventario y revertir caja. Si el proveedor la deja pendiente o la rechaza, la
transacción local se revierte y la venta queda disponible para reintentar.

## Pendientes operativos

- Configurar las URLs públicas de IPN/webhooks en cada cuenta productiva y
  verificar que el despliegue tenga HTTPS y sea accesible desde Internet.
- Ejecutar pruebas reales de sandbox con credenciales de cada proveedor antes
  de activar una pasarela en producción.
- Confirmar con NubeFact si la cuenta contratada usará su API PSE o la ruta
  SEE-OSE; ambas modalidades tienen operación y contrato comercial distintos.
- Las notas de crédito por devolución de ítems (motivo `07`) permiten seleccionar
  productos y cantidades. El backend recalcula el importe proporcional desde
  los detalles originales de la venta.

## Referencias oficiales

- [Verifac: Swagger/OpenAPI](https://api.verifac.pe/swagger-ui/index.html)
- [Verifac: desarrolladores](https://verifac.pe/desarrolladores/)
- [NubeFact: integración API](https://www.nubefact.com/integracion)
- [SUNAT: Sistema de Emisión del Contribuyente](https://cpe.sunat.gob.pe/sistema_emision/see_contribuyente)
- [SUNAT: Boleta de Venta Electrónica](https://cpe.sunat.gob.pe/tipos_de_comprobantes/boleta)
- [NubeFact: descargas y manuales](https://ayuda.nubefact.com/descargas)
- [Izipay: inicio rápido web](https://developers.izipay.pe/web-core/quickstart/)
- [Izipay: credenciales](https://developers.izipay.pe/credentials/)
- [Izipay: notificaciones](https://developers.izipay.pe/web-core/notifications/)
- [Culqi: llaves](https://docs.culqi.com/es/documentacion/pagos-online/llaves)
- [Culqi: webhooks](https://docs.culqi.com/es/documentacion/pagos-online/webhooks/)
- [Verifac](https://verifac.pe/)
