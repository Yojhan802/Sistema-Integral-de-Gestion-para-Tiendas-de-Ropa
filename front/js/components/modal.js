// Modal genérico: una sola instancia visible a la vez, con foco atrapado,
// cierre con Escape, y devolución del foco al elemento que lo abrió
// (docs/06 §8). Cada instancia lleva un token: si un callback abre un modal
// nuevo antes de que el que lo invocó termine de cerrarse, el cierre
// "atrasado" del modal viejo no debe llevarse por delante al nuevo.

import { escapeHtml } from '../core/format.js';

let activeModal = null;
let lastFocused = null;
let nextToken = 0;

function ensureRoot() {
  let root = document.querySelector('#modal-root');
  if (!root) {
    root = document.createElement('div');
    root.id = 'modal-root';
    document.body.appendChild(root);
  }
  return root;
}

export function openModal({ title, subtitle = '', body, footer = '', maxWidth = '520px' }) {
  closeModal();
  lastFocused = document.activeElement;
  const token = ++nextToken;

  const root = ensureRoot();
  const overlay = document.createElement('div');
  overlay.className = 'modal-overlay';
  overlay.innerHTML = `
    <div class="modal" style="--modal-max-width:${maxWidth};" role="dialog" aria-modal="true" aria-labelledby="modal-title">
      <div class="modal-header">
        <div>
          <h3 id="modal-title">${escapeHtml(title)}</h3>
          ${subtitle ? `<p>${escapeHtml(subtitle)}</p>` : ''}
        </div>
        <button class="modal-close" type="button" data-modal-close aria-label="Cerrar">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round"/></svg>
        </button>
      </div>
      <div class="modal-body"></div>
      ${footer ? `<div class="modal-footer">${footer}</div>` : ''}
    </div>
  `;
  root.appendChild(overlay);

  const bodyEl = overlay.querySelector('.modal-body');
  if (typeof body === 'string') {
    bodyEl.innerHTML = body;
  } else {
    bodyEl.appendChild(body);
  }

  overlay.addEventListener('click', (event) => {
    if (event.target === overlay) closeModal();
  });
  overlay.querySelector('[data-modal-close]').addEventListener('click', () => closeModal());

  const handleKeydown = (event) => {
    if (event.key === 'Escape') {
      closeModal();
      return;
    }
    if (event.key === 'Tab') {
      const focusable = overlay.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
  };
  document.addEventListener('keydown', handleKeydown);

  const firstField = overlay.querySelector('input, select, textarea, button');
  firstField?.focus();

  activeModal = { overlay, handleKeydown, token };

  return {
    element: overlay,
    body: bodyEl,
    footer: overlay.querySelector('.modal-footer'),
    // Cierra esta instancia concreta: si mientras tanto se abrió otro modal
    // (mismo flujo de un callback onConfirm), no le toca la puerta a ese otro.
    close: () => closeModal(token),
  };
}

export function closeModal(token) {
  if (!activeModal) return;
  if (token !== undefined && activeModal.token !== token) return;
  document.removeEventListener('keydown', activeModal.handleKeydown);
  activeModal.overlay.remove();
  activeModal = null;
  lastFocused?.focus?.();
}
