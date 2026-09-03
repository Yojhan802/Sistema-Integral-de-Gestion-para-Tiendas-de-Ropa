import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import type { StoreConfig, StoreTemplate } from '../types';
import { storeApi } from '../services/api';
import { isValidColor } from '../utils';

const allowed: StoreTemplate[] = ['CLASSIC', 'MINIMAL', 'FASHION', 'SPORT', 'LUXURY', 'BOUTIQUE', 'CATALOG', 'MARKET', 'EDITORIAL', 'URBAN'];
const TemplateContext = createContext<StoreTemplate>('CLASSIC');
const StoreConfigContext = createContext<StoreConfig>({});

export function useStoreTemplate() { return useContext(TemplateContext); }

/**
 * Configuración pública del tenant ya resuelta: identificación del proveedor, IGV y
 * moneda. Vive aquí porque el provider ya la pide para elegir la plantilla — así el
 * pie, el checkout y el detalle de producto no repiten la misma llamada.
 */
export function useStoreConfig() { return useContext(StoreConfigContext); }

function normalizeTemplate(value?: string | null): StoreTemplate {
  const normalized = value?.trim().toUpperCase() as StoreTemplate | undefined;
  return normalized && allowed.includes(normalized) ? normalized : 'CLASSIC';
}

export function resolveStorePage(pathname: string) {
  if (pathname.includes('/carrito')) return 'cart';
  if (pathname.includes('/checkout')) return 'checkout';
  if (pathname.includes('/cuenta/pedidos')) return 'orders';
  if (pathname.includes('/producto')) return 'product';
  if (pathname.includes('/cuenta/')) return 'account';
  return 'home';
}

export function TemplateProvider({ children }: { children: ReactNode }) {
  const preview = new URLSearchParams(window.location.search).get('previewTemplate');
  const [template, setTemplate] = useState<StoreTemplate>(normalizeTemplate(preview));
  const isStatusRoute = ['/suspendido.html', '/tienda/no-disponible.html', '/no-disponible.html'].includes(window.location.pathname);
  // El panel de plataforma no es tienda: sin excluirlo se le aplicarían el tema del
  // tenant y el cargador «Preparando la tienda…».
  const isPanelRoute = window.location.pathname.startsWith('/admin') || window.location.pathname.startsWith('/plataforma');
  const isStoreRoute = !isPanelRoute && !isStatusRoute;
  const [ready, setReady] = useState(!isStoreRoute || Boolean(preview));
  const [config, setConfig] = useState<StoreConfig>({});

  useEffect(() => {
    let active = true;
    if (!isStoreRoute) return () => { active = false; };
    storeApi.get<StoreConfig>('/store/catalog/config').then((loaded) => {
      if (!active) return;
      setConfig(loaded ?? {});
      // El título de cada página se compone con el nombre del negocio; avisamos para
      // que se recomponga cuando llega, no solo en el primer render.
      if (loaded?.legalName) {
        document.body.dataset.storeBrand = loaded.legalName;
        window.dispatchEvent(new Event('qynex-brand-ready'));
      }
      if (preview) return;
      setTemplate(normalizeTemplate(loaded?.template));
      applyBrand(loaded);
    }).catch(() => undefined).finally(() => { if (active) setReady(true); });
    document.body.dataset.storeTemplate = normalizeTemplate(preview);
    return () => { active = false; };
  }, [isStoreRoute, preview]);

  useEffect(() => {
    document.body.dataset.storeTemplate = template;
    document.body.dataset.storeTemplateReady = ready ? 'true' : 'false';
    document.body.dataset.storePage = resolveStorePage(window.location.pathname);
    document.body.classList.toggle('store-body', isStoreRoute);
    document.body.classList.toggle(`store-template-${template.toLowerCase()}`, isStoreRoute);
    document.body.classList.toggle('react-template-loading', isStoreRoute && !ready);
    return () => {
      document.body.classList.remove('store-body', 'react-template-loading', ...allowed.map((key) => `store-template-${key.toLowerCase()}`));
    };
  }, [isStoreRoute, ready, template]);

  if (isStoreRoute && !ready) return <div className="store-template-loading" role="status"><span className="store-spinner" aria-hidden="true" />Preparando la tienda…</div>;
  return <TemplateContext.Provider value={template}><StoreConfigContext.Provider value={config}>{children}</StoreConfigContext.Provider></TemplateContext.Provider>;
}

export function applyBrand(config?: StoreConfig | null) {
  const params = new URLSearchParams(window.location.search);
  const values = {
    '--brand-black': params.get('previewPrimaryColor') ?? config?.primaryColor,
    '--brand-accent': params.get('previewAccentColor') ?? config?.accentColor,
    '--color-background': params.get('previewBackgroundColor') ?? config?.backgroundColor,
  };
  Object.entries(values).forEach(([name, value]) => {
    if (isValidColor(value)) document.body.style.setProperty(name, value);
  });
}
