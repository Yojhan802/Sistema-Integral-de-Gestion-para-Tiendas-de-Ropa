const RECONNECT_DELAY = 3000;

export type LiveElectronicDocument = {
  id: number;
  saleId: number;
  saleNumber?: string;
  documentType: string;
  status: string;
  series?: string;
  documentNumber?: string;
  providerStatus?: string;
  cdrCode?: string;
  cdrMessage?: string;
};

export function connectCustomerNotifications(
  onOrderUpdated: (order: { id: number; status: string; orderNumber?: string }) => void,
  getToken: () => string | undefined,
  refreshToken: () => Promise<boolean>,
  onElectronicDocumentUpdated?: (document: LiveElectronicDocument) => void,
) {
  let closed = false;
  let source: EventSource | null = null;
  let retry: number | undefined;
  const open = () => {
    if (closed) return;
    const token = getToken();
    if (!token) return;
    source = new EventSource(`/api/store/notifications/stream?token=${encodeURIComponent(token)}`);
    source.addEventListener('pedido-actualizado', (event) => { try { onOrderUpdated(JSON.parse((event as MessageEvent).data)); } catch { /* invalid event, ignore */ } });
    source.addEventListener('comprobante-actualizado', (event) => { try { onElectronicDocumentUpdated?.(JSON.parse((event as MessageEvent).data)); } catch { /* invalid event, ignore */ } });
    source.onerror = () => {
      source?.close();
      source = null;
      if (closed) return;
      // EventSource no expone el status HTTP; no reabrimos si el refresh fallÃ³,
      // porque eso convertirÃ­a un 401 persistente en un bucle de solicitudes.
      void refreshToken().then((refreshed) => {
        if (refreshed && !closed) retry = window.setTimeout(open, RECONNECT_DELAY);
      });
    };
  };
  open();
  return { close() { closed = true; source?.close(); if (retry) window.clearTimeout(retry); } };
}

export function connectStaffNotifications(
  onOrderCreated: (order: { id: number; status: string; orderNumber?: string; total?: number; eventType?: 'created' | 'updated' }) => void,
  getToken: () => string | undefined,
  refreshToken: () => Promise<boolean>,
  onElectronicDocumentUpdated?: (document: LiveElectronicDocument) => void,
) {
  let closed = false;
  let source: EventSource | null = null;
  let retry: number | undefined;

  const open = () => {
    if (closed) return;
    const token = getToken();
    if (!token) return;
    source = new EventSource(`/api/notifications/stream?token=${encodeURIComponent(token)}`);
    source.addEventListener('pedido-nuevo', (event) => {
      try { onOrderCreated({ ...JSON.parse((event as MessageEvent).data), eventType: 'created' }); } catch { /* invalid event, ignore */ }
    });
    source.addEventListener('pedido-actualizado', (event) => {
      try { onOrderCreated({ ...JSON.parse((event as MessageEvent).data), eventType: 'updated' }); } catch { /* invalid event, ignore */ }
    });
    source.addEventListener('comprobante-actualizado', (event) => { try { onElectronicDocumentUpdated?.(JSON.parse((event as MessageEvent).data)); } catch { /* invalid event, ignore */ } });
    source.onerror = () => {
      source?.close();
      source = null;
      if (closed) return;
      // Solo reintentamos cuando recibimos un access token nuevo.
      void refreshToken().then((refreshed) => {
        if (refreshed && !closed) retry = window.setTimeout(open, RECONNECT_DELAY);
      });
    };
  };

  open();
  return { close() { closed = true; source?.close(); if (retry) window.clearTimeout(retry); } };
}

export function connectCatalogUpdates(onUpdated: () => void) {
  let closed = false;
  let source: EventSource | null = null;
  let retry: number | undefined;
  const fallback = window.setInterval(() => { if (!closed) onUpdated(); }, 30000);

  const open = () => {
    if (closed) return;
    source = new EventSource('/api/store/catalog/stream');
    source.addEventListener('catalogo-actualizado', () => onUpdated());
    source.onerror = () => {
      source?.close();
      if (!closed) retry = window.setTimeout(open, RECONNECT_DELAY);
    };
  };

  open();
  return { close() { closed = true; source?.close(); if (retry) window.clearTimeout(retry); window.clearInterval(fallback); } };
}
