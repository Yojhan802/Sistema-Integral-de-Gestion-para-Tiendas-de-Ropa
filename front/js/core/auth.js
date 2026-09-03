import { api } from './api.js';
import { getSession, setSession, clearSession } from './session.js';

export { getSession } from './session.js';

export async function login(username, password) {
  const data = await api.post('/auth/login', { username, password }, { auth: false });
  setSession({ accessToken: data.accessToken, refreshToken: data.refreshToken, user: data.user });
  return data;
}

export async function logout() {
  const session = getSession();
  if (session?.refreshToken) {
    try {
      await api.post('/auth/logout', { refreshToken: session.refreshToken });
    } catch {
      // best-effort: si el servidor no responde, igual cerramos la sesión local
    }
  }
  clearSession();
  window.location.href = 'login.html';
}

export function requireSession() {
  const session = getSession();
  if (!session) {
    window.location.href = 'login.html';
    return null;
  }
  const currentPage = window.location.pathname.split('/').pop() || 'index.html';
  if (session.user?.mustChangePassword && currentPage !== 'cambiar-contrasena.html') {
    window.location.href = 'cambiar-contrasena.html';
    return null;
  }
  return session;
}

export function hasPermission(code) {
  return (getSession()?.user?.permissions ?? []).includes(code);
}

export function initials(fullName) {
  return fullName
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('');
}
