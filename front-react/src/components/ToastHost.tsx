import { useEffect, useState } from 'react';

interface ToastState { title: string; message: string; type: 'success' | 'error' | 'info'; }
let lastToastSignature = '';
let lastToastAt = 0;

export function showToast(message: string, title = 'Confirmación', type: ToastState['type'] = 'success') {
  window.dispatchEvent(new CustomEvent('qynex:toast', { detail: { title, message, type } }));
}

export function ToastHost() {
  const [toast, setToast] = useState<ToastState | null>(null);

  useEffect(() => {
    let timeout: number | undefined;
    const onToast = (event: Event) => {
      const detail = (event as CustomEvent<Partial<ToastState>>).detail;
      if (!detail?.message) return;
      const signature = `${detail.title || 'Confirmación'}|${detail.message}|${detail.type || 'success'}`;
      const now = Date.now();
      if (signature === lastToastSignature && now - lastToastAt < 600) return;
      lastToastSignature = signature;
      lastToastAt = now;
      setToast({ title: detail.title || 'Confirmación', message: detail.message, type: detail.type || 'success' });
      if (timeout) window.clearTimeout(timeout);
      timeout = window.setTimeout(() => setToast(null), 4200);
    };
    window.addEventListener('qynex:toast', onToast);
    return () => { window.removeEventListener('qynex:toast', onToast); if (timeout) window.clearTimeout(timeout); };
  }, []);

  if (!toast) return null;
  return <div className="react-toast-region" aria-live="polite" aria-atomic="true"><div className={`react-toast react-toast-${toast.type}`} role="status"><span className="react-toast-icon" aria-hidden="true">{toast.type === 'success' ? '✓' : toast.type === 'error' ? '!' : 'i'}</span><div><strong>{toast.title}</strong><span>{toast.message}</span></div><button type="button" aria-label="Cerrar notificación" onClick={() => setToast(null)}>×</button></div></div>;
}
