import type { Product } from './types';

export function formatCurrency(value: number | null | undefined) {
  return new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' }).format(Number(value ?? 0));
}

export function formatDate(value: string | null | undefined) {
  if (!value) return 'Sin fecha';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Fecha no disponible';
  return new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

export function isValidColor(value?: string | null): value is string {
  return Boolean(value && /^#[0-9a-f]{6}$/i.test(value));
}

export function escapeText(value: unknown) { return String(value ?? ''); }

export function resolveProductImage(product?: Pick<Product, 'imageUrl' | 'images'> | null) {
  if (!product) return undefined;
  return product.imageUrl || product.images?.slice().sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))[0]?.imageUrl;
}
