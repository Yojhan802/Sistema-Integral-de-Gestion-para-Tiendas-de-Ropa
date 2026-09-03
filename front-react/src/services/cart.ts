import type { CartItem } from '../types';

const CART_KEY = 'fsp.customer.cart';

export function getCart(): CartItem[] {
  try {
    const value = JSON.parse(localStorage.getItem(CART_KEY) ?? '[]');
    return Array.isArray(value) ? value : [];
  } catch { return []; }
}

function save(items: CartItem[]) {
  localStorage.setItem(CART_KEY, JSON.stringify(items));
  window.dispatchEvent(new CustomEvent('qynex-cart-change', { detail: items }));
}

export function addToCart(item: Omit<CartItem, 'quantity'>, quantity = 1) {
  const items = getCart();
  const existing = items.find((entry) => entry.variantId === item.variantId);
  if (existing) existing.quantity += quantity;
  else items.push({ ...item, quantity });
  save(items);
  return items;
}

export function updateCartQuantity(variantId: number, quantity: number) {
  const items = getCart().map((item) => item.variantId === variantId ? { ...item, quantity } : item).filter((item) => item.quantity > 0);
  save(items);
  return items;
}

export function removeFromCart(variantId: number) {
  const items = getCart().filter((item) => item.variantId !== variantId);
  save(items);
  return items;
}

export function clearCart() { save([]); }
export const cartCount = (items = getCart()) => items.reduce((total, item) => total + item.quantity, 0);
export const cartTotal = (items = getCart()) => items.reduce((total, item) => total + item.unitPrice * item.quantity, 0);
