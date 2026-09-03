import { getCart, updateCartQuantity, removeFromCart, cartTotal } from './core/cart.js';
import { renderStoreShell, actualizarContadorCarrito } from './components/store-shell.js';
import { renderStoreSteps } from './components/store-steps.js';
import { API_ORIGIN } from './core/store-api.js';
import { formatCurrency, escapeHtml } from '../../../js/core/format.js';

function placeholderImage() {
  return `data:image/svg+xml;utf8,${encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="88" height="88"><rect width="88" height="88" fill="#f1f4f9"/></svg>'
  )}`;
}

function render() {
  const items = getCart();
  const itemsEl = document.querySelector('#cart-items');
  const summaryEl = document.querySelector('#cart-summary');

  if (!items.length) {
    itemsEl.innerHTML = `
      <div class="store-empty-cart">
        <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.5">
          <circle cx="9" cy="21" r="1"/><circle cx="19" cy="21" r="1"/>
          <path d="M2 3h2l2.4 12.4a2 2 0 002 1.6h8.7a2 2 0 002-1.6L21 8H6" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <div>
          <h3 class="store-empty-cart-title">Tu carrito está vacío</h3>
          <p>Explora el catálogo y agrega tus prendas favoritas.</p>
        </div>
        <a class="btn btn-primary" href="index.html">Ver catálogo</a>
      </div>
    `;
    summaryEl.innerHTML = '';
    return;
  }

  itemsEl.innerHTML = items
    .map(
      (it) => `
    <div class="store-cart-item">
      <img src="${it.imageUrl ? `${API_ORIGIN}${it.imageUrl}` : placeholderImage()}" alt="${escapeHtml(it.productName)}" />
      <div class="store-cart-product">
        <div class="store-cart-product-name">${escapeHtml(it.productName)}</div>
        <div class="store-product-meta">${escapeHtml(it.variantLabel)}</div>
        <div class="store-cart-unit-price">${formatCurrency(it.unitPrice)} c/u</div>
        <div class="store-cart-controls">
          <div class="store-qty-stepper">
            <button type="button" data-qty-down="${it.variantId}" aria-label="Restar una unidad">−</button>
            <span>${it.quantity}</span>
            <button type="button" data-qty-up="${it.variantId}" aria-label="Sumar una unidad">+</button>
          </div>
        </div>
        <button class="store-remove-btn" type="button" data-remove="${it.variantId}">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round"/></svg>
          Quitar
        </button>
      </div>
      <div class="store-cart-line-total">${formatCurrency(it.unitPrice * it.quantity)}</div>
    </div>
  `
    )
    .join('');

  itemsEl.querySelectorAll('[data-qty-up]').forEach((btn) => {
    btn.addEventListener('click', () => cambiarCantidad(Number(btn.dataset.qtyUp), 1));
  });
  itemsEl.querySelectorAll('[data-qty-down]').forEach((btn) => {
    btn.addEventListener('click', () => cambiarCantidad(Number(btn.dataset.qtyDown), -1));
  });
  itemsEl.querySelectorAll('[data-remove]').forEach((btn) => {
    btn.addEventListener('click', () => {
      removeFromCart(Number(btn.dataset.remove));
      render();
      actualizarContadorCarrito();
    });
  });

  summaryEl.innerHTML = `
    <div class="store-summary">
      <div class="store-summary-row"><span>Subtotal</span><span>${formatCurrency(cartTotal(items))}</span></div>
      <p class="store-product-meta store-summary-note">El costo de envío se calcula en el siguiente paso.</p>
      <div class="store-summary-total"><span>Total</span><span>${formatCurrency(cartTotal(items))}</span></div>
      <a class="btn btn-primary btn-lg btn-block store-summary-action" href="checkout.html">Continuar al pago</a>
    </div>
  `;
}

function cambiarCantidad(variantId, delta) {
  const actual = getCart().find((it) => it.variantId === variantId);
  if (!actual) return;
  updateCartQuantity(variantId, actual.quantity + delta);
  render();
  actualizarContadorCarrito();
}

renderStoreShell();
renderStoreSteps(document.querySelector('#store-steps'), 1);
render();
