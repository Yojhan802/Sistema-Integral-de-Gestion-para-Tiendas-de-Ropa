# Plantillas del storefront React

Estas hojas son la copia de trabajo React de las diez identidades visuales de la tienda.
Cada plantilla se compone de `template.css` y `surfaces.css`: el primero define su
composiciÃ³n general y el segundo sus superficies dinÃ¡micas (carrito, checkout, pagos,
pedidos y cuenta). Se activa con `body[data-store-template="..."]` y comparte el contrato
de datos y componentes del storefront. No contiene llamadas a API, credenciales ni reglas
de negocio.

Las animaciones de navegación, entrada de páginas y microinteracciones se implementan en
React con Motion; las transiciones puramente CSS quedan reservadas para estados que no
necesitan montar componentes.

Las secciones dinÃ¡micas que tengan animaciÃ³n de entrada deben usar el hook compartido
`components/RevealSection` (`useRevealSections`). Este hook agrega `is-visible` al entrar
en pantalla y muestra el contenido como fallback cuando no existe `IntersectionObserver`
o el usuario prefiere menos movimiento. Las plantillas no deben dejar un `.store-section`
con `opacity: 0` sin ese contrato, porque el catÃ¡logo quedarÃ­a montado pero invisible.
