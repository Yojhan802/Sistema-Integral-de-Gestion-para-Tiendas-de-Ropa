const TEMPLATE_KEYS = new Set([
  'CLASSIC',
  'MINIMAL',
  'FASHION',
  'SPORT',
  'LUXURY',
  'BOUTIQUE',
  'CATALOG',
  'MARKET',
  'EDITORIAL',
  'URBAN',
]);

let baseStylesheet;
let templateStylesheet;

function normalizeTemplate(value) {
  const normalized = typeof value === 'string' ? value.trim().toUpperCase() : '';
  return TEMPLATE_KEYS.has(normalized) ? normalized : 'CLASSIC';
}

function pageName() {
  const path = window.location.pathname.toLowerCase();
  if (path.endsWith('/producto.html')) return 'product';
  if (path.endsWith('/carrito.html')) return 'cart';
  if (path.endsWith('/checkout.html')) return 'checkout';
  if (path.endsWith('/cuenta/login.html')) return 'login';
  if (path.endsWith('/cuenta/registro.html')) return 'register';
  if (path.endsWith('/cuenta/pedidos.html')) return 'orders';
  return 'home';
}

function ensureStylesheet(existing, href, dataName) {
  if (existing) {
    existing.href = href;
    return existing;
  }
  const link = document.createElement('link');
  link.rel = 'stylesheet';
  link.dataset.storeTemplateStylesheet = dataName;
  link.href = href;
  document.head.appendChild(link);
  return link;
}

export function applyStoreTemplate(value, { basePath = '' } = {}) {
  const template = normalizeTemplate(value);
  const body = document.body;
  if (!body) return template;

  body.dataset.storeTemplate = template;
  body.dataset.storePage = pageName();
  body.dataset.storeTemplateReady = 'false';
  body.classList.remove(...[...TEMPLATE_KEYS].map((key) => `store-template-${key.toLowerCase()}`));
  body.classList.add(`store-template-${template.toLowerCase()}`);

  const root = document.documentElement;
  root.dataset.storeTemplate = template;
  baseStylesheet = ensureStylesheet(
    baseStylesheet,
    `${basePath}templates/template-base.css?v=qynex-template-system-5`,
    'base'
  );
  templateStylesheet = ensureStylesheet(
    templateStylesheet,
    `${basePath}templates/${template}/template.css?v=qynex-template-${template.toLowerCase()}-5`,
    'specific'
  );
  templateStylesheet.dataset.template = template;

  const reveal = () => {
    body.dataset.storeTemplateReady = 'true';
  };
  templateStylesheet.onload = reveal;
  if (templateStylesheet.sheet) {
    window.setTimeout(reveal, 0);
  } else {
    // Si un proxy o una extensión bloquea una hoja visual, no dejamos la tienda
    // invisible indefinidamente: el storefront.css común sigue siendo usable.
    window.setTimeout(reveal, 1800);
  }
  return template;
}

export { normalizeTemplate, TEMPLATE_KEYS };
