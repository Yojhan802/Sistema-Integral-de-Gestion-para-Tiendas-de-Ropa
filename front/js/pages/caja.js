import { requireSession } from '../core/auth.js';
import { api, ApiError } from '../core/api.js';
import { renderShell, actualizarEstadoCaja } from '../components/shell.js';
import { fetchCurrentSession } from '../core/cash-session.js';
import { openAbrirCajaModal } from '../components/abrir-caja.js';
import { openModal, closeModal } from '../components/modal.js';
import { renderPagination } from '../components/pagination.js';
import { showToast } from '../components/toast.js';
import { formatCurrency, formatDateLong, escapeHtml } from '../core/format.js';

const MOVEMENT_LABELS = {
  VENTA: 'Venta',
  INGRESO: 'Ingreso',
  GASTO: 'Gasto',
  RETIRO: 'Retiro',
  DEVOLUCION: 'Devolución',
};

let historyPage = 0;
let currentSessionDetail = null;

async function init() {
  document.querySelector('#btn-abrir-caja-page')?.addEventListener('click', () => {
    openAbrirCajaModal({ onOpened: () => { actualizarEstadoCaja(); cargarSesionActual(); } });
  });
  await cargarSesionActual();
  await cargarHistorial();
}

async function cargarSesionActual() {
  const sesion = await fetchCurrentSession();
  document.querySelector('#caja-cerrada').hidden = Boolean(sesion);
  document.querySelector('#caja-abierta').hidden = !sesion;
  document.querySelector('#page-actions').innerHTML = '';

  if (!sesion) return;

  const [detalle, resumen] = await Promise.all([
    api.get(`/cash-registers/sessions/${sesion.id}`),
    api.get(`/cash-registers/sessions/${sesion.id}/summary`),
  ]);
  currentSessionDetail = detalle;

  document.querySelector('#stat-register-name').textContent = detalle.cashRegisterName;
  document.querySelector('#stat-opened-by').textContent = `Abierta por ${detalle.openedByUsername} · ${formatDateLong(detalle.openedAt)}`;
  document.querySelector('#stat-opening-amount').textContent = formatCurrency(detalle.openingAmount);
  document.querySelector('#stat-movements-total').textContent = formatCurrency(resumen.movementsTotal);
  document.querySelector('#stat-expected').textContent = formatCurrency(resumen.expectedAmount);

  const body = document.querySelector('#session-movements-body');
  body.innerHTML = detalle.movements.length
    ? detalle.movements
        .map(
          (m) => `
        <tr>
          <td class="table-cell-muted">${formatDateLong(m.createdAt)}</td>
          <td><span class="badge ${m.amount >= 0 ? 'badge-success' : 'badge-warning'}">${MOVEMENT_LABELS[m.type] ?? m.type}</span></td>
          <td class="mono" style="color:${m.amount >= 0 ? 'var(--color-success-text)' : 'var(--color-danger-text)'};">${m.amount >= 0 ? '+' : ''}${formatCurrency(m.amount)}</td>
          <td>${m.reason ? escapeHtml(m.reason) : '—'}</td>
          <td>${escapeHtml(m.username)}</td>
        </tr>
      `
        )
        .join('')
    : `<tr><td colspan="5"><div class="empty-state"><span>Sin movimientos todavía</span></div></td></tr>`;

  document.querySelector('#btn-nuevo-movimiento').addEventListener('click', abrirModalMovimiento);
  document.querySelector('#btn-cerrar-caja').addEventListener('click', () => abrirModalCierre(sesion.id, resumen.expectedAmount));
}

async function cargarHistorial() {
  const body = document.querySelector('#history-body');
  try {
    const page = await api.get('/cash-registers/sessions', { query: { page: historyPage, size: 10 } });
    body.innerHTML = page.content.length
      ? page.content
          .map((s) => {
            const diffColor = s.difference == null ? '' : s.difference === 0 ? 'var(--color-success-text)' : s.difference < 0 ? 'var(--color-danger-text)' : 'var(--color-info-text)';
            const estadoBadge = s.status === 'OPEN' ? '<span class="badge badge-success">Abierta</span>' : '<span class="badge badge-neutral">Cerrada</span>';
            return `
            <tr>
              <td class="table-cell-primary">${s.cashRegisterName}</td>
              <td>${s.openedByUsername}</td>
              <td class="table-cell-muted">${formatDateLong(s.openedAt)}</td>
              <td class="table-cell-muted">${s.closedAt ? formatDateLong(s.closedAt) : '—'}</td>
              <td class="mono">${s.expectedAmount != null ? formatCurrency(s.expectedAmount) : '—'}</td>
              <td class="mono">${s.countedAmount != null ? formatCurrency(s.countedAmount) : '—'}</td>
              <td class="mono" style="color:${diffColor};">${s.difference != null ? formatCurrency(s.difference) : '—'}</td>
              <td>${estadoBadge}</td>
            </tr>
          `;
          })
          .join('')
      : `<tr><td colspan="8"><div class="empty-state"><span>Sin sesiones registradas</span></div></td></tr>`;
    renderPagination(document.querySelector('#pagination'), page, (p) => { historyPage = p; cargarHistorial(); });
  } catch (error) {
    body.innerHTML = `<tr><td colspan="8"><div class="empty-state"><span>${error instanceof ApiError ? error.message : 'Error al cargar'}</span></div></td></tr>`;
  }
}

function abrirModalMovimiento() {
  const modal = openModal({
    title: 'Nuevo movimiento',
    subtitle: 'Ingreso, gasto o retiro manual sobre la sesión actual.',
    maxWidth: '400px',
    body: `
      <form id="mov-caja-form" novalidate>
        <div class="alert alert-danger" id="mov-caja-error" role="alert" hidden>
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
          <span class="alert-message"></span>
        </div>
        <div class="field" style="margin-bottom: var(--space-4);">
          <label class="field-label" for="mc-type">Tipo</label>
          <select class="select" id="mc-type" required>
            <option value="INGRESO">Ingreso</option>
            <option value="GASTO">Gasto</option>
            <option value="RETIRO">Retiro</option>
          </select>
        </div>
        <div class="field" style="margin-bottom: var(--space-4);">
          <label class="field-label" for="mc-amount">Monto (S/)</label>
          <input class="input" type="number" id="mc-amount" min="0.01" step="0.01" required />
        </div>
        <div class="field">
          <label class="field-label" for="mc-reason">Motivo</label>
          <input class="input" type="text" id="mc-reason" maxlength="255" required />
        </div>
      </form>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-primary" type="submit" form="mov-caja-form">Registrar</button>
    `,
  });
  modal.footer.querySelector('[data-cancel]').addEventListener('click', closeModal);
  modal.body.querySelector('#mov-caja-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = modal.body.querySelector('#mov-caja-error');
    errorAlert.hidden = true;
    try {
      await api.post('/cash-registers/movements', {
        type: modal.body.querySelector('#mc-type').value,
        amount: Number(modal.body.querySelector('#mc-amount').value),
        reason: modal.body.querySelector('#mc-reason').value.trim(),
      });
      closeModal();
      showToast({ type: 'success', title: 'Movimiento registrado' });
      cargarSesionActual();
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudo registrar';
      errorAlert.hidden = false;
    }
  });
}

function abrirModalCierre(sessionId, expectedAmount) {
  const modal = openModal({
    title: 'Cerrar caja',
    subtitle: `Efectivo esperado: ${formatCurrency(expectedAmount)}`,
    maxWidth: '400px',
    body: `
      <form id="cierre-form" novalidate>
        <div class="alert alert-danger" id="cierre-error" role="alert" hidden>
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="10" cy="10" r="8"/><path d="M10 6v5M10 14h.01" stroke-linecap="round"/></svg>
          <span class="alert-message"></span>
        </div>
        <div class="field" style="margin-bottom: var(--space-4);">
          <label class="field-label" for="cc-counted">Efectivo contado (S/)</label>
          <input class="input" type="number" id="cc-counted" min="0" step="0.01" required />
        </div>
        <div class="field">
          <label class="field-label" for="cc-notes">Notas (opcional)</label>
          <input class="input" type="text" id="cc-notes" maxlength="255" />
        </div>
      </form>
    `,
    footer: `
      <button class="btn btn-secondary" type="button" data-cancel>Cancelar</button>
      <button class="btn btn-danger" type="submit" form="cierre-form">Cerrar caja</button>
    `,
  });
  modal.footer.querySelector('[data-cancel]').addEventListener('click', closeModal);
  modal.body.querySelector('#cierre-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const errorAlert = modal.body.querySelector('#cierre-error');
    errorAlert.hidden = true;
    try {
      const resultado = await api.post(`/cash-registers/sessions/${sessionId}/close`, {
        countedAmount: Number(modal.body.querySelector('#cc-counted').value),
        notes: modal.body.querySelector('#cc-notes').value.trim() || null,
      });
      closeModal();
      const diffMsg = resultado.difference === 0 ? 'Caja cuadrada exactamente.' : `Diferencia: ${formatCurrency(resultado.difference)}`;
      showToast({ type: resultado.difference === 0 ? 'success' : 'warning', title: 'Caja cerrada', message: diffMsg });
      actualizarEstadoCaja();
      cargarSesionActual();
      cargarHistorial();
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError ? error.message : 'No se pudo cerrar la caja';
      errorAlert.hidden = false;
    }
  });
}

const session = requireSession();
if (session) {
  renderShell('caja');
  init();
}
