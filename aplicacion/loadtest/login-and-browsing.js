// Fase 5 del plan de multi-tenant: prueba de carga contra un backend real (Docker, MySQL propio,
// aislado del MySQL real de la máquina) para validar el pool de Hikari / hilos de Tomcat del
// perfil "prod" (application.yml) bajo concurrencia real. No pretende reproducir 1500 usuarios
// reales de un VPS chico (2 vCPU/2GB) — este es el hardware de una laptop de desarrollo, así que
// los números absolutos de throughput no son representativos de producción. Lo que SÍ valida:
// (1) que el pool no genere errores/timeouts bajo concurrencia sostenida a un nivel dado,
// (2) que el login concurrente no dispare el deadlock ya conocido (ver ALTA PERF-01,
//     AuthService.login — reintento con @Retryable), y
// (3) un método repetible para volver a correr esto contra el VPS real antes de un pico de
//     tráfico grande (ver docs/08-despliegue.md).
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8090';
const ADMIN_USER = 'admin';
const ADMIN_PASS = 'FreestylePeru#2026';
const PRODUCT_COUNT = 40;
// Perfil "prod" resuelve el tenant por subdominio real (3+ etiquetas) — sin DNS de verdad acá,
// se simula con el header Host apuntando al slug sembrado (V38: 'default').
const TENANT_HOST = { Host: 'default.qynex.pe' };

const loginErrors = new Rate('login_errors');
const loginErrorsDistinct = new Rate('login_errors_distinct');
const browseErrors = new Rate('browse_errors');
const loginDuration = new Trend('login_duration', true);
const loginDurationDistinct = new Trend('login_duration_distinct', true);
const STAFF_COUNT = 50;

export const options = {
    scenarios: {
        // Control deliberadamente adversarial: 50 logins simultáneos contra EL MISMO usuario —
        // aísla el costo de la serialización de locks de InnoDB sobre una sola fila (UPDATE de
        // failed_attempts/last_login_at en cada intento), no la capacidad real del pool/hilos.
        login_storm_same_user: {
            executor: 'per-vu-iterations',
            vus: 50,
            iterations: 1,
            maxDuration: '30s',
            exec: 'loginStormSameUser',
        },
        // El escenario realista: 50 logins simultáneos, cada uno de un usuario DISTINTO — es lo
        // que de verdad pasaría con ~1500 usuarios repartidos en muchas cuentas/tenants (cada
        // login toca una fila distinta, sin contención entre sí). Corre después del anterior
        // (no se solapan) para poder comparar limpio cuánto del costo de arriba es contención de
        // fila vs. capacidad real de pool/hilos.
        login_storm_distinct_users: {
            executor: 'per-vu-iterations',
            vus: 50,
            iterations: 1,
            maxDuration: '30s',
            exec: 'loginStormDistinctUsers',
            startTime: '35s',
        },
        // Navegación sostenida (catálogo, dashboard, clientes) con un token ya obtenido —
        // representa el grueso del tráfico real: staff/clientes que ya iniciaron sesión.
        steady_browsing: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '20s', target: 60 },
                { duration: '40s', target: 60 },
                { duration: '15s', target: 0 },
            ],
            exec: 'steadyBrowsing',
            startTime: '70s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<800'],
        login_errors_distinct: ['rate<0.05'],
        browse_errors: ['rate<0.01'],
    },
};

export function setup() {
    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ username: ADMIN_USER, password: ADMIN_PASS }),
        { headers: { 'Content-Type': 'application/json', ...TENANT_HOST } });
    if (loginRes.status !== 200) {
        throw new Error(`Setup: login falló (${loginRes.status}): ${loginRes.body}`);
    }
    const token = loginRes.json('accessToken');
    const authHeaders = { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, ...TENANT_HOST } };

    const catRes = http.post(`${BASE_URL}/api/categories`, JSON.stringify({ name: `Carga ${Date.now()}` }), authHeaders);
    if (catRes.status !== 201) {
        throw new Error(`Setup: crear categoría falló (${catRes.status}): ${catRes.body}`);
    }
    const categoryId = catRes.json('id');

    const productIds = [];
    for (let i = 0; i < PRODUCT_COUNT; i++) {
        const body = JSON.stringify({
            name: `Producto Carga ${i}`,
            categoryId,
            price: (50 + i).toFixed(2),
        });
        const res = http.post(`${BASE_URL}/api/products`, body, authHeaders);
        if (res.status !== 201) {
            throw new Error(`Setup: crear producto ${i} falló (${res.status}): ${res.body}`);
        }
        productIds.push(res.json('id'));
    }

    const rolesRes = http.get(`${BASE_URL}/api/roles`, authHeaders);
    if (rolesRes.status !== 200) {
        throw new Error(`Setup: listar roles falló (${rolesRes.status}): ${rolesRes.body}`);
    }
    const adminRole = rolesRes.json().find((r) => r.code === 'ADMINISTRADOR');
    if (!adminRole) {
        throw new Error('Setup: no se encontró el rol ADMINISTRADOR sembrado');
    }

    const staffUsernames = [];
    for (let i = 0; i < STAFF_COUNT; i++) {
        const username = `carga.staff.${i}.${Date.now()}`;
        const body = JSON.stringify({
            username,
            email: `${username}@carga.test`,
            password: 'ClaveValida123',
            fullName: `Staff de Carga ${i}`,
            roleIds: [adminRole.id],
        });
        const res = http.post(`${BASE_URL}/api/users`, body, authHeaders);
        if (res.status !== 201) {
            throw new Error(`Setup: crear staff ${i} falló (${res.status}): ${res.body}`);
        }
        staffUsernames.push(username);
    }

    return { token, productIds, staffUsernames };
}

export function loginStormSameUser() {
    const res = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ username: ADMIN_USER, password: ADMIN_PASS }),
        { headers: { 'Content-Type': 'application/json', ...TENANT_HOST }, tags: { name: 'login_same_user' } });
    loginDuration.add(res.timings.duration);
    const ok = check(res, { 'login same-user: 200': (r) => r.status === 200 });
    loginErrors.add(!ok);
}

export function loginStormDistinctUsers(data) {
    const username = data.staffUsernames[(__VU - 1) % data.staffUsernames.length];
    const res = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ username, password: 'ClaveValida123' }),
        { headers: { 'Content-Type': 'application/json', ...TENANT_HOST }, tags: { name: 'login_distinct' } });
    loginDurationDistinct.add(res.timings.duration);
    const ok = check(res, { 'login distinct: 200': (r) => r.status === 200 });
    loginErrorsDistinct.add(!ok);
}

export function steadyBrowsing(data) {
    const authHeaders = { headers: { Authorization: `Bearer ${data.token}`, ...TENANT_HOST } };
    const productId = data.productIds[Math.floor(Math.random() * data.productIds.length)];

    const responses = http.batch([
        ['GET', `${BASE_URL}/api/products?page=0&size=20`, null, { ...authHeaders, tags: { name: 'products_list' } }],
        ['GET', `${BASE_URL}/api/products/${productId}`, null, { ...authHeaders, tags: { name: 'product_detail' } }],
        ['GET', `${BASE_URL}/api/reports/dashboard`, null, { ...authHeaders, tags: { name: 'dashboard' } }],
        ['GET', `${BASE_URL}/api/customers?page=0&size=20`, null, { ...authHeaders, tags: { name: 'customers_list' } }],
    ]);

    for (const res of responses) {
        const ok = check(res, { 'browse: 200': (r) => r.status === 200 });
        browseErrors.add(!ok);
    }
    sleep(Math.random() * 1.5 + 0.5);
}
