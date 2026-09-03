import { renderEan13Svg } from './barcode.js';
import { formatCurrency, escapeHtml } from '../core/format.js';

/** Abre una ventana con etiquetas de precio + código de barras listas para imprimir y pegar (docs §12). */
export function imprimirEtiquetas({ producto, variante, cantidad = 1 }) {
  const ventana = window.open('', `etiqueta-${variante.id}`, 'width=420,height=600');
  if (!ventana) {
    alert('El navegador bloqueó la ventana de la etiqueta. Permite ventanas emergentes para imprimir.');
    return;
  }

  const precio = producto.promoPrice ?? producto.price;
  const etiqueta = `
    <div class="etiqueta">
      <div class="nombre">${escapeHtml(producto.name)}</div>
      <div class="detalle">${escapeHtml(variante.variantLabel)}</div>
      <div class="precio">${formatCurrency(precio)}</div>
      ${renderEan13Svg(variante.barcode, { moduleWidth: 1.3, height: 44 })}
    </div>
  `;

  const total = Math.max(1, Number(cantidad) || 1);

  ventana.document.write(`
<!doctype html>
<html lang="es">
<head>
<meta charset="UTF-8" />
<title>Etiquetas ${variante.sku}</title>
<style>
  @page { size: auto; margin: 8mm; }
  * { box-sizing: border-box; }
  body { font-family: Arial, sans-serif; margin: 0; padding: 16px; }
  .hoja { display: flex; flex-wrap: wrap; gap: 6mm; }
  .etiqueta {
    width: 48mm;
    padding: 3mm;
    border: 1px dashed #999;
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    break-inside: avoid;
  }
  .nombre { font-size: 11px; font-weight: 700; line-height: 1.2; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .detalle { font-size: 10px; color: #444; }
  .precio { font-size: 15px; font-weight: 700; margin: 1px 0; }
  .etiqueta svg { max-width: 100%; height: auto; }
  .print-actions { margin-top: 16px; text-align: center; }
  .print-actions button { font-family: inherit; font-size: 13px; padding: 8px 16px; cursor: pointer; }
  @media print { .print-actions { display: none; } }
</style>
</head>
<body>
  <div class="hoja">${etiqueta.repeat(total)}</div>
  <div class="print-actions">
    <button type="button" onclick="window.print()">Imprimir</button>
    <button type="button" onclick="window.close()">Cerrar</button>
  </div>
</body>
</html>
  `);
  ventana.document.close();
}
