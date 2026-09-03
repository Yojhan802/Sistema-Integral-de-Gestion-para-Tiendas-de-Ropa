import { storeApi, ApiError, API_ORIGIN } from './core/store-api.js';
import { renderStoreShell, actualizarContadorCarrito } from './components/store-shell.js';
import { addToCart } from './core/cart.js';
import { formatCurrency, escapeHtml } from '../../../js/core/format.js';
import { showToast } from '../../../js/components/toast.js';

const productId = new URLSearchParams(window.location.search).get('id');
let producto = null;
/** attributeId -> attributeValueId elegido en cada nivel del cascade (Color, Talla, u otros). */
let seleccion = {};
let selectedVariantId = null;

function placeholderImage() {
  return `data:image/svg+xml;utf8,${encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400"><rect width="400" height="400" fill="#f1f4f9"/></svg>'
  )}`;
}

function galeriaDelProducto() {
  const imagenes = producto.images?.filter((image) => image?.imageUrl) ?? [];
  if (imagenes.length) return imagenes;
  return producto.imageUrl ? [{ imageUrl: producto.imageUrl, altText: producto.name, primary: true }] : [];
}

/** Todas las variantes de un producto comparten el mismo conjunto de atributos, en el mismo
 * orden (ver ProductAttribute) — la primera variante ya nos dice cuántos niveles de selección
 * mostrar y en qué orden (antes esto era Color y Talla fijos; ahora es cualquier lista). */
function niveles() {
  return producto.variants?.[0]?.attributes.map((a) => ({
    attributeId: a.attributeId,
    attributeName: a.attributeName,
    inputType: a.inputType,
  })) ?? [];
}

function valorDeAtributo(variante, attributeId) {
  return variante.attributes.find((a) => a.attributeId === attributeId)?.attributeValueId;
}

function render() {
  const galeria = galeriaDelProducto();
  const imageInicial = galeria[0]?.imageUrl ? `${API_ORIGIN}${galeria[0].imageUrl}` : placeholderImage();
  const tieneDescuento = producto.promoPrice != null;
  const listaNiveles = niveles();
  seleccion = {};
  selectedVariantId = null;

  document.querySelector('#product-detail').innerHTML = `
    <div class="store-detail">
      <div class="store-detail-gallery">
        <div class="store-product-image store-detail-main-image">
          <img id="product-main-image" src="${escapeHtml(imageInicial)}" alt="${escapeHtml(galeria[0]?.altText || producto.name)}" />
        </div>
        ${galeria.length > 1 ? `<div class="store-detail-thumbnails" role="list" aria-label="Imágenes del producto">
          ${galeria.map((image, index) => `
            <button class="store-detail-thumbnail${index === 0 ? ' is-selected' : ''}" type="button" data-gallery-index="${index}" aria-label="Ver imagen ${index + 1}">
              <img src="${escapeHtml(API_ORIGIN + image.imageUrl)}" alt="" loading="lazy" />
            </button>
          `).join('')}
        </div>` : ''}
      </div>
      <div>
        <span class="store-product-meta">${escapeHtml(producto.brandName ?? producto.categoryName)}</span>
        <h1 style="margin: var(--space-2) 0;">${escapeHtml(producto.name)}</h1>
        <div class="store-product-price" style="font-size: var(--font-size-xl); margin-bottom: var(--space-4);">
          ${tieneDescuento ? `<span class="price-old">${formatCurrency(producto.price)}</span>` : ''}
          <span>${formatCurrency(tieneDescuento ? producto.promoPrice : producto.price)}</span>
        </div>
        ${producto.description ? `<p style="margin-bottom: var(--space-4);">${escapeHtml(producto.description)}</p>` : ''}

        ${
          producto.material || producto.fit
            ? `
        <div style="margin-bottom: var(--space-5); display:flex; flex-direction:column; gap:4px; font-size: var(--font-size-sm);">
          ${producto.material ? `<div><strong>Material:</strong> ${producto.material}</div>` : ''}
          ${producto.fit ? `<div><strong>Calce:</strong> ${producto.fit}</div>` : ''}
        </div>
        `
            : ''
        }

        ${listaNiveles
          .map(
            (nivel, i) => `
        <div class="field" style="margin-bottom: ${i === listaNiveles.length - 1 ? 'var(--space-5)' : 'var(--space-4)'};">
          <div style="display:flex; justify-content:space-between; align-items:center;">
            <span class="field-label">${escapeHtml(nivel.attributeName)}</span>
            ${i === 0 && producto.sizeGuideImageUrl ? `<a href="${API_ORIGIN}${producto.sizeGuideImageUrl}" target="_blank" rel="noopener" style="font-size: var(--font-size-xs); color: var(--brand-accent); text-decoration:underline;">Guía de tallas</a>` : ''}
          </div>
          <div class="store-swatches" id="nivel-${nivel.attributeId}">
            ${i === 0 ? '' : `<span class="field-hint">Elige ${escapeHtml(listaNiveles[i - 1].attributeName.toLowerCase())} primero</span>`}
          </div>
        </div>
        `
          )
          .join('')}

        <div style="display:flex; align-items:center; gap: var(--space-3);">
          <input type="number" class="input" id="quantity" value="1" min="1" max="10" style="width:80px;" />
          <button class="btn btn-primary btn-lg" type="button" id="btn-add-cart" disabled>Agregar al carrito</button>
        </div>
      </div>
    </div>
  `;

  if (listaNiveles.length > 0) renderNivel(0);
  document.querySelectorAll('[data-gallery-index]').forEach((button) => {
    button.addEventListener('click', () => {
      const image = galeria[Number(button.dataset.galleryIndex)];
      if (!image) return;
      const main = document.querySelector('#product-main-image');
      main.src = `${API_ORIGIN}${image.imageUrl}`;
      main.alt = image.altText || producto.name;
      document.querySelectorAll('[data-gallery-index]').forEach((item) => item.classList.toggle('is-selected', item === button));
    });
  });
  document.querySelector('#btn-add-cart').addEventListener('click', agregarAlCarrito);
}

/** Pinta las opciones disponibles en un nivel del cascade, filtradas por lo ya elegido en los
 * niveles anteriores — generaliza el viejo "elige color, luego se filtran las tallas de ese
 * color" a cualquier cantidad de niveles. */
function renderNivel(index) {
  const listaNiveles = niveles();
  const nivel = listaNiveles[index];
  const contenedor = document.querySelector(`#nivel-${nivel.attributeId}`);
  if (!contenedor) return;

  const anteriores = listaNiveles.slice(0, index);
  const candidatas = producto.variants.filter((v) =>
    anteriores.every((n) => valorDeAtributo(v, n.attributeId) === seleccion[n.attributeId])
  );

  const esUltimoNivel = index === listaNiveles.length - 1;
  const vistos = new Map();
  candidatas.forEach((v) => {
    const attr = v.attributes.find((a) => a.attributeId === nivel.attributeId);
    if (!attr) return;
    const existente = vistos.get(attr.attributeValueId);
    if (!existente) {
      vistos.set(attr.attributeValueId, { attr, inStock: v.inStock, variantId: v.variantId });
    } else if (v.inStock) {
      existente.inStock = true;
    }
  });

  contenedor.innerHTML = [...vistos.values()]
    .map(
      ({ attr, inStock, variantId }) => `
    <button type="button" class="store-swatch" data-value-id="${attr.attributeValueId}" data-variant-id="${esUltimoNivel ? variantId : ''}"
        aria-pressed="${seleccion[nivel.attributeId] === attr.attributeValueId}" ${esUltimoNivel && !inStock ? 'disabled' : ''}>
      ${attr.inputType === 'SWATCH' && attr.hexCode ? `<span class="store-swatch-dot" style="background:${attr.hexCode};"></span>` : ''}
      ${escapeHtml(attr.value)}
    </button>
  `
    )
    .join('');

  contenedor.onclick = (event) => {
    const btn = event.target.closest('[data-value-id]');
    if (!btn) return;
    seleccion[nivel.attributeId] = Number(btn.dataset.valueId);
    listaNiveles.slice(index + 1).forEach((n) => delete seleccion[n.attributeId]);
    selectedVariantId = esUltimoNivel ? Number(btn.dataset.variantId) : null;
    contenedor.querySelectorAll('[data-value-id]').forEach((b) => b.setAttribute('aria-pressed', String(b === btn)));
    document.querySelector('#btn-add-cart').disabled = !esUltimoNivel;

    if (!esUltimoNivel) {
      renderNivel(index + 1);
      for (let i = index + 2; i < listaNiveles.length; i++) {
        const siguiente = document.querySelector(`#nivel-${listaNiveles[i].attributeId}`);
        if (siguiente) siguiente.innerHTML = `<span class="field-hint">Elige ${escapeHtml(listaNiveles[i - 1].attributeName.toLowerCase())} primero</span>`;
      }
    }
  };
}

function agregarAlCarrito() {
  const variante = producto.variants.find((v) => v.variantId === selectedVariantId);
  if (!variante) return;
  const quantity = Math.max(1, Number(document.querySelector('#quantity').value) || 1);

  addToCart(
    {
      variantId: variante.variantId,
      productId: producto.id,
      productName: producto.name,
      variantLabel: variante.variantLabel,
      unitPrice: producto.promoPrice ?? producto.price,
      imageUrl: producto.imageUrl,
    },
    quantity
  );
  actualizarContadorCarrito();
  showToast({ type: 'success', title: 'Agregado al carrito', message: `${producto.name} (${variante.variantLabel})` });
}

async function init() {
  if (!productId) {
    document.querySelector('#product-detail').innerHTML = `<div class="empty-state"><span>Producto no encontrado.</span></div>`;
    return;
  }
  try {
    producto = await storeApi.get(`/store/catalog/products/${productId}`);
    render();
  } catch (error) {
    document.querySelector('#product-detail').innerHTML = `<div class="empty-state"><span>${error instanceof ApiError ? error.message : 'No se pudo cargar el producto'}</span></div>`;
  }
}

renderStoreShell();
init();
