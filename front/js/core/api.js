import { getSession, setSession, clearSession } from './session.js';

// En producción (Docker/nginx) el frontend y el backend se sirven desde el
// mismo origen y el reverse proxy enruta /api → backend, así que basta una
// ruta relativa. En desarrollo local el backend corre aparte en :8080
// mientras el frontend se sirve estático en :8321 (ver README.md).
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
  const session = getSession();
  if (!session?.refreshToken) return false;

  if (!refreshPromise) {
    refreshPromise = fetch(`${API_BASE}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: session.refreshToken }),
    })
      .then(async (response) => {
        if (!response.ok) return false;
        const data = await response.json();
        setSession({ ...session, accessToken: data.accessToken, refreshToken: data.refreshToken });
        return true;
      })
      .catch(() => false)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

export async function apiRequest(path, { method = 'GET', body, auth = true, query, headers: extraHeaders = {}, isRetry = false } = {}) {
  const url = new URL(`${API_BASE}${path}`, window.location.origin);
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, value);
    });
  }

  const isFormData = body instanceof FormData;
  const headers = isFormData ? {} : { 'Content-Type': 'application/json' };
  if (auth) {
    const session = getSession();
    if (session?.accessToken) headers.Authorization = `Bearer ${session.accessToken}`;
  }
  Object.assign(headers, extraHeaders);

  let response;
  try {
    const requestBody = body === undefined ? undefined : isFormData ? body : JSON.stringify(body);
    response = await fetch(url, { method, headers, body: requestBody });
  } catch {
    throw new ApiError('No se pudo conectar con el servidor. Verifica tu conexión.', 0, null);
  }

  if (response.status === 401 && auth && !isRetry) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      return apiRequest(path, { method, body, auth, query, headers: extraHeaders, isRetry: true });
    }
    clearSession();
    window.location.href = 'login.html';
    throw new ApiError('Sesión expirada', 401, null);
  }

  if (response.status === 402 && !window.location.pathname.endsWith('suspendido.html')) {
    window.location.href = 'suspendido.html';
    throw new ApiError('Servicio suspendido', 402, null);
  }

  if (response.status === 204) return null;

  const isJson = response.headers.get('content-type')?.includes('application/json');
  const data = isJson ? await response.json().catch(() => null) : null;

  if (!response.ok) {
    throw new ApiError(data?.message || 'Ocurrió un error inesperado', response.status, data);
  }

  return data;
}

export const api = {
  get: (path, opts) => apiRequest(path, { ...opts, method: 'GET' }),
  post: (path, body, opts) => apiRequest(path, { ...opts, method: 'POST', body }),
  put: (path, body, opts) => apiRequest(path, { ...opts, method: 'PUT', body }),
  patch: (path, body, opts) => apiRequest(path, { ...opts, method: 'PATCH', body }),
  delete: (path, opts) => apiRequest(path, { ...opts, method: 'DELETE' }),
};

export async function apiDownload(path, { auth = true, isRetry = false } = {}) {
  const headers = {};
  if (auth) {
    const session = getSession();
    if (session?.accessToken) headers.Authorization = `Bearer ${session.accessToken}`;
  }
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, { method: 'GET', headers });
  } catch {
    throw new ApiError('No se pudo conectar con el servidor.', 0, null);
  }
  if (response.status === 401 && auth && !isRetry) {
    const refreshed = await refreshAccessToken();
    if (refreshed) return apiDownload(path, { auth, isRetry: true });
    clearSession();
    window.location.href = 'login.html';
    throw new ApiError('Sesión expirada', 401, null);
  }
  if (!response.ok) {
    const data = await response.json().catch(() => null);
    throw new ApiError(data?.message || 'No se pudo descargar el archivo', response.status, data);
  }
  const disposition = response.headers.get('content-disposition') || '';
  const match = disposition.match(/filename="?([^";]+)"?/i);
  return { blob: await response.blob(), filename: match?.[1] || 'comprobante' };
}
