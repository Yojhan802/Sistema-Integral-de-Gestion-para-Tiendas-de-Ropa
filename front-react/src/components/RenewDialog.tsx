import { useEffect, useMemo, useState } from 'react';
import { ApiError, api } from '../services/api';
import { showToast } from './ToastHost';
import { EmptyState } from './States';

type Pago = {
  id: number; fecha: string; monto: number; metodo: string; referencia?: string | null; comprobanteUrl?: string | null;
  periodoInicio: string; periodoFin: string; origen: string;
  registradoPor?: string | null; nota?: string | null;
};

type Renovacion = {
  nextPaymentDue: string; subscriptionStatus: string;
  reactivada: boolean; pago: Pago;
};

const METODOS = ['TRANSFERENCIA', 'YAPE', 'PLIN', 'EFECTIVO', 'TARJETA', 'OTRO'];
const soles = (valor: number) => `S/ ${Number(valor || 0).toFixed(2)}`;

/** Fecha ISO (yyyy-mm-dd) a texto local, sin que UTC reste un día en Perú. */
function comoFecha(iso?: string | null) {
  if (!iso) return '—';
  const [anio, mes, dia] = iso.slice(0, 10).split('-').map(Number);
  return new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium' }).format(new Date(anio, mes - 1, dia));
}

function comoIso(fecha: Date) {
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
}

/**
 * Inicio del periodo: `max(vencimiento, hoy)`, la misma regla que aplica el servidor.
 * Pagar por adelantado no pierde días; volver tras estar fuera no cobra ese tiempo.
 */
function inicioDelPeriodo(vence: string | null | undefined) {
  const hoy = new Date();
  const soloHoy = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate());
  if (!vence) return soloHoy;
  const [anio, mes, dia] = vence.slice(0, 10).split('-').map(Number);
  const vencimiento = new Date(anio, mes - 1, dia);
  return vencimiento < soloHoy ? soloHoy : vencimiento;
}

function sumarMeses(desde: Date, meses: number) {
  return comoIso(new Date(desde.getFullYear(), desde.getMonth() + meses, desde.getDate()));
}

/**
 * Registra el pago de la mensualidad y mueve el vencimiento.
 *
 * <p>El cobro es anticipado, así que el periodo arranca en `max(vencimiento, hoy)`. El
 * diálogo muestra el tramo resultante antes de cobrar y explica cuál de los dos casos
 * aplica, porque la diferencia importa: al que vuelve tras una pausa no se le cobra el
 * tiempo en que no tuvo el sistema.
 */
export function RenewDialog({ tenantId, empresa, vence, totalPaquete, onClose, onRenovado }: {
  tenantId: number; empresa: string; vence?: string | null; totalPaquete?: number | null;
  onClose: () => void; onRenovado: () => void;
}) {
  const [monto, setMonto] = useState(String(Number(totalPaquete || 0).toFixed(2)));
  const [metodo, setMetodo] = useState('TRANSFERENCIA');
  const [referencia, setReferencia] = useState('');
  const [meses, setMeses] = useState(1);
  const [nota, setNota] = useState('');
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState('');
  const [historial, setHistorial] = useState<Pago[]>([]);
  // La captura del Yape o de la transferencia: sin ella la referencia sola no sustenta
  // un cobro discutido, y hoy vive en un chat de WhatsApp donde se pierde.
  const [comprobante, setComprobante] = useState<File | null>(null);

  useEffect(() => {
    let vivo = true;
    api.get<Pago[]>(`/platform/tenants/${tenantId}/subscription/payments`, { auth: 'staff' })
      .then((respuesta) => { if (vivo) setHistorial(respuesta ?? []); })
      .catch(() => undefined);
    return () => { vivo = false; };
  }, [tenantId]);

  const inicio = useMemo(() => inicioDelPeriodo(vence), [vence]);
  const nuevoVencimiento = useMemo(() => sumarMeses(inicio, meses), [inicio, meses]);
  // Si el periodo anterior ya se acabó, el nuevo arranca hoy y no se cobra el hueco.
  const reanudaHoy = comoIso(inicio) !== (vence ?? '').slice(0, 10);

  /**
   * El comprobante no se sirve por su ruta estática —está bloqueada— sino por un endpoint
   * que exige ser operador. Como la API usa Bearer y no cookies, un `<img>` o un enlace no
   * podrían autenticarse: hay que pedirlo y abrir el blob.
   */
  async function verComprobante(pagoId: number) {
    try {
      const { blob } = await api.download(`/platform/tenants/${tenantId}/subscription/payments/${pagoId}/proof`,
        { auth: 'staff' });
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener');
      // Se revoca con holgura para que la pestaña alcance a cargarlo.
      window.setTimeout(() => URL.revokeObjectURL(url), 60000);
    } catch {
      showToast('No se pudo abrir el comprobante.', 'Comprobante', 'error');
    }
  }

  async function registrar() {
    setGuardando(true); setError('');
    try {
      const resultado = await api.post<Renovacion>(`/platform/tenants/${tenantId}/subscription/payments`,
        { monto: Number(monto), metodo, referencia: referencia.trim() || null, meses, nota: nota.trim() || null },
        { auth: 'staff' });
      if (comprobante) {
        const cuerpo = new FormData();
        cuerpo.append('file', comprobante);
        // El comprobante es opcional: si falla la subida, el pago ya quedó registrado.
        await api.post(`/platform/tenants/${tenantId}/subscription/payments/${resultado.pago.id}/proof`,
          cuerpo, { auth: 'staff' }).catch(() => showToast('El pago se registró, pero no se pudo subir el comprobante.', 'Comprobante', 'error'));
      }
      const detalle = resultado.reactivada ? ' La empresa quedó reactivada.' : '';
      showToast(`Pago registrado. Vence el ${comoFecha(resultado.nextPaymentDue)}.${detalle}`,
        'Suscripción renovada', 'info');
      setHistorial((actual) => [resultado.pago, ...actual]);
      onRenovado();
      onClose();
    } catch (razon) {
      setError(razon instanceof ApiError ? razon.message : 'No se pudo registrar el pago.');
    } finally { setGuardando(false); }
  }

  return <div className="react-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) onClose(); }}>
    <section className="react-dialog react-renew-dialog" role="dialog" aria-modal="true" aria-labelledby="react-renew-title">
      <div className="react-dialog-header">
        <div>
          <h2 id="react-renew-title">Renovar suscripción · {empresa}</h2>
          <p className="field-hint">Vence actualmente el {comoFecha(vence)}.</p>
        </div>
        <button className="btn btn-ghost btn-sm" type="button" aria-label="Cerrar" onClick={onClose}>×</button>
      </div>

      <div className="react-renew-period">
        <span>Periodo que cubre este pago</span>
        <strong>{comoFecha(comoIso(inicio))} → {comoFecha(nuevoVencimiento)}</strong>
        <small>{reanudaHoy
          ? 'El periodo anterior ya terminó, así que arranca hoy: no se cobra el tiempo en que la empresa no tuvo el sistema.'
          : 'Arranca al terminar el periodo ya pagado, para no perder los días que le quedan.'}</small>
      </div>

      <div className="form-grid">
        <label className="field"><span className="field-label">Monto</span>
          <input className="input" type="number" min="0" step="0.01" value={monto} onChange={(event) => setMonto(event.target.value)} />
          {totalPaquete != null && <small className="field-hint">Paquete actual: {soles(totalPaquete)}</small>}
        </label>
        <label className="field"><span className="field-label">Meses</span>
          <input className="input" type="number" min="1" max="24" value={meses} onChange={(event) => setMeses(Math.max(1, Math.min(24, Number(event.target.value) || 1)))} />
        </label>
        <label className="field"><span className="field-label">Método</span>
          <select className="select" value={metodo} onChange={(event) => setMetodo(event.target.value)}>
            {METODOS.map((valor) => <option value={valor} key={valor}>{valor.charAt(0) + valor.slice(1).toLowerCase()}</option>)}
          </select>
        </label>
        <label className="field"><span className="field-label">Referencia</span>
          <input className="input" maxLength={80} value={referencia} onChange={(event) => setReferencia(event.target.value)} placeholder="N.° de operación" />
        </label>
        <label className="field field-span-2"><span className="field-label">Comprobante</span>
          <input className="input" type="file" accept="image/png,image/jpeg,image/webp"
            onChange={(event) => setComprobante(event.target.files?.[0] || null)} />
          <small className="field-hint">Opcional. Arrastra aquí la captura que te enviaron por WhatsApp; queda guardada junto al pago.</small>
        </label>
        <label className="field field-span-2"><span className="field-label">Nota</span>
          <input className="input" maxLength={255} value={nota} onChange={(event) => setNota(event.target.value)} placeholder="Opcional" />
        </label>
      </div>

      {error && <div className="alert alert-danger" role="alert">{error}</div>}

      <details className="react-modules-history react-renew-history">
        <summary>Historial de pagos ({historial.length})</summary>
        {historial.length ? <ul>
          {historial.map((pago, indice) => <li key={`${pago.fecha}-${indice}`}>
            <div className="react-history-head">
              <span>{new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium' }).format(new Date(pago.fecha))}</span>
              <strong>{soles(pago.monto)}</strong>
            </div>
            <small>{comoFecha(pago.periodoInicio)} → {comoFecha(pago.periodoFin)} · {pago.metodo}{pago.referencia ? ` · ${pago.referencia}` : ''}</small>
            {pago.comprobanteUrl && <button className="react-history-proof" type="button" onClick={() => void verComprobante(pago.id)}>Ver comprobante</button>}
            <small>{pago.registradoPor ? `Por ${pago.registradoPor}` : 'Sin autor'}{pago.origen === 'ONLINE' ? ' · pago online' : ''}</small>
          </li>)}
        </ul> : <EmptyState>Todavía no hay pagos registrados.</EmptyState>}
      </details>

      <div className="react-dialog-actions">
        <button className="btn btn-secondary" type="button" onClick={onClose}>Cancelar</button>
        <button className="btn btn-primary" type="button" disabled={guardando} onClick={registrar}>
          {guardando ? 'Registrando…' : 'Registrar pago y renovar'}
        </button>
      </div>
    </section>
  </div>;
}
