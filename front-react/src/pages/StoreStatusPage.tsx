import { useEffect, useState } from 'react';
import { api } from '../services/api';
import type { StoreConfig } from '../types';

type SystemInfo = { subscriptionStatus?: string };

function navigate(path: string) { window.history.replaceState({}, '', path); window.dispatchEvent(new PopStateEvent('popstate')); }

export function StoreStatusPage({ suspended = false }: { suspended?: boolean }) {
  const [branding, setBranding] = useState<StoreConfig>({ name: 'Qynex', logoUrl: null });
  const [checking, setChecking] = useState(false);
  const [message, setMessage] = useState('');
  useEffect(() => { document.body.classList.add('store-body', 'store-status-body'); let mounted = true; api.get<StoreConfig>('/system/branding').then((value) => { if (mounted && value) setBranding(value); }).catch(() => undefined); return () => { mounted = false; document.body.classList.remove('store-status-body'); }; }, []);
  useEffect(() => { document.title = `${suspended ? 'Servicio suspendido' : 'Tienda no disponible'} · ${branding.name || 'Qynex'}`; }, [branding.name, suspended]);
  async function retry() { setChecking(true); setMessage(''); try { const info = await api.get<SystemInfo>('/system/info'); if (info.subscriptionStatus === 'ACTIVA') { navigate(suspended ? '/admin/login' : '/'); return; } } catch { /* el mensaje de reintento explica la situación sin ocultar el error */ } setMessage(suspended ? 'Todavía sigue suspendido. Si ya pagaste, puede tardar unos minutos en reflejarse.' : 'La tienda todavía no está disponible. Vuelve a intentarlo en un momento.'); setChecking(false); }
  const name = branding.name || 'Qynex';
  return <main className="store-status-page"><div className="store-status-card"><div className="store-status-brand">{branding.logoUrl ? <img src={branding.logoUrl} alt={name} /> : <span>{name.charAt(0).toUpperCase()}</span>}<strong>{name}</strong></div><span className="store-kicker">{suspended ? 'ACCESO AL SISTEMA' : 'TIENDA ONLINE'}</span><h1>{suspended ? 'Servicio suspendido' : 'Tienda temporalmente no disponible'}</h1><p>{suspended ? 'Tu suscripción está vencida y el sistema quedó temporalmente pausado. Contacta a soporte para regularizar el pago y reactivarlo.' : 'Estamos resolviendo un tema con la tienda. Vuelve a intentarlo en un momento.'}</p><button className="btn btn-secondary" type="button" disabled={checking} onClick={() => void retry()}>{checking ? 'Comprobando…' : 'Reintentar'}</button>{message && <p className="store-status-message" role="status">{message}</p>}</div></main>;
}
