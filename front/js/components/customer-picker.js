import { api } from '../core/api.js';
import { escapeHtml } from '../core/format.js';

/** Selector de cliente opcional para el POS. Devuelve { root, getSelected(), clear() }. */
export function createCustomerPicker({ onSelect } = {}) {
  const root = document.createElement('div');
  root.style.position = 'relative';
  root.innerHTML = `
    <input class="input" type="text" placeholder="Buscar cliente por nombre o documento… (opcional)" autocomplete="off" />
    <div class="vp-results" style="display:none; position:absolute; top:calc(100% + 4px); left:0; right:0; z-index:20;
      background:var(--color-surface); border:1px solid var(--color-border); border-radius:var(--radius-md);
      box-shadow:var(--shadow-md); max-height:200px; overflow-y:auto;"></div>
    <div class="cp-selected" style="display:none; margin-top:var(--space-2); display:flex; align-items:center; justify-content:space-between; gap:var(--space-2);
      font-size:var(--font-size-sm); background:var(--color-surface-sunken); padding:var(--space-2) var(--space-3); border-radius:var(--radius-md);"></div>
  `;

  const input = root.querySelector('input');
  const resultsBox = root.querySelector('.vp-results');
  const selectedBox = root.querySelector('.cp-selected');
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
        const results = await api.get('/customers/search', { query: { q: query } });
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
    resultsBox.innerHTML = results.length
      ? results
          .map(
            (c) => `
            <button type="button" class="vp-result" data-id="${c.id}" style="display:block; width:100%; text-align:left; padding:var(--space-3); border-bottom:1px solid var(--color-border);">
              <div style="font-weight:600;">${escapeHtml(c.fullName)}</div>
              ${c.docNumber ? `<div style="font-size:var(--font-size-xs); color:var(--color-text-muted);">${escapeHtml(c.docNumber)}</div>` : ''}
            </button>
          `
          )
          .join('')
      : `<div style="padding:var(--space-3); color:var(--color-text-muted); font-size:var(--font-size-sm);">Sin resultados</div>`;
    resultsBox.querySelectorAll('.vp-result').forEach((btn, index) => {
      btn.addEventListener('click', () => seleccionar(results[index]));
    });
    resultsBox.style.display = 'block';
  }

  function seleccionar(customer) {
    selected = customer;
    resultsBox.style.display = 'none';
    input.value = '';
    input.style.display = 'none';
    selectedBox.style.display = 'flex';
    selectedBox.innerHTML = `<span>${escapeHtml(customer.fullName)}</span><button type="button" class="btn btn-ghost btn-sm" data-clear>Quitar</button>`;
    selectedBox.querySelector('[data-clear]').addEventListener('click', clear);
    onSelect?.(customer);
  }

  function clear() {
    selected = null;
    input.style.display = 'block';
    selectedBox.style.display = 'none';
    onSelect?.(null);
  }

  return { root, getSelected: () => selected, clear };
}
