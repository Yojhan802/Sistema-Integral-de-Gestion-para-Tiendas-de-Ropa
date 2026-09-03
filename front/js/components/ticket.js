import { fetchCompanySettings } from '../core/settings.js';
import { API_ORIGIN } from '../core/api.js';
import { formatCurrency, formatDateTime, escapeHtml } from '../core/format.js';

/** Abre una ventana aparte con el ticket listo para imprimir (docs §63 "Fase 1.5 — POS"). */
export async function imprimirTicket(venta) {
  const ventana = window.open('', `ticket-${venta.id}`, 'width=380,height=680');
  if (!ventana) {
    alert('El navegador bloqueó la ventana del ticket. Permite ventanas emergentes para imprimir.');
    return;
  }
  ventana.document.write('<p style="font-family:sans-serif; padding:24px;">Cargando ticket…</p>');

  const settings = await fetchCompanySettings();
  ventana.document.open();
  ventana.document.write(construirHtml(venta, settings));
  ventana.document.close();
}

function construirHtml(venta, settings) {
  const igvRate = settings?.igvRate != null ? Number(settings.igvRate) : null;
  const igvIncluido = igvRate ? venta.total - venta.total / (1 + igvRate) : null;

  const filasItems = venta.items
    .map(
      (item) => `
    <tr>
      <td>${item.quantity}</td>
      <td>${escapeHtml(item.productName)}<br><span class="muted">${escapeHtml(item.variantLabel)}</span></td>
      <td class="num">${formatCurrency(item.subtotal)}</td>
    </tr>
  `
    )
    .join('');

  const filasPagos = venta.payments
    .map((p) => `<div class="fila"><span>${escapeHtml(p.paymentMethodName)}${p.reference ? ` (${escapeHtml(p.reference)})` : ''}</span><span>${formatCurrency(p.amount)}</span></div>`)
    .join('');

  return `
<!doctype html>
<html lang="es">
<head>
<meta charset="UTF-8" />
<title>${escapeHtml(venta.saleNumber)}</title>
<style>
  @page { size: 80mm auto; margin: 4mm; }
  * { box-sizing: border-box; }
  body { font-family: 'Courier New', Consolas, monospace; font-size: 12px; color: #111; margin: 0; padding: 16px; width: 320px; }
  .center { text-align: center; }
  .muted { color: #555; font-size: 11px; }
  .logo { max-width: 120px; max-height: 60px; margin: 0 auto 8px; display: block; }
  h1 { font-size: 14px; margin: 0 0 2px; }
  hr { border: none; border-top: 1px dashed #999; margin: 8px 0; }
  table { width: 100%; border-collapse: collapse; }
  th { text-align: left; font-size: 11px; border-bottom: 1px dashed #999; padding-bottom: 4px; }
  td { vertical-align: top; padding: 4px 0; font-size: 12px; }
  .num { text-align: right; white-space: nowrap; }
  .fila { display: flex; justify-content: space-between; padding: 2px 0; }
  .total { font-weight: 700; font-size: 14px; }
  .footer { margin-top: 12px; text-align: center; white-space: pre-line; }
  .print-actions { margin-top: 16px; text-align: center; }
  .print-actions button { font-family: inherit; font-size: 13px; padding: 8px 16px; cursor: pointer; }
  @media print { .print-actions { display: none; } }
</style>
</head>
<body>
  <div class="center">
    ${settings?.logoUrl ? `<img class="logo" src="${API_ORIGIN}${settings.logoUrl}" alt="" />` : ''}
    <h1>${escapeHtml(settings?.name ?? 'Qynex')}</h1>
    ${settings?.ruc ? `<div class="muted">RUC ${escapeHtml(settings.ruc)}</div>` : ''}
    ${settings?.address ? `<div class="muted">${escapeHtml(settings.address)}</div>` : ''}
    ${settings?.phone ? `<div class="muted">${escapeHtml(settings.phone)}</div>` : ''}
  </div>
  <hr />
  <div class="fila"><span>N° venta</span><span>${escapeHtml(venta.saleNumber)}</span></div>
  <div class="fila"><span>Fecha</span><span>${formatDateTime(venta.createdAt)}</span></div>
  <div class="fila"><span>Vendedor</span><span>${escapeHtml(venta.sellerName)}</span></div>
  ${venta.customerName ? `<div class="fila"><span>Cliente</span><span>${escapeHtml(venta.customerName)}</span></div>` : ''}
  <hr />
  <table>
    <thead><tr><th>Cant</th><th>Descripción</th><th class="num">Importe</th></tr></thead>
    <tbody>${filasItems}</tbody>
  </table>
  <hr />
  <div class="fila"><span>Subtotal</span><span>${formatCurrency(venta.subtotal)}</span></div>
  ${venta.discountAmount > 0 ? `<div class="fila"><span>Descuento</span><span>-${formatCurrency(venta.discountAmount)}</span></div>` : ''}
  ${igvIncluido != null ? `<div class="fila muted"><span>IGV incluido (${(igvRate * 100).toFixed(0)}%)</span><span>${formatCurrency(igvIncluido)}</span></div>` : ''}
  <div class="fila total"><span>TOTAL</span><span>${formatCurrency(venta.total)}</span></div>
  <hr />
  ${filasPagos}
  ${settings?.ticketFooter ? `<div class="footer">${escapeHtml(settings.ticketFooter)}</div>` : ''}
  <div class="print-actions">
    <button type="button" onclick="window.print()">Imprimir</button>
    <button type="button" onclick="window.close()">Cerrar</button>
  </div>
</body>
</html>
  `;
}
