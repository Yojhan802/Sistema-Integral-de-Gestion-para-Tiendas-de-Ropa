import { requireSession, hasPermission } from '../core/auth.js';
import { api, ApiError } from '../core/api.js';
import { renderShell } from '../components/shell.js';
import { openModal, closeModal } from '../components/modal.js';
import { showToast } from '../components/toast.js';
import { formatCurrency, escapeHtml } from '../core/format.js';
import { debounce } from '../core/debounce.js';

let combosCache = [];

function init() {
  document.querySelector('#btn-nuevo-combo').addEventListener('click', () => abrirFormularioCombo(null));
  if (!hasPermission('COMBOS_GESTIONAR')) {
    document.querySelector('#btn-nuevo-combo').hidden = true;
  }
  cargarCombos();
}

function itemTexto(it) {
  if (it.selectorType === 'CATEGORY') {
    return `${it.quantity} × cualquier producto de ${escapeHtml(it.categoryName)}${it.brandName ? ` (marca ${escapeHtml(it.brandName)})` : ''}`;
  }
  return `${it.quantity} × ${escapeHtml(it.productName)}`;
}

async function cargarCombos() {
  const body = document.querySelector('#combos-body');
  try {
    combosCache = await api.get('/combos');
    body.innerHTML = combosCache.length
      ? combosCache
          .map(
            (c) => `
        <tr>
          <td class="table-cell-primary mono">${escapeHtml(c.code)}</td>
          <td>${escapeHtml(c.name)}</td>
          <td class="table-cell-muted">${c.items.map(itemTexto).join(', ')}</td>
          <td class="mono" style="text-decoration:line-through; color:var(--color-text-muted);">${c.normalTotal != null ? formatCurrency(c.normalTotal) : '—'}</td>
          <td class="mono">${formatCurrency(c.price)}</td>
          <td class="mono">${c.savings != null ? formatCurrency(c.savings) : '—'}</td>
          <td><span class="badge ${c.status === 'ACTIVE' ? 'badge-success' : 'badge-neutral'}">${c.status === 'ACTIVE' ? 'Activo' : 'Inactivo'}</span></td>
          <td>
            <div class="table-actions">
              <button class="btn btn-ghost btn-sm" type="button" data-editar="${c.id}">Editar</button>
              <button class="btn btn-ghost btn-sm" type="button" data-toggle="${c.id}" data-status="${c.status}">
                ${c.status === 'ACTIVE' ? 'Desactivar' : 'Activar'}
              </button>
            </div>
          </td>
        </tr>
      `
          )
          .join('')
      : `<tr><td colspan="8"><div class="empty-state"><span>Todavía no hay combos creados.</span></div></td></tr>`;

    body.querySelectorAll('[data-editar]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const combo = combosCache.find((c) => String(c.id) === btn.dataset.editar);
        abrirFormularioCombo(combo);
      });
    });
    body.querySelectorAll('[data-toggle]').forEach((btn) => {
      btn.addEventListener('click', () => cambiarEstado(btn.dataset.toggle, btn.dataset.status));
    });
  } catch (error) {
    body.innerHTML = `<tr><td colspan="8"><div class="empty-state"><span>${error instanceof ApiError ? error.message : 'Error al cargar los combos'}</span></div></td></tr>`;
  }
}

async function cambiarEstado(id, currentStatus) {
  const nuevoEstado = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
  try {
    await api.patch(`/combos/${id}/status`, { status: nuevoEstado });
    showToast({ type: 'success', title: 'Estado actualizado' });
    cargarCombos();
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo actualizar' });
  }
}

async function abrirFormularioCombo(combo) {
  const esEdicion = Boolean(combo);
  let items = combo
    ? combo.items.map((it) => ({
        selectorType: it.selectorType,
        productId: it.productId,
        productName: it.productName,
        categoryId: it.categoryId,
        categoryName: it.categoryName,
        brandId: it.brandId,
        brandName: it.brandName,
        quantity: it.quantity,
      }))
    : [];

  const categorias = await api.get('/categories').catch(() => []);
  const marcas = await api.get('/brands').catch(() => []);

  const modal = openModal({
    title: esEdicion ? 'Editar combo' : 'Nuevo combo',
    maxWidth: '600px',
    body: `
      <form id="combo-form" novalidate>
        <div class="alert alert-danger" id="combo-form-error" role="alert" hidden>
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
          <span class="alert-message"></span>
        </div>
        <div class="form-grid">
          <div class="field">
            <label class="field-label" for="cf-code">Código</label>
            <input class="input mono" id="cf-code" maxlength="30" required value="${escapeHtml(combo?.code ?? '')}" />
          </div>
          <div class="field">
            <label class="field-label" for="cf-name">Nombre</label>
            <input class="input" id="cf-name" maxlength="150" required value="${escapeHtml(combo?.name ?? '')}" />
          </div>
          <div class="field field-span-2">
            <label class="field-label" for="cf-price">Precio fijo del combo (S/)</label>
            <input class="input" type="number" id="cf-price" min="0.01" step="0.01" required value="${combo?.price ?? ''}" />
          </div>
        </div>

        <div style="margin-top: var(--space-4);">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:var(--space-2);">
            <label class="field-label" style="margin-bottom:0;">Líneas del combo</label>
            <button type="button" class="btn btn-ghost btn-sm" id="cf-toggle-tipo">Agregar por categoría</button>
          </div>

          <div id="cf-add-producto" style="position:relative;">
            <input class="input" id="cf-product-search" placeholder="Buscar producto específico para agregar…" autocomplete="off" />
            <div id="cf-product-results" style="display:none; position:absolute; top:calc(100% + 4px); left:0; right:0; z-index:20;
              background:var(--color-surface); border:1px solid var(--color-border); border-radius:var(--radius-md);
              box-shadow:var(--shadow-md); max-height:200px; overflow-y:auto;"></div>
          </div>

          <div id="cf-add-categoria" style="display:none; gap:var(--space-2); align-items:end;" class="form-grid">
            <div class="field">
              <label class="field-label" for="cf-categoria">Categoría</label>
              <select class="select" id="cf-categoria">
                ${categorias.map((c) => `<option value="${c.id}">${escapeHtml(c.name)}</option>`).join('')}
              </select>
            </div>
            <div class="field">
              <label class="field-label" for="cf-marca">Marca (opcional)</label>
              <select class="select" id="cf-marca">
                <option value="">Cualquier marca</option>
                ${marcas.map((m) => `<option value="${m.id}">${escapeHtml(m.name)}</option>`).join('')}
              </select>
            </div>
            <div class="field">
              <label class="field-label" for="cf-categoria-qty">Cantidad</label>
              <input class="input" type="number" id="cf-categoria-qty" min="1" step="1" value="1" style="width:80px;" />
            </div>
            <button type="button" class="btn btn-secondary btn-sm" id="cf-add-categoria-btn">+ Agregar</button>
          </div>

          <div id="cf-items-list" style="margin-top:var(--space-3); display:flex; flex-direction:column; gap:var(--space-2);"></div>
        </div>
      </form>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-primary" type="submit" form="combo-form">${esEdicion ? 'Guardar cambios' : 'Crear combo'}</button>
    `,
  });

  const addProductoBox = modal.body.querySelector('#cf-add-producto');
  const addCategoriaBox = modal.body.querySelector('#cf-add-categoria');
  const toggleTipo = modal.body.querySelector('#cf-toggle-tipo');
  let modoCategoria = false;
  toggleTipo.addEventListener('click', () => {
    modoCategoria = !modoCategoria;
    addProductoBox.style.display = modoCategoria ? 'none' : '';
    addCategoriaBox.style.display = modoCategoria ? 'flex' : 'none';
    toggleTipo.textContent = modoCategoria ? 'Agregar producto específico' : 'Agregar por categoría';
  });

  function renderItems() {
    const list = modal.body.querySelector('#cf-items-list');
    list.innerHTML = items.length
      ? items
          .map(
            (it, index) => `
        <div style="display:flex; align-items:center; gap:var(--space-3); padding:var(--space-2) var(--space-3); background:var(--color-surface-sunken); border-radius:var(--radius-md);">
          <span style="flex:1; font-size:var(--font-size-sm);">${itemTexto(it)}</span>
          <input class="input" type="number" min="1" step="1" value="${it.quantity}" data-item-qty="${index}" style="width:70px; text-align:right;" />
          <button type="button" class="btn btn-ghost btn-sm" data-item-remove="${index}" aria-label="Quitar">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round"/></svg>
          </button>
        </div>
      `
          )
          .join('')
      : `<p class="table-cell-muted" style="font-size:var(--font-size-sm);">Agrega al menos una línea.</p>`;

    list.querySelectorAll('[data-item-qty]').forEach((input) => {
      input.addEventListener('input', () => {
        items[Number(input.dataset.itemQty)].quantity = Number(input.value) || 1;
      });
    });
    list.querySelectorAll('[data-item-remove]').forEach((btn) => {
      btn.addEventListener('click', () => {
        items.splice(Number(btn.dataset.itemRemove), 1);
        renderItems();
      });
    });
  }
  renderItems();

  const searchInput = modal.body.querySelector('#cf-product-search');
  const resultsBox = modal.body.querySelector('#cf-product-results');
  searchInput.addEventListener('input', debounce(async () => {
    const q = searchInput.value.trim();
    if (q.length < 2) {
      resultsBox.style.display = 'none';
      return;
    }
    try {
      const page = await api.get('/products', { query: { search: q, size: 10 } });
      resultsBox.innerHTML = page.content.length
        ? page.content
            .map(
              (p) => `
          <button type="button" class="vp-result" data-product="${p.id}" data-name="${escapeHtml(p.name)}" style="display:block; width:100%; text-align:left; padding:var(--space-3); border-bottom:1px solid var(--color-border);">
            <div style="font-weight:600;">${escapeHtml(p.name)}</div>
            <div style="font-size:var(--font-size-xs); color:var(--color-text-muted);">${formatCurrency(p.promoPrice ?? p.price)}</div>
          </button>
        `
            )
            .join('')
        : `<div style="padding:var(--space-3); color:var(--color-text-muted); font-size:var(--font-size-sm);">Sin resultados</div>`;
      resultsBox.querySelectorAll('[data-product]').forEach((btn) => {
        btn.addEventListener('click', () => {
          const productId = Number(btn.dataset.product);
          if (items.some((it) => it.selectorType === 'PRODUCT' && it.productId === productId)) {
            showToast({ type: 'warning', title: 'Ya está en el combo' });
          } else {
            items.push({ selectorType: 'PRODUCT', productId, productName: btn.dataset.name, quantity: 1 });
            renderItems();
          }
          searchInput.value = '';
          resultsBox.style.display = 'none';
        });
      });
      resultsBox.style.display = 'block';
    } catch {
      resultsBox.style.display = 'none';
    }
  }, 300));
  document.addEventListener('click', (event) => {
    if (!event.target.closest('#cf-product-search') && !event.target.closest('#cf-product-results')) resultsBox.style.display = 'none';
  });

  modal.body.querySelector('#cf-add-categoria-btn').addEventListener('click', () => {
    const categoryId = Number(modal.body.querySelector('#cf-categoria').value);
    const categoryName = modal.body.querySelector('#cf-categoria').selectedOptions[0]?.textContent;
    const brandSelect = modal.body.querySelector('#cf-marca');
    const brandId = brandSelect.value ? Number(brandSelect.value) : null;
    const brandName = brandId ? brandSelect.selectedOptions[0]?.textContent : null;
    const quantity = Number(modal.body.querySelector('#cf-categoria-qty').value) || 1;

    if (!categoryId) return;
    items.push({ selectorType: 'CATEGORY', categoryId, categoryName, brandId, brandName, quantity });
    renderItems();
  });

  modal.footer.querySelector('[data-cancel]').addEventListener('click', closeModal);
  modal.body.querySelector('#combo-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = modal.body.querySelector('#combo-form-error');
    errorAlert.hidden = true;

    if (items.length === 0) {
      errorAlert.querySelector('.alert-message').textContent = 'Agrega al menos una línea al combo.';
      errorAlert.hidden = false;
      return;
    }

    const payload = {
      code: modal.body.querySelector('#cf-code').value.trim().toUpperCase(),
      name: modal.body.querySelector('#cf-name').value.trim(),
      price: Number(modal.body.querySelector('#cf-price').value),
      items: items.map((it) => ({
        selectorType: it.selectorType,
        productId: it.selectorType === 'PRODUCT' ? it.productId : null,
        categoryId: it.selectorType === 'CATEGORY' ? it.categoryId : null,
        brandId: it.selectorType === 'CATEGORY' ? it.brandId : null,
        quantity: it.quantity,
      })),
    };

    try {
      if (esEdicion) {
        await api.put(`/combos/${combo.id}`, payload);
      } else {
        await api.post('/combos', payload);
      }
      closeModal();
      showToast({ type: 'success', title: esEdicion ? 'Combo actualizado' : 'Combo creado' });
      cargarCombos();
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudo guardar el combo';
      errorAlert.hidden = false;
    }
  });
}

const session = requireSession();
if (session) {
  renderShell('combos');
  init();
}
