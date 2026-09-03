import { requireSession } from '../core/auth.js';
import { api, ApiError, API_ORIGIN } from '../core/api.js';
import { renderShell } from '../components/shell.js';
import { renderPagination } from '../components/pagination.js';
import { openModal, closeModal } from '../components/modal.js';
import { confirmAction } from '../components/confirm.js';
import { showToast } from '../components/toast.js';
import { imprimirTicket } from '../components/ticket.js';
import { formatCurrency, formatDateTime, escapeHtml } from '../core/format.js';

const STATUS_LABELS = { PENDING_PAYMENT: 'Pendiente de pago', CONFIRMED: 'Confirmado', CANCELLED: 'Anulado' };
const STATUS_CLASSES = { PENDING_PAYMENT: 'badge-warning', CONFIRMED: 'badge-success', CANCELLED: 'badge-danger' };

let state = { page: 0, status: '' };

function orderStatusBadge(status) {
  return `<span class="badge ${STATUS_CLASSES[status] || 'badge-neutral'}">${STATUS_LABELS[status] || status}</span>`;
}

function init() {
  document.querySelector('#filter-status').addEventListener('change', (event) => {
    state.status = event.target.value;
    state.page = 0;
    cargarPedidos();
  });
  // El bell del topbar (shell.js) dispara este evento al recibir un pedido nuevo por SSE —
  // así alguien viendo esta pantalla no tiene que refrescar manualmente para verlo.
  window.addEventListener('fsp:pedido-nuevo', () => cargarPedidos());
  cargarPedidos();
}

async function cargarPedidos() {
  const body = document.querySelector('#orders-body');
  try {
    const page = await api.get('/orders', {
      query: { status: state.status || undefined, page: state.page, size: 20 },
    });

    body.innerHTML = page.content.length
      ? page.content
          .map(
            (o) => `
        <tr>
          <td class="table-cell-primary mono">${escapeHtml(o.orderNumber)}</td>
          <td>${escapeHtml(o.customerName)}</td>
          <td>${formatCurrency(o.total)}</td>
          <td>${orderStatusBadge(o.status)}</td>
          <td>${formatDateTime(o.createdAt)}</td>
          <td>
            <div class="table-actions">
              <button class="btn btn-ghost btn-sm" type="button" data-action="detalle" data-id="${o.id}">Ver detalle</button>
            </div>
          </td>
        </tr>
      `
          )
          .join('')
      : `<tr><td colspan="6"><div class="empty-state"><span>No se encontraron pedidos.</span></div></td></tr>`;

    body.querySelectorAll('[data-action="detalle"]').forEach((btn) => {
      btn.addEventListener('click', () => verDetallePedido(btn.dataset.id));
    });

    renderPagination(document.querySelector('#pagination'), page, (p) => { state.page = p; cargarPedidos(); });
  } catch (error) {
    body.innerHTML = `<tr><td colspan="6"><div class="empty-state"><span>${error instanceof ApiError ? error.message : 'Error al cargar pedidos'}</span></div></td></tr>`;
  }
}

async function verDetallePedido(id) {
  try {
    const pedido = await api.get(`/orders/${id}`);
    abrirModalDetalle(pedido);
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo cargar el pedido' });
  }
}

function abrirModalDetalle(pedido) {
  const itemsHtml = pedido.items
    .map(
      (it) => `
    <tr>
      <td>${escapeHtml(it.productName)}</td>
      <td>${escapeHtml(it.variantLabel)}</td>
      <td class="mono">${it.quantity}</td>
      <td>${formatCurrency(it.unitPrice)}</td>
      <td>${formatCurrency(it.subtotal)}</td>
    </tr>
  `
    )
    .join('');

  const body = document.createElement('div');
  body.innerHTML = `
    <div style="display:flex; justify-content:space-between; align-items:flex-start; margin-bottom: var(--space-4);">
      <div>
        <div style="font-weight:600;">${escapeHtml(pedido.recipientFirstName)} ${escapeHtml(pedido.recipientLastNamePaterno)} ${escapeHtml(pedido.recipientLastNameMaterno)}</div>
        <div style="color: var(--color-text-secondary); font-size: var(--font-size-sm);">DNI ${escapeHtml(pedido.recipientDni)} · ${escapeHtml(pedido.phone)}</div>
        <div style="color: var(--color-text-secondary); font-size: var(--font-size-sm);">${escapeHtml(pedido.address)}, ${escapeHtml(pedido.district)}, ${escapeHtml(pedido.province)}, ${escapeHtml(pedido.department)}</div>
        ${pedido.notes ? `<div style="color: var(--color-text-muted); font-size: var(--font-size-sm); margin-top:4px;">Nota: ${escapeHtml(pedido.notes)}</div>` : ''}
      </div>
      ${orderStatusBadge(pedido.status)}
    </div>

    <table class="data-table">
      <thead><tr><th>Producto</th><th>Variante</th><th>Cant.</th><th>P. unit.</th><th>Subtotal</th></tr></thead>
      <tbody>${itemsHtml}</tbody>
    </table>

    <div style="margin-top: var(--space-3);">
      <div style="display:flex; justify-content:space-between;"><span>Subtotal</span><span>${formatCurrency(pedido.subtotal)}</span></div>
      <div style="display:flex; justify-content:space-between;"><span>Envío</span><span>${pedido.shippingCost > 0 ? formatCurrency(pedido.shippingCost) : 'Gratis'}</span></div>
      <div style="display:flex; justify-content:space-between; font-weight:600; margin-top: var(--space-1);">
        <span>Total</span><span>${formatCurrency(pedido.total)}</span>
      </div>
    </div>

    <div style="margin-top: var(--space-4); padding-top: var(--space-3); border-top: 1px solid var(--color-border);">
      <div><strong>Método de pago:</strong> ${pedido.paymentMethodName}</div>
      ${pedido.paymentReference ? `<div><strong>Referencia:</strong> ${pedido.paymentReference}</div>` : ''}
      ${pedido.confirmedByUsername ? `<div><strong>Confirmado por:</strong> ${pedido.confirmedByUsername} · ${formatDateTime(pedido.confirmedAt)}</div>` : ''}
      ${pedido.cancellationReason ? `<div><strong>Motivo de anulación:</strong> ${pedido.cancellationReason}</div>` : ''}
      ${
        pedido.paymentProofUrl
          ? `<div style="margin-top: var(--space-3);">
               <div class="field-label" style="margin-bottom: var(--space-2);">Comprobante de pago</div>
               <a href="${API_ORIGIN}${pedido.paymentProofUrl}" target="_blank" rel="noopener">
                 <img src="${API_ORIGIN}${pedido.paymentProofUrl}" alt="Comprobante de pago" style="max-width:220px; border-radius: var(--radius-md); border:1px solid var(--color-border);" />
               </a>
             </div>`
          : ''
      }
    </div>
  `;

  const footerButtons = [];
  if (pedido.saleId) {
    footerButtons.push('<button class="btn btn-secondary" type="button" data-imprimir>Imprimir ticket</button>');
  }
  if (pedido.status === 'PENDING_PAYMENT') {
    footerButtons.push('<button class="btn btn-primary" type="button" data-confirmar>Confirmar pago</button>');
  }
  if (pedido.status !== 'CANCELLED') {
    footerButtons.push('<button class="btn btn-danger" type="button" data-cancelar>Cancelar pedido</button>');
  }

  const modal = openModal({
    title: `Pedido ${pedido.orderNumber}`,
    body,
    footer: footerButtons.join(''),
    maxWidth: '640px',
  });

  modal.footer?.querySelector('[data-imprimir]')?.addEventListener('click', () => imprimirTicketDelPedido(pedido.saleId));
  modal.footer?.querySelector('[data-confirmar]')?.addEventListener('click', () => confirmarPago(pedido.id));
  modal.footer?.querySelector('[data-cancelar]')?.addEventListener('click', () => cancelarPedido(pedido.id));
}

async function imprimirTicketDelPedido(saleId) {
  try {
    const venta = await api.get(`/sales/${saleId}`);
    imprimirTicket(venta);
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo cargar el ticket' });
  }
}

async function confirmarPago(id) {
  const confirmado = await confirmAction({
    title: 'Confirmar pago',
    message: 'Esto descontará el stock de los productos del pedido. ¿Confirmas que el pago fue recibido?',
    confirmLabel: 'Confirmar pago',
    danger: false,
  });
  if (!confirmado) return;

  try {
    const pedido = await api.post(`/orders/${id}/confirm`, {});
    closeModal();
    showToast({ type: 'success', title: 'Pago confirmado', message: 'Se generó la venta — abriendo el ticket…' });
    cargarPedidos();
    if (pedido.saleId) {
      imprimirTicketDelPedido(pedido.saleId);
    }
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo confirmar el pago' });
  }
}

async function cancelarPedido(id) {
  const reason = window.prompt('Motivo de la anulación:');
  if (!reason || !reason.trim()) return;

  try {
    await api.post(`/orders/${id}/cancel`, { reason: reason.trim() });
    closeModal();
    showToast({ type: 'success', title: 'Pedido anulado' });
    cargarPedidos();
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo anular el pedido' });
  }
}

const session = requireSession();
if (session) {
  renderShell('pedidos');
  init();
}
