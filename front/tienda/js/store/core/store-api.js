import { getCustomerSession, setCustomerSession, clearCustomerSession } from './customer-session.js';

// Mismo criterio que js/core/api.js del admin: en dev el estático corre en
// :8321 y el backend en :8080; en producción comparten origen.
const DEV_STATIC_PORT = '8321';
export const API_ORIGIN = window.location.port === DEV_STATIC_PORT ? 'http://localhost:8080' : '';
const API_BASE = `${API_ORIGIN}/api`;

export class ApiError extends Error {
  constructor(message, status, body) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

let refreshPromise = null;

export async function refreshAccessToken() {
  const session = getCustomerSession();
  if (!session?.refreshToken) return false;

  if (!refreshPromise) {
    refreshPromise = fetch(`${API_BASE}/store/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: session.refreshToken }),
    })
      .then(async (response) => {
        if (!response.ok) return false;
        const data = await response.json();
        setCustomerSession({ accessToken: data.accessToken, refreshToken: data.refreshToken, customer: data.customer });
        return true;
      })
      .catch(() => false)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

export async function storeApiRequest(path, { method = 'GET', body, auth = false, query, headers: extraHeaders = {}, isRetry = false } = {}) {
  const url = new URL(`${API_BASE}${path}`, window.location.origin);
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, value);
    });
  }

  const isFormData = body instanceof FormData;
  const headers = isFormData ? {} : { 'Content-Type': 'application/json' };
  Object.assign(headers, extraHeaders);
  if (auth) {
    const session = getCustomerSession();
    if (session?.accessToken) headers.Authorization = `Bearer ${session.accessToken}`;
  }

  let response;
  try {
    const requestBody = body === undefined ? undefined : isFormData ? body : JSON.stringify(body);
    response = await fetch(url, {
      method,
      headers,
      body: requestBody,
      cache: path === '/store/catalog/config' ? 'no-store' : 'default',
    });
  } catch {
    throw new ApiError('No se pudo conectar con el servidor. Verifica tu conexión.', 0, null);
  }

  if (response.status === 401 && auth && !isRetry) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      return storeApiRequest(path, { method, body, auth, query, headers: extraHeaders, isRetry: true });
    }
    clearCustomerSession();
    throw new ApiError('Tu sesión expiró, vuelve a iniciar sesión.', 401, null);
  }

  if (response.status === 402 && !window.location.pathname.endsWith('no-disponible.html')) {
    // tienda/ (primer nivel) vs tienda/cuenta/ (donde no-disponible.html es hermano, no hijo).
    const esNivelCuenta = window.location.pathname.includes('/cuenta/');
    window.location.href = esNivelCuenta ? '../no-disponible.html' : 'no-disponible.html';
    throw new ApiError('Tienda no disponible', 402, null);
  }

  if (response.status === 204) return null;

  const isJson = response.headers.get('content-type')?.includes('application/json');
  const data = isJson ? await response.json().catch(() => null) : null;

  if (!response.ok) {
    throw new ApiError(data?.message || 'Ocurrió un error inesperado', response.status, data);
  }

  return data;
}

export const storeApi = {
  get: (path, opts) => storeApiRequest(path, { ...opts, method: 'GET' }),
  post: (path, body, opts) => storeApiRequest(path, { ...opts, method: 'POST', body }),
};
