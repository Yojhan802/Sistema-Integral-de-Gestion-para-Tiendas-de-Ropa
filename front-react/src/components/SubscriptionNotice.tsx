import type { StoreConfig } from '../types';

/** Desde cuántos días antes se avisa. Una semana da margen para pagar sin agobiar. */
const DIAS_DE_AVISO = 7;

/** Días que faltan para la fecha dada; negativo si ya pasó. */
function diasHasta(fecha: string): number | null {
  const partes = fecha.split('-').map(Number);
  if (partes.length !== 3 || partes.some(Number.isNaN)) return null;
  // Se compara a medianoche local: usar Date.parse trataría la fecha como UTC y en
  // Perú (UTC-5) restaría un día.
  const vence = new Date(partes[0], partes[1] - 1, partes[2]);
  const hoy = new Date();
  const inicioHoy = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate());
  return Math.round((vence.getTime() - inicioHoy.getTime()) / 86400000);
}

function mensaje(dias: number): { texto: string; tono: 'warning' | 'danger' } {
  if (dias < 0) return { texto: `Tu suscripción venció hace ${Math.abs(dias)} ${Math.abs(dias) === 1 ? 'día' : 'días'}.`, tono: 'danger' };
  if (dias === 0) return { texto: 'Tu suscripción vence hoy.', tono: 'danger' };
  if (dias === 1) return { texto: 'Tu suscripción vence mañana.', tono: 'warning' };
  return { texto: `Tu suscripción vence en ${dias} días.`, tono: 'warning' };
}

/**
 * Aviso de vencimiento dentro del panel del cliente.
 *
 * <p>Va aquí y no por correo porque el backend no tiene infraestructura de envío: es el
 * canal que sí existe hoy y llega a quien puede pagar. Solo aparece cerca del
 * vencimiento; el resto del tiempo no ocupa espacio.
 */
export function SubscriptionNotice({ settings }: { settings: StoreConfig }) {
  if (settings.subscriptionStatus === 'SUSPENDIDA') {
    return <div className="react-subscription-notice is-danger" role="alert">
      <strong>Servicio suspendido.</strong>
      <span>Regulariza el pago para volver a operar con normalidad.</span>
    </div>;
  }
  if (!settings.nextPaymentDue) return null;
  const dias = diasHasta(settings.nextPaymentDue);
  if (dias === null || dias > DIAS_DE_AVISO) return null;
  const { texto, tono } = mensaje(dias);
  return <div className={`react-subscription-notice is-${tono}`} role="status">
    <strong>{texto}</strong>
    <span>Fecha de pago: {settings.nextPaymentDue}. Renueva para no perder el acceso.</span>
  </div>;
}
