import { useEffect, useMemo, useState, type MouseEvent, type ReactNode } from 'react';
import { getCustomerSession, clearCustomerSession, storeApi } from '../services/api';
import { cartCount } from '../services/cart';
import { igvNotice, legalDocuments } from '../services/legal';
import { useStoreConfig } from './TemplateProvider';
import { CookieBanner } from './CookieBanner';
import type { StoreConfig } from '../types';

function navigate(path: string) {
  window.history.pushState({}, '', path);
  window.dispatchEvent(new PopStateEvent('popstate'));
}

export function StoreShell({ children, active }: { children: ReactNode; active?: string }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [count, setCount] = useState(cartCount());
  const [brand, setBrand] = useState<StoreConfig>({});
  const [logoFailed, setLogoFailed] = useState(false);
  const customer = getCustomerSession()?.customer;
  const config = useStoreConfig();
  const legalLinks = useMemo(() => legalDocuments(config), [config]);

  useEffect(() => {
    const onCart = (event: Event) => setCount(((event as CustomEvent).detail ?? []).reduce((sum: number, item: { quantity: number }) => sum + item.quantity, 0));
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') setMenuOpen(false); };
    window.addEventListener('qynex-cart-change', onCart);
    window.addEventListener('keydown', onKeyDown);
    Promise.all([
      storeApi.get<StoreConfig>('/system/branding'),
      storeApi.get<StoreConfig>('/store/catalog/config'),
    ]).then(([branding, config]) => setBrand({ ...config, ...branding })).catch(() => undefined);
    return () => {
      window.removeEventListener('qynex-cart-change', onCart);
      window.removeEventListener('keydown', onKeyDown);
    };
  }, []);

  const go = (event: MouseEvent<HTMLAnchorElement>, path: string) => {
    event.preventDefault();
    setMenuOpen(false);
    navigate(path);
  };
  const goCategories = (event: MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    setMenuOpen(false);
    if (window.location.pathname === '/') {
      document.querySelector('#category-banners')?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' });
      return;
    }
    navigate('/#category-banners');
    window.setTimeout(() => document.querySelector('#category-banners')?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' }), 100);
  };
  const initial = (brand.name || 'Qynex Shop').trim().charAt(0).toUpperCase() || 'Q';

  return <>
    <a className="skip-link" href="#store-main">Saltar al contenido</a>
    <div className="store-promo-bar">{brand.name ? `${brand.name} · tienda online` : 'Tienda online'}</div>
    <header className={`store-header ${menuOpen ? 'is-menu-open' : ''}`} id="store-header">
      <a className="store-brand" href="/" onClick={(event) => go(event, '/')} aria-label="Ir al catálogo">
        {!logoFailed && brand.logoUrl ? <img id="store-brand-logo" src={brand.logoUrl} alt={brand.name || 'Logo'} onError={() => setLogoFailed(true)} /> : <span className="store-brand-logobox" id="store-brand-logobox">{initial}</span>}
        <span id="store-brand-name">{brand.name || 'Qynex Shop'}</span>
      </a>
      <nav className={`store-nav ${menuOpen ? 'is-open' : ''}`} id="store-nav" aria-label="Navegación de la tienda">
        <a className={active === 'catalogo' ? 'is-active' : ''} href="/" aria-current={active === 'catalogo' ? 'page' : undefined} onClick={(event) => go(event, '/')}>Catálogo</a>
        <a href="/#category-banners" onClick={goCategories}>Categorías</a>
        {customer && <a className={active === 'pedidos' ? 'is-active' : ''} href="/cuenta/pedidos" aria-current={active === 'pedidos' ? 'page' : undefined} onClick={(event) => go(event, '/cuenta/pedidos')}>Mis pedidos</a>}
        {customer ? <a href="/" onClick={(event) => { event.preventDefault(); clearCustomerSession(); setMenuOpen(false); navigate('/'); }}>Salir ({customer.fullName.split(' ')[0]})</a> : <a href="/cuenta/login" onClick={(event) => go(event, '/cuenta/login')}>Ingresar</a>}
      </nav>
      <div className="store-header-actions">
        <button className="store-mobile-menu-toggle" type="button" aria-expanded={menuOpen} aria-controls="store-nav" aria-label={menuOpen ? 'Cerrar menú' : 'Abrir menú'} onClick={() => setMenuOpen((value) => !value)}><span /><span /><span /></button>
        <a className="store-icon-btn store-cart-link" href="/carrito" aria-label={`Carrito, ${count} productos`} onClick={(event) => go(event, '/carrito')}>
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><circle cx="9" cy="21" r="1" /><circle cx="19" cy="21" r="1" /><path d="M2 3h2l2.4 12.4a2 2 0 002 1.6h8.7a2 2 0 002-1.6L21 8H6" strokeLinecap="round" strokeLinejoin="round" /></svg>
          <span className="store-cart-count">{count}</span>
        </a>
      </div>
    </header>
    <main className="store-main" id="store-main">{children}</main>
    <footer className="store-footer" id="store-footer">
      <div className="store-footer-top">
        <div>
          <div className="store-footer-brand" id="store-footer-name">{brand.name || 'Qynex Shop'}</div>
          <p>Envíos a todo el Perú. Compra segura y atención al cliente.</p>
          {/* El Código de Consumo obliga a identificar al proveedor ante el comprador. */}
          <address className="store-footer-legal-id">
            {config.legalName && <span>{config.legalName}</span>}
            {config.ruc && <span>RUC {config.ruc}</span>}
            {config.address && <span>{config.address}</span>}
          </address>
        </div>
        <div>
          <h3>TIENDA</h3>
          <div className="store-footer-links">
            <a href="/" onClick={(event) => go(event, '/')}>Inicio</a>
            <a href="/#catalog-sections" onClick={(event) => go(event, '/#catalog-sections')}>Catálogo</a>
            <a href="/#category-banners" onClick={goCategories}>Categorías</a>
            {customer && <a href="/cuenta/pedidos" onClick={(event) => go(event, '/cuenta/pedidos')}>Mis pedidos</a>}
          </div>
        </div>
        <div>
          <h3>LEGAL</h3>
          <div className="store-footer-links">
            <a className="store-footer-complaint" href="/libro-reclamaciones" onClick={(event) => go(event, '/libro-reclamaciones')}>Libro de Reclamaciones</a>
            {legalLinks.map((document) => <a href={document.path} key={document.path} onClick={(event) => go(event, document.path)}>{document.title}</a>)}
          </div>
        </div>
        {(config.phone || config.email) && <div>
          <h3>CONTACTO</h3>
          <div className="store-footer-links">
            {config.phone && <a href={`tel:${config.phone.replace(/[^0-9+]/g, '')}`}>{config.phone}</a>}
            {config.email && <a href={`mailto:${config.email}`}>{config.email}</a>}
          </div>
        </div>}
      </div>
      <div className="store-footer-copy">
        <span>© {new Date().getFullYear()} {config.legalName || brand.name || 'Qynex Shop'}. Todos los derechos reservados.</span>
        {igvNotice(config) && <span className="store-footer-tax">{igvNotice(config)}</span>}
      </div>
    </footer>
    <CookieBanner />
  </>;
}
