# Pruebas de carga (Fase 5 del plan de multi-tenant)

Scripts de [k6](https://k6.io) para probar capacidad contra un backend real —
nunca contra la base de datos real del negocio. Corren contra el stack
Docker aislado (MySQL propio, credenciales propias, ver `.env`), con el
override `docker-compose.loadtest.yml` que expone el backend directo en el
host (bypass de nginx) para que k6 le pegue sin pasar por el frontend.

## Cómo correrlas

```bash
# 1. Desde la raíz del repo, con un .env real (copiar de .env.example) o uno
#    descartable con credenciales generadas — nunca apuntar esto a producción.
docker compose build backend
docker compose -f docker-compose.yml -f docker-compose.loadtest.yml up -d mysql backend

# 2. Esperar a que /actuator/health responda UP en localhost:8090, luego:
BASE_URL=http://localhost:8090 k6 run aplicacion/loadtest/browse-only.js
BASE_URL=http://localhost:8090 k6 run aplicacion/loadtest/login-and-browsing.js

# 3. Para probar con otro tamaño de pool/hilos (ver perfil prod en application.yml):
DB_POOL_MAX=20 DB_POOL_MIN=5 TOMCAT_MAX_THREADS=100 \
  docker compose -f docker-compose.yml -f docker-compose.loadtest.yml up -d backend

# 4. Terminar y limpiar:
docker compose down
```

El perfil "prod" resuelve el tenant por subdominio real (`TenantResolutionFilter`,
3+ etiquetas) — sin DNS real acá, los scripts simulan esto con el header
`Host: default.qynex.pe` (`default` es el slug que la migración `V38` le
asigna al tenant sembrado, id=1).

## Scripts

- **`browse-only.js`** — navegación sostenida (catálogo, detalle de
  producto, dashboard, clientes) con un token ya obtenido. Es el tráfico que
  domina el uso real: staff/clientes ya autenticados navegando. Rampa hasta
  150 VUs.
- **`login-and-browsing.js`** — compara una ráfaga de 50 logins simultáneos
  contra EL MISMO usuario vs. 50 logins simultáneos contra 50 usuarios
  DISTINTOS (crea el staff necesario en `setup()`), y después corre
  navegación sostenida.

## Hallazgo real (corrido 2026-08-29, laptop de desarrollo — ver limitación abajo)

- **Navegación sostenida**: 150 VUs, 0% errores, `p95=35ms`, ~365 req/s
  sostenidos, sin señales de saturación con `DB_POOL_MAX=20`. Este es el
  patrón de tráfico dominante en uso real y escala bien.
- **Ráfaga de 50 logins simultáneos** (sea contra el mismo usuario o contra
  50 usuarios distintos — **ambos casos fallan igual**, ~60-90% de errores):
  el pool de Hikari (`total=20, active=20, idle=0, waiting=~20`) se agota
  por completo durante ~10s. Los logs del backend muestran la causa real:
  **deadlocks genuinos de InnoDB** (`Deadlock found when trying to get
  lock; try restarting transaction`, MySQL 1213/40001) — no contención de
  fila entre logins de un mismo usuario (se descartó comparando ambos
  escenarios: fallan igual). El `@Retryable` de `AuthService.login()` (ver
  su Javadoc, ALTA PERF-01) SÍ atrapa esto y reintenta, pero cada reintento
  vuelve a pedir una conexión nueva del pool — bajo una ráfaga que ya
  saturó el pool, los reintentos amplifican la demanda de conexiones en
  vez de aliviarla. Subir el pool de 8→20 solo mejoró la tasa de éxito de
  ~12% a ~38%, confirmando que el techo real no es el tamaño del pool en
  sí, sino la ráfaga de reintentos compitiendo por conexiones ya agotadas.

**Limitación explícita**: esto corrió en una laptop de desarrollo (16 CPUs,
8GB asignados a Docker), no en el VPS chico real (2 vCPU/2GB) que
`docs/08-despliegue.md` describe — los números absolutos de throughput NO
son representativos de producción. Lo que sí es válido y trasladable: (1) la
navegación escala limpio y no es la preocupación principal de capacidad,
(2) una ráfaga de logins simultáneos (sea gente golpeando "iniciar sesión"
al mismo tiempo, o un bot/integración mal comportada) es el escenario real
de riesgo, y (3) el método para volver a medir esto mismo contra el VPS
real antes de un pico de tráfico grande (recomendado en
`docs/08-despliegue.md`).

**No se tocó código de aplicación para "arreglar" esto** — no está claro
que haga falta: una ráfaga de 50 logins simultáneos NO es un patrón
esperado en un negocio real (el login se reparte en el tiempo, no explota a
la vez), y los reintentos existentes SÍ recuperan una fracción de esos
logins en vez de fallar directo. Si en producción se observa esto de
verdad (ej. un cliente que integra su login contra la API en un script mal
hecho), la mitigación más simple es un rate-limit específico en
`/api/auth/login` — no está implementado, es una recomendación para si el
problema se vuelve real, no una tarea completada.
