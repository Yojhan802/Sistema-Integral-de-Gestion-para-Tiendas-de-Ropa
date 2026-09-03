import { requireSession } from '../core/auth.js';
import { api, ApiError } from '../core/api.js';
import { renderShell } from '../components/shell.js';
import { renderPagination } from '../components/pagination.js';
import { statusBadge } from '../components/status-badge.js';
import { createVariantPicker } from '../components/variant-picker.js';
import { openModal, closeModal } from '../components/modal.js';
import { showToast } from '../components/toast.js';
import { formatDateLong, escapeHtml } from '../core/format.js';
import { debounce } from '../core/debounce.js';

const MOVEMENT_LABELS = {
  ENTRADA: { label: 'Entrada', cls: 'badge-success' },
  SALIDA: { label: 'Salida', cls: 'badge-warning' },
  VENTA: { label: 'Venta', cls: 'badge-info' },
  DEVOLUCION: { label: 'Devolución', cls: 'badge-info' },
  AJUSTE: { label: 'Ajuste', cls: 'badge-neutral' },
  MERMA: { label: 'Merma', cls: 'badge-danger' },
};

let activeTab = 'stock';
let state = { page: 0, search: '', movementType: '' };

function init() {
  document.querySelectorAll('.tab').forEach((tab) => {
    tab.addEventListener('click', () => cambiarTab(tab.dataset.tab));
  });

  document.querySelector('#stock-search').addEventListener('input', debounce((event) => {
    state.search = event.target.value.trim();
    state.page = 0;
    cargarTabActiva();
  }, 350));

  document.querySelector('#movement-type-filter').addEventListener('change', (event) => {
    state.movementType = event.target.value;
    state.page = 0;
    cargarTabActiva();
  });

  document.querySelector('#btn-entrada').addEventListener('click', () => abrirModalMovimiento('entry'));
  document.querySelector('#btn-salida').addEventListener('click', () => abrirModalMovimiento('exit'));
  document.querySelector('#btn-ajuste').addEventListener('click', () => abrirModalAjuste());

  cargarTabActiva();
}

function cambiarTab(tab) {
  activeTab = tab;
  state.page = 0;
  document.querySelectorAll('.tab').forEach((t) => t.setAttribute('aria-selected', String(t.dataset.tab === tab)));
  document.querySelectorAll('[id^="tab-"]').forEach((table) => (table.hidden = table.id !== `tab-${tab}`));
  document.querySelector('#stock-filter').hidden = tab !== 'stock';
  document.querySelector('#movements-filter').hidden = tab !== 'movements';
  cargarTabActiva();
}

function cargarTabActiva() {
  if (activeTab === 'stock') return cargarStock();
  if (activeTab === 'low-stock') return cargarStockBajo();
  if (activeTab === 'out-of-stock') return cargarAgotados();
  if (activeTab === 'movements') return cargarMovimientos();
}

async function cargarStock() {
  const body = document.querySelector('#stock-body');
  try {
    const page = await api.get('/inventory', { query: { search: state.search || undefined, page: state.page, size: 20 } });
    body.innerHTML = page.content.length
      ? page.content.map((item) => `
          <tr>
            <td class="table-cell-primary">${escapeHtml(item.productName)}</td>
            <td>${escapeHtml(item.variantLabel)}</td>
            <td class="mono">${escapeHtml(item.sku)}</td>
            <td class="mono">${item.barcode ? escapeHtml(item.barcode) : '—'}</td>
            <td>${item.stock}</td>
            <td>${item.minStock}</td>
            <td>${statusBadge(item.status)}</td>
          </tr>
        `).join('')
      : vacio(7, 'No se encontraron variantes.');
    renderPagination(document.querySelector('#pagination'), page, (p) => { state.page = p; cargarStock(); });
  } catch (error) {
    body.innerHTML = errorFila(7, error);
  }
}

async function cargarStockBajo() {
  const body = document.querySelector('#low-stock-body');
  document.querySelector('#pagination').innerHTML = '';
  try {
    const items = await api.get('/inventory/low-stock');
    body.innerHTML = items.length
      ? items.map((item) => `
          <tr>
            <td class="table-cell-primary">${escapeHtml(item.productName)}</td>
            <td>${escapeHtml(item.variantLabel)}</td>
            <td class="mono">${escapeHtml(item.sku)}</td>
            <td style="color:var(--color-warning-text); font-weight:600;">${item.stock}</td>
            <td>${item.minStock}</td>
            <td>${Math.max(item.minStock - item.stock, 0)}</td>
          </tr>
        `).join('')
      : vacio(6, 'No hay variantes con stock bajo.');
  } catch (error) {
    body.innerHTML = errorFila(6, error);
  }
}

async function cargarAgotados() {
  const body = document.querySelector('#out-of-stock-body');
  document.querySelector('#pagination').innerHTML = '';
  try {
    const items = await api.get('/inventory/out-of-stock');
    body.innerHTML = items.length
      ? items.map((item) => `
          <tr>
            <td class="table-cell-primary">${escapeHtml(item.productName)}</td>
            <td>${escapeHtml(item.variantLabel)}</td>
            <td class="mono">${escapeHtml(item.sku)}</td>
            <td class="mono">${item.barcode ? escapeHtml(item.barcode) : '—'}</td>
          </tr>
        `).join('')
      : vacio(4, 'No hay variantes agotadas.');
  } catch (error) {
    body.innerHTML = errorFila(4, error);
  }
}

async function cargarMovimientos() {
  const body = document.querySelector('#movements-body');
  try {
    const page = await api.get('/inventory/movements', {
      query: { type: state.movementType || undefined, page: state.page, size: 20, sort: 'createdAt,desc' },
    });
    body.innerHTML = page.content.length
      ? page.content.map((m) => {
          const meta = MOVEMENT_LABELS[m.type] || { label: m.type, cls: 'badge-neutral' };
          const signo = m.quantity > 0 ? '+' : '';
          return `
            <tr>
              <td class="table-cell-muted">${formatDateLong(m.createdAt)}</td>
              <td><span class="badge ${meta.cls}">${meta.label}</span></td>
              <td>${escapeHtml(m.productName)} <span class="table-cell-muted mono">${escapeHtml(m.variantSku)}</span></td>
              <td class="mono" style="color:${m.quantity > 0 ? 'var(--color-success-text)' : 'var(--color-danger-text)'};">${signo}${m.quantity}</td>
              <td class="mono">${m.stockBefore} → ${m.stockAfter}</td>
              <td>${m.reason ? escapeHtml(m.reason) : '—'}</td>
              <td>${escapeHtml(m.username)}</td>
            </tr>
          `;
        }).join('')
      : vacio(7, 'Sin movimientos registrados.');
    renderPagination(document.querySelector('#pagination'), page, (p) => { state.page = p; cargarMovimientos(); });
  } catch (error) {
    body.innerHTML = errorFila(7, error);
  }
}

function vacio(colspan, mensaje) {
  return `<tr><td colspan="${colspan}"><div class="empty-state"><span>${mensaje}</span></div></td></tr>`;
}

function errorFila(colspan, error) {
  const message = error instanceof ApiError ? error.message : 'No se pudo cargar la información';
  return `<tr><td colspan="${colspan}"><div class="empty-state"><span>${message}</span></div></td></tr>`;
}

function abrirModalMovimiento(tipo) {
  const esEntrada = tipo === 'entry';
  const picker = createVariantPicker({});

  const modal = openModal({
    title: esEntrada ? 'Registrar entrada' : 'Registrar salida',
    subtitle: esEntrada ? 'Aumenta el stock de una variante (compra, reposición, etc.).' : 'Disminuye el stock de una variante (traslado, muestra, etc.).',
    body: buildBody(picker, esEntrada),
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-primary" type="submit" form="movimiento-form">${esEntrada ? 'Registrar entrada' : 'Registrar salida'}</button>
    `,
  });
  wireMovimientoForm(modal, picker, tipo);
}

function buildBody(picker, reasonRequired) {
  const wrapper = document.createElement('div');
  wrapper.innerHTML = `
    <form id="movimiento-form" novalidate>
      <div class="alert alert-danger" id="movimiento-error" role="alert" hidden>
        <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
        <span class="alert-message"></span>
      </div>
      <div class="field field-span-2" style="margin-bottom: var(--space-4);">
        <label class="field-label">Variante</label>
        <div id="picker-mount"></div>
      </div>
      <div class="form-grid">
        <div class="field">
          <label class="field-label" for="mv-quantity">Cantidad</label>
          <input class="input" type="number" id="mv-quantity" min="1" step="1" required />
        </div>
        <div class="field">
          <label class="field-label" for="mv-reason">Motivo ${reasonRequired ? '' : '(opcional)'}</label>
          <input class="input" type="text" id="mv-reason" maxlength="255" ${reasonRequired ? 'required' : ''} />
        </div>
      </div>
    </form>
  `;
  wrapper.querySelector('#picker-mount').appendChild(picker.root);
  return wrapper;
}

function wireMovimientoForm(modal, picker, tipo) {
  modal.footer.querySelector('[data-cancel]').addEventListener('click', closeModal);
  modal.body.querySelector('#movimiento-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = modal.body.querySelector('#movimiento-error');
    errorAlert.hidden = true;

    const variante = picker.getSelected();
    if (!variante) {
      mostrarError(errorAlert, 'Selecciona una variante.');
      return;
    }
    const quantity = Number(modal.body.querySelector('#mv-quantity').value);
    const reason = modal.body.querySelector('#mv-reason').value.trim() || undefined;

    try {
      const path = tipo === 'entry' ? '/inventory/entry' : '/inventory/exit';
      await api.post(path, { variantId: variante.variantId, quantity, reason });
      closeModal();
      showToast({ type: 'success', title: tipo === 'entry' ? 'Entrada registrada' : 'Salida registrada' });
      cargarTabActiva();
    } catch (error) {
      mostrarError(errorAlert, error instanceof ApiError ? error.message : 'No se pudo registrar el movimiento');
    }
  });
}

function abrirModalAjuste() {
  const picker = createVariantPicker({});
  const wrapper = document.createElement('div');
  wrapper.innerHTML = `
    <form id="ajuste-form" novalidate>
      <div class="alert alert-danger" id="ajuste-error" role="alert" hidden>
        <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
        <span class="alert-message"></span>
      </div>
      <div class="field field-span-2" style="margin-bottom: var(--space-4);">
        <label class="field-label">Variante</label>
        <div id="picker-mount-ajuste"></div>
      </div>
      <div class="form-grid">
        <div class="field">
          <label class="field-label" for="aj-new-stock">Stock real contado</label>
          <input class="input" type="number" id="aj-new-stock" min="0" step="1" required />
          <span class="field-hint">El sistema calcula la diferencia automáticamente.</span>
        </div>
        <div class="field">
          <label class="field-label" for="aj-reason">Motivo</label>
          <input class="input" type="text" id="aj-reason" maxlength="255" required placeholder="Recuento físico del…" />
        </div>
      </div>
    </form>
  `;
  wrapper.querySelector('#picker-mount-ajuste').appendChild(picker.root);

  const modal = openModal({
    title: 'Registrar ajuste',
    subtitle: 'Corrige el stock a partir de un recuento físico. Queda registrado como movimiento de ajuste.',
    body: wrapper,
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-primary" type="submit" form="ajuste-form">Registrar ajuste</button>
    `,
  });

  modal.footer.querySelector('[data-cancel]').addEventListener('click', closeModal);
  modal.body.querySelector('#ajuste-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = modal.body.querySelector('#ajuste-error');
    errorAlert.hidden = true;

    const variante = picker.getSelected();
    if (!variante) {
      mostrarError(errorAlert, 'Selecciona una variante.');
      return;
    }
    const newStock = Number(modal.body.querySelector('#aj-new-stock').value);
    const reason = modal.body.querySelector('#aj-reason').value.trim();

    try {
      await api.post('/inventory/adjustment', { variantId: variante.variantId, newStock, reason });
      closeModal();
      showToast({ type: 'success', title: 'Ajuste registrado' });
      cargarTabActiva();
    } catch (error) {
      mostrarError(errorAlert, error instanceof ApiError ? error.message : 'No se pudo registrar el ajuste');
    }
  });
}

function mostrarError(alertEl, message) {
  alertEl.querySelector('.alert-message').textContent = message;
  alertEl.hidden = false;
}

const session = requireSession();
if (session) {
  renderShell('inventario');
  init();
}
