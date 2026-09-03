// Capacidad pura de navegación (sin ráfaga de login) — el tipo de tráfico que domina el uso real
// (staff/clientes ya autenticados navegando catálogo/inventario/reportes). Ver loadtest.js para
// el hallazgo de la ráfaga de login (contención real de InnoDB, no un límite de esta capacidad).
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8090';
const TENANT_HOST = { Host: 'default.qynex.pe' };
const browseErrors = new Rate('browse_errors');

export const options = {
    scenarios: {
        steady_browsing: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 150 },
                { duration: '60s', target: 150 },
                { duration: '15s', target: 0 },
            ],
            exec: 'steadyBrowsing',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<1000'],
        browse_errors: ['rate<0.01'],
    },
};

export function setup() {
    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ username: 'admin', password: 'FreestylePeru#2026' }),
        { headers: { 'Content-Type': 'application/json', ...TENANT_HOST } });
    if (loginRes.status !== 200) {
        throw new Error(`Setup: login falló (${loginRes.status}): ${loginRes.body}`);
    }
    const token = loginRes.json('accessToken');
    const authHeaders = { headers: { Authorization: `Bearer ${token}`, ...TENANT_HOST } };
    const listRes = http.get(`${BASE_URL}/api/products?page=0&size=50`, authHeaders);
    const productIds = listRes.json('content').map((p) => p.id);
    return { token, productIds };
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
