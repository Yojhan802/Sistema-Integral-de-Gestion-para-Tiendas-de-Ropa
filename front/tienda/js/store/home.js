import { storeApi, ApiError, API_ORIGIN } from './core/store-api.js';
import { renderStoreShell } from './components/store-shell.js';
import { formatCurrency, escapeHtml } from '../../../js/core/format.js';
import { debounce } from '../../../js/core/debounce.js';
import { renderPagination } from '../../../js/components/pagination.js';

const PRODUCTOS_POR_SECCION = 8;
const MAX_BANNERS = 6;

const params = new URLSearchParams(window.location.search);
let state = {
  page: 0,
  search: params.get('search') ?? '',
  categoryId: params.get('categoryId') ?? '',
  brandId: params.get('brandId') ?? '',
};
let categorias = [];

function hayFiltroActivo() {
  return Boolean(state.search || state.categoryId || state.brandId);
}

function placeholderImage() {
  return `data:image/svg+xml;utf8,${encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><rect width="200" height="200" fill="#f1f4f9"/></svg>'
  )}`;
}

function productImagePath(product) {
  if (!product) return null;
  return product.imageUrl || product.images?.slice().sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))[0]?.imageUrl || null;
}

function activarFallbacksDeImagen(root = document) {
  root.querySelectorAll('img').forEach((image) => {
    if (image.dataset.fallbackBound === 'true') return;
    image.dataset.fallbackBound = 'true';
    const aplicarFallback = () => {
      if (image.dataset.fallbackApplied === 'true') return;
      image.dataset.fallbackApplied = 'true';
      image.src = placeholderImage();
      image.classList.add('store-image-fallback');
    };
    image.addEventListener('error', aplicarFallback, { once: true });
    if (image.complete && image.naturalWidth === 0) aplicarFallback();
  });
}

function swatchesHtml(colors) {
  if (!colors?.length) return '';
  return `
    <div class="store-card-swatches">
      ${colors.map((c) => {
        const color = typeof c.hexCode === 'string' && /^#[0-9a-f]{6}$/i.test(c.hexCode) ? c.hexCode : '#cccccc';
        return `<span class="store-card-swatch" style="background:${color};" title="${escapeHtml(c.name)}"></span>`;
      }).join('')}
    </div>
  `;
}

function productCard(p) {
  const primaryImage = productImagePath(p);
  const imageUrl = primaryImage ? `${API_ORIGIN}${primaryImage}` : placeholderImage();
  const secondaryImage = p.images?.find((image) => image.imageUrl && image.imageUrl !== primaryImage);
  const tieneDescuento = p.promoPrice != null;
  const descuentoPct = tieneDescuento ? Math.round((1 - p.promoPrice / p.price) * 100) : null;
  return `
    <a class="store-product-card" href="producto.html?id=${encodeURIComponent(p.id)}">
      <div class="store-product-image">
        <img src="${escapeHtml(imageUrl)}" alt="${escapeHtml(p.name)}" loading="lazy" />
        ${secondaryImage ? `<img class="store-product-image-secondary" src="${escapeHtml(API_ORIGIN + secondaryImage.imageUrl)}" alt="" loading="lazy" />` : ''}
        ${descuentoPct ? `<span class="store-product-tag">-${descuentoPct}%</span>` : ''}
      </div>
      <div class="store-product-body">
        <span class="store-product-meta">${escapeHtml(p.brandName ?? p.categoryName ?? '')}</span>
        <span class="store-product-name">${escapeHtml(p.name)}</span>
        <span class="store-product-price">
          ${tieneDescuento ? `<span class="price-old">${formatCurrency(p.price)}</span>` : ''}
          <span>${formatCurrency(tieneDescuento ? p.promoPrice : p.price)}</span>
        </span>
        ${swatchesHtml(p.colors)}
        ${!p.inStock ? '<span class="badge badge-neutral">Agotado</span>' : ''}
      </div>
    </a>
  `;
}

async function cargarFiltros() {
  try {
    const [cats, marcas] = await Promise.all([
      storeApi.get('/store/catalog/categories'),
      storeApi.get('/store/catalog/brands'),
    ]);
    categorias = cats;
    const catSelect = document.querySelector('#filter-category');
    categorias.forEach((c) => catSelect.insertAdjacentHTML('beforeend', `<option value="${c.id}">${escapeHtml(c.name)}</option>`));
    catSelect.value = state.categoryId;

    const brandSelect = document.querySelector('#filter-brand');
    marcas.forEach((b) => brandSelect.insertAdjacentHTML('beforeend', `<option value="${b.id}">${escapeHtml(b.name)}</option>`));
    brandSelect.value = state.brandId;
  } catch {
    // Filtros son un extra — si fallan, el catálogo sigue navegable sin ellos.
  }
}

async function cargarProductosPlano() {
  const grid = document.querySelector('#product-grid');
  try {
    const page = await storeApi.get('/store/catalog/products', {
      query: {
        search: state.search || undefined,
        categoryId: state.categoryId || undefined,
        brandId: state.brandId || undefined,
        page: state.page,
        size: 12,
      },
    });

    grid.innerHTML = page.content.length
      ? page.content.map(productCard).join('')
      : `<div class="empty-state" style="grid-column: 1 / -1;"><span>No se encontraron productos.</span></div>`;
    activarFallbacksDeImagen(grid);

    renderPagination(document.querySelector('#pagination'), page, (p) => {
      state.page = p;
      cargarProductosPlano();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  } catch (error) {
    grid.innerHTML = `<div class="empty-state" style="grid-column: 1 / -1;"><span>${error instanceof ApiError ? error.message : 'No se pudo cargar el catálogo'}</span></div>`;
  }
}

async function cargarSecciones() {
  const contenedor = document.querySelector('#catalog-sections');
  try {
    const paginas = await Promise.all(
      categorias.map((c) => storeApi.get('/store/catalog/products', { query: { categoryId: c.id, size: PRODUCTOS_POR_SECCION } }))
    );

    const secciones = categorias
      .map((c, i) => ({ categoria: c, page: paginas[i] }))
      .filter(({ page }) => page.totalElements > 0);

    contenedor.innerHTML = secciones.length
      ? secciones
            .map(
            ({ categoria, page }) => `
        <section class="store-section">
          <div class="store-section-header">
            <h2>${escapeHtml(categoria.name)}</h2>
            <a href="index.html?categoryId=${encodeURIComponent(categoria.id)}">Ver todo →</a>
          </div>
          <div class="store-grid">${page.content.map(productCard).join('')}</div>
        </section>
      `
          )
          .join('')
      : `<div class="empty-state"><span>Todavía no hay productos publicados.</span></div>`;

    const banners = await storeApi.get('/store/catalog/banners').catch(() => []);
    renderBanners(secciones, banners);
    renderHero(secciones);
    activarFallbacksDeImagen(contenedor);
  } catch (error) {
    contenedor.innerHTML = `<div class="empty-state"><span>${error instanceof ApiError ? error.message : 'No se pudo cargar el catálogo'}</span></div>`;
  }
}

function renderBanners(secciones, banners = []) {
  const contenedor = document.querySelector('#category-banners');
  if (banners.length) {
    contenedor.hidden = false;
    contenedor.innerHTML = banners.map((banner) => `
      <a class="store-category-banner store-promotional-banner" href="${escapeHtml(banner.ctaUrl || '#catalog-sections')}">
        <img src="${escapeHtml(API_ORIGIN + banner.imageUrl)}" alt="${escapeHtml(banner.headline || '')}" loading="lazy" />
        ${banner.headline ? `<strong>${escapeHtml(banner.headline)}</strong>` : ''}
        ${banner.ctaLabel ? `<span>${escapeHtml(banner.ctaLabel)}</span>` : ''}
      </a>
    `).join('');
    return;
  }
  const destacadas = categorias
    .map((categoria) => ({ categoria, page: secciones.find((section) => section.categoria.id === categoria.id)?.page }))
    .filter(({ categoria, page }) => categoria.imageUrl || productImagePath(page?.content?.[0]))
    .slice(0, MAX_BANNERS);

  if (!destacadas.length) {
    contenedor.hidden = true;
    return;
  }
  contenedor.hidden = false;
  contenedor.innerHTML = destacadas
    .map(
      ({ categoria, page }) => `
    <a class="store-category-banner" href="index.html?categoryId=${encodeURIComponent(categoria.id)}">
      <img src="${escapeHtml(API_ORIGIN + (categoria.imageUrl || productImagePath(page?.content?.[0])))}" alt="" loading="lazy" />
      <span>${escapeHtml(categoria.name)}</span>
    </a>
  `
    )
    .join('');
}

async function cargarSugerencias(query) {
  const datalist = document.querySelector('#filter-search-suggestions');
  if (!datalist || query.length < 2) {
    if (datalist) datalist.innerHTML = '';
    return;
  }
  try {
    const suggestions = await storeApi.get('/store/catalog/search-suggestions', { query: { q: query } });
    datalist.innerHTML = suggestions.map((item) => `<option value="${escapeHtml(item.title)}">${escapeHtml(item.subtitle || '')}</option>`).join('');
  } catch {
    datalist.innerHTML = '';
  }
}

function renderHero(secciones) {
  const hero = document.querySelector('#store-hero');
  const lead = secciones.find(({ page }) => page.content[0]?.imageUrl);
  if (!hero || !lead) {
    if (hero) hero.hidden = true;
    return;
  }

  const product = lead.page.content[0];
  hero.hidden = false;
  const image = hero.querySelector('#store-hero-image');
  image.src = `${API_ORIGIN}${product.imageUrl}`;
  image.alt = '';
  hero.querySelector('#store-hero-badge').textContent = `Explora ${lead.categoria.name}`;
  hero.querySelector('#store-hero-title').textContent = lead.categoria.name;
  hero.querySelector('#store-hero-description').textContent = `${lead.page.totalElements} productos disponibles para descubrir.`;
  hero.querySelector('#store-hero-link').href = `index.html?categoryId=${encodeURIComponent(lead.categoria.id)}`;
  activarFallbacksDeImagen(hero);
}

function render() {
  const seccionesEl = document.querySelector('#catalog-sections');
  const planoEl = document.querySelector('#catalog-flat');
  const bannersEl = document.querySelector('#category-banners');

  if (hayFiltroActivo()) {
    seccionesEl.hidden = true;
    bannersEl.hidden = true;
    planoEl.hidden = false;
    cargarProductosPlano();
  } else {
    planoEl.hidden = true;
    seccionesEl.hidden = false;
    cargarSecciones();
  }
}

function desplazarAlAnclaSolicitada() {
  const hash = window.location.hash;
  if (!hash) return;
  const target = document.querySelector(hash);
  if (!target) return;
  window.setTimeout(() => {
    target.scrollIntoView({
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
      block: 'start',
    });
  }, 120);
}

async function init() {
  await cargarFiltros();

  document.querySelector('#filter-search').value = state.search;
  document.querySelector('#filter-search').addEventListener('input', debounce((event) => {
    state.search = event.target.value.trim();
    state.page = 0;
    render();
  }, 350));
  document.querySelector('#filter-search').addEventListener('input', debounce((event) => {
    cargarSugerencias(event.target.value.trim());
  }, 220));
  document.querySelector('#filter-category').addEventListener('change', (event) => {
    state.categoryId = event.target.value;
    state.page = 0;
    render();
  });
  document.querySelector('#filter-brand').addEventListener('change', (event) => {
    state.brandId = event.target.value;
    state.page = 0;
    render();
  });

  render();
  desplazarAlAnclaSolicitada();
}

renderStoreShell({ active: 'catalogo' });
init();
