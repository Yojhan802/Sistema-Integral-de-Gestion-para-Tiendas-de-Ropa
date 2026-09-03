import { requireSession } from '../core/auth.js';
import { api, ApiError } from '../core/api.js';
import { renderShell, actualizarEstadoCaja } from '../components/shell.js';
import { renderPagination } from '../components/pagination.js';
import { openModal, closeModal } from '../components/modal.js';
import { confirmAction } from '../components/confirm.js';
import { showToast } from '../components/toast.js';
import { createCustomerPicker } from '../components/customer-picker.js';
import { createVariantPicker } from '../components/variant-picker.js';
import { openPagoModal } from '../components/pago-modal.js';
import { openAbrirCajaModal } from '../components/abrir-caja.js';
import { fetchCurrentSession } from '../core/cash-session.js';
import { fetchCompanySettings } from '../core/settings.js';
import { formatCurrency, formatDateTime, escapeHtml } from '../core/format.js';
import { debounce } from '../core/debounce.js';

const STATUS_LABELS = { RESERVADO: 'Reservado', COMPLETADO: 'Completado', CANCELADO: 'Cancelado', VENCIDO: 'Vencido' };
const STATUS_CLASSES = { RESERVADO: 'badge-warning', COMPLETADO: 'badge-success', CANCELADO: 'badge-danger', VENCIDO: 'badge-neutral' };

let state = { page: 0, status: '', buyerName: '' };
let seleccionadas = new Map(); // id -> { customerName, guest }
let permissions = new Set();

// Estado del carrito de la modal "Nueva separación" — un solo flujo puede
// estar abierto a la vez (igual que el resto de modales del panel), así que
// vive a nivel de módulo, igual que el carrito del POS (pos.js).
let reservaCart = [];
let reservaComboGroupCounter = 0;
let reservaMainView = null;
let reservaComboView = null;
let reservaFooterMain = null;
let reservaFooterCombo = null;

function reservaStatusBadge(status) {
  return `<span class="badge ${STATUS_CLASSES[status] || 'badge-neutral'}">${STATUS_LABELS[status] || status}</span>`;
}

function init() {
  document.querySelector('#filter-status').addEventListener('change', (event) => {
    state.status = event.target.value;
    state.page = 0;
    cargarSeparaciones();
  });
  document.querySelector('#filter-buyer').addEventListener('input', debounce((event) => {
    state.buyerName = event.target.value.trim();
    state.page = 0;
    cargarSeparaciones();
  }, 350));
  document.querySelector('#btn-nueva-separacion').addEventListener('click', abrirFormularioNuevaSeparacion);
  document.querySelector('#btn-completar-seleccionadas').addEventListener('click', completarSeleccionadas);
  cargarSeparaciones();
}

function actualizarBarraSeleccion() {
  const bar = document.querySelector('#batch-actions-bar');
  bar.hidden = seleccionadas.size === 0;
  document.querySelector('#batch-actions-count').textContent =
    seleccionadas.size === 1 ? '1 separación seleccionada' : `${seleccionadas.size} separaciones seleccionadas`;
}

async function cargarSeparaciones() {
  const body = document.querySelector('#reservations-body');
  seleccionadas.clear();
  actualizarBarraSeleccion();
  try {
    const page = await api.get('/reservations', {
      query: { status: state.status || undefined, buyerName: state.buyerName || undefined, page: state.page, size: 20 },
    });

    body.innerHTML = page.content.length
      ? page.content
          .map(
            (r) => `
        <tr>
          <td>${r.status === 'RESERVADO' ? `<input type="checkbox" data-select="${r.id}" data-buyer="${escapeHtml(r.customerName)}" data-guest="${r.guest}" />` : ''}</td>
          <td class="table-cell-primary mono">${escapeHtml(r.reservationNumber)}</td>
          <td>${escapeHtml(r.customerName)}${r.guest ? ' <span class="table-cell-muted" style="font-size:var(--font-size-xs);">(ocasional)</span>' : ''}</td>
          <td>${escapeHtml(r.itemsSummary)}</td>
          <td class="mono">${r.totalQuantity}</td>
          <td>${formatCurrency(r.total)}</td>
          <td>${formatCurrency(r.depositAmount)}</td>
          <td>${reservaStatusBadge(r.status)}</td>
          <td>${r.status === 'RESERVADO' ? formatDateTime(r.expiresAt) : '—'}</td>
          <td>
            <div class="table-actions">
              <button class="btn btn-ghost btn-sm" type="button" data-action="detalle" data-id="${r.id}">Ver detalle</button>
            </div>
          </td>
        </tr>
      `
          )
          .join('')
      : `<tr><td colspan="10"><div class="empty-state"><span>No se encontraron separaciones.</span></div></td></tr>`;

    body.querySelectorAll('[data-action="detalle"]').forEach((btn) => {
      btn.addEventListener('click', () => verDetalleSeparacion(btn.dataset.id));
    });
    body.querySelectorAll('[data-select]').forEach((checkbox) => {
      checkbox.addEventListener('change', () => {
        const id = Number(checkbox.dataset.select);
        if (checkbox.checked) {
          seleccionadas.set(id, { customerName: checkbox.dataset.buyer, guest: checkbox.dataset.guest === 'true' });
        } else {
          seleccionadas.delete(id);
        }
        actualizarBarraSeleccion();
      });
    });

    renderPagination(document.querySelector('#pagination'), page, (p) => { state.page = p; cargarSeparaciones(); });
  } catch (error) {
    body.innerHTML = `<tr><td colspan="10"><div class="empty-state"><span>${error instanceof ApiError ? error.message : 'Error al cargar las separaciones'}</span></div></td></tr>`;
  }
}

async function completarSeleccionadas() {
  const ids = Array.from(seleccionadas.keys());
  if (ids.length === 0) return;

  const compradores = new Set(Array.from(seleccionadas.values()).map((v) => v.customerName));
  if (compradores.size > 1) {
    showToast({ type: 'warning', title: 'Compradores distintos', message: 'Selecciona separaciones de un mismo comprador.' });
    return;
  }

  const cashSession = await fetchCurrentSession();
  if (!cashSession) {
    showToast({ type: 'warning', title: 'Caja cerrada', message: 'Abre una caja antes de completar el pago.' });
    openAbrirCajaModal({ onOpened: () => actualizarEstadoCaja() });
    return;
  }

  let preview;
  try {
    preview = await api.get('/reservations/complete-batch/preview', { query: { ids } });
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo calcular el cobro' });
    return;
  }

  if (preview.comboNombre) {
    showToast({ type: 'success', title: 'Combo detectado', message: `${preview.comboNombre} — ${formatCurrency(preview.totalFinal)}` });
  }

  await openPagoModal({
    total: preview.saldoPendiente,
    onConfirm: async ({ payments }) => {
      const completadas = await api.post('/reservations/complete-batch', {
        reservationIds: ids, cashSessionId: cashSession.id, payments,
      });
      showToast({ type: 'success', title: 'Separaciones completadas', message: `${completadas.length} separaciones cobradas` });
      cargarSeparaciones();
      actualizarEstadoCaja();
    },
  });
}

// ---------------------------------------------------------------------
// "Nueva separación" — carrito con varios productos y, opcionalmente,
// combos elegidos con el botón "+ Agregar combo" (mismo patrón que el
// POS: elegir combo → llenar cada línea con una variante concreta).
// ---------------------------------------------------------------------

async function abrirFormularioNuevaSeparacion() {
  reservaCart = [];
  reservaComboGroupCounter = 0;

  const [metodos, promotores, settings] = await Promise.all([
    api.get('/payment-methods').then((lista) => lista.filter((m) => m.status === 'ACTIVE' && !m.affectsCash)),
    api.get('/promoters').then((lista) => lista.filter((p) => p.status === 'ACTIVE')).catch(() => []),
    fetchCompanySettings(),
  ]);

  const depositoPorDefecto = settings?.reservationDepositAmount ?? null;

  const modal = openModal({
    title: 'Nueva separación',
    subtitle: 'La seña no puede pagarse en efectivo — solo Yape, Plin, transferencia o tarjeta.',
    maxWidth: '600px',
    body: `
      <form id="reserva-form" novalidate>
        <div class="alert alert-danger" id="reserva-form-error" role="alert" hidden>
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
          <span class="alert-message"></span>
        </div>

        <div id="rf-main-view">
          <div class="field" style="margin-bottom: var(--space-4);">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:var(--space-2);">
              <label class="field-label" style="margin-bottom:0;">Comprador</label>
              <button type="button" class="btn btn-ghost btn-sm" id="rf-toggle-comprador">Buscar cliente registrado</button>
            </div>
            <div id="reserva-guest-fields">
              <input class="input" id="rf-guest-name" placeholder="Nombre completo" maxlength="150" />
              <input class="input" id="rf-guest-phone" placeholder="Teléfono (opcional, para ubicar su comprobante en WhatsApp)" maxlength="20" style="margin-top:var(--space-2);" />
            </div>
            <div id="reserva-customer-picker" style="display:none;"></div>
          </div>

          <div class="field" style="margin-bottom: var(--space-4);">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:var(--space-2);">
              <label class="field-label" style="margin-bottom:0;">Productos</label>
              <button type="button" class="btn btn-ghost btn-sm" id="rf-btn-combo" hidden>+ Agregar combo</button>
            </div>
            <div style="display:flex; gap:var(--space-2); align-items:flex-start;">
              <div id="reserva-variant-picker" style="flex:1;"></div>
              <input class="input" type="number" id="rf-add-qty" min="1" step="1" value="1" style="width:70px; flex-shrink:0;" />
              <button type="button" class="btn btn-secondary" id="rf-btn-agregar-producto" style="flex-shrink:0;">Agregar</button>
            </div>
            <div id="rf-cart-items" style="margin-top:var(--space-3);"></div>
            <div style="display:flex; flex-direction:column; gap:2px; margin-top:var(--space-2); font-size:var(--font-size-sm);">
              <div style="display:flex; justify-content:space-between;"><span>Subtotal</span><span class="mono" id="rf-cart-subtotal">S/ 0.00</span></div>
              <div style="display:flex; justify-content:space-between; color:var(--color-text-secondary);"><span>Descuento por combo</span><span class="mono" id="rf-cart-discount">S/ 0.00</span></div>
              <div style="display:flex; justify-content:space-between; font-weight:700; padding-top:2px; border-top:1px solid var(--color-border);"><span>Total</span><span class="mono" id="rf-cart-total">S/ 0.00</span></div>
            </div>
          </div>

          <div class="form-grid">
            <div class="field">
              <label class="field-label" for="rf-deposit-method">Método de pago de la seña</label>
              <select class="select" id="rf-deposit-method" required>
                ${metodos.map((m) => `<option value="${m.id}">${m.name}</option>`).join('')}
              </select>
            </div>
            <div class="field">
              <label class="field-label" for="rf-deposit-amount">Monto de la seña${depositoPorDefecto != null ? ` (por defecto ${formatCurrency(depositoPorDefecto)})` : ''}</label>
              <input class="input" type="number" id="rf-deposit-amount" min="0" step="0.01" placeholder="${depositoPorDefecto ?? '20.00'}" />
            </div>
            <div class="field" style="align-self:end;">
              <button type="button" class="btn btn-ghost btn-sm" id="rf-toggle-ref" style="padding-left:0;">+ Agregar N° de operación (opcional)</button>
              <input class="input" id="rf-deposit-ref" maxlength="50" placeholder="N° de operación" style="display:none; margin-top:var(--space-2);" />
            </div>
            ${
              promotores.length > 0
                ? `
            <div class="field field-span-2">
              <label class="field-label" for="rf-promoter">Promotor (opcional — para comisión de venta en vivo)</label>
              <select class="select" id="rf-promoter">
                <option value="">Ninguno</option>
                ${promotores.map((p) => `<option value="${p.id}">${p.name}</option>`).join('')}
              </select>
            </div>
            `
                : ''
            }
            <div class="field field-span-2">
              <label class="field-label" for="rf-notes">Notas (opcional)</label>
              <input class="input" id="rf-notes" maxlength="255" placeholder="Ej. separado en el live del sábado" />
            </div>
          </div>
        </div>

        <div id="rf-combo-view" hidden></div>
      </form>
    `,
    footer: `
      <div id="rf-footer-main" style="display:flex; gap:var(--space-2); justify-content:flex-end; width:100%;">
        <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
        <button class="btn btn-primary" type="submit" form="reserva-form" disabled>Crear separación</button>
      </div>
      <div id="rf-footer-combo" hidden style="display:flex; gap:var(--space-2); justify-content:flex-end; width:100%;"></div>
    `,
  });

  reservaMainView = modal.body.querySelector('#rf-main-view');
  reservaComboView = modal.body.querySelector('#rf-combo-view');
  reservaFooterMain = modal.footer.querySelector('#rf-footer-main');
  reservaFooterCombo = modal.footer.querySelector('#rf-footer-combo');

  const customerPicker = createCustomerPicker();
  modal.body.querySelector('#reserva-customer-picker').appendChild(customerPicker.root);

  const variantPicker = createVariantPicker({ placeholder: 'Buscar por SKU, código de barras o producto…' });
  modal.body.querySelector('#reserva-variant-picker').appendChild(variantPicker.root);

  const metodoSelect = modal.body.querySelector('#rf-deposit-method');

  // Por defecto se captura solo nombre (y opcionalmente teléfono) de un
  // comprador ocasional — no hace falta registrarlo como cliente para una
  // separación de un live. El botón permite cambiar a buscar uno ya
  // registrado (ej. alguien que ya compra por la tienda online).
  const guestFields = modal.body.querySelector('#reserva-guest-fields');
  const customerPickerBox = modal.body.querySelector('#reserva-customer-picker');
  const toggleComprador = modal.body.querySelector('#rf-toggle-comprador');
  let modoCliente = false;
  toggleComprador.addEventListener('click', () => {
    modoCliente = !modoCliente;
    guestFields.style.display = modoCliente ? 'none' : '';
    customerPickerBox.style.display = modoCliente ? '' : 'none';
    toggleComprador.textContent = modoCliente ? 'Comprador ocasional (solo nombre)' : 'Buscar cliente registrado';
  });

  const refInput = modal.body.querySelector('#rf-deposit-ref');
  const toggleRef = modal.body.querySelector('#rf-toggle-ref');
  toggleRef.addEventListener('click', () => {
    refInput.style.display = '';
    toggleRef.style.display = 'none';
    refInput.focus();
  });

  modal.body.querySelector('#rf-btn-agregar-producto').addEventListener('click', () => {
    const variante = variantPicker.getSelected();
    if (!variante) return;
    const cantidad = Number(modal.body.querySelector('#rf-add-qty').value) || 1;
    if (agregarProductoSuelto(variante, cantidad)) {
      variantPicker.clear();
      modal.body.querySelector('#rf-add-qty').value = 1;
      renderCarritoReserva();
    }
  });

  if (permissions.has('COMBOS_CONSULTAR')) {
    const btnCombo = modal.body.querySelector('#rf-btn-combo');
    btnCombo.hidden = false;
    btnCombo.addEventListener('click', abrirVistaCombosReserva);
  }

  reservaFooterMain.querySelector('[data-cancel]').addEventListener('click', closeModal);
  modal.body.querySelector('#reserva-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = modal.body.querySelector('#reserva-form-error');
    errorAlert.hidden = true;

    const cliente = modoCliente ? customerPicker.getSelected() : null;
    const guestName = modoCliente ? '' : modal.body.querySelector('#rf-guest-name').value.trim();

    if ((modoCliente && !cliente) || (!modoCliente && !guestName)) {
      errorAlert.querySelector('.alert-message').textContent = modoCliente
        ? 'Elige un cliente registrado.'
        : 'Indica el nombre del comprador.';
      errorAlert.hidden = false;
      return;
    }
    if (reservaCart.length === 0) {
      errorAlert.querySelector('.alert-message').textContent = 'Agrega al menos un producto.';
      errorAlert.hidden = false;
      return;
    }

    const depositAmountRaw = modal.body.querySelector('#rf-deposit-amount').value.trim();

    const payload = {
      customerId: cliente?.id ?? null,
      guestName: guestName || null,
      guestPhone: modoCliente ? null : modal.body.querySelector('#rf-guest-phone').value.trim() || null,
      items: reservaCart.map((item) => ({
        variantId: item.variantId,
        quantity: item.quantity,
        comboId: item.comboId,
        comboGroup: item.comboGroup,
      })),
      depositAmount: depositAmountRaw ? Number(depositAmountRaw) : null,
      depositPaymentMethodId: Number(metodoSelect.value),
      depositReference: refInput.value.trim() || null,
      promoterId: modal.body.querySelector('#rf-promoter')?.value ? Number(modal.body.querySelector('#rf-promoter').value) : null,
      notes: modal.body.querySelector('#rf-notes').value.trim() || null,
    };

    try {
      const creada = await api.post('/reservations', payload);
      closeModal();
      showToast({ type: 'success', title: 'Separación creada', message: creada.reservationNumber });
      cargarSeparaciones();
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudo crear la separación';
      errorAlert.hidden = false;
    }
  });

  renderCarritoReserva();
}

function mostrarVistaPrincipalReserva() {
  reservaMainView.hidden = false;
  reservaComboView.hidden = true;
  reservaFooterMain.hidden = false;
  reservaFooterCombo.hidden = true;
}

function mostrarVistaComboReserva() {
  reservaMainView.hidden = true;
  reservaComboView.hidden = false;
  reservaFooterMain.hidden = true;
  reservaFooterCombo.hidden = false;
}

function agregarProductoSuelto(variante, cantidad) {
  const existente = reservaCart.find((i) => i.variantId === variante.variantId && !i.comboId);
  const enCarrito = existente?.quantity ?? 0;
  if (enCarrito + cantidad > variante.stock) {
    showToast({ type: 'warning', title: 'Stock insuficiente', message: `Solo hay ${variante.stock} unidades disponibles.` });
    return false;
  }
  if (existente) {
    existente.quantity += cantidad;
  } else {
    reservaCart.push({
      variantId: variante.variantId,
      productName: variante.productName,
      variantLabel: variante.variantLabel,
      sku: variante.sku,
      unitPrice: variante.effectivePrice,
      stock: variante.stock,
      quantity: cantidad,
      comboId: null,
      comboName: null,
      comboPrice: null,
      comboGroup: null,
    });
  }
  return true;
}

function cambiarCantidadReserva(variantId, delta) {
  const item = reservaCart.find((i) => i.variantId === variantId && !i.comboId);
  if (!item) return;
  const nueva = item.quantity + delta;
  if (nueva <= 0) {
    reservaCart = reservaCart.filter((i) => i !== item);
  } else if (nueva > item.stock) {
    showToast({ type: 'warning', title: 'Stock insuficiente', message: `Solo hay ${item.stock} unidades disponibles.` });
    return;
  } else {
    item.quantity = nueva;
  }
  renderCarritoReserva();
}

function quitarDeCarritoReserva(variantId) {
  reservaCart = reservaCart.filter((i) => !(i.variantId === variantId && !i.comboId));
  renderCarritoReserva();
}

function quitarGrupoComboReserva(comboId, comboGroup) {
  reservaCart = reservaCart.filter((i) => !(i.comboId === comboId && i.comboGroup === comboGroup));
  renderCarritoReserva();
}

function comboItemTextoReserva(it) {
  return it.selectorType === 'CATEGORY'
    ? `${it.quantity} × cualquier producto de ${escapeHtml(it.categoryName)}${it.brandName ? ` (marca ${escapeHtml(it.brandName)})` : ''}`
    : `${it.quantity} × ${escapeHtml(it.productName)}`;
}

async function abrirVistaCombosReserva() {
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

  reservaComboView.innerHTML = combos
    .map(
      (c) => `
    <button type="button" class="vp-result" data-combo="${c.id}" style="display:block; width:100%; text-align:left; padding:var(--space-3); border-bottom:1px solid var(--color-border);">
      <div style="display:flex; justify-content:space-between; font-weight:600;">
        <span>${escapeHtml(c.name)}</span><span class="mono">${formatCurrency(c.price)}</span>
      </div>
      <div style="font-size:var(--font-size-xs); color:var(--color-text-muted);">
        ${c.items.map(comboItemTextoReserva).join(' + ')}
      </div>
    </button>
  `
    )
    .join('');

  reservaFooterCombo.innerHTML = `<button class="btn btn-secondary" type="button" data-combo-volver>Cancelar</button>`;
  reservaFooterCombo.querySelector('[data-combo-volver]').addEventListener('click', mostrarVistaPrincipalReserva);
  reservaComboView.querySelectorAll('[data-combo]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const combo = combos.find((c) => String(c.id) === btn.dataset.combo);
      abrirVistaSlotsComboReserva(combo);
    });
  });

  mostrarVistaComboReserva();
}

function abrirVistaSlotsComboReserva(combo) {
  // Un picker por unidad — incluso una línea de "4 polos" puede terminar
  // siendo 4 variantes distintas (tallas/colores), no una sola repetida.
  const slots = combo.items.flatMap((it) => Array.from({ length: it.quantity }, () => it));
  const pickers = slots.map(() => createVariantPicker({ placeholder: 'Buscar variante…' }));

  reservaComboView.innerHTML = `
    <div class="alert alert-danger" id="rf-combo-slots-error" role="alert" hidden>
      <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
      <span class="alert-message"></span>
    </div>
    <p style="margin: 0 0 var(--space-3); font-weight:600;">${combo.name} · <span class="mono">${formatCurrency(combo.price)}</span></p>
    <div style="display:flex; flex-direction:column; gap:var(--space-4);">
      ${slots
        .map(
          (it, index) => `
        <div>
          <label class="field-label">${it.selectorType === 'CATEGORY' ? `Cualquier producto de ${it.categoryName}${it.brandName ? ` (marca ${it.brandName})` : ''}` : it.productName}</label>
          <div id="rf-combo-slot-${index}"></div>
        </div>
      `
        )
        .join('')}
    </div>
  `;
  slots.forEach((it, index) => {
    reservaComboView.querySelector(`#rf-combo-slot-${index}`).appendChild(pickers[index].root);
  });

  reservaFooterCombo.innerHTML = `
    <button class="btn btn-secondary" type="button" data-combo-volver>Cancelar</button>
    <button class="btn btn-primary" type="button" data-combo-confirmar>Agregar al carrito</button>
  `;
  reservaFooterCombo.querySelector('[data-combo-volver]').addEventListener('click', mostrarVistaPrincipalReserva);
  reservaFooterCombo.querySelector('[data-combo-confirmar]').addEventListener('click', () => {
    const errorAlert = reservaComboView.querySelector('#rf-combo-slots-error');
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

    const grupo = reservaComboGroupCounter++;
    seleccionadas.forEach((variante) => {
      reservaCart.push({
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
        comboGroup: grupo,
      });
    });
    mostrarVistaPrincipalReserva();
    renderCarritoReserva();
  });

  mostrarVistaComboReserva();
}

function calcularTotalesReserva() {
  const subtotal = reservaCart.reduce((acc, item) => acc + item.unitPrice * item.quantity, 0);
  const gruposVistos = new Set(reservaCart.filter((i) => i.comboId).map((i) => `${i.comboId}::${i.comboGroup}`));
  let descuento = 0;
  gruposVistos.forEach((clave) => {
    const itemsGrupo = reservaCart.filter((i) => i.comboId && `${i.comboId}::${i.comboGroup}` === clave);
    const normal = itemsGrupo.reduce((acc, i) => acc + i.unitPrice * i.quantity, 0);
    descuento += normal - itemsGrupo[0].comboPrice;
  });
  return { subtotal, descuento, total: subtotal - descuento };
}

function itemSueltoHtmlReserva(item) {
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
      </div>
      <div style="text-align:right; display:flex; flex-direction:column; align-items:flex-end; justify-content:space-between;">
        <button class="btn btn-ghost btn-sm" type="button" data-remove="${item.variantId}" aria-label="Quitar">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round"/></svg>
        </button>
        <span class="mono" style="font-weight:600;">${formatCurrency(item.unitPrice * item.quantity)}</span>
      </div>
    </div>
  `;
}

function grupoComboHtmlReserva(comboId, comboGroup, items) {
  const normal = items.reduce((acc, i) => acc + i.unitPrice * i.quantity, 0);
  return `
    <div style="border-radius: var(--radius-md); background: var(--color-surface-sunken); margin: var(--space-2) 0; padding: var(--space-3);">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:var(--space-2);">
        <span class="badge badge-info">Combo · ${escapeHtml(items[0].comboName)}</span>
        <button class="btn btn-ghost btn-sm" type="button" data-remove-grupo="${comboId}::${comboGroup}" aria-label="Quitar combo">
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

function renderCarritoReserva() {
  const container = reservaMainView.querySelector('#rf-cart-items');
  const gruposVistos = [];
  const bloques = [];
  for (const item of reservaCart) {
    if (item.comboId) {
      const clave = `${item.comboId}::${item.comboGroup}`;
      if (!gruposVistos.includes(clave)) {
        gruposVistos.push(clave);
        bloques.push(grupoComboHtmlReserva(item.comboId, item.comboGroup,
            reservaCart.filter((i) => i.comboId === item.comboId && i.comboGroup === item.comboGroup)));
      }
    } else {
      bloques.push(itemSueltoHtmlReserva(item));
    }
  }

  container.innerHTML = reservaCart.length
    ? bloques.join('')
    : `<div class="empty-state" style="padding: var(--space-4) 0;"><span>Todavía no agregaste productos.</span></div>`;

  container.querySelectorAll('[data-qty-up]').forEach((btn) => btn.addEventListener('click', () => cambiarCantidadReserva(Number(btn.dataset.qtyUp), 1)));
  container.querySelectorAll('[data-qty-down]').forEach((btn) => btn.addEventListener('click', () => cambiarCantidadReserva(Number(btn.dataset.qtyDown), -1)));
  container.querySelectorAll('[data-remove]').forEach((btn) => btn.addEventListener('click', () => quitarDeCarritoReserva(Number(btn.dataset.remove))));
  container.querySelectorAll('[data-remove-grupo]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const [comboId, comboGroup] = btn.dataset.removeGrupo.split('::').map(Number);
      quitarGrupoComboReserva(comboId, comboGroup);
    });
  });

  const { subtotal, descuento, total } = calcularTotalesReserva();
  reservaMainView.querySelector('#rf-cart-subtotal').textContent = formatCurrency(subtotal);
  reservaMainView.querySelector('#rf-cart-discount').textContent = formatCurrency(descuento);
  reservaMainView.querySelector('#rf-cart-total').textContent = formatCurrency(total);
  reservaFooterMain.querySelector('[type="submit"]').disabled = reservaCart.length === 0;
}

async function verDetalleSeparacion(id) {
  try {
    const reserva = await api.get(`/reservations/${id}`);
    abrirModalDetalle(reserva);
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo cargar la separación' });
  }
}

function abrirModalDetalle(reserva) {
  const total = reserva.total;
  const saldoPendiente = total - reserva.depositAmount;

  const body = document.createElement('div');
  body.innerHTML = `
    <div style="display:flex; justify-content:space-between; align-items:flex-start; margin-bottom: var(--space-4);">
      <div>
        <div style="font-weight:600;">${escapeHtml(reserva.customerName)}${reserva.guest ? ' <span class="badge badge-neutral" style="font-weight:400;">Comprador ocasional</span>' : ''}</div>
        ${reserva.guest && reserva.guestPhone ? `<div style="color: var(--color-text-secondary); font-size: var(--font-size-sm);">Tel.: ${escapeHtml(reserva.guestPhone)}</div>` : ''}
        ${reserva.promoterName ? `<div style="color: var(--color-text-secondary); font-size: var(--font-size-sm);">Promotor: ${escapeHtml(reserva.promoterName)}</div>` : ''}
        ${reserva.notes ? `<div style="color: var(--color-text-muted); font-size: var(--font-size-sm); margin-top:4px;">Nota: ${escapeHtml(reserva.notes)}</div>` : ''}
      </div>
      ${reservaStatusBadge(reserva.status)}
    </div>

    <div style="display:flex; flex-direction:column; gap:var(--space-1); font-size:var(--font-size-sm); margin-bottom: var(--space-3); padding-bottom: var(--space-3); border-bottom:1px solid var(--color-border);">
      ${reserva.items
        .map(
          (item) => `
        <div style="display:flex; justify-content:space-between;">
          <span>${item.quantity} × ${escapeHtml(item.productName)} <span class="table-cell-muted mono">${escapeHtml(item.variantLabel)}</span>${item.comboName ? ` <span class="badge badge-info" style="font-weight:400;">${escapeHtml(item.comboName)}</span>` : ''}</span>
          <span class="mono">${formatCurrency(item.subtotal)}</span>
        </div>
      `
        )
        .join('')}
    </div>

    <div style="display:flex; flex-direction:column; gap:var(--space-1); font-size:var(--font-size-sm);">
      <div style="display:flex; justify-content:space-between;"><span>Total</span><span class="mono">${formatCurrency(total)}</span></div>
      <div style="display:flex; justify-content:space-between;"><span>Seña pagada (${reserva.depositPaymentMethodName}${reserva.depositReference ? ` · ${reserva.depositReference}` : ''})</span><span class="mono">${formatCurrency(reserva.depositAmount)}</span></div>
      <div style="display:flex; justify-content:space-between; font-weight:600; padding-top:var(--space-2); border-top:1px solid var(--color-border);"><span>Saldo pendiente</span><span class="mono">${formatCurrency(saldoPendiente)}</span></div>
    </div>

    <div style="margin-top: var(--space-4); padding-top: var(--space-3); border-top: 1px solid var(--color-border); font-size:var(--font-size-sm);">
      <div><strong>Creada por:</strong> ${reserva.createdByUsername} · ${formatDateTime(reserva.createdAt)}</div>
      ${reserva.status === 'RESERVADO' ? `<div><strong>Vence:</strong> ${formatDateTime(reserva.expiresAt)}</div>` : ''}
      ${reserva.completedAt ? `<div><strong>Completada por:</strong> ${reserva.completedByUsername} · ${formatDateTime(reserva.completedAt)}</div>` : ''}
      ${reserva.cancelledAt ? `<div><strong>Cancelada por:</strong> ${reserva.cancelledByUsername} · ${formatDateTime(reserva.cancelledAt)}</div>` : ''}
      ${reserva.cancellationReason ? `<div><strong>Motivo:</strong> ${reserva.cancellationReason}</div>` : ''}
    </div>
  `;

  const footerButtons = [];
  if (reserva.status === 'RESERVADO') {
    footerButtons.push('<button class="btn btn-danger" type="button" data-cancelar>Cancelar separación</button>');
    footerButtons.push('<button class="btn btn-primary" type="button" data-completar>Completar pago</button>');
  }

  const modal = openModal({
    title: `Separación ${reserva.reservationNumber}`,
    body,
    footer: footerButtons.join(''),
    maxWidth: '520px',
  });

  modal.footer?.querySelector('[data-completar]')?.addEventListener('click', () => completarSeparacion(reserva, saldoPendiente));
  modal.footer?.querySelector('[data-cancelar]')?.addEventListener('click', () => cancelarSeparacion(reserva.id));
}

async function completarSeparacion(reserva, saldoPendiente) {
  const cashSession = await fetchCurrentSession();
  if (!cashSession) {
    closeModal();
    showToast({ type: 'warning', title: 'Caja cerrada', message: 'Abre una caja antes de completar el pago de la separación.' });
    openAbrirCajaModal({ onOpened: () => { actualizarEstadoCaja(); verDetalleSeparacion(reserva.id); } });
    return;
  }

  closeModal();
  await openPagoModal({
    total: saldoPendiente,
    onConfirm: async ({ payments }) => {
      const completada = await api.post(`/reservations/${reserva.id}/complete`, { cashSessionId: cashSession.id, payments });
      showToast({ type: 'success', title: 'Separación completada', message: completada.reservationNumber });
      cargarSeparaciones();
      actualizarEstadoCaja();
    },
  });
}

async function cancelarSeparacion(id) {
  const confirmado = await confirmAction({
    title: 'Cancelar separación',
    message: 'Se liberará el stock apartado. La seña ya pagada no se devuelve automáticamente.',
    confirmLabel: 'Cancelar separación',
  });
  if (!confirmado) return;

  const reason = window.prompt('Motivo de la cancelación:');
  if (!reason || !reason.trim()) return;

  try {
    await api.post(`/reservations/${id}/cancel`, { reason: reason.trim() });
    closeModal();
    showToast({ type: 'success', title: 'Separación cancelada' });
    cargarSeparaciones();
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo cancelar la separación' });
  }
}

const session = requireSession();
if (session) {
  permissions = new Set(session.user.permissions ?? []);
  renderShell('separaciones');
  init();
}
