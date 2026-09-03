import { getCustomerSession, logoutCliente } from '../core/customer-auth.js';
import { cartCount } from '../core/cart.js';
import { fetchPublicBranding, getCachedPublicBranding, resolveLogoUrl, applyFavicon } from '../../../../js/core/public-branding.js';
import { mountAiWidget } from './store-ai-widget.js';
import { storeApi } from '../core/store-api.js';
import { applyStoreTemplate } from '../../../templates/template-system.js';
import { enhanceClassicStore } from '../../../templates/CLASSIC/template.js';
import { enhanceFashionStore } from '../../../templates/FASHION/template.js';

/**
 * Header/footer comunes de la tienda pública. `basePath` es la ruta relativa
 * hacia la raíz de tienda/ (vacío para páginas de primer nivel, '../' para
 * las de cuenta/), ya que este componente se usa desde ambos niveles.
 */
export function renderStoreShell({ basePath = '', active = '' } = {}) {
  const header = document.querySelector('#store-header');
  const footer = document.querySelector('#store-footer');
  const session = getCustomerSession();

  if (header) {
    header.innerHTML = `
      <a class="store-brand" href="${basePath}index.html">
        <span class="store-brand-logobox" id="store-brand-logobox">Q</span>
        <img id="store-brand-logo" src="${basePath}../assets/brand/logo-mark-dark.png" alt="" hidden />
        <span id="store-brand-name">Qynex</span>
      </a>
      <nav class="store-nav" id="store-nav" aria-label="Navegación de la tienda">
        <a href="${basePath}index.html" ${active === 'catalogo' ? "aria-current='page'" : ''}>Catálogo</a>
        <a href="${basePath}index.html#category-banners">Categorías</a>
        ${
          session
            ? `<a href="${basePath}cuenta/pedidos.html" ${active === 'pedidos' ? "aria-current='page'" : ''}>Mis pedidos</a>
               <a href="#" data-logout>Salir (${session.customer.fullName.split(' ')[0]})</a>`
            : `<a href="${basePath}cuenta/login.html">Ingresar</a>`
        }
      </nav>
      <div class="store-header-actions">
        <button class="store-mobile-menu-toggle" type="button" data-store-mobile-menu aria-controls="store-nav" aria-expanded="false" aria-label="Abrir menú">
          <span></span><span></span><span></span>
        </button>
        <a class="store-icon-btn store-cart-link" href="${basePath}carrito.html" aria-label="Carrito">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><circle cx="9" cy="21" r="1"/><circle cx="19" cy="21" r="1"/><path d="M2 3h2l2.4 12.4a2 2 0 002 1.6h8.7a2 2 0 002-1.6L21 8H6" stroke-linecap="round" stroke-linejoin="round"/></svg>
          <span class="store-cart-count" id="store-cart-count">${cartCount()}</span>
        </a>
      </div>
    `;
    header.querySelector('[data-logout]')?.addEventListener('click', (event) => {
      event.preventDefault();
      logoutCliente();
      window.location.href = `${basePath}index.html`;
    });
  }

  if (footer) {
    footer.innerHTML = `
      <div class="store-footer-top">
        <div>
          <div class="store-footer-brand" id="store-footer-name">Qynex</div>
          <p>Envíos a todo el Perú. Paga con Yape, Plin, transferencia o contraentrega en Huacho.</p>
        </div>
        <div>
          <h3>TIENDA</h3>
          <div class="store-footer-links">
            <a href="${basePath}index.html">Inicio</a>
            <a href="${basePath}index.html#catalog-sections">Catálogo</a>
            <a href="${basePath}index.html#category-banners">Categorías</a>
          </div>
        </div>
      </div>
      <div class="store-footer-copy" id="store-footer-text">© ${new Date().getFullYear()} Qynex. Todos los derechos reservados.</div>
    `;
  }

  aplicarMarcaTienda(getCachedPublicBranding());
  fetchPublicBranding().then(aplicarMarcaTienda);
  const configuracionTienda = storeApi.get('/store/catalog/config').catch(() => null);
  const fallbackConfiguracion = new Promise((resolve) => window.setTimeout(() => resolve(null), 5000));
  Promise.race([configuracionTienda, fallbackConfiguracion]).then((config) => aplicarConfiguracionTienda(config, basePath));

  if (!document.querySelector('.ai-widget')) mountAiWidget(basePath);
}

function aplicarConfiguracionTienda(config, basePath = '') {
  const query = new URLSearchParams(window.location.search);
  const template = query.get('previewTemplate') || config?.template;
  const value = typeof template === 'string' && template.trim() ? template.trim().toUpperCase() : 'CLASSIC';
  applyStoreTemplate(value, { basePath });
  if (value === 'FASHION') enhanceFashionStore();
  else enhanceClassicStore();
  const primary = colorSeguro(query.get('previewPrimaryColor') || config?.primaryColor);
  const accent = colorSeguro(query.get('previewAccentColor') || config?.accentColor);
  const background = colorSeguro(query.get('previewBackgroundColor') || config?.backgroundColor);
  if (primary) document.body.style.setProperty('--brand-black', primary);
  if (accent) document.body.style.setProperty('--brand-accent', accent);
  if (background) document.body.style.setProperty('--color-background', background);
}

function colorSeguro(value) {
  return typeof value === 'string' && /^#[0-9a-f]{6}$/i.test(value.trim()) ? value.trim().toUpperCase() : null;
}

function aplicarMarcaTienda(branding) {
  document.title = document.title.replace('Qynex', branding.name);

  document.querySelectorAll('#store-brand-name, #store-footer-name').forEach((el) => {
    el.textContent = branding.name;
  });

  const logoboxEl = document.querySelector('#store-brand-logobox');
  if (logoboxEl) logoboxEl.textContent = branding.name.trim().charAt(0).toUpperCase() || 'Q';

  const logoEl = document.querySelector('#store-brand-logo');
  const logoUrl = resolveLogoUrl(branding);
  if (logoEl) {
    logoEl.onerror = () => {
      logoEl.hidden = true;
      if (logoboxEl) logoboxEl.hidden = false;
    };
    logoEl.hidden = !logoUrl;
    if (logoUrl) logoEl.src = logoUrl;
    logoEl.alt = branding.name;
  }
  if (logoboxEl) logoboxEl.hidden = Boolean(logoUrl);
  applyFavicon(logoUrl);

  const footerEl = document.querySelector('#store-footer-text');
  if (footerEl) footerEl.textContent = `© ${new Date().getFullYear()} ${branding.name}. Todos los derechos reservados.`;
}

export function actualizarContadorCarrito() {
  const badge = document.querySelector('#store-cart-count');
  if (badge) badge.textContent = String(cartCount());
}
