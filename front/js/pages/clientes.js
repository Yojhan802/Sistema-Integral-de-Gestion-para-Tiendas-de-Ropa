import { requireSession } from '../core/auth.js';
import { api, ApiError } from '../core/api.js';
import { renderShell } from '../components/shell.js';
import { renderPagination } from '../components/pagination.js';
import { statusBadge } from '../components/status-badge.js';
import { openClienteForm } from '../components/cliente-form.js';
import { showToast } from '../components/toast.js';
import { debounce } from '../core/debounce.js';
import { escapeHtml } from '../core/format.js';

const DOC_LABELS = { DNI: 'DNI', RUC: 'RUC', CE: 'CE', SIN_DOCUMENTO: '' };

let state = { page: 0, search: '', status: '' };

function init() {
  document.querySelector('#btn-nuevo-cliente').addEventListener('click', () => {
    openClienteForm({ onSaved: () => cargarClientes() });
  });
  document.querySelector('#filter-search').addEventListener('input', debounce((event) => {
    state.search = event.target.value.trim();
    state.page = 0;
    cargarClientes();
  }, 350));
  document.querySelector('#filter-status').addEventListener('change', (event) => {
    state.status = event.target.value;
    state.page = 0;
    cargarClientes();
  });
  cargarClientes();
}

async function cargarClientes() {
  const body = document.querySelector('#clients-body');
  try {
    const page = await api.get('/customers', {
      query: { search: state.search || undefined, status: state.status || undefined, page: state.page, size: 20, sort: 'fullName,asc' },
    });

    body.innerHTML = page.content.length
      ? page.content
          .map(
            (c) => `
        <tr>
          <td class="table-cell-primary">${escapeHtml(c.fullName)}</td>
          <td class="mono">${c.docNumber ? `${DOC_LABELS[c.docType]} ${escapeHtml(c.docNumber)}` : '—'}</td>
          <td>${c.phone ? escapeHtml(c.phone) : '—'}</td>
          <td>${c.email ? escapeHtml(c.email) : '—'}</td>
          <td>${statusBadge(c.status)}</td>
          <td>
            <div class="table-actions">
              <button class="btn btn-ghost btn-sm" type="button" data-action="editar" data-id="${c.id}">Editar</button>
              <button class="btn btn-ghost btn-sm" type="button" data-action="toggle" data-id="${c.id}" data-status="${c.status}">
                ${c.status === 'ACTIVE' ? 'Desactivar' : 'Activar'}
              </button>
            </div>
          </td>
        </tr>
      `
          )
          .join('')
      : `<tr><td colspan="6"><div class="empty-state"><span>No se encontraron clientes.</span></div></td></tr>`;

    body.querySelectorAll('[data-action="editar"]').forEach((btn) => {
      btn.addEventListener('click', async () => {
        const cliente = page.content.find((c) => String(c.id) === btn.dataset.id);
        openClienteForm({ cliente, onSaved: () => cargarClientes() });
      });
    });
    body.querySelectorAll('[data-action="toggle"]').forEach((btn) => {
      btn.addEventListener('click', () => cambiarEstado(btn.dataset.id, btn.dataset.status));
    });

    renderPagination(document.querySelector('#pagination'), page, (p) => { state.page = p; cargarClientes(); });
  } catch (error) {
    body.innerHTML = `<tr><td colspan="6"><div class="empty-state"><span>${error instanceof ApiError ? error.message : 'Error al cargar clientes'}</span></div></td></tr>`;
  }
}

async function cambiarEstado(id, currentStatus) {
  const nuevoEstado = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
  try {
    await api.patch(`/customers/${id}/status`, { status: nuevoEstado });
    showToast({ type: 'success', title: 'Estado actualizado' });
    cargarClientes();
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo actualizar' });
  }
}

const session = requireSession();
if (session) {
  renderShell('clientes');
  init();
}
