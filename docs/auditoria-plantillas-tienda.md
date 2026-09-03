# Auditoría de plantillas del storefront

Este documento es el criterio permanente para revisar `front/tienda/templates`
cada vez que se agregue o modifique una plantilla.

## Contrato común

- Las diez claves permitidas son `CLASSIC`, `MINIMAL`, `FASHION`, `SPORT`,
  `LUXURY`, `BOUTIQUE`, `CATALOG`, `MARKET`, `EDITORIAL` y `URBAN`.
- Todas usan el HTML, APIs, carrito, checkout, autenticación y pedidos
  compartidos de `front/tienda`.
- Una plantilla solo puede cambiar presentación. No puede introducir lógica de
  negocio, credenciales, HTML del cliente, CSS arbitrario ni un segundo carrito.
- Todo selector específico debe estar bajo `body[data-store-template='KEY']`.
- Si una clave es inválida o la hoja falla, el fallback obligatorio es
  `CLASSIC` y la tienda debe seguir siendo usable.

## Auditoría visual obligatoria

Revisar cada plantilla en 320 px, 768 px y 1440 px, en estas páginas:

1. Inicio/catálogo.
2. Detalle de producto con galería, variantes y sin stock.
3. Carrito vacío y con productos.
4. Checkout con errores y métodos de pago.
5. Login, registro y pedidos.
6. Tienda suspendida/no disponible.

Comprobar especialmente:

- `Categorías` y `Catálogo` hacen desplazamiento suave a sus anclas; nunca
  deben saltar bruscamente.
- No existe scroll horizontal accidental ni contenido cortado en el hero.
- Logo, nombre, precios, descuentos, imágenes y banners se adaptan sin
  overflow.
- Hover, focus, disabled, carga, vacío, error y sin stock son legibles.
- Contraste WCAG AA, foco visible, teclado completo y controles táctiles de
  mínimo 44 px.
- `prefers-reduced-motion` desactiva transiciones y desplazamientos animados.
- La hoja de la plantilla no deja un destello de CLASSIC durante la carga.
- Cambiar de tenant o de plantilla no reutiliza datos visuales del tenant
  anterior.

## Auditoría funcional de regresión

- El catálogo real se carga desde `/api/store/catalog/**`.
- Las imágenes usan `images[]` y `imageUrl` como compatibilidad.
- El carrito conserva productos entre navegación y refresh.
- El checkout mantiene validación server-side y no expone secretos.
- Los enlaces de banners solo aceptan rutas internas o `http/https`.
- `node --check` debe pasar para los módulos modificados y las pruebas Maven
  deben pasar antes de publicar.

## Resultado de esta revisión

Las diez carpetas oficiales existen y tienen su `template.css`. La lógica de
comportamiento sigue centralizada en `store-shell.js`, `home.js`, `producto.js`
y las capas comunes. Se corrigió el desplazamiento suave de anclas, el recorte
horizontal del hero y la aplicación de `prefers-reduced-motion`; estas reglas
aplican a todas las plantillas futuras.

## Auditoria ejecutada

En la revision visual local se detecto y corrigio un defecto comun: solo
`CLASSIC` posicionaba la imagen del hero, mientras las otras plantillas la
dejaban en el flujo normal y ocultaban su contenido. La capa base ahora
posiciona la imagen, conserva el texto y adapta el hero en movil.

La plantilla `FASHION` incorpora una capa editorial propia: cabecera de dos
niveles con estado compacto, hero de imagen completa, composicion asimetrica
de categorias, reticula de productos con jerarquia variable, detalle con
informacion sticky y galeria priorizada, ademas de estilos coherentes para
carrito, checkout, cuenta, pedidos y tienda no disponible. Su comportamiento
visual vive en `templates/FASHION/template.js`; no duplica reglas de carrito,
variantes, stock, checkout ni autenticacion.

Tambien se incorporaron el menu movil compartido, el fallback de la inicial
cuando falla el logo subido y un placeholder para imagenes de producto/banner
que no esten disponibles. En las capturas locales se verificaron las diez
variantes en escritorio y muestras representativas en movil; el siguiente
control pendiente es repetir la matriz completa en el navegador interactivo
cuando su runtime de Node este reiniciado.
