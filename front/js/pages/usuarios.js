import { requireSession, getSession, hasPermission } from '../core/auth.js';
import { api, ApiError } from '../core/api.js';
import { renderShell } from '../components/shell.js';
import { renderPagination } from '../components/pagination.js';
import { statusBadge } from '../components/status-badge.js';
import { openUsuarioForm } from '../components/usuario-form.js';
import { confirmAction } from '../components/confirm.js';
import { openModal, closeModal } from '../components/modal.js';
import { showToast } from '../components/toast.js';
import { debounce } from '../core/debounce.js';
import { formatDateTime, escapeHtml } from '../core/format.js';

const ESTADO_SIGUIENTE = {
  ACTIVE: { status: 'INACTIVE', label: 'Desactivar' },
  INACTIVE: { status: 'ACTIVE', label: 'Activar' },
  BLOCKED: { status: 'ACTIVE', label: 'Desbloquear' },
};

let state = { page: 0, search: '', status: '' };

function init() {
  document.querySelector('#btn-nuevo-usuario').addEventListener('click', () => {
    openUsuarioForm({ onSaved: () => cargarUsuarios() });
  });
  document.querySelector('#filter-search').addEventListener(
    'input',
    debounce((event) => {
      state.search = event.target.value.trim();
      state.page = 0;
      cargarUsuarios();
    }, 350)
  );
  document.querySelector('#filter-status').addEventListener('change', (event) => {
    state.status = event.target.value;
    state.page = 0;
    cargarUsuarios();
  });
  cargarUsuarios();
}

async function cargarUsuarios() {
  const body = document.querySelector('#users-body');
  const currentUserId = getSession()?.user?.id;
  try {
    const page = await api.get('/users', {
      query: { search: state.search || undefined, status: state.status || undefined, page: state.page, size: 20, sort: 'fullName,asc' },
    });

    body.innerHTML = page.content.length
      ? page.content
          .map((u) => {
            const siguiente = ESTADO_SIGUIENTE[u.status] ?? ESTADO_SIGUIENTE.ACTIVE;
            const esUsuarioActual = u.id === currentUserId;
            return `
        <tr>
          <td>
            <div class="table-cell-primary">${escapeHtml(u.fullName)}</div>
            <div class="table-cell-muted mono">${escapeHtml(u.username)}</div>
          </td>
          <td>${u.roles.map((r) => `<span class="badge badge-neutral">${escapeHtml(r.name)}</span>`).join(' ') || '—'}</td>
          <td class="table-cell-muted">${formatDateTime(u.lastLoginAt)}</td>
          <td>${statusBadge(u.status)}</td>
          <td>
            <div class="table-actions">
              <button class="btn btn-ghost btn-sm" type="button" data-action="editar" data-id="${u.id}">Editar</button>
              ${hasPermission('USUARIOS_RESETEAR_CONTRASENA') ? `<button class="btn btn-ghost btn-sm" type="button" data-action="reset" data-id="${u.id}">Resetear contraseña</button>` : ''}
              <button class="btn btn-ghost btn-sm" type="button" data-action="toggle" data-id="${u.id}" data-status="${u.status}" ${esUsuarioActual ? 'disabled title="No puedes cambiar tu propio estado"' : ''}>
                ${siguiente.label}
              </button>
            </div>
          </td>
        </tr>
      `;
          })
          .join('')
      : `<tr><td colspan="5"><div class="empty-state"><span>No se encontraron usuarios.</span></div></td></tr>`;

    body.querySelectorAll('[data-action="editar"]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const usuario = page.content.find((u) => String(u.id) === btn.dataset.id);
        openUsuarioForm({ usuario, onSaved: () => cargarUsuarios() });
      });
    });
    body.querySelectorAll('[data-action="toggle"]').forEach((btn) => {
      btn.addEventListener('click', () => cambiarEstado(btn.dataset.id, btn.dataset.status));
    });
    body.querySelectorAll('[data-action="reset"]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const usuario = page.content.find((u) => String(u.id) === btn.dataset.id);
        resetearPassword(usuario);
      });
    });

    renderPagination(document.querySelector('#pagination'), page, (p) => {
      state.page = p;
      cargarUsuarios();
    });
  } catch (error) {
    body.innerHTML = `<tr><td colspan="5"><div class="empty-state"><span>${error instanceof ApiError ? error.message : 'Error al cargar usuarios'}</span></div></td></tr>`;
  }
}

async function cambiarEstado(id, currentStatus) {
  const siguiente = ESTADO_SIGUIENTE[currentStatus] ?? ESTADO_SIGUIENTE.ACTIVE;
  const confirmado = await confirmAction({
    title: `${siguiente.label} usuario`,
    message: `¿Seguro que deseas ${siguiente.label.toLowerCase()} este usuario?`,
    confirmLabel: siguiente.label,
    danger: siguiente.status !== 'ACTIVE',
  });
  if (!confirmado) return;

  try {
    await api.patch(`/users/${id}/status`, { status: siguiente.status });
    showToast({ type: 'success', title: 'Estado actualizado' });
    cargarUsuarios();
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo actualizar' });
  }
}

async function resetearPassword(usuario) {
  const confirmado = await confirmAction({
    title: 'Resetear contraseña',
    message: `Se generará una contraseña temporal para <strong>${escapeHtml(usuario.fullName)}</strong>, que deberá cambiar en su próximo ingreso. ¿Continuar?`,
    confirmLabel: 'Generar contraseña',
    danger: false,
  });
  if (!confirmado) return;

  try {
    const { temporaryPassword } = await api.post(`/users/${usuario.id}/reset-password`, {});
    mostrarPasswordTemporal(usuario, temporaryPassword);
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo resetear la contraseña' });
  }
}

function mostrarPasswordTemporal(usuario, temporaryPassword) {
  const modal = openModal({
    title: 'Contraseña temporal generada',
    subtitle: usuario.username,
    maxWidth: '440px',
    body: `
      <p style="color: var(--color-text-secondary); margin-bottom: var(--space-3);">
        Comparte esta contraseña con <strong>${escapeHtml(usuario.fullName)}</strong> por un canal seguro. No volverá a mostrarse.
      </p>
      <div class="mono" style="font-size: var(--font-size-lg); font-weight:700; text-align:center; padding: var(--space-4); background: var(--color-surface-muted); border-radius: var(--radius-md); letter-spacing: 1px;">
        ${temporaryPassword}
      </div>
    `,
    footer: `<button class="btn btn-primary" type="button" data-close>Entendido</button>`,
  });
  modal.footer.querySelector('[data-close]').addEventListener('click', () => closeModal());
}

const session = requireSession();
if (session) {
  renderShell('usuarios');
  init();
}
