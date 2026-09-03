import { requireSession } from '../core/auth.js';
import { api, ApiError, API_ORIGIN } from '../core/api.js';
import { hasPermission } from '../core/auth.js';
import { renderShell } from '../components/shell.js';
import { loadCatalog, activeOnly } from '../core/catalog.js';
import { openModal, closeModal } from '../components/modal.js';
import { confirmAction } from '../components/confirm.js';
import { openProductoForm } from '../components/producto-form.js';
import { statusBadge } from '../components/status-badge.js';
import { renderEan13Svg } from '../components/barcode.js';
import { imprimirEtiquetas } from '../components/label.js';
import { showToast } from '../components/toast.js';
import { formatCurrency, formatDateLong, escapeHtml } from '../core/format.js';

const session = requireSession();
const productId = new URLSearchParams(window.location.search).get('id');

if (session && productId) {
  renderShell('productos');
  init();
} else if (session) {
  window.location.href = 'productos.html';
}

let catalog = null;
let producto = null;
let imagenes = [];
const puedeEditarGaleria = hasPermission('PRODUCTOS_EDITAR');

async function init() {
  const [catalogData] = await Promise.all([loadCatalog(), cargarProducto()]);
  catalog = catalogData;

  const agregarImagenesLabel = document.querySelector('#product-gallery-add-label');
  const agregarImagenesInput = document.querySelector('#product-gallery-input');
  if (!puedeEditarGaleria) {
    agregarImagenesLabel?.remove();
    agregarImagenesInput?.remove();
  } else {
    agregarImagenesInput?.addEventListener('change', (event) => subirImagenesGaleria(event.target.files));
  }
  if (producto) await cargarGaleria();

  document.querySelector('#btn-nueva-variante').addEventListener('click', abrirModalNuevaVariante);
  document.querySelector('#btn-generar-matriz').addEventListener('click', abrirModalMatriz);
}

async function cargarProducto() {
  try {
    producto = await api.get(`/products/${productId}`);
    renderHeader();
    renderVariantes();
  } catch (error) {
    const message = error instanceof ApiError ? error.message : 'No se pudo cargar el producto';
    document.querySelector('#producto-header').innerHTML = `<div class="empty-state"><span>${message}</span></div>`;
  }
}

function renderHeader() {
  const precio = producto.promoPrice
    ? `<span style="text-decoration:line-through;color:var(--color-text-muted);">${formatCurrency(producto.price)}</span> <strong>${formatCurrency(producto.promoPrice)}</strong>`
    : `<strong>${formatCurrency(producto.price)}</strong>`;
  const ruta = [producto.categoryName, producto.subcategoryName].filter(Boolean).join(' / ');
  const toggleLabel = producto.status === 'ACTIVE' ? 'Desactivar' : 'Activar';

  document.querySelector('#producto-header').innerHTML = `
    <div class="page-header">
      <div>
        <div style="display:flex; align-items:center; gap: var(--space-3);">
          <h1>${producto.name}</h1>
          ${statusBadge(producto.status)}
        </div>
        <p class="mono" style="margin-top: var(--space-1);">${producto.sku} · ${producto.internalCode}</p>
      </div>
      <div class="page-actions">
        <button class="btn btn-secondary" type="button" id="btn-toggle-estado">${toggleLabel}</button>
        <button class="btn btn-primary" type="button" id="btn-editar-producto">Editar producto</button>
      </div>
    </div>
    <div class="card">
      <div class="card-body" style="display:grid; grid-template-columns:repeat(auto-fit,minmax(160px,1fr)); gap: var(--space-5);">
        <div>
          <div class="stat-label">Precio</div>
          <div style="margin-top:4px;">${precio}</div>
        </div>
        <div>
          <div class="stat-label">Categoría</div>
          <div style="margin-top:4px;">${ruta || '—'}</div>
        </div>
        <div>
          <div class="stat-label">Marca</div>
          <div style="margin-top:4px;">${producto.brandName ?? '—'}</div>
        </div>
        <div>
          <div class="stat-label">Creado</div>
          <div style="margin-top:4px;">${formatDateLong(producto.createdAt)}</div>
        </div>
        ${producto.description ? `<div style="grid-column:1/-1;"><div class="stat-label">Descripción</div><p style="margin-top:4px;">${producto.description}</p></div>` : ''}
      </div>
    </div>
  `;

  document.querySelector('#btn-editar-producto').addEventListener('click', () => {
    openProductoForm({
      catalog,
      producto,
      onSaved: (actualizado) => {
        producto = { ...producto, ...actualizado };
        renderHeader();
        cargarGaleria();
      },
    });
  });

  document.querySelector('#btn-toggle-estado').addEventListener('click', async () => {
    const nuevoEstado = producto.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    const confirmado = nuevoEstado === 'INACTIVE'
      ? await confirmAction({
          title: 'Desactivar producto',
          message: `"${escapeHtml(producto.name)}" dejará de estar disponible en el POS y en los listados activos. ¿Continuar?`,
          confirmLabel: 'Desactivar',
        })
      : true;
    if (!confirmado) return;
    try {
      const resultado = await api.patch(`/products/${producto.id}/status`, { status: nuevoEstado });
      producto = { ...producto, status: resultado.status };
      renderHeader();
      showToast({ type: 'success', title: 'Estado actualizado' });
    } catch (error) {
      showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo actualizar' });
    }
  });
}

async function cargarGaleria() {
  const body = document.querySelector('#product-gallery-body');
  if (!body || !producto) return;
  try {
    imagenes = await api.get(`/products/${producto.id}/images`);
    renderGaleria();
  } catch (error) {
    body.innerHTML = `<div class="empty-state"><span>${error instanceof ApiError ? error.message : 'No se pudo cargar la galería'}</span></div>`;
  }
}

function renderGaleria() {
  const body = document.querySelector('#product-gallery-body');
  if (!body) return;

  if (!imagenes.length) {
    body.innerHTML = `
      <div class="empty-state" style="padding:var(--space-6) 0;">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="8.5" cy="9" r="1.5"/><path d="m3 16 4.5-4 3.5 3 3-2.5L21 17"/></svg>
        <span>Este producto todavía no tiene imágenes adicionales.</span>
      </div>
    `;
    return;
  }

  const ordenadas = [...imagenes].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id);
  body.innerHTML = `
    <div class="product-gallery-grid">
      ${ordenadas.map((image, index) => `
        <article class="product-gallery-item" data-image-id="${image.id}">
          <div class="product-gallery-preview">
            <img src="${API_ORIGIN}${escapeHtml(image.imageUrl)}" alt="${escapeHtml(image.altText || producto.name)}" loading="lazy" />
            ${image.primary ? '<span class="badge badge-success product-gallery-primary">Portada</span>' : ''}
          </div>
          <div class="product-gallery-meta">
            <label class="field-label" for="gallery-alt-${image.id}">Texto alternativo</label>
            <input class="input" id="gallery-alt-${image.id}" data-gallery-alt type="text" maxlength="150" value="${escapeHtml(image.altText || '')}" placeholder="Describe la imagen" />
            <div class="product-gallery-actions">
              ${puedeEditarGaleria ? `
                <button class="btn btn-ghost btn-sm" type="button" data-gallery-action="save" data-id="${image.id}">Guardar</button>
                <button class="btn btn-ghost btn-sm" type="button" data-gallery-action="primary" data-id="${image.id}" ${image.primary ? 'disabled' : ''}>${image.primary ? 'Portada actual' : 'Usar como portada'}</button>
                <button class="btn btn-ghost btn-sm danger-action" type="button" data-gallery-action="delete" data-id="${image.id}">Eliminar</button>
                <button class="btn btn-ghost btn-sm" type="button" data-gallery-action="up" data-id="${image.id}" ${index === 0 ? 'disabled' : ''} aria-label="Mover imagen arriba">↑</button>
                <button class="btn btn-ghost btn-sm" type="button" data-gallery-action="down" data-id="${image.id}" ${index === ordenadas.length - 1 ? 'disabled' : ''} aria-label="Mover imagen abajo">↓</button>
              ` : '<span class="table-cell-muted">Solo lectura</span>'}
            </div>
          </div>
        </article>
      `).join('')}
    </div>
  `;

  body.querySelectorAll('[data-gallery-action]').forEach((button) => {
    button.addEventListener('click', () => ejecutarAccionGaleria(button.dataset.galleryAction, Number(button.dataset.id)));
  });
}

async function subirImagenesGaleria(files) {
  const seleccionadas = [...(files || [])];
  const input = document.querySelector('#product-gallery-input');
  if (!seleccionadas.length || !producto) return;

  input.disabled = true;
  try {
    for (const [index, file] of seleccionadas.entries()) {
      const formData = new FormData();
      formData.append('file', file);
      const sortOrder = imagenes.length + index;
      await api.post(`/products/${producto.id}/images?sortOrder=${sortOrder}&primary=false`, formData);
    }
    showToast({ type: 'success', title: 'Galería actualizada', message: `Se agregaron ${seleccionadas.length} imagen${seleccionadas.length === 1 ? '' : 'es'}.` });
    await cargarGaleria();
  } catch (error) {
    showToast({ type: 'danger', title: 'No se pudieron subir todas las imágenes', message: error instanceof ApiError ? error.message : undefined });
    await cargarGaleria();
  } finally {
    input.value = '';
    input.disabled = false;
  }
}

async function ejecutarAccionGaleria(accion, imageId) {
  const image = imagenes.find((item) => item.id === imageId);
  if (!image) return;

  if (accion === 'delete') {
    const confirmado = await confirmAction({
      title: 'Eliminar imagen',
      message: `La imagen se retirará de la galería de "${escapeHtml(producto.name)}". ¿Continuar?`,
      confirmLabel: 'Eliminar',
    });
    if (!confirmado) return;
  }

  try {
    if (accion === 'save') {
      const input = document.querySelector(`#gallery-alt-${imageId}`);
      await api.patch(`/products/${producto.id}/images/${imageId}`, { altText: input?.value.trim() || null, sortOrder: image.sortOrder });
      showToast({ type: 'success', title: 'Imagen actualizada' });
    } else if (accion === 'primary') {
      await api.patch(`/products/${producto.id}/images/${imageId}/primary`, {});
      showToast({ type: 'success', title: 'Portada actualizada' });
    } else if (accion === 'delete') {
      await api.delete?.(`/products/${producto.id}/images/${imageId}`);
      showToast({ type: 'success', title: 'Imagen eliminada' });
    } else if (accion === 'up' || accion === 'down') {
      await moverImagen(imageId, accion === 'up' ? -1 : 1);
      return;
    }
    await cargarGaleria();
  } catch (error) {
    showToast({ type: 'danger', title: 'No se pudo actualizar la galería', message: error instanceof ApiError ? error.message : undefined });
  }
}

async function moverImagen(imageId, delta) {
  const ordenadas = [...imagenes].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id);
  const actual = ordenadas.findIndex((image) => image.id === imageId);
  const destino = actual + delta;
  if (actual < 0 || destino < 0 || destino >= ordenadas.length) return;
  [ordenadas[actual], ordenadas[destino]] = [ordenadas[destino], ordenadas[actual]];
  try {
    await Promise.all(ordenadas.map((image, index) => api.patch(`/products/${producto.id}/images/${image.id}`, { sortOrder: index })));
    showToast({ type: 'success', title: 'Orden actualizado' });
    await cargarGaleria();
  } catch (error) {
    showToast({ type: 'danger', title: 'No se pudo cambiar el orden', message: error instanceof ApiError ? error.message : undefined });
    await cargarGaleria();
  }
}

function renderVariantes() {
  const head = document.querySelector('#variants-head');
  const body = document.querySelector('#variants-body');

  // Todas las variantes de un producto comparten el mismo conjunto de atributos (ver
  // ProductAttribute) — la primera variante ya nos dice qué columnas mostrar, en orden.
  const nombresAtributos = producto.variants?.[0]?.attributes.map((a) => a.attributeName) ?? ['Variante'];
  const columnasFijas = 6; // SKU, código de barras, stock, mínimo, estado, acciones
  head.innerHTML = `<tr>${nombresAtributos.map((n) => `<th>${escapeHtml(n)}</th>`).join('')}
    <th>SKU</th><th>Código de barras</th><th>Stock</th><th>Mínimo</th><th>Estado</th><th></th></tr>`;

  if (!producto.variants || producto.variants.length === 0) {
    body.innerHTML = `
      <tr><td colspan="${nombresAtributos.length + columnasFijas}">
        <div class="empty-state">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="4" y="7" width="16" height="13" rx="1.5"/><path d="M8 7V5.5A2.5 2.5 0 0110.5 3h3A2.5 2.5 0 0116 5.5V7"/></svg>
          <span>Este producto todavía no tiene variantes. Agrega una o genera la matriz de combinaciones.</span>
        </div>
      </td></tr>
    `;
    return;
  }

  body.innerHTML = producto.variants
    .map((v) => {
      const toggleLabel = v.status === 'ACTIVE' ? 'Desactivar' : 'Activar';
      const stockStyle = v.stock === 0 ? 'color:var(--color-danger-text);font-weight:600;' : v.stock <= v.minStock ? 'color:var(--color-warning-text);font-weight:600;' : '';
      const celdasAtributos = v.attributes
        .map(
          (a) => `
          <td>
            <span style="display:inline-flex; align-items:center; gap:6px;">
              ${a.inputType === 'SWATCH' && a.hexCode ? `<span style="width:12px;height:12px;border-radius:3px;background:${a.hexCode};border:1px solid var(--color-border-strong);display:inline-block;"></span>` : ''}
              ${escapeHtml(a.value)}
            </span>
          </td>`
        )
        .join('');
      return `
        <tr>
          ${celdasAtributos}
          <td class="mono">${escapeHtml(v.sku)}</td>
          <td>
            ${v.barcode
              ? `<button class="btn btn-ghost btn-sm mono" type="button" data-action="ver-codigo" data-id="${v.id}">${escapeHtml(v.barcode)}</button>`
              : `<button class="btn btn-secondary btn-sm" type="button" data-action="asignar-codigo" data-id="${v.id}">Generar código</button>`}
          </td>
          <td style="${stockStyle}">${v.stock}</td>
          <td>${v.minStock}</td>
          <td>${statusBadge(v.status)}</td>
          <td>
            <div class="table-actions">
              <button class="btn btn-ghost btn-sm" type="button" data-action="editar-minimo" data-id="${v.id}" data-min-stock="${v.minStock}">Editar</button>
              <button class="btn btn-ghost btn-sm" type="button" data-action="toggle-variante" data-id="${v.id}" data-status="${v.status}">${toggleLabel}</button>
            </div>
          </td>
        </tr>
      `;
    })
    .join('');

  body.querySelectorAll('[data-action="ver-codigo"]').forEach((btn) => btn.addEventListener('click', () => verCodigo(btn.dataset.id)));
  body.querySelectorAll('[data-action="asignar-codigo"]').forEach((btn) => btn.addEventListener('click', () => asignarCodigo(btn.dataset.id)));
  body.querySelectorAll('[data-action="editar-minimo"]').forEach((btn) =>
    btn.addEventListener('click', () => editarMinimo(btn.dataset.id, Number(btn.dataset.minStock)))
  );
  body.querySelectorAll('[data-action="toggle-variante"]').forEach((btn) =>
    btn.addEventListener('click', () => toggleVariante(btn.dataset.id, btn.dataset.status))
  );
}

function verCodigo(variantId) {
  const variante = producto.variants.find((v) => String(v.id) === variantId);
  const modal = openModal({
    title: 'Código de barras',
    subtitle: `${producto.name} · ${variante.variantLabel}`,
    maxWidth: '360px',
    body: `
      <div style="display:flex; justify-content:center; padding: var(--space-2) 0;">${renderEan13Svg(variante.barcode)}</div>
      <div class="field" style="max-width:160px; margin:0 auto;">
        <label class="field-label" for="etiqueta-cantidad">Cantidad de etiquetas</label>
        <input class="input" type="number" id="etiqueta-cantidad" min="1" step="1" value="${Math.max(1, variante.stock)}" />
      </div>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cerrar</button>
      <button class="btn btn-primary" type="button" data-imprimir>Imprimir etiquetas</button>
    `,
  });
  modal.footer.querySelector('[data-cancel]').addEventListener('click', () => closeModal());
  modal.footer.querySelector('[data-imprimir]').addEventListener('click', () => {
    const cantidad = Number(modal.body.querySelector('#etiqueta-cantidad').value) || 1;
    imprimirEtiquetas({ producto, variante, cantidad });
  });
}

async function asignarCodigo(variantId) {
  try {
    const resultado = await api.post(`/variants/${variantId}/barcode`, {});
    actualizarVarianteLocal(resultado);
    showToast({ type: 'success', title: 'Código de barras asignado', message: resultado.barcode });
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo generar el código' });
  }
}

async function toggleVariante(variantId, currentStatus) {
  const nuevoEstado = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
  try {
    const resultado = await api.patch(`/variants/${variantId}/status`, { status: nuevoEstado });
    actualizarVarianteLocal(resultado);
    showToast({ type: 'success', title: 'Estado actualizado' });
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo actualizar' });
  }
}

function editarMinimo(variantId, minStockActual) {
  const modal = openModal({
    title: 'Stock mínimo',
    maxWidth: '360px',
    body: `
      <form id="minimo-form">
        <div class="field">
          <label class="field-label" for="min-stock-input">Unidades</label>
          <input class="input" type="number" min="0" step="1" id="min-stock-input" value="${minStockActual}" required />
          <span class="field-hint">El sistema avisará cuando el stock llegue a este nivel o menos.</span>
        </div>
      </form>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-primary" type="submit" form="minimo-form">Guardar</button>
    `,
  });
  modal.footer.querySelector('[data-cancel]').addEventListener('click', closeModal);
  modal.body.querySelector('#minimo-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      const resultado = await api.put(`/variants/${variantId}`, { minStock: Number(modal.body.querySelector('#min-stock-input').value) });
      actualizarVarianteLocal(resultado);
      closeModal();
      showToast({ type: 'success', title: 'Stock mínimo actualizado' });
    } catch (error) {
      showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo actualizar' });
    }
  });
}

function actualizarVarianteLocal(varianteActualizada) {
  const index = producto.variants.findIndex((v) => v.id === varianteActualizada.id);
  if (index >= 0) producto.variants[index] = varianteActualizada;
  renderVariantes();
}

function abrirModalNuevaVariante() {
  const atributos = activeOnly(catalog.attributes);

  const modal = openModal({
    title: 'Agregar variante',
    subtitle: producto.name,
    body: `
      <form id="variante-form" novalidate>
        <div class="alert alert-danger" id="variante-form-error" role="alert" hidden>
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
          <span class="alert-message"></span>
        </div>
        <div class="form-grid">
          ${atributos
            .map(
              (a) => `
          <div class="field">
            <label class="field-label" for="vf-attr-${a.id}">${escapeHtml(a.name)}</label>
            <select class="select" id="vf-attr-${a.id}" data-attribute-id="${a.id}" required>
              <option value="">Selecciona…</option>
              ${activeOnly(a.values).map((v) => `<option value="${v.id}">${escapeHtml(v.value)}</option>`).join('')}
            </select>
          </div>`
            )
            .join('')}
          <div class="field">
            <label class="field-label" for="vf-stock">Stock inicial</label>
            <input class="input" type="number" id="vf-stock" min="0" step="1" value="0" />
          </div>
          <div class="field">
            <label class="field-label" for="vf-min-stock">Stock mínimo</label>
            <input class="input" type="number" id="vf-min-stock" min="0" step="1" value="1" />
          </div>
          <div class="field field-span-2">
            <label class="checkbox-field"><input type="checkbox" id="vf-generate-barcode" checked /> Generar código de barras automáticamente</label>
          </div>
        </div>
      </form>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-primary" type="submit" form="variante-form">Agregar</button>
    `,
  });
  modal.footer.querySelector('[data-cancel]').addEventListener('click', closeModal);
  modal.body.querySelector('#variante-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = modal.body.querySelector('#variante-form-error');
    errorAlert.hidden = true;

    const attributeValueIds = atributos.map((a) => Number(modal.body.querySelector(`#vf-attr-${a.id}`).value) || null);
    if (attributeValueIds.some((id) => !id)) {
      errorAlert.querySelector('.alert-message').textContent = `Selecciona ${atributos.map((a) => a.name.toLowerCase()).join(' y ')}.`;
      errorAlert.hidden = false;
      return;
    }

    const payload = {
      attributeValueIds,
      stock: Number(modal.body.querySelector('#vf-stock').value) || 0,
      minStock: Number(modal.body.querySelector('#vf-min-stock').value) || 0,
      generateBarcode: modal.body.querySelector('#vf-generate-barcode').checked,
    };

    try {
      const nuevaVariante = await api.post(`/products/${producto.id}/variants`, payload);
      producto.variants.push(nuevaVariante);
      renderVariantes();
      closeModal();
      showToast({ type: 'success', title: 'Variante agregada', message: nuevaVariante.variantLabel });
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudo agregar la variante';
      errorAlert.hidden = false;
    }
  });
}

function abrirModalMatriz() {
  const atributos = activeOnly(catalog.attributes);

  const modal = openModal({
    title: 'Generar matriz de variantes',
    subtitle: 'Crea todas las combinaciones de los valores seleccionados. Las que ya existan se omiten.',
    maxWidth: '560px',
    body: `
      <form id="matriz-form" novalidate>
        <div class="alert alert-danger" id="matriz-form-error" role="alert" hidden>
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
          <span class="alert-message"></span>
        </div>
        <div class="form-grid">
          ${atributos
            .map(
              (a) => `
          <div class="field">
            <span class="field-label">${escapeHtml(a.name)}</span>
            <div style="display:flex; flex-direction:column; gap:8px; max-height:180px; overflow-y:auto; padding: var(--space-2) 0;">
              ${activeOnly(a.values).map((v) => `<label class="checkbox-field"><input type="checkbox" name="mf-attr-${a.id}" value="${v.id}" /> ${escapeHtml(v.value)}</label>`).join('')}
            </div>
          </div>`
            )
            .join('')}
          <div class="field">
            <label class="field-label" for="mf-min-stock">Stock mínimo</label>
            <input class="input" type="number" id="mf-min-stock" min="0" step="1" value="1" />
          </div>
          <div class="field" style="justify-content:flex-end;">
            <label class="checkbox-field"><input type="checkbox" id="mf-generate-barcodes" checked /> Generar códigos de barras</label>
          </div>
        </div>
      </form>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-primary" type="submit" form="matriz-form">Generar</button>
    `,
  });
  modal.footer.querySelector('[data-cancel]').addEventListener('click', closeModal);
  modal.body.querySelector('#matriz-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = modal.body.querySelector('#matriz-form-error');
    errorAlert.hidden = true;

    const attributeValueIdGroups = atributos.map((a) =>
      [...modal.body.querySelectorAll(`input[name="mf-attr-${a.id}"]:checked`)].map((el) => Number(el.value))
    );
    if (attributeValueIdGroups.some((grupo) => grupo.length === 0)) {
      errorAlert.querySelector('.alert-message').textContent = `Selecciona al menos un valor de ${atributos.map((a) => a.name.toLowerCase()).join(' y ')}.`;
      errorAlert.hidden = false;
      return;
    }

    const payload = {
      attributeValueIdGroups,
      minStock: Number(modal.body.querySelector('#mf-min-stock').value) || 0,
      generateBarcodes: modal.body.querySelector('#mf-generate-barcodes').checked,
    };

    try {
      const nuevas = await api.post(`/products/${producto.id}/variants/bulk`, payload);
      producto.variants.push(...nuevas);
      renderVariantes();
      closeModal();
      showToast({
        type: 'success',
        title: 'Matriz generada',
        message: nuevas.length > 0 ? `Se crearon ${nuevas.length} variantes nuevas.` : 'No había combinaciones nuevas por crear.',
      });
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudo generar la matriz';
      errorAlert.hidden = false;
    }
  });
}
