import { useEffect, useMemo, useState } from 'react';
import { storeApi, imageUrl } from '../services/api';
import type { Brand, Category, Page, Product, PublicSearchSuggestion, StorefrontBanner } from '../types';
import { ProductCard } from '../components/ProductCard';
import { useRevealSections } from '../components/RevealSection';
import { EmptyState, ErrorState, LoadingState } from '../components/States';
import { StoreShell } from '../components/StoreShell';
import { resolveProductImage } from '../utils';
import { connectCatalogUpdates } from '../services/live';

const PAGE_SIZE = 24;

export function StoreHomePage() {
  const params = useMemo(() => new URLSearchParams(window.location.search), []);
  const [categories, setCategories] = useState<Category[]>([]);
  const [brands, setBrands] = useState<Brand[]>([]);
  const [banners, setBanners] = useState<StorefrontBanner[]>([]);
  const [suggestions, setSuggestions] = useState<PublicSearchSuggestion[]>([]);
  const [sections, setSections] = useState<Array<{ category: Category; page: Page<Product> }>>([]);
  const [results, setResults] = useState<Page<Product> | null>(null);
  const [search, setSearch] = useState(params.get('search') ?? '');
  const [categoryId, setCategoryId] = useState(params.get('categoryId') ?? '');
  const [brandId, setBrandId] = useState(params.get('brandId') ?? '');
  const [currentPage, setCurrentPage] = useState(Number(params.get('page') || 0));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [catalogRevision, setCatalogRevision] = useState(0);
  const filtered = Boolean(search || categoryId || brandId);

  useEffect(() => {
    const stream = connectCatalogUpdates(() => setCatalogRevision((value) => value + 1));
    return () => stream.close();
  }, []);

  useEffect(() => {
    Promise.all([
      storeApi.get<Category[]>('/store/catalog/categories'),
      storeApi.get<Brand[]>('/store/catalog/brands'),
      storeApi.get<StorefrontBanner[]>('/store/catalog/banners').catch(() => []),
    ]).then(([cats, brandList, bannerList]) => { setCategories(cats ?? []); setBrands(brandList ?? []); setBanners(bannerList ?? []); }).catch(() => undefined);
  }, [catalogRevision]);

  useEffect(() => {
    if (!search.trim()) { setSuggestions([]); return; }
    const timer = window.setTimeout(() => storeApi.get<PublicSearchSuggestion[]>('/store/catalog/search-suggestions', { query: { q: search.trim() } }).then(setSuggestions).catch(() => setSuggestions([])), 220);
    return () => window.clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    let alive = true;
    setLoading(true); setError('');
    if (filtered) {
      storeApi.get<Page<Product>>('/store/catalog/products', { query: { search, categoryId, brandId, page: currentPage, size: PAGE_SIZE } }).then((value) => alive && setResults(value)).catch((reason) => alive && setError(reason.message)).finally(() => alive && setLoading(false));
    } else if (categories.length) {
      Promise.all(categories.map(async (category) => ({ category, page: await storeApi.get<Page<Product>>('/store/catalog/products', { query: { categoryId: category.id, size: 8 } }) }))).then((value) => alive && setSections(value.filter(({ page: result }) => result.totalElements > 0))).catch((reason) => alive && setError(reason.message)).finally(() => alive && setLoading(false));
    } else { setLoading(false); }
    return () => { alive = false; };
  }, [categories, search, categoryId, brandId, currentPage, filtered, catalogRevision]);

  const updateUrl = (next: { search?: string; categoryId?: string; brandId?: string; page?: number }) => {
    const query = new URLSearchParams();
    Object.entries(next).forEach(([key, value]) => { if (value !== undefined && value !== '') query.set(key, String(value)); });
    window.history.replaceState({}, '', `/?${query}`);
  };
  const updateSearch = (value: string) => { setSearch(value); setCurrentPage(0); updateUrl({ search: value, categoryId, brandId }); };
  const updateCategory = (value: string) => { setCategoryId(value); setCurrentPage(0); updateUrl({ search, categoryId: value, brandId }); };
  const updateBrand = (value: string) => { setBrandId(value); setCurrentPage(0); updateUrl({ search, categoryId, brandId: value }); };
  const goPage = (value: number) => { setCurrentPage(value); updateUrl({ search, categoryId, brandId, page: value }); window.setTimeout(() => document.querySelector('#catalog-sections')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 0); };
  const lead = sections.find(({ page: result }) => resolveProductImage(result.content[0]));
  const categoryImages = new Map(categories.map((category) => [category.id, category.imageUrl]));
  sections.forEach(({ category, page }) => {
    if (!categoryImages.get(category.id)) categoryImages.set(category.id, resolveProductImage(page.content[0]));
  });
  useRevealSections(sections.length);

  return <StoreShell active="catalogo"><div className="store-page-heading"><span className="store-kicker">CATÁLOGO QYNEX</span><h1>Encuentra algo que te represente.</h1><p>Explora productos publicados por esta empresa.</p></div>
    {banners.length ? <section className="store-promotional-banners" aria-label="Promociones">{banners.slice(0, 3).map((banner) => <a className="store-promotional-banner" href={banner.ctaUrl || '#catalog-sections'} key={banner.id}><img src={imageUrl(banner.imageUrl)} alt="" /><strong>{banner.headline || 'Descubre la colección'}</strong><span>{banner.ctaLabel || 'Explorar'}</span></a>)}</section> : lead && <section className="store-hero" aria-label="Producto destacado"><img className="store-hero-media" src={imageUrl(lead.page.content[0].imageUrl)} alt="" onError={(event) => { event.currentTarget.src = imageUrl(); }} /><div className="store-hero-content"><span className="store-hero-badge">Explora {lead.category.name}</span><h2>{lead.category.name}</h2><p>{lead.page.totalElements} productos disponibles para descubrir.</p><a className="btn btn-primary" href={`/?categoryId=${lead.category.id}`}>Explorar catálogo →</a></div></section>}
    <div className="store-section-head" id="category-banners"><h2>Explora por categoría</h2><a className="section-link" href="#catalog-sections">Ver todo →</a></div>
    <div className="store-category-banners">{categories.slice(0, 6).map((category, index) => <a className="store-category-banner" key={category.id} href={`/?categoryId=${category.id}`}><img src={imageUrl(categoryImages.get(category.id))} alt="" loading="lazy" onError={(event) => { event.currentTarget.src = imageUrl(); }} /><span>{String(index + 1).padStart(2, '0')}</span><strong>{category.name}</strong></a>)}</div>
    <div className="store-section-head" id="catalog-sections"><h2>{filtered ? 'Resultados' : 'Catálogo'}</h2></div>
    <div className="store-filters"><label className="topbar-search"><span aria-hidden="true">⌕</span><input type="search" list="store-search-suggestions" value={search} onChange={(event) => updateSearch(event.target.value)} placeholder="Buscar producto…" aria-label="Buscar productos" /><datalist id="store-search-suggestions">{suggestions.map((item) => <option key={`${item.type}-${item.id}`} value={item.title}>{item.subtitle || ''}</option>)}</datalist></label><select className="select" value={categoryId} onChange={(event) => updateCategory(event.target.value)} aria-label="Filtrar por categoría"><option value="">Todas las categorías</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select><select className="select" value={brandId} onChange={(event) => updateBrand(event.target.value)} aria-label="Filtrar por marca"><option value="">Todas las marcas</option>{brands.map((brand) => <option key={brand.id} value={brand.id}>{brand.name}</option>)}</select></div>
    {loading ? <LoadingState label="Cargando catálogo…" /> : error ? <ErrorState message={error} /> : filtered ? <><div className="store-grid">{results?.content.length ? results.content.map((product) => <ProductCard key={product.id} product={product} />) : <EmptyState>No se encontraron productos.</EmptyState>}</div>{results && (results.totalPages ?? 0) > 1 && <nav className="pagination-bar" aria-label="Paginación del catálogo">{Array.from({ length: results.totalPages ?? 0 }, (_, index) => <button className={index === currentPage ? 'is-active' : ''} type="button" key={index} onClick={() => goPage(index)}>{index + 1}</button>)}</nav>}</> : sections.length ? sections.map(({ category, page: result }) => <section className="store-section" key={category.id}><div className="store-section-header"><h2>{category.name}</h2><a href={`/?categoryId=${category.id}`}>Ver todo →</a></div><div className="store-grid">{result.content.map((product) => <ProductCard key={product.id} product={product} />)}</div></section>) : <EmptyState>Todavía no hay productos publicados.</EmptyState>}
  </StoreShell>;
}
