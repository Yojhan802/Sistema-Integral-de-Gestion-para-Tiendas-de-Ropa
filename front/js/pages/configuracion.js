import { requireSession, hasPermission } from '../core/auth.js';
import { api, ApiError, API_ORIGIN } from '../core/api.js';
import { renderShell } from '../components/shell.js';
import { openModal, closeModal } from '../components/modal.js';
import { statusBadge } from '../components/status-badge.js';
import { showToast } from '../components/toast.js';
import { escapeHtml, formatDateTime } from '../core/format.js';

const TIPO_LABELS = { CASH: 'Efectivo', DIGITAL_WALLET: 'Billetera digital', CARD: 'Tarjeta', TRANSFER: 'Transferencia' };
const STORE_TEMPLATE_LABELS = {
  CLASSIC: 'Clasica', MINIMAL: 'Minimalista', FASHION: 'Moda', SPORT: 'Deportiva', LUXURY: 'Premium',
  BOUTIQUE: 'Boutique', CATALOG: 'Catalogo', MARKET: 'Marketplace', EDITORIAL: 'Editorial', URBAN: 'Urbana',
};
const STORE_TEMPLATE_DESCRIPTIONS = {
  CLASSIC: 'Equilibrada y corporativa para cualquier negocio.',
  MINIMAL: 'Mucho espacio en blanco y foco en el producto.',
  FASHION: 'Editorial y visual para marcas de moda.',
  SPORT: 'Energetica, contrastada y rapida para catalogos grandes.',
  LUXURY: 'Sobria y premium para productos de alto valor.',
  BOUTIQUE: 'Calida y cercana para marcas independientes.',
  CATALOG: 'Densa y practica para comparar productos.',
  MARKET: 'Pensada para varias categorias y familias.',
  EDITORIAL: 'Narrativa, con banners y bloques destacados.',
  URBAN: 'Moderna y juvenil, priorizando el movil.',
};
const PLAN_ORDER = ['STARTER', 'PROFESIONAL', 'ECOMMERCE', 'IA'];
const PLAN_LABELS = { STARTER: 'Starter', PROFESIONAL: 'Profesional', ECOMMERCE: 'Ecommerce', IA: 'IA' };
const PLAN_BADGE_CLASS = { STARTER: 'badge-neutral', PROFESIONAL: 'badge-info', ECOMMERCE: 'badge-success', IA: 'badge-warning' };
const PLAN_DESCRIPTIONS = {
  STARTER: 'Ventas, inventario y caja para empezar — hasta 3 usuarios.',
  PROFESIONAL: 'Todo Starter, más promotores, auditoría, separaciones, combos y promociones, y usuarios ilimitados.',
  ECOMMERCE: 'Todo Profesional, más tienda online: catálogo, carrito, pedidos y notificaciones en tiempo real.',
  IA: 'Todo Ecommerce, más funciones de inteligencia artificial.',
};
const PLAN_MODULES = [
  { label: 'Ventas y POS', minPlan: 'STARTER' },
  { label: 'Inventario y almacén', minPlan: 'STARTER' },
  { label: 'Caja', minPlan: 'STARTER' },
  { label: 'Clientes', minPlan: 'STARTER' },
  { label: 'Reportes', minPlan: 'STARTER' },
  { label: 'Usuarios y roles (hasta 3 en Starter, ilimitado desde Profesional)', minPlan: 'STARTER' },
  { label: 'Promotores', minPlan: 'PROFESIONAL' },
  { label: 'Auditoría', minPlan: 'PROFESIONAL' },
  { label: 'Separaciones (apartados con depósito)', minPlan: 'PROFESIONAL' },
  { label: 'Combos y promociones', minPlan: 'PROFESIONAL' },
  { label: 'Tienda online (catálogo, carrito, pedidos)', minPlan: 'ECOMMERCE' },
  { label: 'Notificaciones en tiempo real (pedidos nuevos y su estado)', minPlan: 'ECOMMERCE' },
  { label: 'Inteligencia artificial', minPlan: 'IA' },
];

let activeTab = 'empresa';

function init() {
  document.querySelectorAll('.tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      activeTab = tab.dataset.tab;
      document.querySelectorAll('.tab').forEach((t) => t.setAttribute('aria-selected', String(t.dataset.tab === activeTab)));
      document.querySelector('#panel-empresa').hidden = activeTab !== 'empresa';
      document.querySelector('#panel-pagos').hidden = activeTab !== 'pagos';
      document.querySelector('#panel-tienda').hidden = activeTab !== 'tienda';
      cargarPanelActivo();
    });
  });

  cargarPanelActivo();
}

function cargarPanelActivo() {
  if (activeTab === 'empresa') cargarEmpresa();
  else if (activeTab === 'pagos') cargarMetodosPago();
  else cargarTienda();
}

async function cargarEmpresa() {
  const content = document.querySelector('#empresa-content');
  try {
    const settings = await api.get('/settings/company');
    renderPlanCard(settings);
    renderFormularioEmpresa(settings);
  } catch (error) {
    content.innerHTML = `<div class="empty-state"><span>${error instanceof ApiError ? error.message : 'No se pudo cargar la configuración'}</span></div>`;
  }
}

function renderPlanCard(settings) {
  const container = document.querySelector('#plan-content');
  const plan = settings.plan;
  const planIndex = PLAN_ORDER.indexOf(plan);

  const items = PLAN_MODULES.map((mod) => {
    const incluido = planIndex >= PLAN_ORDER.indexOf(mod.minPlan);
    const icon = incluido
      ? `<svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" style="color: var(--color-success); flex-shrink:0;"><path d="M4 10l4 4 8-8" stroke-linecap="round" stroke-linejoin="round"/></svg>`
      : `<svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" style="color: var(--color-text-muted); flex-shrink:0;"><path d="M6 6l8 8M14 6l-8 8" stroke-linecap="round"/></svg>`;
    return `<li style="display:flex; align-items:center; gap: var(--space-2); ${incluido ? '' : 'color: var(--color-text-muted);'}">${icon}<span>${mod.label}</span></li>`;
  }).join('');

  container.innerHTML = `
    <div style="display:flex; align-items:center; justify-content:space-between; margin-bottom: var(--space-4);">
      <div>
        <p class="table-cell-muted" style="margin-bottom: var(--space-1);">Plan actual</p>
        <div style="display:flex; align-items:center; gap: var(--space-2);">
          <span class="badge ${PLAN_BADGE_CLASS[plan] ?? 'badge-neutral'}">${PLAN_LABELS[plan] ?? plan}</span>
        </div>
      </div>
      ${renderEstadoSuscripcion(settings)}
    </div>
    <p style="margin-bottom: var(--space-4);">${PLAN_DESCRIPTIONS[plan] ?? ''}</p>
    <ul style="list-style:none; padding:0; margin:0; display:grid; gap: var(--space-2);">${items}</ul>
    <p class="table-cell-muted" style="margin-top: var(--space-4); padding-top: var(--space-4); border-top: 1px solid var(--color-border);">
      El plan y la suscripción los gestiona el proveedor del sistema — contáctalo para ampliar el plan o regularizar el pago.
    </p>
  `;
}

function renderEstadoSuscripcion(settings) {
  const { subscriptionStatus, nextPaymentDue } = settings;
  if (subscriptionStatus === 'SUSPENDIDA') {
    return `
      <div style="text-align:right;">
        <p class="table-cell-muted" style="margin-bottom: var(--space-1);">Suscripción</p>
        <span class="badge badge-danger">Suspendida</span>
      </div>
    `;
  }
  if (!nextPaymentDue) return '';

  const dias = Math.ceil((new Date(nextPaymentDue) - new Date()) / (1000 * 60 * 60 * 24));
  const fecha = new Date(nextPaymentDue + 'T00:00:00').toLocaleDateString('es-PE', { day: 'numeric', month: 'long', year: 'numeric' });
  const proximoAVencer = dias <= 7;
  return `
    <div style="text-align:right;">
      <p class="table-cell-muted" style="margin-bottom: var(--space-1);">Próximo pago</p>
      <span class="badge ${proximoAVencer ? 'badge-warning' : 'badge-neutral'}">${fecha}</span>
    </div>
  `;
}

function renderFormularioEmpresa(settings) {
  const content = document.querySelector('#empresa-content');
  const puedeIdentidad = hasPermission('CONFIGURACION_IDENTIDAD_EDITAR');
  const puedeOperativo = hasPermission('CONFIGURACION_EDITAR');

  const secciones = [];
  if (puedeIdentidad) secciones.push('<div class="table-card" style="padding: var(--space-5);" id="identidad-card"></div>');
  if (puedeOperativo) secciones.push('<div class="table-card" style="padding: var(--space-5);" id="operativo-card"></div>');
  if (secciones.length === 0) {
    secciones.push(`
      <div class="table-card" style="padding: var(--space-5);">
        <p class="table-cell-muted">No tienes permisos para editar más configuración de la empresa.</p>
      </div>
    `);
  }
  content.innerHTML = secciones.join('');

  if (puedeIdentidad) renderIdentidadForm(settings);
  if (puedeOperativo) renderOperativoForm(settings);
}

function renderIdentidadForm(settings) {
  const card = document.querySelector('#identidad-card');
  const logoSrc = settings.logoUrl ? `${API_ORIGIN}${settings.logoUrl}` : null;

  card.innerHTML = `
    <h3 style="margin: 0 0 var(--space-4);">Identidad de la empresa</h3>
    <div style="display:flex; align-items:center; gap: var(--space-4); margin-bottom: var(--space-5); padding-bottom: var(--space-5); border-bottom: 1px solid var(--color-border);">
      <div style="width:72px; height:72px; border-radius: var(--radius-md); background: var(--color-surface-muted); display:flex; align-items:center; justify-content:center; overflow:hidden; flex-shrink:0;">
        ${
          logoSrc
            ? `<img src="${logoSrc}" alt="Logo de la empresa" style="width:100%; height:100%; object-fit:contain;" />`
            : `<svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M4 8l8-4 8 4-8 4-8-4z"/><path d="M4 8v8l8 4 8-4V8"/></svg>`
        }
      </div>
      <div>
        <label class="btn btn-secondary btn-sm" for="logo-input" style="cursor:pointer;">Cambiar logo</label>
        <input type="file" id="logo-input" accept="image/png,image/jpeg,image/webp,image/svg+xml" style="display:none;" />
        <p class="table-cell-muted" style="margin-top: var(--space-1);">PNG, JPG, WEBP o SVG.</p>
      </div>
    </div>

    <form id="identidad-form" novalidate>
      <div class="alert alert-danger" id="identidad-form-error" role="alert" hidden>
        <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
        <span class="alert-message"></span>
      </div>
      <div class="form-grid">
        <div class="field field-span-2">
          <label class="field-label" for="ef-name">Razón social</label>
          <input class="input" id="ef-name" required maxlength="150" value="${settings.name}" />
        </div>
        <div class="field">
          <label class="field-label" for="ef-ruc">RUC</label>
          <input class="input" id="ef-ruc" maxlength="15" value="${settings.ruc ?? ''}" />
        </div>
        <div class="field">
          <label class="field-label" for="ef-phone">Teléfono</label>
          <input class="input" id="ef-phone" maxlength="20" value="${settings.phone ?? ''}" />
        </div>
        <div class="field field-span-2">
          <label class="field-label" for="ef-address">Dirección</label>
          <input class="input" id="ef-address" maxlength="255" value="${settings.address ?? ''}" />
        </div>
        <div class="field field-span-2">
          <label class="field-label" for="ef-email">Email</label>
          <input class="input" type="email" id="ef-email" maxlength="120" value="${settings.email ?? ''}" />
        </div>
        <div class="field">
          <label class="field-label" for="ef-vertical">Rubro del negocio</label>
          <select class="select" id="ef-vertical">
            <option value="CLOTHING" ${settings.businessVertical === 'CLOTHING' ? 'selected' : ''}>Ropa</option>
            <option value="GENERAL" ${settings.businessVertical === 'GENERAL' ? 'selected' : ''}>Otro (general)</option>
          </select>
          <span class="field-hint">Ajusta cómo responde el asistente de IA de la tienda.</span>
        </div>
        <div class="field field-span-2">
          <label class="field-label" for="ef-vertical-desc">Descripción del negocio (opcional)</label>
          <input class="input" id="ef-vertical-desc" maxlength="255" placeholder="Ej. una ferretería en Perú" value="${settings.businessDescription ?? ''}" />
          <span class="field-hint">Cómo se presenta el asistente de IA al cliente. Si lo dejas vacío, se usa un texto genérico según el rubro.</span>
        </div>
      </div>

      <div style="display:flex; justify-content:space-between; align-items:center; padding-top: var(--space-4); border-top: 1px solid var(--color-border);">
        <p class="table-cell-muted">
          ${settings.updatedByUsername ? `Última edición por ${settings.updatedByUsername} · ${formatDateTime(settings.updatedAt)}` : ''}
        </p>
        <button class="btn btn-primary" type="submit">Guardar cambios</button>
      </div>
    </form>
  `;

  document.querySelector('#logo-input').addEventListener('change', (event) => {
    const file = event.target.files?.[0];
    if (file) subirLogo(file);
  });

  document.querySelector('#identidad-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = document.querySelector('#identidad-form-error');
    errorAlert.hidden = true;
    try {
      const actualizado = await api.put('/settings/company/identity', {
        name: document.querySelector('#ef-name').value.trim(),
        ruc: document.querySelector('#ef-ruc').value.trim() || null,
        address: document.querySelector('#ef-address').value.trim() || null,
        phone: document.querySelector('#ef-phone').value.trim() || null,
        email: document.querySelector('#ef-email').value.trim() || null,
        businessVertical: document.querySelector('#ef-vertical').value,
        businessDescription: document.querySelector('#ef-vertical-desc').value.trim() || null,
      });
      showToast({ type: 'success', title: 'Configuración guardada' });
      renderIdentidadForm(actualizado);
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudo guardar la configuración';
      errorAlert.hidden = false;
    }
  });
}

async function subirLogo(file) {
  const formData = new FormData();
  formData.append('file', file);
  try {
    const actualizado = await api.post('/settings/company/logo', formData);
    showToast({ type: 'success', title: 'Logo actualizado' });
    renderIdentidadForm(actualizado);
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo subir el logo' });
  }
}

function renderOperativoForm(settings) {
  const card = document.querySelector('#operativo-card');

  card.innerHTML = `
    <h3 style="margin: 0 0 var(--space-4);">Datos operativos</h3>
    <form id="operativo-form" novalidate>
      <div class="alert alert-danger" id="operativo-form-error" role="alert" hidden>
        <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
        <span class="alert-message"></span>
      </div>
      <div class="form-grid">
        <div class="field">
          <label class="field-label" for="ef-currency-code">Moneda (código)</label>
          <input class="input mono" id="ef-currency-code" required maxlength="3" value="${settings.currencyCode}" style="text-transform:uppercase;" />
        </div>
        <div class="field">
          <label class="field-label" for="ef-currency-symbol">Símbolo</label>
          <input class="input mono" id="ef-currency-symbol" required maxlength="5" value="${settings.currencySymbol}" />
        </div>
        <div class="field">
          <label class="field-label" for="ef-igv">IGV (%)</label>
          <input class="input" type="number" id="ef-igv" required min="0" max="100" step="0.01" value="${(settings.igvRate * 100).toFixed(2)}" />
        </div>
        <div class="field">
          <label class="field-label" for="ef-shipping">Tarifa de envío (S/)</label>
          <input class="input" type="number" id="ef-shipping" required min="0" step="0.01" value="${settings.shippingFlatRate ?? '0.00'}" />
          <span class="field-hint">Se cobra en todos los pedidos online, salvo contraentrega en Huacho (gratis).</span>
        </div>
        <div class="field field-span-2">
          <label class="field-label" for="ef-footer">Pie de ticket</label>
          <textarea class="input" id="ef-footer" maxlength="255" rows="2">${settings.ticketFooter ?? ''}</textarea>
        </div>
        <div class="field">
          <label class="field-label" for="ef-reservation-deposit">Seña por defecto de separaciones (S/)</label>
          <input class="input" type="number" id="ef-reservation-deposit" required min="0" step="0.01" value="${settings.reservationDepositAmount ?? '20.00'}" />
          <span class="field-hint">El cajero puede ajustarla al crear cada separación.</span>
        </div>
        <div class="field">
          <label class="field-label" for="ef-reservation-days">Vencimiento de separaciones (días)</label>
          <input class="input" type="number" id="ef-reservation-days" required min="1" step="1" value="${settings.reservationExpirationDays ?? 3}" />
          <span class="field-hint">Pasado este plazo se libera el stock y la seña se pierde.</span>
        </div>
        <div class="field field-span-2" style="display:grid; gap: var(--space-3);">
          <span class="field-label">Integraciones opcionales por empresa</span>
          <label style="display:flex; align-items:flex-start; gap: var(--space-2);">
            <input type="checkbox" id="ef-online-payments" ${settings.onlinePaymentsEnabled === true ? 'checked' : ''} />
            <span><strong>Pagos online</strong><br /><span class="field-hint">Habilita el uso de Niubiz, Culqi o Izipay para esta empresa. Los pagos manuales no se desactivan.</span></span>
          </label>
          <label style="display:flex; align-items:flex-start; gap: var(--space-2);">
            <input type="checkbox" id="ef-electronic-invoicing" ${settings.electronicInvoicingEnabled === true ? 'checked' : ''} />
            <span><strong>Facturación electrónica</strong><br /><span class="field-hint">Habilita la emisión mediante el proveedor configurado para esta empresa.</span></span>
          </label>
        </div>
      </div>

      <div style="display:flex; justify-content:space-between; align-items:center; padding-top: var(--space-4); border-top: 1px solid var(--color-border);">
        <p class="table-cell-muted">
          ${settings.updatedByUsername ? `Última edición por ${settings.updatedByUsername} · ${formatDateTime(settings.updatedAt)}` : ''}
        </p>
        <button class="btn btn-primary" type="submit">Guardar cambios</button>
      </div>
    </form>
  `;

  document.querySelector('#operativo-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = document.querySelector('#operativo-form-error');
    errorAlert.hidden = true;
    try {
      const actualizado = await api.put('/settings/company', {
        currencyCode: document.querySelector('#ef-currency-code').value.trim().toUpperCase(),
        currencySymbol: document.querySelector('#ef-currency-symbol').value.trim(),
        igvRate: Number(document.querySelector('#ef-igv').value) / 100,
        ticketFooter: document.querySelector('#ef-footer').value.trim() || null,
        shippingFlatRate: Number(document.querySelector('#ef-shipping').value),
        reservationDepositAmount: Number(document.querySelector('#ef-reservation-deposit').value),
        reservationExpirationDays: Number(document.querySelector('#ef-reservation-days').value),
        onlinePaymentsEnabled: document.querySelector('#ef-online-payments').checked,
        electronicInvoicingEnabled: document.querySelector('#ef-electronic-invoicing').checked,
      });
      showToast({ type: 'success', title: 'Configuración guardada' });
      renderOperativoForm(actualizado);
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudo guardar la configuración';
      errorAlert.hidden = false;
    }
  });
}

async function cargarTienda() {
  const content = document.querySelector('#storefront-content');
  try {
    const settings = await api.get('/settings/company');
    renderTiendaBuilder(settings);
  } catch (error) {
    content.innerHTML = `<div class="empty-state"><span>${error instanceof ApiError ? error.message : 'No se pudo cargar la configuracion de la tienda'}</span></div>`;
  }
}

function renderTiendaBuilder(settings) {
  const content = document.querySelector('#storefront-content');
  const puedePlantillas = PLAN_ORDER.indexOf(settings.plan) >= PLAN_ORDER.indexOf('ECOMMERCE');
  let selectedTemplate = settings.storeTemplate || 'CLASSIC';
  const initialPrimary = settings.storePrimaryColor || '#17324D';
  const initialAccent = settings.storeAccentColor || '#17324D';
  const initialBackground = settings.storeBackgroundColor || '#F5F7FA';

  const previewUrl = () => {
    const url = new URL('tienda/index.html', window.location.href);
    url.searchParams.set('previewTemplate', selectedTemplate);
    url.searchParams.set('previewPrimaryColor', document.querySelector('#store-primary-color')?.value || initialPrimary);
    url.searchParams.set('previewAccentColor', document.querySelector('#store-accent-color')?.value || initialAccent);
    url.searchParams.set('previewBackgroundColor', document.querySelector('#store-background-color')?.value || initialBackground);
    return url.toString();
  };

  content.innerHTML = `
    <div class="table-card" style="padding:var(--space-5);">
      <div style="display:flex; justify-content:space-between; gap:var(--space-4); align-items:flex-start; flex-wrap:wrap;">
        <div>
          <p class="table-cell-muted" style="margin-bottom:var(--space-1);">Diseño publicado</p>
          <h2 id="storefront-current-title" style="margin:0;">${STORE_TEMPLATE_LABELS[settings.storeTemplate || 'CLASSIC'] || 'Clasica'}</h2>
          <p class="table-cell-muted" style="margin-top:var(--space-2);">${puedePlantillas ? 'Personaliza la apariencia sin modificar catalogo, carrito ni pedidos.' : 'Requiere el plan Ecommerce o superior.'}</p>
        </div>
        <button class="btn btn-secondary" type="button" id="storefront-preview" ${puedePlantillas ? '' : 'disabled'}>Vista previa</button>
      </div>
    </div>

    <div class="table-card" style="padding:var(--space-5);">
      <div style="display:flex; justify-content:space-between; align-items:baseline; gap:var(--space-3); flex-wrap:wrap; margin-bottom:var(--space-4);">
        <div>
          <h3 style="margin:0;">Elegir plantilla</h3>
          <p class="table-cell-muted" style="margin-top:var(--space-1);">Selecciona una propuesta y revisala antes de publicarla.</p>
        </div>
        <span class="badge ${puedePlantillas ? 'badge-success' : 'badge-neutral'}">${puedePlantillas ? 'Ecommerce' : 'Plan requerido'}</span>
      </div>
      <div id="storefront-template-grid" style="display:grid; grid-template-columns:repeat(auto-fit,minmax(180px,1fr)); gap:var(--space-3);">
        ${Object.entries(STORE_TEMPLATE_LABELS).map(([key, label]) => `
          <button type="button" class="store-template-card ${key === selectedTemplate ? 'is-selected' : ''}" data-template="${key}" ${puedePlantillas ? '' : 'disabled'}>
            <span class="store-template-preview store-template-preview-${key.toLowerCase()}"><span>${key === selectedTemplate ? 'Actual' : 'Vista'}</span></span>
            <strong>${label}</strong>
            <small>${STORE_TEMPLATE_DESCRIPTIONS[key]}</small>
          </button>
        `).join('')}
      </div>
    </div>

    <div class="table-card" style="padding:var(--space-5);">
      <h3 style="margin:0 0 var(--space-1);">Personalizacion visual</h3>
      <p class="table-cell-muted" style="margin-bottom:var(--space-4);">Los colores se aplican a la tienda completa y se validan como valores hexadecimales.</p>
      <div class="form-grid">
        <div class="field"><label class="field-label" for="store-primary-color">Color principal</label><input class="input" type="color" id="store-primary-color" value="${initialPrimary}" ${puedePlantillas ? '' : 'disabled'} /></div>
        <div class="field"><label class="field-label" for="store-accent-color">Color de acento</label><input class="input" type="color" id="store-accent-color" value="${initialAccent}" ${puedePlantillas ? '' : 'disabled'} /></div>
        <div class="field"><label class="field-label" for="store-background-color">Color de fondo</label><input class="input" type="color" id="store-background-color" value="${initialBackground}" ${puedePlantillas ? '' : 'disabled'} /></div>
      </div>
      <div style="display:flex; justify-content:flex-end; gap:var(--space-3); margin-top:var(--space-5); padding-top:var(--space-4); border-top:1px solid var(--color-border);">
        <button class="btn btn-secondary" type="button" id="storefront-preview-bottom" ${puedePlantillas ? '' : 'disabled'}>Vista previa</button>
        <button class="btn btn-primary" type="button" id="storefront-publish" ${puedePlantillas ? '' : 'disabled'}>Publicar cambios</button>
      </div>
      <div class="alert alert-danger" id="storefront-error" role="alert" hidden><span class="alert-message"></span></div>
    </div>
  `;

  const openPreview = () => window.open(previewUrl(), '_blank', 'noopener,noreferrer');
  content.querySelector('#storefront-preview')?.addEventListener('click', openPreview);
  content.querySelector('#storefront-preview-bottom')?.addEventListener('click', openPreview);
  content.querySelectorAll('[data-template]').forEach((button) => button.addEventListener('click', () => {
    selectedTemplate = button.dataset.template;
    content.querySelectorAll('[data-template]').forEach((item) => item.classList.toggle('is-selected', item.dataset.template === selectedTemplate));
  }));
  content.querySelector('#storefront-publish')?.addEventListener('click', async (event) => {
    const button = event.currentTarget;
    const errorAlert = content.querySelector('#storefront-error');
    errorAlert.hidden = true;
    button.disabled = true;
    button.textContent = 'Publicando...';
    try {
      const actualizado = await api.put('/settings/company/storefront', {
        template: selectedTemplate,
        primaryColor: content.querySelector('#store-primary-color').value.toUpperCase(),
        accentColor: content.querySelector('#store-accent-color').value.toUpperCase(),
        backgroundColor: content.querySelector('#store-background-color').value.toUpperCase(),
      });
      showToast({ type: 'success', title: 'Tienda publicada', message: STORE_TEMPLATE_LABELS[actualizado.storeTemplate] || 'Cambios aplicados' });
      renderTiendaBuilder(actualizado);
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudieron publicar los cambios';
      errorAlert.hidden = false;
      button.disabled = false;
      button.textContent = 'Publicar cambios';
    }
  });
}

async function cargarMetodosPago() {
  const body = document.querySelector('#payment-methods-body');
  const integrations = document.querySelector('#payment-integrations-content');
  try {
    const metodos = await api.get('/payment-methods');
    body.innerHTML = metodos.length
      ? metodos
          .map(
            (m) => `
        <tr>
          <td>
            <div class="table-cell-primary">${m.name}</div>
            <div class="table-cell-muted">${TIPO_LABELS[m.type] ?? m.type}</div>
          </td>
          <td>${m.accountHolder ?? '—'}</td>
          <td class="mono">${m.accountNumber ?? '—'}</td>
          <td>${statusBadge(m.status)}</td>
          <td>
            <div class="table-actions">
              <button class="btn btn-ghost btn-sm" type="button" data-action="editar" data-id="${m.id}">Editar</button>
              <button class="btn btn-ghost btn-sm" type="button" data-action="toggle" data-id="${m.id}" data-status="${m.status}">
                ${m.status === 'ACTIVE' ? 'Desactivar' : 'Activar'}
              </button>
            </div>
          </td>
        </tr>
      `
          )
          .join('')
      : `<tr><td colspan="5"><div class="empty-state"><span>No hay métodos de pago configurados.</span></div></td></tr>`;

    body.querySelectorAll('[data-action="editar"]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const metodo = metodos.find((m) => String(m.id) === btn.dataset.id);
        abrirFormularioMetodoPago(metodo);
      });
    });
    body.querySelectorAll('[data-action="toggle"]').forEach((btn) => {
      btn.addEventListener('click', () => cambiarEstadoMetodoPago(btn.dataset.id, btn.dataset.status));
    });
  } catch (error) {
    body.innerHTML = `<tr><td colspan="5"><div class="empty-state"><span>${error instanceof ApiError ? error.message : 'Error al cargar métodos de pago'}</span></div></td></tr>`;
  }

  const [proveedoresResult, facturacionResult] = await Promise.allSettled([
    api.get('/settings/payment-providers'),
    api.get('/settings/billing'),
  ]);
  const proveedores = proveedoresResult.status === 'fulfilled' ? proveedoresResult.value : [];
  const facturacion = facturacionResult.status === 'fulfilled'
    ? facturacionResult.value
    : { provider: 'VERIFACT', enabled: false, environment: 'TEST', apiUrl: null, invoiceSeries: null, receiptSeries: null, creditNoteSeries: null, debitNoteSeries: null, configured: false, credentialKeys: [] };
  const errores = [proveedoresResult, facturacionResult]
    .filter((result) => result.status === 'rejected')
    .map((result) => result.reason)
    .filter((error) => error instanceof ApiError)
    .map((error) => error.message);
  if (proveedoresResult.status === 'rejected' && facturacionResult.status === 'rejected' && integrations) {
    integrations.innerHTML = `<div class="table-card" style="padding: var(--space-5);"><div class="empty-state"><span>No se pudieron cargar las integraciones</span></div></div>`;
  } else {
    renderIntegracionesPago(proveedores, facturacion, errores);
  }
}

function renderIntegracionesPago(proveedores, facturacion, errores = []) {
  const content = document.querySelector('#payment-integrations-content');
  if (!content) return;
  const providerLabels = { NIUBIZ: 'Niubiz', CULQI: 'Culqi', IZIPAY: 'Izipay', VERIFACT: 'Verifac', NUBEFACT: 'NubeFact' };
  const puedeConfigurarPagos = hasPermission('CONFIGURACION_PAGOS');
  const puedeConfigurarFacturacion = hasPermission('CONFIGURACION_EDITAR');
  content.innerHTML = `
    <div class="table-card" style="padding: var(--space-5);">
      <h3 style="margin: 0 0 var(--space-2);">Integraciones por empresa</h3>
      <p class="table-cell-muted" style="margin: 0 0 var(--space-4);">Cada empresa puede activar sus integraciones de forma independiente. Las credenciales nunca se muestran en esta pantalla.</p>
      ${errores.length ? `<div class="alert alert-warning" role="status"><span class="alert-message">${escapeHtml(errores.join(' · '))}</span></div>` : ''}
      <div style="display:grid; gap: var(--space-2);">
        ${proveedores.map((p) => `
          <div style="display:flex; justify-content:space-between; align-items:center; gap: var(--space-3); padding: var(--space-3) 0; border-top: 1px solid var(--color-border);">
            <div><strong>${providerLabels[p.provider] ?? p.provider}</strong><div class="table-cell-muted">${p.configured ? 'Credenciales guardadas' : 'Sin credenciales configuradas'}</div></div>
            <div style="display:flex; align-items:center; gap: var(--space-3);">
              <span>${p.enabled ? statusBadge('ACTIVE') : statusBadge('INACTIVE')}</span>
              ${puedeConfigurarPagos ? `<button class="btn btn-secondary btn-sm" type="button" data-config-provider="${p.provider}">Configurar</button>` : ''}
            </div>
          </div>
        `).join('')}
        <div style="display:flex; justify-content:space-between; align-items:center; gap: var(--space-3); padding: var(--space-3) 0; border-top: 1px solid var(--color-border);">
          <div><strong>Facturación electrónica</strong><div class="table-cell-muted">${providerLabels[facturacion.provider] ?? facturacion.provider} · ${facturacion.configured ? 'Credenciales guardadas' : 'Sin credenciales configuradas'}</div></div>
          <div style="display:flex; align-items:center; gap: var(--space-3);">
            <span>${facturacion.enabled ? statusBadge('ACTIVE') : statusBadge('INACTIVE')}</span>
            ${puedeConfigurarFacturacion ? '<button class="btn btn-secondary btn-sm" type="button" data-config-billing>Configurar</button>' : ''}
          </div>
        </div>
      </div>
    </div>
  `;

  content.querySelectorAll('[data-config-provider]').forEach((button) => {
    const config = proveedores.find((item) => item.provider === button.dataset.configProvider);
    button.addEventListener('click', () => abrirFormularioIntegracion('payment', config));
  });
  content.querySelector('[data-config-billing]')?.addEventListener('click', () => abrirFormularioIntegracion('billing', facturacion));
}

function abrirFormularioIntegracion(kind, config) {
  const isBilling = kind === 'billing';
  const providerLabels = { NIUBIZ: 'Niubiz', CULQI: 'Culqi', IZIPAY: 'Izipay', VERIFACT: 'Verifac', NUBEFACT: 'NubeFact' };
  const title = isBilling ? `Facturación electrónica · ${providerLabels[config.provider] ?? config.provider}` : `Pasarela · ${providerLabels[config.provider] ?? config.provider}`;
  const existingKeys = config.credentialKeys ?? [];
  const credentialHint = isBilling && config.provider === 'NUBEFACT'
    ? 'NubeFact: token. La URL de API debe ser la ruta completa entregada para la cuenta.'
    : isBilling
    ? 'Usa los nombres entregados por Verifact, por ejemplo apiKey o token.'
    : config.provider === 'IZIPAY'
      ? 'Izipay: newPaymentButtonApiKey y hashKey. Opcionales: sessionTokenUrl, ipnUrl y apiKeyPrefix.'
      : config.provider === 'NIUBIZ'
        ? 'Niubiz: username y password. Las URLs específicas pueden guardarse como securityUrl, sessionUrl o authorizationUrl.'
        : 'Culqi: secretKey o privateKey. La llave pública va en el campo superior.';
  const credentialRows = existingKeys.length
    ? existingKeys.map((key) => credentialRow(key, true)).join('')
    : credentialRow('', false);
  const modal = openModal({
    title,
    subtitle: 'Las credenciales se cifran antes de guardarse y nunca se vuelven a mostrar.',
    maxWidth: '620px',
    body: `
      <form id="integration-form" novalidate>
        <div class="alert alert-danger" id="integration-form-error" role="alert" hidden>
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
          <span class="alert-message"></span>
        </div>
        <div class="form-grid">
          ${isBilling ? `<div class="field"><label class="field-label" for="integration-billing-provider">Proveedor</label><select class="select" id="integration-billing-provider"><option value="VERIFACT" ${config.provider === 'VERIFACT' ? 'selected' : ''}>Verifac</option><option value="NUBEFACT" ${config.provider === 'NUBEFACT' ? 'selected' : ''}>NubeFact</option></select></div>` : ''}
          <div class="field">
            <label class="field-label" for="integration-environment">Ambiente</label>
            <select class="select" id="integration-environment">
              <option value="TEST" ${config.environment === 'TEST' ? 'selected' : ''}>Pruebas</option>
              <option value="PRODUCTION" ${config.environment === 'PRODUCTION' ? 'selected' : ''}>Producción</option>
            </select>
          </div>
          <div class="field" style="display:flex; align-items:center; padding-top: var(--space-5);">
            <label style="display:flex; align-items:center; gap: var(--space-2);"><input type="checkbox" id="integration-enabled" ${config.enabled ? 'checked' : ''} /> Activar integración</label>
          </div>
          ${isBilling
            ? `<div class="field field-span-2"><label class="field-label" for="integration-api-url">URL de API</label><input class="input" id="integration-api-url" maxlength="500" value="${escapeHtml(config.apiUrl ?? '')}" placeholder="Ruta completa de API del proveedor" /></div><div class="field"><label class="field-label" for="integration-invoice-series">Serie factura</label><input class="input mono" id="integration-invoice-series" maxlength="10" value="${escapeHtml(config.invoiceSeries ?? '')}" placeholder="F001" /></div><div class="field"><label class="field-label" for="integration-receipt-series">Serie boleta</label><input class="input mono" id="integration-receipt-series" maxlength="10" value="${escapeHtml(config.receiptSeries ?? '')}" placeholder="B001" /></div><div class="field"><label class="field-label" for="integration-credit-note-series">Serie nota de crédito</label><input class="input mono" id="integration-credit-note-series" maxlength="10" value="${escapeHtml(config.creditNoteSeries ?? '')}" placeholder="FC01" /></div><div class="field"><label class="field-label" for="integration-debit-note-series">Serie nota de débito</label><input class="input mono" id="integration-debit-note-series" maxlength="10" value="${escapeHtml(config.debitNoteSeries ?? '')}" placeholder="FD01" /></div>`
            : `<div class="field field-span-2"><label class="field-label" for="integration-api-url">URL base de API</label><input class="input" id="integration-api-url" maxlength="500" value="${escapeHtml(config.apiUrl ?? '')}" placeholder="La URL entregada por la pasarela" /></div><div class="field"><label class="field-label" for="integration-merchant-code">Código de comercio</label><input class="input mono" id="integration-merchant-code" maxlength="100" value="${escapeHtml(config.merchantCode ?? '')}" /></div><div class="field"><label class="field-label" for="integration-public-key">Clave pública</label><input class="input mono" id="integration-public-key" maxlength="500" value="${escapeHtml(config.publicKey ?? '')}" /></div>`}
        </div>
        <div style="margin-top: var(--space-5); padding-top: var(--space-4); border-top: 1px solid var(--color-border);">
          <div style="display:flex; justify-content:space-between; align-items:center; gap: var(--space-3); margin-bottom: var(--space-2);">
            <div><div class="field-label">Credenciales privadas</div><div class="field-hint">${credentialHint}</div></div>
            <button class="btn btn-secondary btn-sm" type="button" id="add-credential">Agregar campo</button>
          </div>
          <div id="credential-rows" style="display:grid; gap: var(--space-2);">${credentialRows}</div>
        </div>
      </form>
    `,
    footer: '<button class="btn btn-secondary" type="button" data-cancel>Cancelar</button><button class="btn btn-primary" type="submit" form="integration-form">Guardar</button>',
  });

  modal.footer.querySelector('[data-cancel]').addEventListener('click', () => closeModal());
  modal.body.querySelector('#add-credential').addEventListener('click', () => {
    modal.body.querySelector('#credential-rows').insertAdjacentHTML('beforeend', credentialRow('', false));
  });
  modal.body.querySelector('#credential-rows').addEventListener('click', (event) => {
    if (event.target.closest('[data-remove-credential]')) {
      event.target.closest('[data-credential-row]')?.remove();
    }
  });
  modal.body.querySelector('#integration-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = modal.body.querySelector('#integration-form-error');
    errorAlert.hidden = true;
    try {
      const credentials = {};
      modal.body.querySelectorAll('[data-credential-row]').forEach((row) => {
        const key = row.querySelector('[data-credential-key]').value.trim();
        const value = row.querySelector('[data-credential-value]').value;
        if (key && value) credentials[key] = value;
      });
      const request = { enabled: modal.body.querySelector('#integration-enabled').checked, environment: modal.body.querySelector('#integration-environment').value, credentials };
      if (isBilling) {
        request.provider = modal.body.querySelector('#integration-billing-provider').value;
        request.apiUrl = modal.body.querySelector('#integration-api-url').value.trim() || null;
        request.invoiceSeries = modal.body.querySelector('#integration-invoice-series').value.trim() || null;
        request.receiptSeries = modal.body.querySelector('#integration-receipt-series').value.trim() || null;
        request.creditNoteSeries = modal.body.querySelector('#integration-credit-note-series').value.trim() || null;
        request.debitNoteSeries = modal.body.querySelector('#integration-debit-note-series').value.trim() || null;
        await api.put('/settings/billing', request);
      } else {
        request.apiUrl = modal.body.querySelector('#integration-api-url').value.trim() || null;
        request.merchantCode = modal.body.querySelector('#integration-merchant-code').value.trim() || null;
        request.publicKey = modal.body.querySelector('#integration-public-key').value.trim() || null;
        await api.put(`/settings/payment-providers/${config.provider}`, request);
      }
      closeModal();
      showToast({ type: 'success', title: 'Integración guardada' });
      cargarMetodosPago();
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudo guardar la integración';
      errorAlert.hidden = false;
    }
  });
}

function credentialRow(key, existing) {
  return `<div data-credential-row style="display:grid; grid-template-columns: 1fr 1fr auto; gap: var(--space-2);"><input class="input mono" data-credential-key maxlength="80" value="${escapeHtml(key)}" ${existing ? 'readonly' : ''} placeholder="Nombre" /><input class="input mono" type="password" data-credential-value autocomplete="new-password" placeholder="${existing ? 'Dejar vacío para conservar' : 'Valor'}" /><button class="btn btn-ghost btn-sm" type="button" data-remove-credential aria-label="Quitar credencial">×</button></div>`;
}

function abrirFormularioMetodoPago(metodo) {
  const qrSrc = metodo.qrImageUrl ? `${API_ORIGIN}${metodo.qrImageUrl}` : null;

  const modal = openModal({
    title: `Editar ${metodo.name}`,
    subtitle: TIPO_LABELS[metodo.type] ?? metodo.type,
    maxWidth: '480px',
    body: `
      <form id="pm-form" novalidate>
        <div class="alert alert-danger" id="pm-form-error" role="alert" hidden>
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
          <span class="alert-message"></span>
        </div>
        ${
          metodo.requiresReference
            ? `
        <div style="display:flex; align-items:center; gap: var(--space-4); margin-bottom: var(--space-5); padding-bottom: var(--space-5); border-bottom: 1px solid var(--color-border);">
          <div id="pm-qr-preview-wrap" style="width:96px; height:96px; border-radius: var(--radius-md); background: var(--color-surface-muted); display:flex; align-items:center; justify-content:center; overflow:hidden; flex-shrink:0;">
            ${
              qrSrc
                ? `<img id="pm-qr-preview" src="${qrSrc}" alt="QR de pago" style="width:100%; height:100%; object-fit:contain;" />`
                : `<svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="4" y="4" width="7" height="7"/><rect x="13" y="4" width="7" height="7"/><rect x="4" y="13" width="7" height="7"/></svg>`
            }
          </div>
          <div>
            <label class="btn btn-secondary btn-sm" for="pm-qr-input" style="cursor:pointer;">Subir código QR</label>
            <input type="file" id="pm-qr-input" accept="image/png,image/jpeg,image/webp,image/svg+xml" style="display:none;" />
            <p class="table-cell-muted" style="margin-top: var(--space-1);">Se muestra en el cobro cuando el cajero elige este método.</p>
          </div>
        </div>
        `
            : ''
        }
        <div class="form-grid">
          <div class="field field-span-2">
            <label class="field-label" for="pm-name">Nombre a mostrar</label>
            <input class="input" id="pm-name" required maxlength="40" value="${metodo.name}" />
          </div>
          <div class="field field-span-2">
            <label class="field-label" for="pm-holder">Titular de la cuenta</label>
            <input class="input" id="pm-holder" maxlength="150" value="${metodo.accountHolder ?? ''}" />
          </div>
          <div class="field field-span-2">
            <label class="field-label" for="pm-number">Número / cuenta</label>
            <input class="input mono" id="pm-number" maxlength="30" value="${metodo.accountNumber ?? ''}" />
          </div>
        </div>
      </form>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-primary" type="submit" form="pm-form">Guardar</button>
    `,
  });

  modal.footer.querySelector('[data-cancel]').addEventListener('click', () => closeModal());
  modal.body.querySelector('#pm-qr-input')?.addEventListener('change', async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    const formData = new FormData();
    formData.append('file', file);
    try {
      const actualizado = await api.post(`/payment-methods/${metodo.id}/qr`, formData);
      metodo.qrImageUrl = actualizado.qrImageUrl;
      const wrap = modal.body.querySelector('#pm-qr-preview-wrap');
      wrap.innerHTML = `<img id="pm-qr-preview" src="${API_ORIGIN}${actualizado.qrImageUrl}" alt="QR de pago" style="width:100%; height:100%; object-fit:contain;" />`;
      showToast({ type: 'success', title: 'Código QR actualizado' });
    } catch (error) {
      showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo subir el QR' });
    }
  });

  modal.body.querySelector('#pm-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = modal.body.querySelector('#pm-form-error');
    try {
      await api.put(`/payment-methods/${metodo.id}`, {
        name: modal.body.querySelector('#pm-name').value.trim(),
        accountHolder: modal.body.querySelector('#pm-holder').value.trim() || null,
        accountNumber: modal.body.querySelector('#pm-number').value.trim() || null,
        qrImageUrl: metodo.qrImageUrl ?? null,
      });
      closeModal();
      showToast({ type: 'success', title: 'Método de pago actualizado' });
      cargarMetodosPago();
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudo actualizar';
      errorAlert.hidden = false;
    }
  });
}

async function cambiarEstadoMetodoPago(id, currentStatus) {
  const nuevoEstado = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
  try {
    await api.patch(`/payment-methods/${id}/status`, { status: nuevoEstado });
    showToast({ type: 'success', title: 'Estado actualizado' });
    cargarMetodosPago();
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo actualizar' });
  }
}

const session = requireSession();
if (session) {
  renderShell('configuracion');
  init();
}
