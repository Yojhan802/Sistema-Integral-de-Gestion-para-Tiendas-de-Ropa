const currencyFormatter = new Intl.NumberFormat('es-PE', {
  style: 'currency',
  currency: 'PEN',
  minimumFractionDigits: 2,
});

const compactFormatter = new Intl.NumberFormat('es-PE', {
  notation: 'compact',
  maximumFractionDigits: 1,
});

const integerFormatter = new Intl.NumberFormat('es-PE');

export function formatCurrency(value) {
  return currencyFormatter.format(value);
}

export function formatCompact(value) {
  return compactFormatter.format(value);
}

export function formatInteger(value) {
  return integerFormatter.format(value);
}

export function formatDateLong(isoDate) {
  return new Intl.DateTimeFormat('es-PE', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  }).format(new Date(isoDate));
}

export function formatDateTime(isoDate) {
  if (!isoDate) return '—';
  return new Intl.DateTimeFormat('es-PE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(isoDate));
}

/** Escapa texto antes de interpolarlo en innerHTML/document.write — nunca confiar en datos que vienen del backend (p. ej. nombres de cliente). */
export function escapeHtml(texto) {
  return String(texto ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
