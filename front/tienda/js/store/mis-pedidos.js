import { storeApi, ApiError, API_ORIGIN, refreshAccessToken } from './core/store-api.js';
import { requireCustomerSession } from './core/customer-auth.js';
import { getCustomerSession } from './core/customer-session.js';
import { renderStoreShell } from './components/store-shell.js';
import { formatCurrency, formatDateTime, escapeHtml } from '../../../js/core/format.js';
import { openModal } from '../../../js/components/modal.js';
import { showToast } from '../../../js/components/toast.js';
import { connectLiveStream } from '../../../js/core/live-stream.js';

const STATUS_LABELS = { PENDING_PAYMENT: 'Pendiente de pago', CONFIRMED: 'Confirmado', CANCELLED: 'Anulado' };
const STATUS_CLASSES = { PENDING_PAYMENT: 'badge-warning', CONFIRMED: 'badge-success', CANCELLED: 'badge-danger' };

function pedidoCard(p) {
  return `
    <div class="card store-order-card" data-id="${p.id}">
      <div class="card-header store-order-card-header">
        <div>
          <h3>${escapeHtml(p.orderNumber)}</h3>
          <p>${formatDateTime(p.createdAt)}</p>
        </div>
        <span class="badge ${STATUS_CLASSES[p.status] || 'badge-neutral'}">${STATUS_LABELS[p.status] || p.status}</span>
      </div>
      <div class="card-body">
        <div class="store-summary-total"><span>Total</span><span>${formatCurrency(p.total)}</span></div>
      </div>
    </div>
  `;
}

async function verDetalle(id) {
  try {
    const pedido = await storeApi.get(`/store/orders/${id}`, { auth: true });
    abrirModalDetalle(pedido);
  } catch (error) {
    showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo cargar el pedido' });
  }
}

function abrirModalDetalle(pedido) {
  const itemsHtml = pedido.items
    .map(
      (it) => `
    <div class="store-summary-row">
      <span>${escapeHtml(it.productName)} (${escapeHtml(it.variantLabel)}) × ${it.quantity}</span>
      <span>${formatCurrency(it.subtotal)}</span>
    </div>
  `
    )
    .join('');

  const body = document.createElement('div');
  body.innerHTML = `
    <div class="store-order-detail-meta">
      <div><strong>Entrega:</strong> ${escapeHtml(pedido.address)}, ${escapeHtml(pedido.district)}, ${escapeHtml(pedido.province)}, ${escapeHtml(pedido.department)}</div>
      <div><strong>Método de pago:</strong> ${escapeHtml(pedido.paymentMethodName)}</div>
      ${pedido.confirmedAt ? `<div><strong>Confirmado:</strong> ${formatDateTime(pedido.confirmedAt)}</div>` : ''}
      ${pedido.status === 'CANCELLED' && pedido.cancellationReason ? `<div><strong>Motivo de anulación:</strong> ${escapeHtml(pedido.cancellationReason)}</div>` : ''}
    </div>

    ${itemsHtml}
    <div class="store-summary-row"><span>Subtotal</span><span>${formatCurrency(pedido.subtotal)}</span></div>
    <div class="store-summary-row"><span>Envío</span><span>${pedido.shippingCost > 0 ? formatCurrency(pedido.shippingCost) : 'Gratis'}</span></div>
    <div class="store-summary-total"><span>Total</span><span>${formatCurrency(pedido.total)}</span></div>

    <div id="proof-section" class="store-order-proof-section"></div>
  `;

  const modal = openModal({ title: pedido.orderNumber, body, maxWidth: '520px' });
  renderSeccionComprobante(modal.body.querySelector('#proof-section'), pedido);
}

function renderSeccionComprobante(container, pedido) {
  if (pedido.paymentProofUrl) {
    container.innerHTML = `
      <div class="field-label store-proof-label">Comprobante de pago</div>
      <a href="${API_ORIGIN}${pedido.paymentProofUrl}" target="_blank" rel="noopener">
        <img class="store-proof-image" src="${API_ORIGIN}${pedido.paymentProofUrl}" alt="Comprobante de pago" />
      </a>
    `;
    return;
  }

  if (pedido.status !== 'PENDING_PAYMENT') return;

  container.innerHTML = `
    <div class="field-label store-proof-label">Comprobante de pago (opcional)</div>
    <input type="file" class="input store-proof-input" id="proof-input" accept="image/png,image/jpeg,image/webp" />
    <button class="btn btn-secondary" type="button" id="btn-upload-proof">Subir comprobante</button>
  `;

  container.querySelector('#btn-upload-proof').addEventListener('click', async () => {
    const file = container.querySelector('#proof-input').files?.[0];
    if (!file) {
      showToast({ type: 'danger', title: 'Selecciona un archivo primero' });
      return;
    }
    const formData = new FormData();
    formData.append('file', file);
    try {
      const actualizado = await storeApi.post(`/store/orders/${pedido.id}/payment-proof`, formData, { auth: true });
      showToast({ type: 'success', title: 'Comprobante subido' });
      renderSeccionComprobante(container, actualizado);
    } catch (error) {
      showToast({ type: 'danger', title: 'Error', message: error instanceof ApiError ? error.message : 'No se pudo subir el comprobante' });
    }
  });
}

function actualizarBadgeEnLista(pedido) {
  const badge = document.querySelector(`[data-id="${pedido.id}"] .badge`);
  if (!badge) return;
  badge.className = `badge ${STATUS_CLASSES[pedido.status] || 'badge-neutral'}`;
  badge.textContent = STATUS_LABELS[pedido.status] || pedido.status;
}

function conectarNotificaciones() {
  connectLiveStream(`${API_ORIGIN}/api/store/notifications/stream`, {
    getToken: () => getCustomerSession()?.accessToken,
    refreshToken: refreshAccessToken,
    onEvent: {
      'pedido-actualizado': (pedido) => {
        actualizarBadgeEnLista(pedido);
        showToast({
          type: pedido.status === 'CONFIRMED' ? 'success' : 'warning',
          title: pedido.status === 'CONFIRMED' ? '¡Tu pedido fue confirmado!' : 'Tu pedido cambió de estado',
          message: pedido.orderNumber,
        });
      },
    },
  });
}

async function init() {
  const session = requireCustomerSession('login.html');
  if (!session) return;
  renderStoreShell({ basePath: '../', active: 'pedidos' });

  const contenedor = document.querySelector('#orders-list');
  try {
    const page = await storeApi.get('/store/orders', { auth: true, query: { size: 50 } });
    contenedor.innerHTML = page.content.length
      ? page.content.map(pedidoCard).join('')
      : `<div class="empty-state"><span>Todavía no tienes pedidos.</span></div>`;
    contenedor.querySelectorAll('[data-id]').forEach((card) => {
      card.addEventListener('click', () => verDetalle(card.dataset.id));
    });
  } catch (error) {
    contenedor.innerHTML = `<div class="empty-state"><span>${error instanceof ApiError ? error.message : 'No se pudieron cargar tus pedidos'}</span></div>`;
  }

  conectarNotificaciones();
}

init();
