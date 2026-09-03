import { requireSession } from '../core/auth.js';
import { api, apiDownload, ApiError } from '../core/api.js';
import { renderShell, actualizarEstadoCaja } from '../components/shell.js';
import { fetchCurrentSession } from '../core/cash-session.js';
import { openAbrirCajaModal } from '../components/abrir-caja.js';
import { createCustomerPicker } from '../components/customer-picker.js';
import { createVariantPicker } from '../components/variant-picker.js';
import { openPagoModal } from '../components/pago-modal.js';
import { openModal, closeModal } from '../components/modal.js';
import { confirmAction } from '../components/confirm.js';
import { showToast } from '../components/toast.js';
import { formatCurrency, formatDateTime, escapeHtml } from '../core/format.js';
import { debounce } from '../core/debounce.js';
import { renderPagination } from '../components/pagination.js';
import { imprimirTicket } from '../components/ticket.js';
import { statusBadge } from '../components/status-badge.js';

const SALE_STATUS_LABELS = { COMPLETED: 'Completada', CANCELLED: 'Anulada', PARTIALLY_RETURNED: 'Parcialmente devuelta', RETURNED: 'Devuelta' };
const SALE_STATUS_CLASSES = { COMPLETED: 'badge-success', CANCELLED: 'badge-danger', PARTIALLY_RETURNED: 'badge-warning', RETURNED: 'badge-neutral' };

const session = requireSession();
let cart = [];
let cashSession = null;
let customerPicker = null;
let permissions = new Set(session?.user.permissions ?? []);
let historialPage = 0;
let devolucionesPage = 0;
let promotoresCache = [];

if (session) {
  renderShell('ventas');
  init();
}

async function init() {
  document.querySelectorAll('.tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      const activo = tab.dataset.tab;
      document.querySelectorAll('.tab').forEach((t) => t.setAttribute('aria-selected', String(t.dataset.tab === activo)));
      document.querySelector('#panel-nueva').hidden = activo !== 'nueva';
      document.querySelector('#panel-historial').hidden = activo !== 'historial';
      document.querySelector('#panel-devoluciones').hidden = activo !== 'devoluciones';
      document.querySelector('#panel-promotores').hidden = activo !== 'promotores';
      if (activo === 'historial') cargarHistorial();
      if (activo === 'devoluciones') cargarDevoluciones();
      if (activo === 'promotores') cargarPromotores();
    });
  });
  document.querySelector('#btn-filtrar-historial').addEventListener('click', () => {
    historialPage = 0;
    cargarHistorial();
  });
  document.querySelector('#btn-nuevo-promotor').addEventListener('click', () => abrirFormularioPromotor(null));

  const ventaId = new URLSearchParams(window.location.search).get('ventaId');
  if (ventaId) {
    document.querySelectorAll('.tab').forEach((t) => t.setAttribute('aria-selected', String(t.dataset.tab === 'historial')));
    document.querySelector('#panel-nueva').hidden = true;
    document.querySelector('#panel-historial').hidden = false;
    cargarHistorial();
    verDetalleVenta(Number(ventaId));
  }

  cashSession = await fetchCurrentSession();
  if (!cashSession) {
    document.querySelector('#pos-blocked').hidden = false;
    document.querySelector('#btn-abrir-caja-pos').addEventListener('click', () => {
      openAbrirCajaModal({
        onOpened: async (sesion) => {
          cashSession = sesion;
          document.querySelector('#pos-blocked').hidden = true;
          document.querySelector('#pos-screen').hidden = false;
          actualizarEstadoCaja();
          iniciarPantalla();
        },
      });
    });
    return;
  }
  document.querySelector('#pos-screen').hidden = false;
  iniciarPantalla();
}

function iniciarPantalla() {
  customerPicker = createCustomerPicker();
  document.querySelector('#pos-customer-mount').appendChild(customerPicker.root);

  const scanInput = document.querySelector('#pos-scan-input');
  scanInput.addEventListener('input', debounce(() => {
    const value = scanInput.value.trim();
    if (value.length >= 2) buscar(value);
  }, 300));
  scanInput.addEventListener('keydown', async (event) => {
    if (event.key !== 'Enter') return;
    event.preventDefault();
    const value = scanInput.value.trim();
    if (!value) return;
    try {
      const variante = await api.get(`/variants/barcode/${encodeURIComponent(value)}`);
      agregarAlCarrito(variante);
      scanInput.value = '';
      mostrarResultadosVacios();
    } catch {
      buscar(value);
    }
  });
  scanInput.focus();

  document.querySelector('#btn-vaciar-carrito').addEventListener('click', () => {
    cart = [];
    renderCart();
  });
  document.querySelector('#btn-cobrar').addEventListener('click', cobrar);

  if (permissions.has('COMBOS_CONSULTAR')) {
    const btnCombo = document.querySelector('#btn-agregar-combo');
    btnCombo.hidden = false;
    btnCombo.addEventListener('click', abrirSelectorCombo);
  }

  renderCart();
}

async function buscar(query) {
  const resultsBox = document.querySelector('#pos-results');
  try {
    const resultados = await api.get('/variants/search', { query: { q: query } });
    if (resultados.length === 0) {
      resultsBox.innerHTML = `<div class="empty-state"><span>Sin resultados para "${query}"</span></div>`;
      return;
    }
    resultsBox.innerHTML = `
      <div class="table-scroll">
        <table class="data-table">
          <thead><tr><th>Producto</th><th>SKU</th><th>Precio</th><th>Stock</th><th></th></tr></thead>
          <tbody>
            ${resultados
              .map(
                (v) => `
              <tr class="${v.stock === 0 ? '' : 'clickable'}" data-id="${v.variantId}" style="${v.stock === 0 ? 'opacity:.5;' : ''}">
                <td class="table-cell-primary">${escapeHtml(v.productName)} <span class="table-cell-muted">${escapeHtml(v.variantLabel)}</span></td>
                <td class="mono">${escapeHtml(v.sku)}</td>
                <td class="mono">${formatCurrency(v.effectivePrice)}</td>
                <td>${v.stock}</td>
                <td>${v.stock > 0 ? '<span class="badge badge-info">Agregar</span>' : '<span class="badge badge-danger">Sin stock</span>'}</td>
              </tr>
            `
              )
              .join('')}
          </tbody>
        </table>
      </div>
    `;
    resultsBox.querySelectorAll('tr[data-id].clickable').forEach((row) => {
      const variante = resultados.find((v) => String(v.variantId) === row.dataset.id);
      if (variante) row.addEventListener('click', () => agregarAlCarrito(variante));
    });
  } catch (error) {
    resultsBox.innerHTML = `<div class="empty-state"><span>${error instanceof ApiError ? error.message : 'Error al buscar'}</span></div>`;
  }
}

function mostrarResultadosVacios() {
  document.querySelector('#pos-results').innerHTML = `
    <div class="empty-state">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/></svg>
      <span>Escanea un producto o escribe para buscar.</span>
    </div>
  `;
}

function agregarAlCarrito(variante) {
  if (variante.status !== 'ACTIVE') {
    showToast({ type: 'warning', title: 'Variante inactiva', message: 'Esta variante no está disponible para la venta.' });
    return;
  }
  const existente = cart.find((item) => item.variantId === variante.variantId && !item.comboId);
  const enCarrito = existente?.quantity ?? 0;
  if (enCarrito + 1 > variante.stock) {
    showToast({ type: 'warning', title: 'Stock insuficiente', message: `Solo hay ${variante.stock} unidades disponibles.` });
    return;
  }
  if (existente) {
    existente.quantity += 1;
  } else {
    cart.push({
      variantId: variante.variantId,
      productName: variante.productName,
      variantLabel: variante.variantLabel,
      sku: variante.sku,
      unitPrice: variante.effectivePrice,
      stock: variante.stock,
      quantity: 1,
      comboId: null,
      comboName: null,
      comboPrice: null,
      promotionId: null,
      promotionName: null,
      promotionDiscount: 0,
    });
  }
  renderCart();
}

function cambiarCantidad(variantId, delta) {
  const item = cart.find((i) => i.variantId === variantId && !i.comboId);
  if (!item) return;
  const nueva = item.quantity + delta;
  if (nueva <= 0) {
    cart = cart.filter((i) => i !== item);
  } else if (nueva > item.stock) {
    showToast({ type: 'warning', title: 'Stock insuficiente', message: `Solo hay ${item.stock} unidades disponibles.` });
    return;
  } else {
    item.quantity = nueva;
  }
  renderCart();
}

function quitarDelCarrito(variantId) {
  cart = cart.filter((i) => !(i.variantId === variantId && !i.comboId));
  renderCart();
}

function quitarCombo(comboId) {
  cart = cart.filter((i) => i.comboId !== comboId);
  renderCart();
}

async function abrirSelectorCombo() {
  let combos;
  try {
    combos = (await api.get('/combos')).filter((c) => c.status === 'ACTIVE');
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudieron cargar los combos' });
    return;
  }
  if (combos.length === 0) {
    showToast({ type: 'warning', title: 'Sin combos', message: 'No hay combos activos configurados.' });
    return;
  }

  const body = document.createElement('div');
  body.innerHTML = combos
    .map(
      (c) => `
    <button type="button" class="vp-result" data-combo="${c.id}" style="display:block; width:100%; text-align:left; padding:var(--space-3); border-bottom:1px solid var(--color-border);">
      <div style="display:flex; justify-content:space-between; font-weight:600;">
        <span>${escapeHtml(c.name)}</span><span class="mono">${formatCurrency(c.price)}</span>
      </div>
      <div style="font-size:var(--font-size-xs); color:var(--color-text-muted);">
        ${c.items.map(comboItemTexto).join(' + ')}
      </div>
    </button>
  `
    )
    .join('');

  const modal = openModal({ title: 'Elegir combo', body, maxWidth: '440px' });
  modal.body.querySelectorAll('[data-combo]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const combo = combos.find((c) => String(c.id) === btn.dataset.combo);
      closeModal();
      abrirFormularioComboItems(combo);
    });
  });
}

function comboItemTexto(it) {
  return it.selectorType === 'CATEGORY'
    ? `${it.quantity} × cualquier producto de ${escapeHtml(it.categoryName)}${it.brandName ? ` (marca ${escapeHtml(it.brandName)})` : ''}`
    : `${it.quantity} × ${escapeHtml(it.productName)}`;
}

function abrirFormularioComboItems(combo) {
  // Un picker por unidad — incluso una línea de "4 polos" puede terminar
  // siendo 4 variantes distintas (tallas/colores), no una sola repetida.
  const slots = combo.items.flatMap((it) => Array.from({ length: it.quantity }, () => it));
  const pickers = slots.map(() => createVariantPicker({ placeholder: 'Buscar variante…' }));

  const body = document.createElement('div');
  body.innerHTML = `
    <div class="alert alert-danger" id="combo-form-error" role="alert" hidden>
      <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
      <span class="alert-message"></span>
    </div>
    <div style="display:flex; flex-direction:column; gap:var(--space-4);">
      ${slots
        .map(
          (it, index) => `
        <div>
          <label class="field-label">${it.selectorType === 'CATEGORY' ? `Cualquier producto de ${it.categoryName}${it.brandName ? ` (marca ${it.brandName})` : ''}` : it.productName}</label>
          <div id="combo-item-picker-${index}"></div>
        </div>
      `
        )
        .join('')}
    </div>
  `;
  slots.forEach((it, index) => {
    body.querySelector(`#combo-item-picker-${index}`).appendChild(pickers[index].root);
  });

  const modal = openModal({
    title: combo.name,
    subtitle: `Precio del combo: ${formatCurrency(combo.price)}`,
    body,
    maxWidth: '480px',
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-primary" type="button" data-confirmar>Agregar al carrito</button>
    `,
  });

  modal.footer.querySelector('[data-cancel]').addEventListener('click', closeModal);
  modal.footer.querySelector('[data-confirmar]').addEventListener('click', () => {
    const errorAlert = modal.body.querySelector('#combo-form-error');
    errorAlert.hidden = true;

    const seleccionadas = pickers.map((p) => p.getSelected());
    if (seleccionadas.some((v) => !v)) {
      errorAlert.querySelector('.alert-message').textContent = 'Elige una variante para cada línea del combo.';
      errorAlert.hidden = false;
      return;
    }
    const sinStock = seleccionadas.find((v) => v.stock < 1);
    if (sinStock) {
      errorAlert.querySelector('.alert-message').textContent = `Stock insuficiente de ${sinStock.productName}.`;
      errorAlert.hidden = false;
      return;
    }

    seleccionadas.forEach((variante) => {
      cart.push({
        variantId: variante.variantId,
        productName: variante.productName,
        variantLabel: variante.variantLabel,
        sku: variante.sku,
        unitPrice: variante.effectivePrice,
        stock: variante.stock,
        quantity: 1,
        comboId: combo.id,
        comboName: combo.name,
        comboPrice: combo.price,
        promotionId: null,
        promotionName: null,
        promotionDiscount: 0,
      });
    });
    closeModal();
    renderCart();
  });
}

async function abrirSelectorPromocion(item) {
  let promociones;
  try {
    promociones = await api.get('/promotions/applicable', { query: { variantId: item.variantId } });
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudieron cargar las promociones' });
    return;
  }
  if (promociones.length === 0) {
    showToast({ type: 'warning', title: 'Sin promociones', message: 'No hay promociones vigentes para este producto.' });
    return;
  }

  const body = document.createElement('div');
  body.innerHTML = `
    <div style="display:flex; flex-direction:column;">
      ${
        item.promotionId
          ? `<button type="button" class="vp-result" data-quitar style="display:block; width:100%; text-align:left; padding:var(--space-3); border-bottom:1px solid var(--color-border); color:var(--color-danger-text);">Quitar promoción aplicada</button>`
          : ''
      }
      ${promociones
        .map(
          (p) => `
        <button type="button" class="vp-result" data-promo="${p.id}" style="display:block; width:100%; text-align:left; padding:var(--space-3); border-bottom:1px solid var(--color-border);">
          <div style="font-weight:600;">${escapeHtml(p.name)}</div>
          <div style="font-size:var(--font-size-xs); color:var(--color-text-muted);">${p.discountType === 'PERCENTAGE' ? `${p.discountValue}% de descuento` : `${formatCurrency(p.discountValue)} de descuento`}</div>
        </button>
      `
        )
        .join('')}
    </div>
  `;

  const modal = openModal({ title: 'Aplicar promoción', subtitle: item.productName, body, maxWidth: '400px' });
  modal.body.querySelector('[data-quitar]')?.addEventListener('click', () => {
    item.promotionId = null;
    item.promotionName = null;
    item.promotionDiscount = 0;
    closeModal();
    renderCart();
  });
  modal.body.querySelectorAll('[data-promo]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const promo = promociones.find((p) => String(p.id) === btn.dataset.promo);
      const bruto = item.unitPrice * item.quantity;
      const descuento = promo.discountType === 'PERCENTAGE' ? bruto * (promo.discountValue / 100) : Math.min(promo.discountValue, bruto);
      item.promotionId = promo.id;
      item.promotionName = promo.name;
      item.promotionDiscount = Math.round(descuento * 100) / 100;
      closeModal();
      renderCart();
    });
  });
}

function calcularTotales() {
  const subtotal = cart.reduce((acc, item) => acc + item.unitPrice * item.quantity, 0);
  const combosVistos = new Set(cart.filter((i) => i.comboId).map((i) => i.comboId));
  let descuento = 0;
  combosVistos.forEach((comboId) => {
    const itemsCombo = cart.filter((i) => i.comboId === comboId);
    const normal = itemsCombo.reduce((acc, i) => acc + i.unitPrice * i.quantity, 0);
    descuento += normal - itemsCombo[0].comboPrice;
  });
  descuento += cart.filter((i) => !i.comboId).reduce((acc, i) => acc + i.promotionDiscount, 0);
  return { subtotal, descuento, total: subtotal - descuento };
}

function itemIndividualHtml(item) {
  return `
    <div style="display:flex; gap:var(--space-3); padding:var(--space-3) 0; border-bottom:1px solid var(--color-border);">
      <div style="flex:1; min-width:0;">
        <div style="font-weight:600; font-size:var(--font-size-sm);">${escapeHtml(item.productName)}</div>
        <div class="table-cell-muted mono">${escapeHtml(item.variantLabel)}</div>
        <div style="display:flex; align-items:center; gap:var(--space-2); margin-top:var(--space-2);">
          <button class="btn btn-ghost btn-sm" type="button" data-qty-down="${item.variantId}" style="width:28px; padding:0;">−</button>
          <span class="mono" style="min-width:24px; text-align:center;">${item.quantity}</span>
          <button class="btn btn-ghost btn-sm" type="button" data-qty-up="${item.variantId}" style="width:28px; padding:0;">+</button>
        </div>
        ${
          permissions.has('PROMOCIONES_APLICAR')
            ? item.promotionId
              ? `<div style="margin-top:var(--space-2); font-size:var(--font-size-xs);"><span class="badge badge-success" data-promo-badge="${item.variantId}" style="cursor:pointer;">${escapeHtml(item.promotionName)} −${formatCurrency(item.promotionDiscount)}</span></div>`
              : `<button type="button" class="btn btn-ghost btn-sm" data-promo-badge="${item.variantId}" style="margin-top:var(--space-2); padding:0; font-size:var(--font-size-xs);">+ Promoción</button>`
            : ''
        }
      </div>
      <div style="text-align:right; display:flex; flex-direction:column; align-items:flex-end; justify-content:space-between;">
        <button class="btn btn-ghost btn-sm" type="button" data-remove="${item.variantId}" aria-label="Quitar">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round"/></svg>
        </button>
        <span class="mono" style="font-weight:600;">${formatCurrency(item.unitPrice * item.quantity - item.promotionDiscount)}</span>
      </div>
    </div>
  `;
}

function comboGroupHtml(comboId, items) {
  const normal = items.reduce((acc, i) => acc + i.unitPrice * i.quantity, 0);
  return `
    <div style="padding:var(--space-3) 0; border-bottom:1px solid var(--color-border); border-radius: var(--radius-md); background: var(--color-surface-sunken); margin: var(--space-2) 0; padding: var(--space-3);">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:var(--space-2);">
        <span class="badge badge-info">Combo · ${escapeHtml(items[0].comboName)}</span>
        <button class="btn btn-ghost btn-sm" type="button" data-remove-combo="${comboId}" aria-label="Quitar combo">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round"/></svg>
        </button>
      </div>
      ${items
        .map(
          (item) => `
        <div style="display:flex; justify-content:space-between; font-size:var(--font-size-sm); padding: 2px 0;">
          <span>${item.quantity} × ${escapeHtml(item.productName)} <span class="table-cell-muted mono">${escapeHtml(item.variantLabel)}</span></span>
        </div>
      `
        )
        .join('')}
      <div style="display:flex; justify-content:space-between; margin-top:var(--space-2); padding-top:var(--space-2); border-top:1px dashed var(--color-border); font-weight:600;">
        <span class="table-cell-muted" style="font-weight:400; text-decoration:line-through;">${formatCurrency(normal)}</span>
        <span class="mono">${formatCurrency(items[0].comboPrice)}</span>
      </div>
    </div>
  `;
}

function renderCart() {
  const container = document.querySelector('#cart-items');
  const combosVistos = [];
  const bloques = [];
  for (const item of cart) {
    if (item.comboId) {
      if (!combosVistos.includes(item.comboId)) {
        combosVistos.push(item.comboId);
        bloques.push(comboGroupHtml(item.comboId, cart.filter((i) => i.comboId === item.comboId)));
      }
    } else {
      bloques.push(itemIndividualHtml(item));
    }
  }

  container.innerHTML = cart.length
    ? bloques.join('')
    : `<div class="empty-state" style="padding: var(--space-8) 0;"><span>El carrito está vacío</span></div>`;

  container.querySelectorAll('[data-qty-up]').forEach((btn) => btn.addEventListener('click', () => cambiarCantidad(Number(btn.dataset.qtyUp), 1)));
  container.querySelectorAll('[data-qty-down]').forEach((btn) => btn.addEventListener('click', () => cambiarCantidad(Number(btn.dataset.qtyDown), -1)));
  container.querySelectorAll('[data-remove]').forEach((btn) => btn.addEventListener('click', () => quitarDelCarrito(Number(btn.dataset.remove))));
  container.querySelectorAll('[data-remove-combo]').forEach((btn) => btn.addEventListener('click', () => quitarCombo(Number(btn.dataset.removeCombo))));
  container.querySelectorAll('[data-promo-badge]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const item = cart.find((i) => i.variantId === Number(btn.dataset.promoBadge) && !i.comboId);
      if (item) abrirSelectorPromocion(item);
    });
  });

  const { subtotal, descuento, total } = calcularTotales();
  document.querySelector('#cart-subtotal').textContent = formatCurrency(subtotal);
  document.querySelector('#cart-discount').textContent = formatCurrency(descuento);
  document.querySelector('#cart-total').textContent = formatCurrency(total);
  document.querySelector('#btn-cobrar').disabled = cart.length === 0;
}

async function cobrar() {
  const { total } = calcularTotales();
  const cliente = customerPicker.getSelected();

  await openPagoModal({
    total,
    onConfirm: async ({ payments, promoterId }) => {
      const request = {
        customerId: cliente?.id ?? null,
        promoterId,
        cashSessionId: cashSession.id,
        discountAmount: 0,
        notes: null,
        items: cart.map((item) => ({
          variantId: item.variantId,
          quantity: item.quantity,
          discountAmount: 0,
          comboId: item.comboId,
          promotionId: item.promotionId,
        })),
        payments,
      };
      const venta = await api.post('/sales', request);
      mostrarTicket(venta);
      cart = [];
      customerPicker.clear();
      renderCart();
      cashSession = await fetchCurrentSession();
      actualizarEstadoCaja();
    },
  });
}

function mostrarTicket(venta) {
  const modal = openModal({
    title: '¡Venta registrada!',
    subtitle: venta.saleNumber,
    maxWidth: '380px',
    body: `
      <div style="display:flex; flex-direction:column; gap:var(--space-2); font-size:var(--font-size-sm);">
        ${venta.items
          .map(
            (item) => `
          <div style="display:flex; justify-content:space-between;">
            <span>${item.quantity} × ${escapeHtml(item.productName)} (${escapeHtml(item.variantLabel)})</span>
            <span class="mono">${formatCurrency(item.subtotal)}</span>
          </div>
        `
          )
          .join('')}
        <div style="display:flex; justify-content:space-between; font-weight:700; font-size:var(--font-size-lg); margin-top:var(--space-3); padding-top:var(--space-3); border-top:1px solid var(--color-border);">
          <span>Total</span><span class="mono">${formatCurrency(venta.total)}</span>
        </div>
      </div>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-imprimir>Imprimir ticket</button>
      <button class="btn btn-secondary" type="button" data-comprobante>Emitir comprobante</button>
      <button class="btn btn-dark btn-block" type="button" data-close>Nueva venta</button>
    `,
  });
  modal.footer.querySelector('[data-imprimir]').addEventListener('click', () => imprimirTicket(venta));
  modal.footer.querySelector('[data-comprobante]').addEventListener('click', () => abrirDocumentosVenta(venta));
  modal.footer.querySelector('[data-close]').addEventListener('click', () => {
    closeModal();
    document.querySelector('#pos-scan-input').focus();
  });
  showToast({ type: 'success', title: 'Venta registrada', message: `${venta.saleNumber} · ${formatCurrency(venta.total)}` });
}

async function cargarHistorial() {
  const body = document.querySelector('#historial-body');
  try {
    const from = document.querySelector('#hist-from').value;
    const to = document.querySelector('#hist-to').value;
    const page = await api.get('/sales', {
      query: {
        status: document.querySelector('#hist-status').value || undefined,
        from: from ? `${from}T00:00:00` : undefined,
        to: to ? `${to}T23:59:59` : undefined,
        page: historialPage,
        size: 20,
        sort: 'createdAt,desc',
      },
    });

    body.innerHTML = page.content.length
      ? page.content
          .map(
            (v) => `
        <tr>
          <td class="table-cell-primary mono">${escapeHtml(v.saleNumber)}</td>
          <td class="table-cell-muted">${formatDateTime(v.createdAt)}</td>
          <td>${v.customerName ? escapeHtml(v.customerName) : '—'}</td>
          <td>${escapeHtml(v.sellerName)}</td>
          <td class="mono">${formatCurrency(v.total)}</td>
          <td><span class="badge ${SALE_STATUS_CLASSES[v.status] ?? 'badge-neutral'}">${SALE_STATUS_LABELS[v.status] ?? v.status}</span></td>
          <td>
            <button class="btn btn-ghost btn-sm" type="button" data-ver="${v.id}">Ver</button>
          </td>
        </tr>
      `
          )
          .join('')
      : `<tr><td colspan="7"><div class="empty-state"><span>No se encontraron ventas.</span></div></td></tr>`;

    body.querySelectorAll('[data-ver]').forEach((btn) => {
      btn.addEventListener('click', () => verDetalleVenta(Number(btn.dataset.ver)));
    });

    renderPagination(document.querySelector('#historial-pagination'), page, (p) => {
      historialPage = p;
      cargarHistorial();
    });
  } catch (error) {
    body.innerHTML = `<tr><td colspan="7"><div class="empty-state"><span>${error instanceof ApiError ? error.message : 'Error al cargar el historial'}</span></div></td></tr>`;
  }
}

async function verDetalleVenta(saleId) {
  let venta;
  try {
    venta = await api.get(`/sales/${saleId}`);
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo cargar la venta' });
    return;
  }

  const puedeAnular = venta.status === 'COMPLETED' && permissions.has('VENTAS_ANULAR');
  const puedeDevolver = (venta.status === 'COMPLETED' || venta.status === 'PARTIALLY_RETURNED') && permissions.has('VENTAS_DEVOLVER');

  const modal = openModal({
    title: venta.saleNumber,
    subtitle: `${formatDateTime(venta.createdAt)} · Vendedor: ${venta.sellerName}${venta.customerName ? ` · Cliente: ${venta.customerName}` : ''}${venta.promoterName ? ` · Promotor: ${venta.promoterName}` : ''}`,
    maxWidth: '480px',
    body: `
      <div style="display:flex; flex-direction:column; gap:var(--space-2); font-size:var(--font-size-sm);">
        ${venta.items
          .map(
            (item) => `
          <div style="display:flex; justify-content:space-between;">
            <span>${item.quantity} × ${escapeHtml(item.productName)} (${escapeHtml(item.variantLabel)})</span>
            <span class="mono">${formatCurrency(item.subtotal)}</span>
          </div>
        `
          )
          .join('')}
        <div style="display:flex; justify-content:space-between; font-weight:700; font-size:var(--font-size-lg); margin-top:var(--space-3); padding-top:var(--space-3); border-top:1px solid var(--color-border);">
          <span>Total</span><span class="mono">${formatCurrency(venta.total)}</span>
        </div>
        <div style="margin-top: var(--space-3); padding-top: var(--space-3); border-top:1px solid var(--color-border);">
          <div style="font-weight:600; margin-bottom: var(--space-2);">Pagos</div>
          ${venta.payments.map((p) => `<div style="display:flex; justify-content:space-between;"><span>${escapeHtml(p.paymentMethodName)}${p.reference ? ` (${escapeHtml(p.reference)})` : ''}</span><span class="mono">${formatCurrency(p.amount)}</span></div>`).join('')}
        </div>
        ${
          venta.status !== 'COMPLETED'
            ? `<div class="alert alert-warning" style="margin-top: var(--space-3);"><span class="alert-message">${
                venta.status === 'CANCELLED'
                  ? `Anulada${venta.cancelledByUsername ? ` por ${escapeHtml(venta.cancelledByUsername)}` : ''}: ${escapeHtml(venta.cancellationReason ?? '')}`
                  : escapeHtml(SALE_STATUS_LABELS[venta.status] ?? venta.status)
              }</span></div>`
            : ''
        }
      </div>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel-modal>Cerrar</button>
      <button class="btn btn-secondary" type="button" data-imprimir>Imprimir ticket</button>
      ${venta.status === 'COMPLETED' ? '<button class="btn btn-secondary" type="button" data-comprobante>Comprobante</button>' : ''}
      ${puedeDevolver ? `<button class="btn btn-secondary" type="button" data-devolver>Devolver</button>` : ''}
      ${puedeAnular ? `<button class="btn btn-danger" type="button" data-anular>Anular venta</button>` : ''}
    `,
  });

  modal.footer.querySelector('[data-cancel-modal]').addEventListener('click', () => closeModal());
  modal.footer.querySelector('[data-imprimir]').addEventListener('click', () => imprimirTicket(venta));
  modal.footer.querySelector('[data-comprobante]')?.addEventListener('click', () => abrirDocumentosVenta(venta));
  modal.footer.querySelector('[data-devolver]')?.addEventListener('click', () => abrirFormularioDevolucion(venta));
  modal.footer.querySelector('[data-anular]')?.addEventListener('click', () => anularVenta(venta));
}

const ELECTRONIC_DOCUMENT_LABELS = {
  BOLETA: 'Boleta',
  FACTURA: 'Factura',
  NOTA_CREDITO: 'Nota de crédito',
  NOTA_DEBITO: 'Nota de débito',
};

const BILLING_PROVIDER_LABELS = {
  VERIFACT: 'Verifac',
  NUBEFACT: 'NubeFact',
};

const ELECTRONIC_DOCUMENT_STATUS_LABELS = {
  DRAFT: 'Borrador',
  GENERATED: 'Generado',
  PENDING: 'Pendiente',
  SENT: 'Enviado',
  ACCEPTED: 'Aceptado',
  REJECTED: 'Rechazado',
  CANCELLED: 'Anulado',
  ERROR: 'Error',
};

const ELECTRONIC_DOCUMENT_STATUS_CLASSES = {
  ACCEPTED: 'badge-success',
  REJECTED: 'badge-danger',
  ERROR: 'badge-danger',
  PENDING: 'badge-warning',
  SENT: 'badge-info',
  DRAFT: 'badge-neutral',
};

function fingerprintText(value) {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = ((hash << 5) - hash) + value.charCodeAt(index);
    hash |= 0;
  }
  return (hash >>> 0).toString(36);
}

const ELECTRONIC_NOTE_REASONS = {
  NOTA_CREDITO: [
    ['01', 'AnulaciÃ³n de la operaciÃ³n'],
    ['02', 'AnulaciÃ³n por error en el RUC'],
    ['03', 'CorrecciÃ³n por error en la descripciÃ³n'],
    ['04', 'Descuento global'],
    ['05', 'Descuento por Ã­tem'],
    ['06', 'DevoluciÃ³n total'],
    ['07', 'DevoluciÃ³n por Ã­tem'],
    ['08', 'BonificaciÃ³n'],
    ['09', 'DisminuciÃ³n en el valor'],
    ['10', 'Otros conceptos'],
    ['11', 'Ajuste de exportaciÃ³n'],
    ['12', 'Ajuste afecto al IVAP'],
    ['13', 'CorrecciÃ³n del monto neto pendiente'],
  ],
  NOTA_DEBITO: [
    ['01', 'Intereses por mora'],
    ['02', 'Aumento en el valor'],
    ['03', 'Penalidades / otros conceptos'],
  ],
};

async function abrirDocumentosVenta(venta) {
  let documentos;
  try {
    documentos = await api.get(`/sales/${venta.id}/electronic-documents`);
  } catch (error) {
    showToast({ type: 'danger', title: 'Comprobantes', message: error instanceof ApiError ? error.message : 'No se pudieron cargar los comprobantes' });
    return;
  }

  const renderDocumentos = () => documentos.length
    ? documentos.map((documento) => `
        <div style="display:flex; justify-content:space-between; gap:var(--space-3); align-items:center; padding:var(--space-3) 0; border-bottom:1px solid var(--color-border);">
          <div>
            <div style="font-weight:600;">${escapeHtml(ELECTRONIC_DOCUMENT_LABELS[documento.documentType] ?? documento.documentType)} ${escapeHtml(documento.series ?? '')}${documento.documentNumber ? `-${escapeHtml(documento.documentNumber)}` : ''}</div>
            <div style="font-size:var(--font-size-xs); color:var(--color-text-secondary);">${documento.providerDocumentId ? `${escapeHtml(BILLING_PROVIDER_LABELS[documento.provider] ?? documento.provider ?? 'Proveedor')} · ID: ${escapeHtml(documento.providerDocumentId)}` : 'Sin identificador externo todavía'}</div>
          </div>
          <div style="display:flex; flex-direction:column; align-items:flex-end; gap:var(--space-2);"><span class="badge ${ELECTRONIC_DOCUMENT_STATUS_CLASSES[documento.status] ?? 'badge-neutral'}">${escapeHtml(ELECTRONIC_DOCUMENT_STATUS_LABELS[documento.status] ?? documento.status)}</span>${['PENDING', 'SENT'].includes(documento.status) && documento.providerDocumentId ? '<button class="btn btn-ghost btn-sm" type="button" data-doc-status data-doc-id="' + documento.id + '">Actualizar</button>' : ''}${['ERROR', 'REJECTED'].includes(documento.status) && documento.providerDocumentId ? '<button class="btn btn-secondary btn-sm" type="button" data-doc-retry data-doc-id="' + documento.id + '">Reintentar</button>' : ''}${documento.providerDocumentId && documento.status === 'ACCEPTED' ? '<div style="display:flex; gap:var(--space-1);"><button class="btn btn-ghost btn-sm" type="button" data-doc-download="pdf" data-doc-id="' + documento.id + '">PDF</button><button class="btn btn-ghost btn-sm" type="button" data-doc-download="xml" data-doc-id="' + documento.id + '">XML</button><button class="btn btn-ghost btn-sm" type="button" data-doc-download="cdr" data-doc-id="' + documento.id + '">CDR</button></div>' : ''}${documento.providerDocumentId && documento.status === 'ACCEPTED' && ['BOLETA', 'FACTURA'].includes(documento.documentType) ? '<div style="display:flex; gap:var(--space-1);"><button class="btn btn-ghost btn-sm" type="button" data-create-note="NOTA_CREDITO" data-source-id="' + documento.id + '">NC</button><button class="btn btn-ghost btn-sm" type="button" data-create-note="NOTA_DEBITO" data-source-id="' + documento.id + '">ND</button></div>' : ''}</div>
        </div>
      `).join('')
    : '<div class="empty-state" style="padding:var(--space-5) 0;"><span>Esta venta todavía no tiene comprobante.</span></div>';

  const modal = openModal({
    title: 'Comprobante electrónico',
    subtitle: venta.saleNumber,
    maxWidth: '520px',
    body: `
      <div id="documentos-venta-body">
        <div id="documentos-list">${renderDocumentos()}</div>
        <form id="comprobante-form" style="margin-top:var(--space-5);">
          <div class="field">
            <label class="field-label" for="comprobante-tipo">Tipo de comprobante</label>
            <select class="select" id="comprobante-tipo" required>
              <option value="BOLETA">Boleta</option>
              <option value="FACTURA">Factura</option>
              <option value="NOTA_CREDITO">Nota de crédito</option>
              <option value="NOTA_DEBITO">Nota de débito</option>
            </select>
          </div>
          <div id="nota-fields" hidden>
            <div class="field">
              <label class="field-label" for="nota-origen">Comprobante de origen</label>
              <select class="select" id="nota-origen"></select>
            </div>
            <div class="field">
              <label class="field-label" for="nota-motivo">Motivo fiscal</label>
              <select class="select" id="nota-motivo"></select>
            </div>
            <div class="field">
              <label class="field-label" for="nota-descripcion">Descripción del motivo</label>
              <textarea class="input" id="nota-descripcion" maxlength="250" rows="2" placeholder="Describe el motivo de la nota"></textarea>
            </div>
            <div class="field" id="nota-items" hidden></div>
          </div>
          <p style="margin:var(--space-3) 0 0; color:var(--color-text-secondary); font-size:var(--font-size-xs);">
            La empresa debe tener facturación electrónica habilitada y sus credenciales/series del proveedor configuradas.
          </p>
          <div class="alert alert-danger" id="comprobante-error" role="alert" hidden><span class="alert-message"></span></div>
        </form>
      </div>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-cerrar-comprobante>Cerrar</button>
      <button class="btn btn-primary" type="submit" form="comprobante-form" data-emitir-comprobante>Crear y enviar</button>
    `,
  });

  modal.footer.querySelector('[data-cerrar-comprobante]').addEventListener('click', () => modal.close());
  const typeSelect = modal.body.querySelector('#comprobante-tipo');
  const noteFields = modal.body.querySelector('#nota-fields');
  const sourceSelect = modal.body.querySelector('#nota-origen');
  const reasonSelect = modal.body.querySelector('#nota-motivo');
  const noteItems = modal.body.querySelector('#nota-items');
  const syncNoteFields = () => {
    const isNote = typeSelect.value === 'NOTA_CREDITO' || typeSelect.value === 'NOTA_DEBITO';
    noteFields.hidden = !isNote;
    sourceSelect.required = isNote;
    reasonSelect.required = isNote;
    modal.body.querySelector('#nota-descripcion').required = isNote;
    if (isNote) {
      const sources = documentos.filter((item) => item.status === 'ACCEPTED' && ['BOLETA', 'FACTURA'].includes(item.documentType));
      sourceSelect.innerHTML = sources.length
        ? sources.map((item) => `<option value="${item.id}">${escapeHtml(ELECTRONIC_DOCUMENT_LABELS[item.documentType] ?? item.documentType)} ${escapeHtml(item.series)}-${escapeHtml(item.documentNumber)}</option>`).join('')
        : '<option value="">No hay comprobantes aceptados</option>';
      const previousReason = reasonSelect.value;
      reasonSelect.innerHTML = (ELECTRONIC_NOTE_REASONS[typeSelect.value] ?? [])
        .map(([code, label]) => `<option value="${code}">${code} · ${label}</option>`).join('');
      if (Array.from(reasonSelect.options).some((option) => option.value === previousReason)) {
        reasonSelect.value = previousReason;
      }
      const partial = typeSelect.value === 'NOTA_CREDITO' && reasonSelect.value === '07';
      noteItems.hidden = !partial;
      noteItems.innerHTML = partial
        ? `<label class="field-label">Productos y cantidades a devolver</label>${venta.items.map((item) => `
            <label style="display:flex; align-items:center; gap:var(--space-2); margin-top:var(--space-2);">
              <input type="checkbox" data-note-variant="${item.variantId}" />
              <span style="flex:1;">${escapeHtml(item.productName)} (${escapeHtml(item.variantLabel)})</span>
              <input class="input" style="width:80px;" type="number" min="1" max="${item.quantity}" value="${item.quantity}" data-note-quantity="${item.variantId}" disabled />
            </label>`).join('')}`
        : '';
      noteItems.querySelectorAll('[data-note-variant]').forEach((checkbox) => {
        checkbox.addEventListener('change', () => {
          const quantity = noteItems.querySelector(`[data-note-quantity="${checkbox.dataset.noteVariant}"]`);
          if (quantity) quantity.disabled = !checkbox.checked;
        });
      });
    }
  };
  typeSelect.addEventListener('change', syncNoteFields);
  reasonSelect.addEventListener('change', syncNoteFields);
  syncNoteFields();
  modal.body.addEventListener('click', async (event) => {
    const button = event.target.closest('[data-doc-status], [data-doc-retry], [data-doc-download], [data-create-note]');
    if (!button) return;
    if (button.dataset.createNote) {
      typeSelect.value = button.dataset.createNote;
      syncNoteFields();
      sourceSelect.value = button.dataset.sourceId;
      modal.body.querySelector('#nota-descripcion').focus();
      return;
    }
    const documentId = Number(button.dataset.docId || 0);
    const documento = documentos.find((item) => item.id === documentId);
    if (!documento) return;
    button.disabled = true;
    try {
      if (button.dataset.docDownload) {
        const file = await apiDownload(`/electronic-documents/${documentId}/${button.dataset.docDownload}`);
        const url = URL.createObjectURL(file.blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = file.filename;
        anchor.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      } else {
        const actualizado = button.dataset.docStatus
          ? await api.get(`/electronic-documents/${documentId}/status`)
          : await api.post(`/electronic-documents/${documentId}/retry`);
        documentos = [actualizado, ...documentos.filter((item) => item.id !== actualizado.id)];
        modal.body.querySelector('#documentos-list').innerHTML = renderDocumentos();
        showToast({ type: actualizado.status === 'ACCEPTED' ? 'success' : 'warning', title: 'Comprobante', message: ELECTRONIC_DOCUMENT_STATUS_LABELS[actualizado.status] ?? actualizado.status });
      }
    } catch (error) {
      showToast({ type: 'danger', title: 'Comprobante', message: error instanceof ApiError ? error.message : 'No se pudo completar la operación' });
    } finally {
      button.disabled = false;
    }
  });
  modal.body.querySelector('#comprobante-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const submit = modal.footer.querySelector('[data-emitir-comprobante]');
    const errorAlert = modal.body.querySelector('#comprobante-error');
    const errorMessage = errorAlert.querySelector('.alert-message');
    submit.disabled = true;
    submit.textContent = 'Enviando…';
    errorAlert.hidden = true;
    try {
      const documentType = modal.body.querySelector('#comprobante-tipo').value;
      const isNote = documentType === 'NOTA_CREDITO' || documentType === 'NOTA_DEBITO';
      const sourceDocumentId = isNote ? Number(modal.body.querySelector('#nota-origen').value || 0) : null;
      const reasonCode = isNote ? modal.body.querySelector('#nota-motivo').value : null;
      const reasonDescription = isNote ? modal.body.querySelector('#nota-descripcion').value.trim() : null;
      const selectedItems = isNote && reasonCode === '07'
        ? Array.from(modal.body.querySelectorAll('[data-note-variant]:checked')).map((checkbox) => ({
          variantId: Number(checkbox.dataset.noteVariant),
          quantity: Number(modal.body.querySelector(`[data-note-quantity="${checkbox.dataset.noteVariant}"]`).value),
        }))
        : [];
      if (isNote && reasonCode === '07' && selectedItems.length === 0) {
        errorMessage.textContent = 'Selecciona al menos un producto para la devolución.';
        errorAlert.hidden = false;
        submit.disabled = false;
        submit.textContent = 'Crear y enviar';
        return;
      }
      const label = ELECTRONIC_DOCUMENT_LABELS[documentType] ?? documentType;
      const confirmado = await confirmAction({
        title: `Confirmar ${isNote ? 'envio de nota' : 'emision'}`,
        message: `Se enviara ${label.toLowerCase()} al proveedor configurado para esta empresa y se solicitara su procesamiento ante SUNAT. Esta operacion no debe repetirse fuera del sistema.`,
        confirmLabel: 'Enviar a SUNAT',
        danger: false,
      });
      if (!confirmado) {
        submit.disabled = false;
        submit.textContent = 'Crear y enviar';
        return;
      }
      const idempotencyKey = isNote
        ? `sale-${venta.id}-${documentType}-source-${sourceDocumentId}-reason-${reasonCode}`
        : `sale-${venta.id}-${documentType}`;
      const itemsFingerprint = selectedItems.length
        ? fingerprintText(selectedItems.map((item) => `${item.variantId}x${item.quantity}`).join('_'))
        : null;
      const borrador = await api.post(`/sales/${venta.id}/electronic-documents`, {
        documentType,
        sourceDocumentId: isNote ? sourceDocumentId : undefined,
        reasonCode: isNote ? reasonCode : undefined,
        reasonDescription: isNote ? reasonDescription : undefined,
        items: selectedItems.length ? selectedItems : undefined,
      }, {
        headers: { 'Idempotency-Key': itemsFingerprint ? `${idempotencyKey}-items-${itemsFingerprint}` : idempotencyKey },
      });
      const enviado = await api.post(`/electronic-documents/${borrador.id}/submit`);
      documentos = [enviado, ...documentos.filter((item) => item.id !== enviado.id)];
      modal.body.querySelector('#documentos-list').innerHTML = renderDocumentos();
      errorAlert.className = `alert ${enviado.status === 'ACCEPTED' ? 'alert-success' : enviado.status === 'ERROR' || enviado.status === 'REJECTED' ? 'alert-danger' : 'alert-warning'}`;
      const proveedor = BILLING_PROVIDER_LABELS[enviado.provider] ?? enviado.provider ?? 'el proveedor configurado';
      errorMessage.textContent = enviado.status === 'ACCEPTED'
        ? `Comprobante aceptado por ${proveedor}.`
        : `Estado actual: ${ELECTRONIC_DOCUMENT_STATUS_LABELS[enviado.status] ?? enviado.status}.`;
      errorAlert.hidden = false;
      submit.disabled = false;
      submit.textContent = 'Crear y enviar';
      syncNoteFields();
      showToast({ type: enviado.status === 'ACCEPTED' ? 'success' : 'warning', title: 'Comprobante', message: ELECTRONIC_DOCUMENT_STATUS_LABELS[enviado.status] ?? enviado.status });
    } catch (error) {
      errorMessage.textContent = error instanceof ApiError ? error.message : 'No se pudo emitir el comprobante';
      errorAlert.hidden = false;
      submit.disabled = false;
      submit.textContent = 'Crear y enviar';
    }
  });
}

async function anularVenta(venta) {
  const modal = openModal({
    title: 'Anular venta',
    subtitle: venta.saleNumber,
    maxWidth: '400px',
    body: `
      <form id="anular-form" novalidate>
        <div class="alert alert-danger" id="anular-error" role="alert" hidden>
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
          <span class="alert-message"></span>
        </div>
        <div class="field">
          <label class="field-label" for="anular-reason">Motivo</label>
          <input class="input" id="anular-reason" maxlength="255" required autofocus />
          <small class="field-hint">Si la venta tiene una factura o boleta aceptada, se generará automáticamente la nota de crédito fiscal.</small>
        </div>
      </form>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-danger" type="submit" form="anular-form">Anular venta</button>
    `,
  });
  modal.footer.querySelector('[data-cancel]').addEventListener('click', () => closeModal());
  modal.body.querySelector('#anular-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const submit = modal.footer.querySelector('button[type="submit"]');
    const errorAlert = modal.body.querySelector('#anular-error');
    const reason = modal.body.querySelector('#anular-reason').value.trim();
    if (!reason) {
      errorAlert.querySelector('.alert-message').textContent = 'Ingresa el motivo de la anulacion.';
      errorAlert.hidden = false;
      return;
    }

    const confirmado = await confirmAction({
      title: 'Confirmar anulacion de venta',
      message: `Se anulara la venta ${escapeHtml(venta.saleNumber)}. Si tiene una factura o boleta aceptada, se generara y enviara una nota de credito de anulacion al proveedor configurado antes de completar la anulacion local.`,
      confirmLabel: 'Anular y enviar',
      danger: true,
    });
    if (!confirmado) return;

    submit.disabled = true;
    try {
      await api.post(`/sales/${venta.id}/cancel`, { reason });
      closeModal();
      showToast({ type: 'success', title: 'Venta anulada', message: venta.saleNumber });
      cargarHistorial();
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudo anular la venta';
      errorAlert.hidden = false;
      submit.disabled = false;
    }
  });
}

async function abrirFormularioDevolucion(venta) {
  let items;
  let metodos;
  try {
    [items, metodos] = await Promise.all([
      api.get(`/sales/${venta.id}/returnable-items`),
      api.get('/payment-methods'),
    ]);
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo cargar la venta' });
    return;
  }

  const devolvibles = items.filter((i) => i.quantityReturnable > 0);
  if (devolvibles.length === 0) {
    showToast({ type: 'warning', title: 'Nada por devolver', message: 'Todos los artículos de esta venta ya fueron devueltos.' });
    return;
  }
  const metodosActivos = metodos.filter((m) => m.status === 'ACTIVE');

  const modal = openModal({
    title: 'Registrar devolución',
    subtitle: venta.saleNumber,
    maxWidth: '520px',
    body: `
      <form id="devolucion-form" novalidate>
        <div class="alert alert-danger" id="devolucion-error" role="alert" hidden>
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
          <span class="alert-message"></span>
        </div>
        <div style="display:flex; flex-direction:column; gap:var(--space-3); margin-bottom: var(--space-4);">
          ${devolvibles
            .map(
              (item) => `
            <div style="border:1px solid var(--color-border); border-radius: var(--radius-md); padding: var(--space-3);">
              <div style="display:flex; justify-content:space-between; align-items:center; gap:var(--space-3);">
                <div>
                  <div style="font-weight:600; font-size:var(--font-size-sm);">${escapeHtml(item.productName)}</div>
                  <div class="table-cell-muted mono">${escapeHtml(item.variantSku)} · disponible para devolver: ${item.quantityReturnable}</div>
                </div>
                <input class="input" type="number" min="0" max="${item.quantityReturnable}" value="0" style="width:80px; text-align:right;" data-dev-qty="${item.saleDetailId}" />
              </div>
              <label class="checkbox-field" style="margin-top: var(--space-2);">
                <input type="checkbox" data-dev-restock="${item.saleDetailId}" checked /> Reingresar a stock
              </label>
            </div>
          `
            )
            .join('')}
        </div>
        <div class="form-grid">
          <div class="field field-span-2">
            <label class="field-label" for="dev-reason">Motivo</label>
            <input class="input" id="dev-reason" maxlength="255" required />
          </div>
          <div class="field field-span-2">
            <label class="field-label" for="dev-refund-method">Método de reembolso</label>
            <select class="select" id="dev-refund-method" required>
              ${metodosActivos.map((m) => `<option value="${m.id}">${m.name}</option>`).join('')}
            </select>
          </div>
        </div>
      </form>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-primary" type="submit" form="devolucion-form">Registrar devolución</button>
    `,
  });

  modal.footer.querySelector('[data-cancel]').addEventListener('click', () => closeModal());
  modal.body.querySelector('#devolucion-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = modal.body.querySelector('#devolucion-error');
    errorAlert.hidden = true;

    const itemsSeleccionados = devolvibles
      .map((item) => ({
        saleDetailId: item.saleDetailId,
        quantity: Number(modal.body.querySelector(`[data-dev-qty="${item.saleDetailId}"]`).value) || 0,
        restock: modal.body.querySelector(`[data-dev-restock="${item.saleDetailId}"]`).checked,
      }))
      .filter((item) => item.quantity > 0);

    if (itemsSeleccionados.length === 0) {
      errorAlert.querySelector('.alert-message').textContent = 'Indica la cantidad a devolver de al menos un artículo.';
      errorAlert.hidden = false;
      return;
    }

    try {
      const devolucion = await api.post('/returns', {
        saleId: venta.id,
        reason: modal.body.querySelector('#dev-reason').value.trim(),
        refundMethodId: Number(modal.body.querySelector('#dev-refund-method').value),
        items: itemsSeleccionados,
      });
      closeModal();
      showToast({ type: 'success', title: 'Devolución registrada', message: `${devolucion.returnNumber} · ${formatCurrency(devolucion.totalAmount)}` });
      cargarHistorial();
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudo registrar la devolución';
      errorAlert.hidden = false;
    }
  });
}

async function cargarDevoluciones() {
  const body = document.querySelector('#devoluciones-body');
  try {
    const page = await api.get('/returns', { query: { page: devolucionesPage, size: 20, sort: 'createdAt,desc' } });

    body.innerHTML = page.content.length
      ? page.content
          .map(
            (d) => `
        <tr>
          <td class="table-cell-primary mono">${d.returnNumber}</td>
          <td class="mono">${d.saleNumber}</td>
          <td class="table-cell-muted">${formatDateTime(d.createdAt)}</td>
          <td>${d.reason}</td>
          <td>${d.refundMethodName}</td>
          <td class="mono">${formatCurrency(d.totalAmount)}</td>
          <td>${d.username}</td>
        </tr>
      `
          )
          .join('')
      : `<tr><td colspan="7"><div class="empty-state"><span>No se han registrado devoluciones.</span></div></td></tr>`;

    renderPagination(document.querySelector('#devoluciones-pagination'), page, (p) => {
      devolucionesPage = p;
      cargarDevoluciones();
    });
  } catch (error) {
    body.innerHTML = `<tr><td colspan="7"><div class="empty-state"><span>${error instanceof ApiError ? error.message : 'Error al cargar devoluciones'}</span></div></td></tr>`;
  }
}

async function cargarPromotores() {
  const body = document.querySelector('#promotores-body');
  try {
    promotoresCache = await api.get('/promoters');
    body.innerHTML = promotoresCache.length
      ? promotoresCache
          .map(
            (p) => `
        <tr>
          <td class="table-cell-primary">${escapeHtml(p.name)}</td>
          <td>${statusBadge(p.status)}</td>
          <td>
            <div class="table-actions">
              <button class="btn btn-ghost btn-sm" type="button" data-editar-promotor="${p.id}">Editar</button>
              <button class="btn btn-ghost btn-sm" type="button" data-toggle-promotor="${p.id}" data-status="${p.status}">
                ${p.status === 'ACTIVE' ? 'Desactivar' : 'Activar'}
              </button>
            </div>
          </td>
        </tr>
      `
          )
          .join('')
      : `<tr><td colspan="3"><div class="empty-state"><span>Todavía no hay promotores registrados.</span></div></td></tr>`;

    body.querySelectorAll('[data-editar-promotor]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const promotor = promotoresCache.find((p) => String(p.id) === btn.dataset.editarPromotor);
        abrirFormularioPromotor(promotor);
      });
    });
    body.querySelectorAll('[data-toggle-promotor]').forEach((btn) => {
      btn.addEventListener('click', () => cambiarEstadoPromotor(btn.dataset.togglePromotor, btn.dataset.status));
    });
  } catch (error) {
    body.innerHTML = `<tr><td colspan="3"><div class="empty-state"><span>${error instanceof ApiError ? error.message : 'Error al cargar promotores'}</span></div></td></tr>`;
  }
}

function abrirFormularioPromotor(promotor) {
  const esEdicion = Boolean(promotor);
  const modal = openModal({
    title: esEdicion ? 'Editar promotor' : 'Nuevo promotor',
    maxWidth: '380px',
    body: `
      <form id="promotor-form" novalidate>
        <div class="alert alert-danger" id="promotor-form-error" role="alert" hidden>
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
          <span class="alert-message"></span>
        </div>
        <div class="field">
          <label class="field-label" for="pm-nombre">Nombre</label>
          <input class="input" id="pm-nombre" maxlength="120" required value="${promotor?.name ?? ''}" />
        </div>
      </form>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-primary" type="submit" form="promotor-form">${esEdicion ? 'Guardar cambios' : 'Crear'}</button>
    `,
  });
  modal.footer.querySelector('[data-cancel]').addEventListener('click', () => closeModal());
  modal.body.querySelector('#promotor-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = modal.body.querySelector('#promotor-form-error');
    const nombre = modal.body.querySelector('#pm-nombre').value.trim();
    try {
      if (esEdicion) {
        await api.put(`/promoters/${promotor.id}`, { name: nombre });
      } else {
        await api.post('/promoters', { name: nombre });
      }
      closeModal();
      showToast({ type: 'success', title: esEdicion ? 'Promotor actualizado' : 'Promotor creado' });
      cargarPromotores();
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudo guardar';
      errorAlert.hidden = false;
    }
  });
}

async function cambiarEstadoPromotor(id, currentStatus) {
  const nuevoEstado = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
  try {
    await api.patch(`/promoters/${id}/status`, { status: nuevoEstado });
    showToast({ type: 'success', title: 'Estado actualizado' });
    cargarPromotores();
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo actualizar' });
  }
}
