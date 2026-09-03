import { requireSession } from '../core/auth.js';
import { api, ApiError, API_ORIGIN } from '../core/api.js';
import { renderShell } from '../components/shell.js';
import { openModal, closeModal } from '../components/modal.js';
import { statusBadge } from '../components/status-badge.js';
import { showToast } from '../components/toast.js';
import { loadCatalog, activeOnly } from '../core/catalog.js';
import { escapeHtml } from '../core/format.js';

let catalog = null;
let activeTab = 'categorias';

const TABS_FIJAS = {
  categorias: {
    label: 'Categoría',
    endpoint: '/categories',
    items: () => catalog.categories,
    headers: ['Nombre', 'Imagen', 'Estado', ''],
    row: (item) => [
      item.name,
      item.imageUrl
        ? `<img src="${API_ORIGIN}${escapeHtml(item.imageUrl)}" alt="" style="width:56px;height:42px;object-fit:cover;border-radius:var(--radius-sm);border:1px solid var(--color-border);" />`
        : '<span class="table-cell-muted">Sin imagen</span>',
      statusBadge(item.status),
    ],
    fields: [{ id: 'name', label: 'Nombre', type: 'text', maxlength: 80 }],
    toRequest: (v) => ({ name: v.name }),
  },
  subcategorias: {
    label: 'Subcategoría',
    endpoint: '/subcategories',
    items: () => catalog.subcategories,
    headers: ['Categoría', 'Subcategoría', 'Estado', ''],
    row: (item) => [item.categoryName, item.name, statusBadge(item.status)],
    fields: [
      { id: 'categoryId', label: 'Categoría', type: 'select', options: () => activeOnly(catalog.categories).map((c) => ({ value: c.id, label: c.name })) },
      { id: 'name', label: 'Nombre', type: 'text', maxlength: 80 },
    ],
    toRequest: (v) => ({ categoryId: Number(v.categoryId), name: v.name }),
  },
  marcas: {
    label: 'Marca',
    endpoint: '/brands',
    items: () => catalog.brands,
    headers: ['Nombre', 'Estado', ''],
    row: (item) => [item.name, statusBadge(item.status)],
    fields: [{ id: 'name', label: 'Nombre', type: 'text', maxlength: 80 }],
    toRequest: (v) => ({ name: v.name }),
  },
};

/** Una pestaña por cada atributo que el negocio tenga configurado (Color, Talla, u otros —
 * ver AtributoService). Los valores se crean en /attributes/{id}/values pero se editan/
 * desactivan en /attributes/values/{id} — endpoint distinto según la operación, por eso esta
 * pestaña define createUrl/updateUrl/statusUrl en vez del único `endpoint` de las fijas. */
function pestanaDeAtributo(attribute) {
  const esSwatch = attribute.inputType === 'SWATCH';
  return {
    label: attribute.name,
    attributeId: attribute.id,
    items: () => (catalog.attributes.find((a) => a.id === attribute.id)?.values ?? []),
    headers: esSwatch ? ['', 'Nombre', 'Código', 'Orden', 'Estado', ''] : ['Nombre', 'Orden', 'Estado', ''],
    row: (item) => esSwatch
      ? [
          `<span style="display:inline-block; width:20px; height:20px; border-radius:var(--radius-sm); border:1px solid var(--color-border); background:${item.hexCode || '#000'};"></span>`,
          item.value,
          item.hexCode || '—',
          item.sortOrder,
          statusBadge(item.status),
        ]
      : [item.value, item.sortOrder, statusBadge(item.status)],
    fields: esSwatch
      ? [
          { id: 'value', label: 'Nombre', type: 'text', maxlength: 40 },
          { id: 'hexCode', label: 'Color', type: 'color', default: '#000000' },
          { id: 'sortOrder', label: 'Orden', type: 'number', default: 0 },
        ]
      : [
          { id: 'value', label: 'Nombre', type: 'text', maxlength: 40 },
          { id: 'sortOrder', label: 'Orden', type: 'number', default: 0 },
        ],
    toRequest: esSwatch
      ? (v) => ({ value: v.value, hexCode: v.hexCode, sortOrder: Number(v.sortOrder) || 0 })
      : (v) => ({ value: v.value, sortOrder: Number(v.sortOrder) || 0 }),
    createUrl: () => `/attributes/${attribute.id}/values`,
    updateUrl: (id) => `/attributes/values/${id}`,
    statusUrl: (id) => `/attributes/values/${id}/status`,
  };
}

function tabActual() {
  if (TABS_FIJAS[activeTab]) return TABS_FIJAS[activeTab];
  const attribute = catalog.attributes.find((a) => String(a.id) === String(activeTab));
  return attribute ? pestanaDeAtributo(attribute) : null;
}

async function init() {
  document.querySelector('#tab-nuevo-atributo').addEventListener('click', abrirFormularioAtributo);
  document.querySelectorAll('#catalogo-tabs .tab[data-tab]').forEach((tab) => {
    tab.addEventListener('click', () => seleccionarTab(tab.dataset.tab));
  });
  document.querySelector('#btn-nuevo-item').addEventListener('click', () => abrirFormulario(null));

  await cargarCatalogo();
  renderTabsDeAtributos();
  renderTabla();
}

function seleccionarTab(tabId) {
  activeTab = tabId;
  document.querySelectorAll('#catalogo-tabs .tab[data-tab]').forEach((t) => t.setAttribute('aria-selected', String(t.dataset.tab === activeTab)));
  document.querySelector('#btn-nuevo-item-label').textContent = `Nueva ${tabActual().label.toLowerCase()}`;
  renderTabla();
}

/** Reconstruye los botones de pestaña de atributos (uno por cada Attribute del negocio),
 * conservando el botón "+ Atributo" siempre al final. */
function renderTabsDeAtributos() {
  const contenedor = document.querySelector('#catalogo-tabs');
  contenedor.querySelectorAll('[data-attribute-tab]').forEach((el) => el.remove());
  const botonNuevo = document.querySelector('#tab-nuevo-atributo');
  catalog.attributes.forEach((attribute) => {
    const boton = document.createElement('button');
    boton.className = 'tab';
    boton.type = 'button';
    boton.setAttribute('role', 'tab');
    boton.setAttribute('data-attribute-tab', '');
    boton.dataset.tab = String(attribute.id);
    boton.setAttribute('aria-selected', String(String(attribute.id) === activeTab));
    boton.textContent = attribute.name;
    boton.addEventListener('click', () => seleccionarTab(String(attribute.id)));
    contenedor.insertBefore(boton, botonNuevo);
  });
}

async function cargarCatalogo() {
  catalog = await loadCatalog({ force: true });
}

function renderTabla() {
  const config = tabActual();
  const head = document.querySelector('#catalogo-head');
  const body = document.querySelector('#catalogo-body');

  head.innerHTML = `<tr>${config.headers.map((h) => `<th>${h}</th>`).join('')}</tr>`;

  const items = config.items();
  body.innerHTML = items.length
    ? items
        .map(
          (item) => `
      <tr>
        ${config.row(item).map((cell) => `<td>${cell}</td>`).join('')}
        <td>
          <div class="table-actions">
            ${activeTab === 'categorias' ? '<button class="btn btn-ghost btn-sm" type="button" data-action="category-image">Imagen</button>' : ''}
            <button class="btn btn-ghost btn-sm" type="button" data-editar="${item.id}">Editar</button>
            <button class="btn btn-ghost btn-sm" type="button" data-toggle="${item.id}" data-status="${item.status}">
              ${item.status === 'ACTIVE' ? 'Desactivar' : 'Activar'}
            </button>
          </div>
        </td>
      </tr>
    `
        )
        .join('')
    : `<tr><td colspan="${config.headers.length + 1}"><div class="empty-state"><span>Todavía no hay ${config.label.toLowerCase()}s registradas.</span></div></td></tr>`;

  body.querySelectorAll('[data-editar]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const item = items.find((i) => String(i.id) === btn.dataset.editar);
      abrirFormulario(item);
    });
  });
  body.querySelectorAll('[data-toggle]').forEach((btn) => {
    btn.addEventListener('click', () => cambiarEstado(btn.dataset.toggle, btn.dataset.status));
  });
  body.querySelectorAll('[data-action="category-image"]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const item = items.find((i) => String(i.id) === btn.closest('tr')?.dataset.id);
      if (item) abrirImagenCategoria(item);
    });
  });
}

function abrirImagenCategoria(item) {
  const src = item.imageUrl ? `${API_ORIGIN}${item.imageUrl}` : '';
  const modal = openModal({
    title: `Imagen de ${item.name}`,
    maxWidth: '460px',
    body: `
      <form id="category-image-form" novalidate>
        <div class="alert alert-danger" id="category-image-error" role="alert" hidden>
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
          <span class="alert-message"></span>
        </div>
        <div class="field">
          <label class="field-label" for="category-image-file">Imagen de categoría</label>
          <input class="input" id="category-image-file" type="file" accept="image/png,image/jpeg,image/webp,image/svg+xml" required />
          <span class="field-hint">Usa una imagen horizontal o cuadrada.</span>
        </div>
        <div id="category-image-preview" style="margin-top:16px; min-height:120px; display:grid; place-items:center; border:1px dashed var(--color-border); border-radius:var(--radius-md); overflow:hidden;">
          ${src ? `<img src="${escapeHtml(src)}" alt="Vista previa de ${escapeHtml(item.name)}" style="display:block; width:100%; max-height:220px; object-fit:cover;" />` : '<span class="table-cell-muted">Todavía no hay imagen</span>'}
        </div>
      </form>
    `,
    footer: `
      ${item.imageUrl ? '<button class="btn btn-ghost" type="button" data-delete-category-image>Quitar imagen</button>' : ''}
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-primary" type="submit" form="category-image-form">Guardar imagen</button>
    `,
  });

  const fileInput = modal.body.querySelector('#category-image-file');
  const preview = modal.body.querySelector('#category-image-preview');
  fileInput.addEventListener('change', () => {
    const file = fileInput.files?.[0];
    if (!file) return;
    preview.innerHTML = '';
    const image = document.createElement('img');
    image.alt = `Vista previa de ${item.name}`;
    image.style.cssText = 'display:block;width:100%;max-height:220px;object-fit:cover;';
    image.src = URL.createObjectURL(file);
    preview.appendChild(image);
  });
  modal.footer.querySelector('[data-cancel]').addEventListener('click', () => closeModal());
  modal.footer.querySelector('[data-delete-category-image]')?.addEventListener('click', async () => {
    try {
      await api.delete(`/categories/${item.id}/image`);
      closeModal();
      showToast({ type: 'success', title: 'Imagen eliminada' });
      await cargarCatalogo();
      renderTabla();
    } catch (error) {
      mostrarErrorImagenCategoria(modal, error);
    }
  });
  modal.body.querySelector('#category-image-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const file = fileInput.files?.[0];
    if (!file) return;
    try {
      const formData = new FormData();
      formData.append('file', file);
      await api.post(`/categories/${item.id}/image`, formData);
      closeModal();
      showToast({ type: 'success', title: 'Imagen de categoría actualizada' });
      await cargarCatalogo();
      renderTabla();
    } catch (error) {
      mostrarErrorImagenCategoria(modal, error);
    }
  });
}

function mostrarErrorImagenCategoria(modal, error) {
  const alert = modal.body.querySelector('#category-image-error');
  alert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudo actualizar la imagen';
  alert.hidden = false;
}

function abrirFormulario(item) {
  const config = tabActual();
  const esEdicion = Boolean(item);

  const modal = openModal({
    title: esEdicion ? `Editar ${config.label.toLowerCase()}` : `Nueva ${config.label.toLowerCase()}`,
    maxWidth: '420px',
    body: `
      <form id="cat-form" novalidate>
        <div class="alert alert-danger" id="cat-form-error" role="alert" hidden>
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
          <span class="alert-message"></span>
        </div>
        <div class="form-grid">
          ${config.fields.map((field) => campoHtml(field, item)).join('')}
        </div>
      </form>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-primary" type="submit" form="cat-form">${esEdicion ? 'Guardar cambios' : 'Crear'}</button>
    `,
  });

  modal.footer.querySelector('[data-cancel]').addEventListener('click', () => closeModal());
  modal.body.querySelector('#cat-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = modal.body.querySelector('#cat-form-error');
    const values = {};
    config.fields.forEach((field) => {
      values[field.id] = modal.body.querySelector(`#cf-${field.id}`).value.trim();
    });

    try {
      if (esEdicion) {
        const url = config.updateUrl ? config.updateUrl(item.id) : `${config.endpoint}/${item.id}`;
        await api.put(url, config.toRequest(values));
      } else {
        const url = config.createUrl ? config.createUrl() : config.endpoint;
        await api.post(url, config.toRequest(values));
      }
      closeModal();
      showToast({ type: 'success', title: esEdicion ? `${config.label} actualizada` : `${config.label} creada` });
      await cargarCatalogo();
      renderTabsDeAtributos();
      renderTabla();
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudo guardar';
      errorAlert.hidden = false;
    }
  });
}

function campoHtml(field, item) {
  const valorActual = item ? item[field.id] : (field.default ?? '');
  if (field.type === 'select') {
    const opciones = field.options();
    return `
      <div class="field field-span-2">
        <label class="field-label" for="cf-${field.id}">${field.label}</label>
        <select class="select" id="cf-${field.id}" required>
          ${opciones.map((o) => `<option value="${o.value}" ${String(o.value) === String(valorActual) ? 'selected' : ''}>${o.label}</option>`).join('')}
        </select>
      </div>
    `;
  }
  if (field.type === 'color') {
    return `
      <div class="field field-span-2">
        <label class="field-label" for="cf-${field.id}">${field.label}</label>
        <input class="input" type="color" id="cf-${field.id}" value="${valorActual || '#000000'}" style="height:44px; padding:4px;" required />
      </div>
    `;
  }
  return `
    <div class="field field-span-2">
      <label class="field-label" for="cf-${field.id}">${field.label}</label>
      <input class="input" type="${field.type}" id="cf-${field.id}" maxlength="${field.maxlength ?? ''}" value="${valorActual}" required />
    </div>
  `;
}

/** Alta de un nuevo tipo de atributo (ej. "Voltaje") — distinto de dar de alta un valor
 * dentro de un atributo ya existente (eso lo maneja abrirFormulario de la pestaña normal). */
function abrirFormularioAtributo() {
  const modal = openModal({
    title: 'Nuevo atributo',
    maxWidth: '420px',
    body: `
      <form id="attr-form" novalidate>
        <div class="alert alert-danger" id="attr-form-error" role="alert" hidden>
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
          <span class="alert-message"></span>
        </div>
        <div class="form-grid">
          <div class="field field-span-2">
            <label class="field-label" for="af-name">Nombre</label>
            <input class="input" type="text" id="af-name" maxlength="40" placeholder="Ej. Voltaje" required />
          </div>
          <div class="field field-span-2">
            <label class="field-label" for="af-inputType">Tipo</label>
            <select class="select" id="af-inputType" required>
              <option value="LIST">Lista (texto simple, ej. Talla)</option>
              <option value="SWATCH">Muestra de color (ej. Color)</option>
            </select>
          </div>
        </div>
      </form>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-primary" type="submit" form="attr-form">Crear</button>
    `,
  });

  modal.footer.querySelector('[data-cancel]').addEventListener('click', () => closeModal());
  modal.body.querySelector('#attr-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = modal.body.querySelector('#attr-form-error');
    const name = modal.body.querySelector('#af-name').value.trim();
    const inputType = modal.body.querySelector('#af-inputType').value;

    try {
      const creado = await api.post('/attributes', { name, inputType });
      closeModal();
      showToast({ type: 'success', title: 'Atributo creado' });
      await cargarCatalogo();
      renderTabsDeAtributos();
      seleccionarTab(String(creado.id));
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudo crear el atributo';
      errorAlert.hidden = false;
    }
  });
}

async function cambiarEstado(id, currentStatus) {
  const config = tabActual();
  const nuevoEstado = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
  try {
    const url = config.statusUrl ? config.statusUrl(id) : `${config.endpoint}/${id}/status`;
    await api.patch(url, { status: nuevoEstado });
    showToast({ type: 'success', title: 'Estado actualizado' });
    await cargarCatalogo();
    renderTabla();
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo actualizar' });
  }
}

const session = requireSession();
if (session) {
  renderShell('productos');
  init();
}
