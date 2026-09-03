import { requireSession } from '../core/auth.js';
import { api, ApiError } from '../core/api.js';
import { renderShell } from '../components/shell.js';
import { renderPagination } from '../components/pagination.js';
import { openModal, closeModal } from '../components/modal.js';
import { showToast } from '../components/toast.js';
import { formatDateTime } from '../core/format.js';
import { debounce } from '../core/debounce.js';

const RESULT_LABELS = { SUCCESS: 'Éxito', DENIED: 'Denegado', FAILURE: 'Falla' };
const RESULT_CLASSES = { SUCCESS: 'badge-success', DENIED: 'badge-warning', FAILURE: 'badge-danger' };

let state = { page: 0, userId: '', action: '', entity: '', result: '', from: '', to: '' };

function resultBadge(result) {
  return `<span class="badge ${RESULT_CLASSES[result] || 'badge-neutral'}">${RESULT_LABELS[result] || result}</span>`;
}

function init() {
  cargarUsuarios();

  document.querySelector('#filter-action').addEventListener('input', debounce((event) => {
    state.action = event.target.value.trim();
    state.page = 0;
    cargarRegistros();
  }, 350));
  document.querySelector('#filter-entity').addEventListener('input', debounce((event) => {
    state.entity = event.target.value.trim();
    state.page = 0;
    cargarRegistros();
  }, 350));
  document.querySelector('#filter-user').addEventListener('change', (event) => {
    state.userId = event.target.value;
    state.page = 0;
    cargarRegistros();
  });
  document.querySelector('#filter-result').addEventListener('change', (event) => {
    state.result = event.target.value;
    state.page = 0;
    cargarRegistros();
  });
  document.querySelector('#filter-from').addEventListener('change', (event) => {
    state.from = event.target.value;
    state.page = 0;
    cargarRegistros();
  });
  document.querySelector('#filter-to').addEventListener('change', (event) => {
    state.to = event.target.value;
    state.page = 0;
    cargarRegistros();
  });

  cargarRegistros();
}

async function cargarUsuarios() {
  try {
    const page = await api.get('/users', { query: { size: 100, sort: 'fullName,asc' } });
    const select = document.querySelector('#filter-user');
    page.content.forEach((u) => select.insertAdjacentHTML('beforeend', `<option value="${u.id}">${escapeHtml(u.fullName)}</option>`));
  } catch {
    // El filtro de usuario es un extra — si falla, la auditoría sigue consultable sin él.
  }
}

async function cargarRegistros() {
  const body = document.querySelector('#audit-body');
  try {
    const page = await api.get('/audit', {
      query: {
        userId: state.userId || undefined,
        action: state.action || undefined,
        entity: state.entity || undefined,
        result: state.result || undefined,
        from: state.from || undefined,
        to: state.to || undefined,
        page: state.page,
        size: 20,
      },
    });

    body.innerHTML = page.content.length
      ? page.content
          .map(
            (e) => `
        <tr>
          <td class="mono">${formatDateTime(e.createdAt)}</td>
          <td>${escapeHtml(e.username) || '—'}</td>
          <td class="mono">${escapeHtml(e.action)}</td>
          <td>${escapeHtml(e.entity)}${e.entityId ? ` #${e.entityId}` : ''}</td>
          <td>${resultBadge(e.result)}</td>
          <td>
            <div class="table-actions">
              <button class="btn btn-ghost btn-sm" type="button" data-action="detalle" data-id="${e.id}">Ver detalle</button>
            </div>
          </td>
        </tr>
      `
          )
          .join('')
      : `<tr><td colspan="6"><div class="empty-state"><span>No se encontraron registros.</span></div></td></tr>`;

    body.querySelectorAll('[data-action="detalle"]').forEach((btn) => {
      btn.addEventListener('click', () => verDetalle(btn.dataset.id));
    });

    renderPagination(document.querySelector('#pagination'), page, (p) => { state.page = p; cargarRegistros(); });
  } catch (error) {
    body.innerHTML = `<tr><td colspan="6"><div class="empty-state"><span>${error instanceof ApiError ? error.message : 'Error al cargar la auditoría'}</span></div></td></tr>`;
  }
}

async function verDetalle(id) {
  try {
    const entry = await api.get(`/audit/${id}`);
    abrirModalDetalle(entry);
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo cargar el registro' });
  }
}

function escapeHtml(value) {
  if (value == null) return '';
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function formatearValor(json) {
  if (!json) return null;
  try {
    return JSON.stringify(JSON.parse(json), null, 2);
  } catch {
    return json;
  }
}

function abrirModalDetalle(entry) {
  const oldValue = escapeHtml(formatearValor(entry.oldValue)) || null;
  const newValue = escapeHtml(formatearValor(entry.newValue)) || null;

  const body = document.createElement('div');
  body.innerHTML = `
    <div style="display:flex; flex-direction:column; gap: var(--space-1); margin-bottom: var(--space-4); font-size: var(--font-size-sm);">
      <div><strong>Usuario:</strong> ${escapeHtml(entry.username) || '—'}${entry.userId ? ` (#${entry.userId})` : ''}</div>
      <div><strong>Acción:</strong> <span class="mono">${escapeHtml(entry.action)}</span></div>
      <div><strong>Entidad:</strong> ${escapeHtml(entry.entity)}${entry.entityId ? ` #${entry.entityId}` : ''}</div>
      <div><strong>Resultado:</strong> ${resultBadge(entry.result)}</div>
      <div><strong>Fecha:</strong> ${formatDateTime(entry.createdAt)}</div>
      <div><strong>IP:</strong> <span class="mono">${escapeHtml(entry.ipAddress) || '—'}</span></div>
      ${entry.userAgent ? `<div><strong>Navegador:</strong> <span style="font-size: var(--font-size-xs); color: var(--color-text-muted);">${escapeHtml(entry.userAgent)}</span></div>` : ''}
    </div>
    ${
      oldValue
        ? `<div style="margin-bottom: var(--space-3);"><div class="field-label" style="margin-bottom: var(--space-1);">Valor anterior</div><pre class="mono" style="background: var(--color-surface-sunken); padding: var(--space-3); border-radius: var(--radius-md); overflow-x:auto; font-size: var(--font-size-xs);">${oldValue}</pre></div>`
        : ''
    }
    ${
      newValue
        ? `<div><div class="field-label" style="margin-bottom: var(--space-1);">Valor nuevo</div><pre class="mono" style="background: var(--color-surface-sunken); padding: var(--space-3); border-radius: var(--radius-md); overflow-x:auto; font-size: var(--font-size-xs);">${newValue}</pre></div>`
        : ''
    }
    ${!oldValue && !newValue ? '<p class="table-cell-muted">Esta acción no guardó un detalle adicional.</p>' : ''}
  `;

  const modal = openModal({ title: `Registro #${entry.id}`, body, footer: '<button class="btn btn-secondary" type="button" data-cerrar>Cerrar</button>', maxWidth: '560px' });
  modal.footer.querySelector('[data-cerrar]').addEventListener('click', closeModal);
}

const session = requireSession();
if (session) {
  renderShell('auditoria');
  init();
}
