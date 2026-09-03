import { api } from './api.js';

// Cambian poco durante una sesión: se cargan una vez y se reutilizan en
// todos los formularios que las necesiten (producto, variante, filtros).
let cache = null;

export async function loadCatalog({ force = false } = {}) {
  if (cache && !force) return cache;
  const [categories, subcategories, brands, attributes] = await Promise.all([
    api.get('/categories'),
    api.get('/subcategories'),
    api.get('/brands'),
    api.get('/attributes'),
  ]);
  cache = { categories, subcategories, brands, attributes };
  return cache;
}

export function subcategoriesOf(catalog, categoryId) {
  if (!categoryId) return [];
  return catalog.subcategories.filter((s) => s.categoryId === Number(categoryId));
}

export function activeOnly(items) {
  return items.filter((item) => item.status === 'ACTIVE');
}
