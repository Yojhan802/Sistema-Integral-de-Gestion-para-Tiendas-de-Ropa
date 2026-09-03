import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { clearStaffSession, getStaffSession, api } from '../services/api';

const PERMISO_OPERADOR = 'PLATAFORMA_EMPRESAS_GESTIONAR';

export const RUTA_PLATAFORMA = '/plataforma/empresas';

function navigate(path: string) {
  if (window.location.pathname === path) return;
  window.history.pushState({}, '', path);
  window.dispatchEvent(new PopStateEvent('popstate'));
}

export function esOperadorDePlataforma() {
  return getStaffSession()?.user.permissions.includes(PERMISO_OPERADOR) ?? false;
}

const seccion = [
  { path: '/plataforma/empresas', label: 'Empresas', hint: 'Altas, módulos y cobros' },
];

/**
 * Cáscara del panel de plataforma: administrar el negocio SaaS, no una tienda.
 *
 * <p>A diferencia de {@code AdminShell}, no pide nada del tenant (configuración, caja,
 * pedidos). Eso no es solo estético: si la empresa del operador quedara suspendida, esas
 * llamadas devolverían 403 y lo dejarían sin poder administrar a las demás.
 */
export function PlatformShell({ children, title, description, activePage }: {
  children: ReactNode; title: string; description?: string; activePage?: string;
}) {
  const session = useMemo(() => getStaffSession(), []);
  const [menuAbierto, setMenuAbierto] = useState(false);
  const [marca, setMarca] = useState('Qynex');

  useEffect(() => {
    if (!session) { navigate('/admin/login'); return; }
    // El permiso es la única guarda necesaria. No hace falta mirar el dominio: el login
    // está acotado al tenant del subdominio, así que un operador no puede siquiera
    // autenticarse desde el dominio de un cliente — y filtrar por host rompería un host
    // propio del panel como admin.qynex.pe.
    if (!session.user.permissions.includes(PERMISO_OPERADOR)) { navigate('/admin/dashboard'); return; }
    let vivo = true;
    api.get<{ name?: string | null }>('/system/branding')
      .then((datos) => { if (vivo && datos?.name) setMarca(datos.name); })
      .catch(() => undefined);
    return () => { vivo = false; };
  }, [session]);

  async function salir() {
    const actual = getStaffSession();
    try {
      if (actual?.refreshToken) await api.post('/auth/logout', { refreshToken: actual.refreshToken }, { auth: 'staff' });
    } catch { /* se cierra igual aunque el servidor no responda */ }
    clearStaffSession();
    navigate('/admin/login');
  }

  if (!session) return null;

  return <div className={`plataforma-shell${menuAbierto ? ' is-menu-open' : ''}`}>
    <aside className="plataforma-sidebar" aria-label="Menú de plataforma">
      <div className="plataforma-brand">
        <span className="plataforma-brand-mark" aria-hidden="true">{marca.trim().charAt(0).toUpperCase() || 'Q'}</span>
        <span><strong>{marca}</strong><small>Plataforma</small></span>
      </div>
      <nav aria-label="Secciones de plataforma">
        {seccion.map((item) => <a className={`plataforma-nav-item${activePage === item.path ? ' is-active' : ''}`}
          href={item.path} key={item.path} aria-current={activePage === item.path ? 'page' : undefined}
          onClick={(event) => { event.preventDefault(); setMenuAbierto(false); navigate(item.path); }}>
          <strong>{item.label}</strong><small>{item.hint}</small>
        </a>)}
      </nav>
      <div className="plataforma-sidebar-footer">
        {/* Salida explícita al panel de tienda: el operador también administra su propia
            empresa, y sin esto tendría que escribir la URL a mano. */}
        <a className="plataforma-nav-secondary" href="/admin/dashboard"
          onClick={(event) => { event.preventDefault(); navigate('/admin/dashboard'); }}>Ir al panel de mi tienda</a>
      </div>
    </aside>

    <div className="plataforma-main">
      <header className="plataforma-topbar">
        <button className="plataforma-menu-toggle" type="button" aria-expanded={menuAbierto}
          aria-label={menuAbierto ? 'Cerrar menú' : 'Abrir menú'}
          onClick={() => setMenuAbierto((valor) => !valor)}>☰</button>
        <div className="plataforma-user">
          <span><strong>{session.user.fullName || session.user.username}</strong><small>Operador de plataforma</small></span>
          <button className="btn btn-ghost btn-sm" type="button" onClick={() => void salir()}>Salir</button>
        </div>
      </header>
      <main className="plataforma-content" id="main-content">
        <div className="plataforma-page-header">
          <span className="store-kicker">PLATAFORMA</span>
          <h1>{title}</h1>
          {description && <p>{description}</p>}
        </div>
        {children}
      </main>
    </div>
    <div className="plataforma-scrim" aria-hidden={!menuAbierto} onClick={() => setMenuAbierto(false)} />
  </div>;
}
