import type { CustomerSession, StaffSession } from '../types';

const API_BASE = '/api';
const STAFF_KEY = 'fsp.session';
const CUSTOMER_KEY = 'fsp.customer.session';
const TENANT_KEY = 'fsp.tenant.slug';

/**
 * En producción el tenant se resuelve por subdominio. En desarrollo local no existe ese DNS,
 * por eso permitimos seleccionar la empresa una vez con ?tenant=moderns y conservamos la
 * selección durante la sesión del navegador. Así el login y todas las peticiones posteriores
 * usan exactamente el mismo tenant.
 */
function tenantSlug(): string | null {
  const queryValue = new URLSearchParams(window.location.search).get('tenant')?.trim().toLowerCase();
  if (queryValue && /^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])?$/.test(queryValue)) {
    sessionStorage.setItem(TENANT_KEY, queryValue);
    return queryValue;
  }
  const host = window.location.hostname.toLowerCase();
  for (const baseDomain of ['.qynex.pe', '.localhost']) {
    if (!host.endsWith(baseDomain)) continue;
    const subdomain = host.slice(0, -baseDomain.length).split('.').pop();
    if (subdomain && /^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])?$/.test(subdomain)) return subdomain;
  }
  return sessionStorage.getItem(TENANT_KEY);
}

function tenantHeaders(): Record<string, string> {
  const slug = tenantSlug();
  return slug ? { 'X-Tenant-Slug': slug } : {};
}

export interface RequestOptions {
  method?: string;
  body?: unknown;
  auth?: 'staff' | 'customer' | false;
  query?: Record<string, string | number | undefined>;
  headers?: Record<string, string>;
  retry?: boolean;
}

export class ApiError extends Error {
  status: number;
  body: unknown;

  constructor(message: string, status = 0, body: unknown = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

function read<T>(key: string): T | null {
  const raw = sessionStorage.getItem(key);
  if (!raw) return null;
  try { return JSON.parse(raw) as T; } catch { return null; }
}

export const getStaffSession = () => read<StaffSession>(STAFF_KEY);
export const getCustomerSession = () => read<CustomerSession>(CUSTOMER_KEY);
export const setStaffSession = (value: StaffSession) => sessionStorage.setItem(STAFF_KEY, JSON.stringify(value));
export const setCustomerSession = (value: CustomerSession) => sessionStorage.setItem(CUSTOMER_KEY, JSON.stringify(value));
export const clearStaffSession = () => sessionStorage.removeItem(STAFF_KEY);
export const clearCustomerSession = () => sessionStorage.removeItem(CUSTOMER_KEY);

let staffRefresh: Promise<boolean> | null = null;
let customerRefresh: Promise<boolean> | null = null;

async function refresh(kind: 'staff' | 'customer'): Promise<boolean> {
  const session = kind === 'staff' ? getStaffSession() : getCustomerSession();
  if (!session?.refreshToken) return false;
  const current = kind === 'staff' ? staffRefresh : customerRefresh;
  if (current) return current;
  const promise = fetch(`${API_BASE}/${kind === 'staff' ? 'auth' : 'store/auth'}/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...tenantHeaders() },
    body: JSON.stringify({ refreshToken: session.refreshToken }),
  }).then(async (response) => {
    if (!response.ok) return false;
    const next = await response.json();
    if (kind === 'staff') setStaffSession({ ...(session as StaffSession), accessToken: next.accessToken, refreshToken: next.refreshToken });
    else setCustomerSession({ ...(session as CustomerSession), accessToken: next.accessToken, refreshToken: next.refreshToken });
    return true;
  }).catch(() => false).finally(() => {
    if (kind === 'staff') staffRefresh = null;
    else customerRefresh = null;
  });
  if (kind === 'staff') staffRefresh = promise;
  else customerRefresh = promise;
  return promise;
}

export function refreshCustomerAccessToken() { return refresh('customer'); }
export function refreshStaffAccessToken() { return refresh('staff'); }

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = false, query, headers: extra = {}, retry = false } = options;
  const url = new URL(`${API_BASE}${path}`, window.location.origin);
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== '') url.searchParams.set(key, String(value));
  });
  const session = auth === 'staff' ? getStaffSession() : auth === 'customer' ? getCustomerSession() : null;
  const isForm = body instanceof FormData;
  const headers: Record<string, string> = { ...tenantHeaders(), ...(isForm ? {} : { 'Content-Type': 'application/json' }) };
  if (session?.accessToken) headers.Authorization = `Bearer ${session.accessToken}`;
  Object.assign(headers, extra);
  let response: Response;
  try {
    response = await fetch(url, { method, headers, body: body === undefined ? undefined : isForm ? body : JSON.stringify(body), cache: path === '/store/catalog/config' ? 'no-store' : 'default' });
  } catch {
    throw new ApiError('No se pudo conectar con el servidor. Verifica tu conexión.');
  }
  if (response.status === 401 && auth && !retry && await refresh(auth)) {
    return request<T>(path, { ...options, retry: true });
  }
  if (response.status === 401 && auth) {
    if (auth === 'staff') clearStaffSession(); else clearCustomerSession();
    throw new ApiError('Tu sesión expiró, vuelve a iniciar sesión.', 401);
  }
  if (response.status === 402) throw new ApiError('El servicio no está disponible para esta cuenta.', 402);
  if (response.status === 204) return null as T;
  const data = response.headers.get('content-type')?.includes('application/json') ? await response.json().catch(() => null) : null;
  if (!response.ok) throw new ApiError((data as { message?: string } | null)?.message ?? 'Ocurrió un error inesperado', response.status, data);
  return data as T;
}

export interface DownloadResult {
  blob: Blob;
  filename: string;
}

async function download(path: string, options: Omit<RequestOptions, 'body'> = {}): Promise<DownloadResult> {
  const { method = 'GET', auth = false, query, headers: extra = {}, retry = false } = options;
  const url = new URL(`${API_BASE}${path}`, window.location.origin);
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== '') url.searchParams.set(key, String(value));
  });
  const session = auth === 'staff' ? getStaffSession() : auth === 'customer' ? getCustomerSession() : null;
  const headers: Record<string, string> = tenantHeaders();
  if (session?.accessToken) headers.Authorization = `Bearer ${session.accessToken}`;
  Object.assign(headers, extra);
  let response: Response;
  try {
    response = await fetch(url, { method, headers, cache: 'no-store' });
  } catch {
    throw new ApiError('No se pudo conectar con el servidor. Verifica tu conexion.');
  }
  if (response.status === 401 && auth && !retry && await refresh(auth)) {
    return download(path, { ...options, retry: true });
  }
  if (response.status === 401 && auth) {
    if (auth === 'staff') clearStaffSession(); else clearCustomerSession();
    throw new ApiError('Tu sesion expiro, vuelve a iniciar sesion.', 401);
  }
  if (response.status === 402) throw new ApiError('El servicio no esta disponible para esta cuenta.', 402);
  if (!response.ok) {
    const data = await response.json().catch(() => null) as { message?: string } | null;
    throw new ApiError(data?.message ?? 'No se pudo descargar el archivo.', response.status, data);
  }
  const disposition = response.headers.get('content-disposition') || '';
  const match = disposition.match(/filename\*?=(?:UTF-8''|\")?([^;\"]+)/i);
  return { blob: await response.blob(), filename: match?.[1] ? decodeURIComponent(match[1]) : 'documento' };
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) => request<T>(path, options),
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'body'>) => request<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'body'>) => request<T>(path, { ...options, method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'body'>) => request<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, options?: RequestOptions) => request<T>(path, { ...options, method: 'DELETE' }),
  download,
};

type StoreRequestOptions = Omit<RequestOptions, 'auth'> & { auth?: boolean };
export const storeApi = {
  get: <T>(path: string, options?: StoreRequestOptions) => request<T>(path, { ...options, auth: options?.auth ? 'customer' : false }),
  post: <T>(path: string, body?: unknown, options?: Omit<StoreRequestOptions, 'body'>) => request<T>(path, { ...options, method: 'POST', body, auth: options?.auth ? 'customer' : false }),
  download: (path: string, options?: StoreRequestOptions) => download(path, { ...options, auth: options?.auth ? 'customer' : false }),
};

export function imageUrl(value?: string | null): string {
  if (!value) return 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22400%22 height=%22400%22%3E%3Crect width=%22100%%22 height=%22100%%22 fill=%22%23eef2f5%22/%3E%3C/svg%3E';
  return value.startsWith('http') || value.startsWith('data:') ? value : value;
}
