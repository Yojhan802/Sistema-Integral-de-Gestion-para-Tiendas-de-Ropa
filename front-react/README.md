# Qynex frontend React

Frontend paralelo para migrar Qynex desde `front/` sin interrumpir la versión
legada. El backend, los tenants, las sesiones, el carrito, los pedidos, los
pagos y las plantillas siguen siendo los contratos existentes.

## Desarrollo local

```text
npm ci
npm run dev
```

La aplicación queda en `http://localhost:8093`. Durante el desarrollo, Vite
envía `/api` y `/uploads` al proxy legado de `http://localhost:8092`, que es el
único punto publicado por Docker en el entorno actual. Así se conserva el mismo
CORS, resolución de tenant y almacenamiento de la aplicación existente.

## Build

```text
npm run check
npm run build
```

El build reutiliza los tokens, CSS base y CSS de las plantillas desde `front/`.
No se copian productos, credenciales ni datos de ejemplo al bundle.

## Rutas React disponibles

- Tienda: `/`, `/producto?id=...`, `/carrito`, `/checkout`.
- Cuenta de cliente: `/cuenta/login`, `/cuenta/registro`, `/cuenta/pedidos`.
- Panel: `/admin/login`, `/admin/dashboard`.

Los demás módulos del panel tienen una frontera de compatibilidad temporal hacia
`VITE_LEGACY_FRONT_URL` (por defecto `http://localhost:8092`). Esto permite
migrarlos uno por uno manteniendo el sistema operable y sin duplicar lógica de
negocio. Antes de reemplazar el servicio de producción, todas esas rutas deben
tener su página React propia y pasar la matriz de regresión.

## Decisiones de seguridad y UX

- Se mantienen separadas las sesiones `fsp.session` y `fsp.customer.session`.
- El precio real y el stock siguen siendo responsabilidad del backend.
- El carrito usa la misma clave `fsp.customer.cart`.
- El checkout mantiene métodos manuales y las tres pasarelas existentes.
- La plantilla se resuelve por configuración del tenant, con fallback seguro a
  `CLASSIC` y sin flash de contenido antes de marcarla como lista.
- Todos los controles nuevos respetan teclado, foco visible, 44 px mínimos,
  `prefers-reduced-motion` y recomposición responsive.

## Docker aislado

El `Dockerfile` está preparado para construirse desde la raíz del repositorio:

```text
docker build -f front-react/Dockerfile -t qynex-front-react .
```

Este es el frontend en uso. Arranca con un `docker compose up` normal:

```text
docker compose up -d --build frontend-react
```

Queda en `http://localhost:8093` (`REACT_HTTP_PORT` permite cambiar el puerto).

El frontend legado (`front/`) está abandonado y ya no arranca solo: quedó tras el
perfil `legacy` para que levantarlo sin querer no dé la impresión de que sigue en
uso. Si algún módulo administrativo todavía enlaza a una pantalla legada, esa
pantalla es lo que falta por migrar, no un servicio que haya que mantener vivo.
