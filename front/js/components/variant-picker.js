import { api } from '../core/api.js';
import { formatCurrency, formatInteger, escapeHtml } from '../core/format.js';

/**
 * Input de búsqueda de variante (por SKU, código de barras o nombre de
 * producto) con resultados desplegables. `onSelect(variante)` recibe un
 * VarianteBusquedaResponse. Devuelve { root, getSelected(), clear() }.
 */
export function createVariantPicker({ placeholder = 'Buscar por SKU, código de barras o producto…', onSelect }) {
  const root = document.createElement('div');
  root.style.position = 'relative';
  root.innerHTML = `
    <div class="input-group">
      <span class="input-group-icon">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3" stroke-linecap="round"/></svg>
      </span>
      <input class="input" type="text" placeholder="${placeholder}" autocomplete="off" style="padding-left:40px;" />
    </div>
    <div class="vp-results" style="display:none; position:absolute; top:calc(100% + 4px); left:0; right:0; z-index:20;
      background:var(--color-surface); border:1px solid var(--color-border); border-radius:var(--radius-md);
      box-shadow:var(--shadow-md); max-height:260px; overflow-y:auto;"></div>
    <div class="vp-selected" style="display:none; margin-top:var(--space-3); padding:var(--space-3); background:var(--color-surface-sunken); border-radius:var(--radius-md);"></div>
  `;

  const input = root.querySelector('input');
  const resultsBox = root.querySelector('.vp-results');
  const selectedBox = root.querySelector('.vp-selected');
  let selected = null;
  let debounceHandle = null;

  input.addEventListener('input', () => {
    const query = input.value.trim();
    clearTimeout(debounceHandle);
    if (query.length < 2) {
      resultsBox.style.display = 'none';
      return;
    }
    debounceHandle = setTimeout(async () => {
      try {
        const results = await api.get('/variants/search', { query: { q: query } });
        renderResults(results);
      } catch {
        resultsBox.style.display = 'none';
      }
    }, 300);
  });

  document.addEventListener('click', (event) => {
    if (!root.contains(event.target)) resultsBox.style.display = 'none';
  });

  function renderResults(results) {
    if (results.length === 0) {
      resultsBox.innerHTML = `<div style="padding:var(--space-3); color:var(--color-text-muted); font-size:var(--font-size-sm);">Sin resultados</div>`;
    } else {
      resultsBox.innerHTML = results
        .map(
          (v) => `
          <button type="button" class="vp-result" data-id="${v.variantId}" style="display:flex; flex-direction:column; gap:2px; width:100%; text-align:left; padding:var(--space-3); border-bottom:1px solid var(--color-border);">
            <span style="font-weight:600;">${escapeHtml(v.productName)} · ${escapeHtml(v.sku)}</span>
            <span style="font-size:var(--font-size-xs); color:var(--color-text-muted);">Stock: ${formatInteger(v.stock)} · ${formatCurrency(v.effectivePrice)}</span>
          </button>
        `
        )
        .join('');
      resultsBox.querySelectorAll('.vp-result').forEach((btn, index) => {
        btn.addEventListener('click', () => seleccionar(results[index]));
      });
    }
    resultsBox.style.display = 'block';
  }

  function seleccionar(variante) {
    selected = variante;
    resultsBox.style.display = 'none';
    input.value = '';
    selectedBox.style.display = 'block';
    selectedBox.innerHTML = `
      <div style="display:flex; align-items:center; justify-content:space-between; gap:var(--space-3);">
        <div>
          <div style="font-weight:600;">${escapeHtml(variante.productName)}</div>
          <div class="mono" style="font-size:var(--font-size-xs); color:var(--color-text-muted);">${escapeHtml(variante.sku)} · Stock actual: ${formatInteger(variante.stock)}</div>
        </div>
        <button type="button" class="btn btn-ghost btn-sm" data-clear>Cambiar</button>
      </div>
    `;
    selectedBox.querySelector('[data-clear]').addEventListener('click', clear);
    onSelect?.(variante);
  }

  function clear() {
    selected = null;
    selectedBox.style.display = 'none';
    selectedBox.innerHTML = '';
    onSelect?.(null);
  }

  return {
    root,
    getSelected: () => selected,
    clear,
  };
}
