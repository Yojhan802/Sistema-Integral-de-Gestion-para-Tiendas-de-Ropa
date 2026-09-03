import { requireSession, hasPermission } from '../core/auth.js';
import { api, ApiError } from '../core/api.js';
import { renderShell } from '../components/shell.js';
import { openModal, closeModal } from '../components/modal.js';
import { showToast } from '../components/toast.js';
import { debounce } from '../core/debounce.js';
import { escapeHtml } from '../core/format.js';

const PLAN_LABELS = { STARTER: 'Starter', PROFESIONAL: 'Profesional', ECOMMERCE: 'Ecommerce', IA: 'IA' };
const STATUS_LABELS = { ACTIVA: 'Activa', SUSPENDIDA: 'Suspendida' };
const STATUS_CLASSES = { ACTIVA: 'badge-success', SUSPENDIDA: 'badge-danger' };
let state = { search: '', status: '' };

function badge(value, labels, classes = {}) {
  return `<span class="badge ${classes[value] || 'badge-neutral'}">${labels[value] || value}</span>`;
}

async function cargarEmpresas() {
  const body = document.querySelector('#tenants-body');
  try {
    const tenants = await api.get('/platform/tenants', { query: { search: state.search || undefined, status: state.status || undefined } });
    body.innerHTML = tenants.length
      ? tenants.map((tenant) => `
        <tr>
          <td><div class="table-cell-primary">${escapeHtml(tenant.name)}</div><div class="table-cell-muted mono">${escapeHtml(tenant.ruc || 'Sin RUC')}</div></td>
          <td><div class="table-cell-primary mono">${escapeHtml(tenant.slug)}</div><div class="table-cell-muted">${escapeHtml(tenant.ownerUsername || 'Sin administrador')}</div></td>
          <td>${badge(tenant.plan, PLAN_LABELS)}</td>
          <td>${badge(tenant.subscriptionStatus, STATUS_LABELS, STATUS_CLASSES)}${tenant.nextPaymentDue ? `<div class="table-cell-muted">${escapeHtml(tenant.nextPaymentDue)}</div>` : ''}</td>
          <td>${tenant.activeUsers}</td>
          <td><button class="btn btn-ghost btn-sm" type="button" data-edit="${tenant.id}">Editar</button></td>
        </tr>`).join('')
      : '<tr><td colspan="6"><div class="empty-state"><span>No se encontraron empresas.</span></div></td></tr>';
    body.querySelectorAll('[data-edit]').forEach((button) => {
      button.addEventListener('click', () => editarEmpresa(tenants.find((tenant) => String(tenant.id) === button.dataset.edit)));
    });
  } catch (error) {
    body.innerHTML = `<tr><td colspan="6"><div class="empty-state"><span>${error instanceof ApiError ? escapeHtml(error.message) : 'No se pudieron cargar las empresas'}</span></div></td></tr>`;
  }
}

function abrirFormulario(tenant = null) {
  const esEdicion = Boolean(tenant);
  const modal = openModal({
    title: esEdicion ? 'Editar empresa' : 'Nueva empresa',
    subtitle: esEdicion ? tenant.slug : 'Se creará un tenant aislado con su administrador inicial',
    maxWidth: '680px',
    body: `
      <form id="tenant-form" novalidate>
        <div class="alert alert-danger" id="tenant-form-error" role="alert" hidden><span class="alert-message"></span></div>
        <div class="form-grid">
          <div class="field field-span-2"><label class="field-label" for="tenant-name">Razón social / nombre</label><input class="input" id="tenant-name" maxlength="150" required value="${escapeHtml(tenant?.name || '')}" /></div>
          ${esEdicion ? '' : '<div class="field field-span-2"><label class="field-label" for="tenant-slug">Subdominio</label><input class="input mono" id="tenant-slug" maxlength="63" required placeholder="mi-tienda" /><span class="field-hint">Se usará como mi-tienda.tudominio.com</span></div>'}
          <div class="field"><label class="field-label" for="tenant-ruc">RUC</label><input class="input" id="tenant-ruc" maxlength="15" value="${escapeHtml(tenant?.ruc || '')}" /></div>
          <div class="field"><label class="field-label" for="tenant-email">Correo de empresa</label><input class="input" type="email" id="tenant-email" maxlength="120" value="${escapeHtml(tenant?.email || '')}" /></div>
          <div class="field field-span-2"><label class="field-label" for="tenant-address">Dirección</label><input class="input" id="tenant-address" maxlength="255" value="${escapeHtml(tenant?.address || '')}" /></div>
          <div class="field"><label class="field-label" for="tenant-phone">Teléfono</label><input class="input" id="tenant-phone" maxlength="20" value="${escapeHtml(tenant?.phone || '')}" /></div>
          <div class="field"><label class="field-label" for="tenant-vertical">Rubro</label><select class="select" id="tenant-vertical"><option value="CLOTHING">Ropa</option><option value="GENERAL">General / otro</option></select></div>
          <div class="field"><label class="field-label" for="tenant-plan">Plan</label><select class="select" id="tenant-plan"><option value="STARTER">Starter</option><option value="PROFESIONAL">Profesional</option><option value="ECOMMERCE">Ecommerce</option><option value="IA">IA</option></select></div>
          <div class="field"><label class="field-label" for="tenant-due">Próximo pago</label><input class="input" type="date" id="tenant-due" value="${escapeHtml(tenant?.nextPaymentDue || '')}" /></div>
          <div class="field"><label class="field-label" for="tenant-status">Suscripción</label><select class="select" id="tenant-status"><option value="ACTIVA">Activa</option><option value="SUSPENDIDA">Suspendida</option></select></div>
          ${esEdicion ? '' : '<div class="field field-span-2" style="padding-top:var(--space-3); border-top:1px solid var(--color-border);"><h4 style="margin:0;">Administrador inicial</h4></div><div class="field"><label class="field-label" for="owner-username">Usuario</label><input class="input" id="owner-username" maxlength="50" required placeholder="admin.tienda" /></div><div class="field"><label class="field-label" for="owner-email">Correo del administrador</label><input class="input" type="email" id="owner-email" maxlength="120" /></div><div class="field field-span-2"><label class="field-label" for="owner-name">Nombre completo</label><input class="input" id="owner-name" maxlength="120" required /></div>'}
        </div>
        <div style="display:flex; justify-content:flex-end; gap:var(--space-3); margin-top:var(--space-5);"><button class="btn btn-secondary" type="button" id="tenant-cancel">Cancelar</button><button class="btn btn-primary" type="submit">${esEdicion ? 'Guardar cambios' : 'Crear empresa'}</button></div>
      </form>`,
  });

  document.querySelector('#tenant-vertical').value = tenant?.businessVertical || 'CLOTHING';
  document.querySelector('#tenant-plan').value = tenant?.plan || 'STARTER';
  document.querySelector('#tenant-status').value = tenant?.subscriptionStatus || 'ACTIVA';
  document.querySelector('#tenant-cancel').addEventListener('click', () => closeModal());
  document.querySelector('#tenant-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const error = document.querySelector('#tenant-form-error');
    error.hidden = true;
    const value = (id) => document.querySelector(id)?.value.trim() || null;
    const payload = {
      name: value('#tenant-name'), ruc: value('#tenant-ruc'), email: value('#tenant-email'),
      address: value('#tenant-address'), phone: value('#tenant-phone'), businessVertical: value('#tenant-vertical'), plan: value('#tenant-plan'),
      nextPaymentDue: value('#tenant-due'), subscriptionStatus: value('#tenant-status'),
    };
    if (!esEdicion) Object.assign(payload, { slug: value('#tenant-slug'), ownerUsername: value('#owner-username'), ownerEmail: value('#owner-email'), ownerFullName: value('#owner-name') });
    try {
      const response = esEdicion ? await api.put(`/platform/tenants/${tenant.id}`, payload) : await api.post('/platform/tenants', payload);
      closeModal();
      cargarEmpresas();
      if (esEdicion) showToast({ type: 'success', title: 'Empresa actualizada' });
      else mostrarCredencialesIniciales(response);
    } catch (requestError) {
      error.querySelector('.alert-message').textContent = requestError instanceof ApiError ? requestError.message : 'No se pudo guardar la empresa';
      error.hidden = false;
    }
  });
}

function mostrarCredencialesIniciales(response) {
  const modal = openModal({
    title: 'Empresa creada', subtitle: response.tenant.name, maxWidth: '520px',
    body: `<div class="alert alert-success"><span>Guarda estas credenciales y entrégalas al administrador por un canal seguro. La contraseña temporal deberá cambiarse al ingresar.</span></div><div style="display:grid; gap:var(--space-3); margin-top:var(--space-4);"><div><span class="table-cell-muted">Subdominio</span><div class="mono">${escapeHtml(response.tenant.slug)}</div></div><div><span class="table-cell-muted">Usuario</span><div class="mono">${escapeHtml(response.ownerUsername)}</div></div><div><span class="table-cell-muted">Contraseña temporal</span><div class="mono" style="font-size:var(--font-size-lg); font-weight:700; padding:var(--space-3); background:var(--color-surface-muted); border-radius:var(--radius-md);">${escapeHtml(response.temporaryPassword)}</div></div></div>`,
    footer: '<button class="btn btn-primary" type="button" data-close>Entendido</button>',
  });
  modal.footer.querySelector('[data-close]').addEventListener('click', () => closeModal());
}

function editarEmpresa(tenant) { if (tenant) abrirFormulario(tenant); }

const session = requireSession();
if (session && hasPermission('PLATAFORMA_EMPRESAS_GESTIONAR')) {
  renderShell('empresas');
  document.querySelector('#btn-nueva-empresa').addEventListener('click', () => abrirFormulario());
  document.querySelector('#filter-search').addEventListener('input', debounce((event) => { state.search = event.target.value; cargarEmpresas(); }, 300));
  document.querySelector('#filter-status').addEventListener('change', (event) => { state.status = event.target.value; cargarEmpresas(); });
  cargarEmpresas();
}
