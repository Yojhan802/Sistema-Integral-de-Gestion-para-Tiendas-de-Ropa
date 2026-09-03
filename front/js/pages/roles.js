import { requireSession } from '../core/auth.js';
import { api, ApiError } from '../core/api.js';
import { renderShell } from '../components/shell.js';
import { openModal, closeModal } from '../components/modal.js';
import { showToast } from '../components/toast.js';
import { escapeHtml } from '../core/format.js';

let roles = [];
let permisos = [];
let selectedRoleId = null;

function init() {
  document.querySelector('#btn-nuevo-rol').addEventListener('click', abrirFormularioNuevoRol);
  cargarTodo();
}

async function cargarTodo() {
  try {
    [roles, permisos] = await Promise.all([api.get('/roles'), api.get('/permissions')]);
    renderListaRoles();
    if (roles.length > 0) {
      selectedRoleId = selectedRoleId ?? roles[0].id;
      renderDetalleRol();
    }
  } catch (error) {
    document.querySelector('#roles-list').innerHTML = `<div class="empty-state"><span>${error instanceof ApiError ? error.message : 'Error al cargar roles'}</span></div>`;
  }
}

function renderListaRoles() {
  const container = document.querySelector('#roles-list');
  container.innerHTML = roles
    .map(
      (r) => `
      <button type="button" class="nav-item" data-role-id="${r.id}" style="width:100%; justify-content:space-between; ${r.id === selectedRoleId ? 'background: var(--color-primary-bg); color: var(--color-primary);' : ''}">
        <span>${escapeHtml(r.name)}</span>
        ${r.isSystem ? '<span class="badge badge-neutral">Sistema</span>' : ''}
      </button>
    `
    )
    .join('');

  container.querySelectorAll('[data-role-id]').forEach((btn) => {
    btn.addEventListener('click', () => {
      selectedRoleId = Number(btn.dataset.roleId);
      renderListaRoles();
      renderDetalleRol();
    });
  });
}

function renderDetalleRol() {
  const detail = document.querySelector('#role-detail');
  const rol = roles.find((r) => r.id === selectedRoleId);
  if (!rol) {
    detail.innerHTML = `<div class="empty-state"><span>Selecciona un rol para ver y editar sus permisos.</span></div>`;
    return;
  }

  const permisosPorModulo = new Map();
  permisos.forEach((p) => {
    if (!permisosPorModulo.has(p.module)) permisosPorModulo.set(p.module, []);
    permisosPorModulo.get(p.module).push(p);
  });
  const asignados = new Set(rol.permisos.map((p) => p.id));

  detail.innerHTML = `
    <div style="display:flex; justify-content:space-between; align-items:flex-start; gap: var(--space-4); margin-bottom: var(--space-5);">
      <div>
        <h2 style="margin:0;">${escapeHtml(rol.name)}</h2>
        <p class="mono" style="color: var(--color-text-muted); margin: var(--space-1) 0 0;">${escapeHtml(rol.code)}</p>
        ${rol.description ? `<p style="color: var(--color-text-secondary); margin: var(--space-2) 0 0;">${escapeHtml(rol.description)}</p>` : ''}
        <p class="table-cell-muted" style="margin: var(--space-2) 0 0;" title="Al crear usuarios, este rol solo puede asignar roles con techo de asignación igual o menor">
          Techo de asignación: <strong>${rol.hierarchyLevel}</strong>
        </p>
      </div>
      <button class="btn btn-secondary btn-sm" type="button" id="btn-editar-rol">Editar datos</button>
    </div>

    <form id="permisos-form">
      ${[...permisosPorModulo.entries()]
        .map(
          ([modulo, items]) => `
        <fieldset style="border:none; margin:0 0 var(--space-5);">
          <legend style="font-weight:600; margin-bottom: var(--space-2);">${modulo}</legend>
          <div style="display:grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: var(--space-2);">
            ${items
              .map(
                (p) => `
              <label class="checkbox-field">
                <input type="checkbox" name="permiso" value="${p.id}" ${asignados.has(p.id) ? 'checked' : ''} ${p.code === 'USUARIOS_CAMBIAR_CONTRASENA' ? 'disabled title="Habilitado para todos los roles"' : ''} ${p.code === 'USUARIOS_RESETEAR_CONTRASENA' ? 'disabled title="Reservado al rol Administrador"' : ''} />
                ${p.description || p.code}
              </label>
            `
              )
              .join('')}
          </div>
        </fieldset>
      `
        )
        .join('')}

      <div style="display:flex; justify-content:flex-end; gap: var(--space-3); padding-top: var(--space-3); border-top: 1px solid var(--color-border);">
        <button class="btn btn-primary" type="submit">Guardar permisos</button>
      </div>
    </form>
  `;

  document.querySelector('#btn-editar-rol').addEventListener('click', () => abrirFormularioEditarRol(rol));
  document.querySelector('#permisos-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const permissionIds = [...detail.querySelectorAll('input[name="permiso"]:checked')].map((el) => Number(el.value));
    try {
      const actualizado = await api.put(`/roles/${rol.id}/permissions`, { permissionIds });
      roles = roles.map((r) => (r.id === actualizado.id ? actualizado : r));
      showToast({ type: 'success', title: 'Permisos actualizados', message: rol.name });
      renderDetalleRol();
    } catch (error) {
      showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudieron guardar los permisos' });
    }
  });
}

function abrirFormularioNuevoRol() {
  const modal = openModal({
    title: 'Nuevo rol',
    maxWidth: '480px',
    body: `
      <form id="rol-form" novalidate>
        <div class="alert alert-danger" id="rol-form-error" role="alert" hidden>
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
          <span class="alert-message"></span>
        </div>
        <div class="form-grid">
          <div class="field field-span-2">
            <label class="field-label" for="rf-code">Código</label>
            <input class="input mono" id="rf-code" required maxlength="30" pattern="[A-Z_]+" placeholder="EJ: SUPERVISOR" style="text-transform:uppercase;" />
          </div>
          <div class="field field-span-2">
            <label class="field-label" for="rf-name">Nombre</label>
            <input class="input" id="rf-name" required maxlength="60" />
          </div>
          <div class="field field-span-2">
            <label class="field-label" for="rf-description">Descripción</label>
            <textarea class="input" id="rf-description" maxlength="255" rows="2"></textarea>
          </div>
          <div class="field field-span-2">
            <label class="field-label" for="rf-hierarchy">Techo de asignación (0-100)</label>
            <input class="input" type="number" id="rf-hierarchy" min="0" max="100" value="0" />
            <span class="field-hint">Al crear usuarios, este rol solo podrá asignar roles con techo igual o menor. Administrador = 100, Supervisor = 50, Vendedor/Almacenero = 10.</span>
          </div>
        </div>
      </form>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-primary" type="submit" form="rol-form">Crear rol</button>
    `,
  });

  modal.footer.querySelector('[data-cancel]').addEventListener('click', () => closeModal());
  modal.body.querySelector('#rf-code').addEventListener('input', (e) => {
    e.target.value = e.target.value.toUpperCase().replace(/[^A-Z_]/g, '');
  });
  modal.body.querySelector('#rol-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = modal.body.querySelector('#rol-form-error');
    try {
      const creado = await api.post('/roles', {
        code: modal.body.querySelector('#rf-code').value.trim(),
        name: modal.body.querySelector('#rf-name').value.trim(),
        description: modal.body.querySelector('#rf-description').value.trim() || null,
        hierarchyLevel: Number(modal.body.querySelector('#rf-hierarchy').value),
      });
      closeModal();
      roles = [...roles, creado];
      selectedRoleId = creado.id;
      showToast({ type: 'success', title: 'Rol creado', message: creado.name });
      renderListaRoles();
      renderDetalleRol();
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudo crear el rol';
      errorAlert.hidden = false;
    }
  });
}

function abrirFormularioEditarRol(rol) {
  const modal = openModal({
    title: 'Editar rol',
    subtitle: rol.code,
    maxWidth: '480px',
    body: `
      <form id="rol-edit-form" novalidate>
        <div class="alert alert-danger" id="rol-edit-error" role="alert" hidden>
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
          <span class="alert-message"></span>
        </div>
        <div class="form-grid">
          <div class="field field-span-2">
            <label class="field-label" for="rf-edit-name">Nombre</label>
            <input class="input" id="rf-edit-name" required maxlength="60" value="${rol.name}" />
          </div>
          <div class="field field-span-2">
            <label class="field-label" for="rf-edit-description">Descripción</label>
            <textarea class="input" id="rf-edit-description" maxlength="255" rows="2">${rol.description ?? ''}</textarea>
          </div>
          <div class="field field-span-2">
            <label class="field-label" for="rf-edit-hierarchy">Techo de asignación (0-100)</label>
            <input class="input" type="number" id="rf-edit-hierarchy" min="0" max="100" value="${rol.hierarchyLevel}" />
            <span class="field-hint">Al crear usuarios, este rol solo podrá asignar roles con techo igual o menor.</span>
          </div>
        </div>
      </form>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-primary" type="submit" form="rol-edit-form">Guardar cambios</button>
    `,
  });

  modal.footer.querySelector('[data-cancel]').addEventListener('click', () => closeModal());
  modal.body.querySelector('#rol-edit-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = modal.body.querySelector('#rol-edit-error');
    try {
      const actualizado = await api.put(`/roles/${rol.id}`, {
        name: modal.body.querySelector('#rf-edit-name').value.trim(),
        description: modal.body.querySelector('#rf-edit-description').value.trim() || null,
        hierarchyLevel: Number(modal.body.querySelector('#rf-edit-hierarchy').value),
      });
      closeModal();
      roles = roles.map((r) => (r.id === actualizado.id ? actualizado : r));
      showToast({ type: 'success', title: 'Rol actualizado', message: actualizado.name });
      renderListaRoles();
      renderDetalleRol();
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudo actualizar el rol';
      errorAlert.hidden = false;
    }
  });
}

const session = requireSession();
if (session) {
  renderShell('usuarios');
  init();
}
