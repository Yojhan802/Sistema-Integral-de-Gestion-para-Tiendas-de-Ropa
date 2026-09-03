import { api, ApiError, API_ORIGIN } from '../core/api.js';
import { activeOnly, subcategoriesOf } from '../core/catalog.js';
import { openModal, closeModal } from './modal.js';
import { showToast } from './toast.js';

/**
 * Abre el modal de crear/editar producto. `producto` es null para crear.
 * `onSaved(productoActualizado)` se invoca tras guardar con éxito.
 */
export function openProductoForm({ catalog, producto = null, onSaved }) {
  const esEdicion = Boolean(producto);
  const categoriasActivas = activeOnly(catalog.categories);
  const marcasActivas = activeOnly(catalog.brands);

  const modal = openModal({
    title: esEdicion ? 'Editar producto' : 'Nuevo producto',
    subtitle: esEdicion ? producto.sku : 'El SKU y el código interno se generan automáticamente.',
    maxWidth: '620px',
    body: `
      <form id="producto-form" novalidate>
        <div class="alert alert-danger" id="producto-form-error" role="alert" hidden>
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
          <span class="alert-message"></span>
        </div>
        <div class="form-grid">
          ${
            esEdicion
              ? `
          <div class="field field-span-2">
            <span class="field-label">Foto del producto</span>
            <div style="display:flex; align-items:center; gap: var(--space-3);">
              <div style="width:64px; height:64px; border-radius: var(--radius-md); overflow:hidden; background: var(--color-surface-sunken); flex-shrink:0;" id="pf-image-preview-wrap">
                ${producto.imageUrl ? `<img id="pf-image-preview" src="${API_ORIGIN}${producto.imageUrl}" alt="" style="width:100%; height:100%; object-fit:cover;" />` : ''}
              </div>
              <label class="btn btn-secondary btn-sm" for="pf-image-input" style="cursor:pointer;">Cambiar foto</label>
              <input type="file" id="pf-image-input" accept="image/png,image/jpeg,image/webp" style="display:none;" />
            </div>
          </div>
          `
              : ''
          }
          <div class="field field-span-2">
            <label class="field-label" for="pf-name">Nombre</label>
            <input class="input" id="pf-name" required maxlength="150" value="${producto?.name ?? ''}" />
          </div>
          <div class="field">
            <label class="field-label" for="pf-category">Categoría</label>
            <select class="select" id="pf-category" required>
              <option value="">Selecciona…</option>
              ${categoriasActivas.map((c) => `<option value="${c.id}" ${producto?.categoryId === c.id ? 'selected' : ''}>${c.name}</option>`).join('')}
            </select>
          </div>
          <div class="field">
            <label class="field-label" for="pf-subcategory">Subcategoría</label>
            <select class="select" id="pf-subcategory">
              <option value="">Ninguna</option>
            </select>
          </div>
          <div class="field">
            <label class="field-label" for="pf-brand">Marca</label>
            <select class="select" id="pf-brand">
              <option value="">Ninguna</option>
              ${marcasActivas.map((b) => `<option value="${b.id}" ${producto?.brandId === b.id ? 'selected' : ''}>${b.name}</option>`).join('')}
            </select>
          </div>
          <div class="field">
            <label class="field-label" for="pf-price">Precio (S/)</label>
            <input class="input" id="pf-price" type="number" min="0.01" step="0.01" required value="${producto?.price ?? ''}" />
          </div>
          <div class="field field-span-2">
            <label class="field-label" for="pf-promo-price">Precio promocional (S/)</label>
            <input class="input" id="pf-promo-price" type="number" min="0.01" step="0.01" value="${producto?.promoPrice ?? ''}" />
            <span class="field-hint">Opcional. Debe ser menor que el precio regular.</span>
          </div>
          <div class="field">
            <label class="field-label" for="pf-material">Material</label>
            <input class="input" id="pf-material" maxlength="150" placeholder="Ej. 100% Algodón" value="${producto?.material ?? ''}" />
          </div>
          <div class="field">
            <label class="field-label" for="pf-fit">Calce</label>
            <input class="input" id="pf-fit" maxlength="100" placeholder="Ej. True to size" value="${producto?.fit ?? ''}" />
          </div>
          <div class="field field-span-2">
            <div style="display:flex; justify-content:space-between; align-items:center;">
              <label class="field-label" for="pf-description">Descripción</label>
              <button class="btn btn-ghost btn-sm" type="button" id="pf-generar-descripcion">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 3v4M12 17v4M3 12h4M17 12h4M5.6 5.6l2.8 2.8M15.6 15.6l2.8 2.8M18.4 5.6l-2.8 2.8M8.4 15.6l-2.8 2.8" stroke-linecap="round"/></svg>
                Generar con IA
              </button>
            </div>
            <textarea class="input" id="pf-description" rows="3" style="height:auto; padding-top:10px; resize:vertical;">${producto?.description ?? ''}</textarea>
          </div>
          ${
            esEdicion
              ? `
          <div class="field field-span-2">
            <span class="field-label">Guía de tallas</span>
            <div style="display:flex; align-items:center; gap: var(--space-3);">
              <div style="width:64px; height:64px; border-radius: var(--radius-md); overflow:hidden; background: var(--color-surface-sunken); flex-shrink:0;" id="pf-size-guide-preview-wrap">
                ${producto.sizeGuideImageUrl ? `<img src="${API_ORIGIN}${producto.sizeGuideImageUrl}" alt="" style="width:100%; height:100%; object-fit:cover;" />` : ''}
              </div>
              <label class="btn btn-secondary btn-sm" for="pf-size-guide-input" style="cursor:pointer;">Cambiar guía</label>
              <input type="file" id="pf-size-guide-input" accept="image/png,image/jpeg,image/webp" style="display:none;" />
            </div>
            <span class="field-hint">Se muestra en la ficha del producto en la tienda online.</span>
          </div>
          `
              : ''
          }
        </div>
      </form>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-primary" type="submit" form="producto-form" id="pf-submit">
        <span class="spinner" hidden></span>
        <span class="btn-label">${esEdicion ? 'Guardar cambios' : 'Crear producto'}</span>
      </button>
    `,
  });

  const categorySelect = modal.body.querySelector('#pf-category');
  const subcategorySelect = modal.body.querySelector('#pf-subcategory');

  function refrescarSubcategorias() {
    const subs = activeOnly(subcategoriesOf(catalog, categorySelect.value));
    subcategorySelect.innerHTML =
      '<option value="">Ninguna</option>' +
      subs.map((s) => `<option value="${s.id}" ${producto?.subcategoryId === s.id ? 'selected' : ''}>${s.name}</option>`).join('');
  }
  categorySelect.addEventListener('change', refrescarSubcategorias);
  refrescarSubcategorias();

  if (esEdicion) {
    modal.body.querySelector('#pf-image-input').addEventListener('change', (event) => {
      const file = event.target.files?.[0];
      if (file) {
        subirImagen(producto.id, file, modal.body.querySelector('#pf-image-preview-wrap'), (actualizado) => {
          // Sin esto, "Guardar cambios" reenviaría el imageUrl viejo (o ninguno) y
          // la foto recién subida se perdería en cuanto se editara cualquier otro campo.
          producto.imageUrl = actualizado.imageUrl;
          onSaved?.(actualizado);
        });
      }
    });

    modal.body.querySelector('#pf-size-guide-input').addEventListener('change', (event) => {
      const file = event.target.files?.[0];
      if (file) subirGuiaTallas(producto.id, file, modal.body.querySelector('#pf-size-guide-preview-wrap'));
    });
  }

  modal.footer.querySelector('[data-cancel]').addEventListener('click', closeModal);

  modal.body.querySelector('#pf-generar-descripcion').addEventListener('click', async () => {
    const nombre = modal.body.querySelector('#pf-name').value.trim();
    if (!nombre) {
      showToast({ type: 'danger', title: 'Escribe el nombre del producto primero' });
      return;
    }

    const boton = modal.body.querySelector('#pf-generar-descripcion');
    boton.disabled = true;
    try {
      const marcaSelect = modal.body.querySelector('#pf-brand');
      const { descripcion } = await api.post('/products/assistant/description', {
        nombre,
        categoria: textoSeleccionado(categorySelect, 'Selecciona…'),
        marca: textoSeleccionado(marcaSelect, 'Ninguna'),
        material: modal.body.querySelector('#pf-material').value.trim() || null,
        calce: modal.body.querySelector('#pf-fit').value.trim() || null,
      });
      modal.body.querySelector('#pf-description').value = descripcion;
    } catch (error) {
      showToast({ type: 'danger', title: 'No se pudo generar la descripción', message: error instanceof ApiError ? error.message : undefined });
    } finally {
      boton.disabled = false;
    }
  });

  modal.body.querySelector('#producto-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = modal.body.querySelector('#producto-form-error');
    errorAlert.hidden = true;

    const payload = {
      name: modal.body.querySelector('#pf-name').value.trim(),
      categoryId: Number(categorySelect.value) || null,
      subcategoryId: subcategorySelect.value ? Number(subcategorySelect.value) : null,
      brandId: modal.body.querySelector('#pf-brand').value ? Number(modal.body.querySelector('#pf-brand').value) : null,
      description: modal.body.querySelector('#pf-description').value.trim() || null,
      material: modal.body.querySelector('#pf-material').value.trim() || null,
      fit: modal.body.querySelector('#pf-fit').value.trim() || null,
      price: Number(modal.body.querySelector('#pf-price').value),
      promoPrice: modal.body.querySelector('#pf-promo-price').value ? Number(modal.body.querySelector('#pf-promo-price').value) : null,
    };
    // La edición reenvía la imagen actual: si no se incluye, el backend la interpreta
    // como "quitar la foto" y la borra (mismo motivo del bug que se estaba arreglando).
    if (esEdicion) payload.imageUrl = producto.imageUrl ?? null;

    if (!payload.categoryId) {
      errorAlert.querySelector('.alert-message').textContent = 'Selecciona una categoría.';
      errorAlert.hidden = false;
      return;
    }

    const submitBtn = modal.footer.querySelector('#pf-submit');
    submitBtn.disabled = true;
    submitBtn.querySelector('.spinner').hidden = false;

    try {
      const resultado = esEdicion
        ? await api.put(`/products/${producto.id}`, payload)
        : await api.post('/products', payload);
      closeModal();
      showToast({
        type: 'success',
        title: esEdicion ? 'Producto actualizado' : 'Producto creado',
        message: resultado.name,
      });
      onSaved?.(resultado);
    } catch (error) {
      const message = error instanceof ApiError ? error.message : 'No se pudo guardar el producto';
      errorAlert.querySelector('.alert-message').textContent = message;
      errorAlert.hidden = false;
      submitBtn.disabled = false;
      submitBtn.querySelector('.spinner').hidden = true;
    }
  });
}

function textoSeleccionado(select, valorVacio) {
  const texto = select.options[select.selectedIndex]?.text;
  return texto && texto !== valorVacio ? texto : null;
}

async function subirImagen(productId, file, previewWrap, onUploaded) {
  const formData = new FormData();
  formData.append('file', file);
  try {
    const actualizado = await api.post(`/products/${productId}/image`, formData);
    previewWrap.innerHTML = `<img src="${API_ORIGIN}${actualizado.imageUrl}" alt="" style="width:100%; height:100%; object-fit:cover;" />`;
    showToast({ type: 'success', title: 'Foto actualizada' });
    onUploaded?.(actualizado);
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo subir la foto' });
  }
}

async function subirGuiaTallas(productId, file, previewWrap) {
  const formData = new FormData();
  formData.append('file', file);
  try {
    const actualizado = await api.post(`/products/${productId}/size-guide`, formData);
    previewWrap.innerHTML = `<img src="${API_ORIGIN}${actualizado.sizeGuideImageUrl}" alt="" style="width:100%; height:100%; object-fit:cover;" />`;
    showToast({ type: 'success', title: 'Guía de tallas actualizada' });
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo subir la guía de tallas' });
  }
}
