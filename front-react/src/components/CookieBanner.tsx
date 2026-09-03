import { useEffect, useState } from 'react';

const STORAGE_KEY = 'fsp.customer.cookie-consent';
export type CookieConsent = 'accepted' | 'rejected';

/**
 * Lee la decisión guardada. Se envuelve en try/catch porque en navegación privada
 * o con el almacenamiento bloqueado el simple acceso a localStorage lanza.
 */
export function readCookieConsent(): CookieConsent | null {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    return stored === 'accepted' || stored === 'rejected' ? stored : null;
  } catch { return null; }
}

function storeCookieConsent(value: CookieConsent) {
  try { window.localStorage.setItem(STORAGE_KEY, value); } catch { /* sin almacenamiento, se vuelve a preguntar */ }
}

function navigate(event: React.MouseEvent<HTMLAnchorElement>) {
  event.preventDefault();
  window.history.pushState({}, '', '/politica-cookies');
  window.dispatchEvent(new PopStateEvent('popstate'));
}

/**
 * Aviso de almacenamiento local con aceptar y rechazar en igualdad de peso visual:
 * un rechazo escondido no es un rechazo. Rechazar no rompe la tienda porque el
 * carrito y la sesión son estrictamente necesarios para el servicio pedido — así
 * lo explica la Política de Cookies.
 */
export function CookieBanner() {
  const [consent, setConsent] = useState<CookieConsent | null>(() => readCookieConsent());
  const [mounted, setMounted] = useState(false);

  useEffect(() => { setMounted(true); }, []);

  if (consent) return null;

  const decide = (value: CookieConsent) => { storeCookieConsent(value); setConsent(value); };

  return <div className={`store-cookie-banner${mounted ? ' is-visible' : ''}`} role="dialog" aria-live="polite" aria-label="Aviso de cookies y almacenamiento local">
    <div className="store-cookie-copy">
      <strong>Usamos almacenamiento local para que puedas comprar</strong>
      <p>Guardamos tu carrito y tu sesión en este navegador. No usamos cookies publicitarias ni compartimos tu navegación con terceros. <a href="/politica-cookies" onClick={navigate}>Leer la política</a>.</p>
    </div>
    <div className="store-cookie-actions">
      <button type="button" className="store-cookie-reject" onClick={() => decide('rejected')}>Rechazar opcionales</button>
      <button type="button" className="store-cookie-accept" onClick={() => decide('accepted')}>Aceptar</button>
    </div>
  </div>;
}
