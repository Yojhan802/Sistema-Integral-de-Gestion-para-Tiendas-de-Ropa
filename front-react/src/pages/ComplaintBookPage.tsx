import { useState, type FormEvent } from 'react';
import { StoreShell } from '../components/StoreShell';
import { ApiError, storeApi } from '../services/api';

type ComplaintType = 'RECLAMO' | 'QUEJA';
type ComplaintStatus = 'PENDIENTE' | 'RESPONDIDO' | 'CERRADO';
type PublicComplaint = { entryNumber: string; type: ComplaintType; status: ComplaintStatus; providerName: string; response?: string | null; createdAt: string; respondedAt?: string | null };
/** Constancia completa que devuelve el registro — es la copia que exige el Art. 5 del D.S. 011-2011-PCM. */
type ComplaintReceipt = PublicComplaint & {
  providerRuc?: string | null; providerAddress?: string | null;
  consumerName: string; consumerDocument?: string | null; consumerEmail: string;
  consumerPhone?: string | null; consumerAddress: string; orderNumber?: string | null;
  productServiceDescription: string; amount?: number | null; detail: string; consumerRequest: string;
  responseDueDate: string;
};
const statusLabel: Record<ComplaintStatus, string> = { PENDIENTE: 'Pendiente de atención', RESPONDIDO: 'Respondido', CERRADO: 'Cerrado' };

export function ComplaintBookPage() {
  const [type, setType] = useState<ComplaintType>('RECLAMO');
  const [form, setForm] = useState({ consumerName: '', consumerDocument: '', consumerEmail: '', consumerPhone: '', consumerAddress: '', orderNumber: '', productServiceDescription: '', amount: '', detail: '', consumerRequest: '' });
  const [saving, setSaving] = useState(false); const [error, setError] = useState(''); const [created, setCreated] = useState<ComplaintReceipt | null>(null);
  const [lookup, setLookup] = useState(''); const [lookupResult, setLookupResult] = useState<PublicComplaint | null>(null); const [lookupError, setLookupError] = useState(''); const [lookingUp, setLookingUp] = useState(false);
  const update = (name: keyof typeof form, value: string) => setForm((current) => ({ ...current, [name]: value }));
  const formatDate = (value: string) => new Intl.DateTimeFormat('es-PE', { dateStyle: 'long', timeStyle: 'short' }).format(new Date(value));
  /** `responseDueDate` llega como fecha sin hora; parsearla con `new Date` la trataría
   *  como UTC y en Perú (UTC-5) mostraría el día anterior. */
  const formatDay = (value: string) => {
    const [year, month, day] = value.split('-').map(Number);
    return new Intl.DateTimeFormat('es-PE', { dateStyle: 'long' }).format(new Date(year, month - 1, day));
  };

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError('');
    if (form.consumerName.trim().length < 2 || !form.consumerEmail.trim() || !form.consumerAddress.trim() || !form.productServiceDescription.trim() || form.detail.trim().length < 10 || form.consumerRequest.trim().length < 5) { setError('Completa los datos obligatorios. El domicilio y el detalle de lo ocurrido son exigidos por el Anexo II del D.S. 011-2011-PCM.'); return; }
    setSaving(true);
    try {
      const result = await storeApi.post<ComplaintReceipt>('/store/complaints', { type, consumerName: form.consumerName.trim(), consumerDocument: form.consumerDocument.trim() || undefined, consumerEmail: form.consumerEmail.trim(), consumerPhone: form.consumerPhone.trim() || undefined, consumerAddress: form.consumerAddress.trim(), orderNumber: form.orderNumber.trim() || undefined, productServiceDescription: form.productServiceDescription.trim(), amount: form.amount ? Number(form.amount) : undefined, detail: form.detail.trim(), consumerRequest: form.consumerRequest.trim() });
      setCreated(result); setLookup(result.entryNumber); setLookupResult(result);
    } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'No se pudo registrar el reclamo. Intenta nuevamente.'); }
    finally { setSaving(false); }
  }

  async function consult(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const code = lookup.trim().toUpperCase(); if (!code) return; setLookingUp(true); setLookupError('');
    try { setLookupResult(await storeApi.get<PublicComplaint>(`/store/complaints/${encodeURIComponent(code)}`)); }
    catch (reason) { setLookupResult(null); setLookupError(reason instanceof ApiError ? reason.message : 'No se encontró una hoja con ese código.'); }
    finally { setLookingUp(false); }
  }

  return <StoreShell>
    <div className="store-page-heading store-legal-heading"><span className="store-kicker">ATENCIÓN AL CLIENTE</span><h1>Libro de Reclamaciones</h1><p>Registra un reclamo o una queja y conserva tu código de seguimiento. Presentarlo es gratuito y no condiciona ninguna compra.</p></div>
    <div className="store-complaint-layout">
      {created ? <article className="store-complaint-receipt">
        <header>
          <div>
            <span className="store-kicker">CONSTANCIA DE {created.type}</span>
            <h2>Hoja N.° {created.entryNumber}</h2>
          </div>
          <button type="button" className="store-complaint-print" onClick={() => window.print()}>Descargar o imprimir</button>
        </header>
        <p className="store-complaint-receipt-note">Esta es tu copia de la hoja registrada en el Libro de Reclamaciones, conforme al Art. 5 del D.S. 011-2011-PCM. Consérvala: acredita la fecha de presentación y el plazo de respuesta.</p>
        <section>
          <h3>1. Identificación del proveedor</h3>
          <dl>
            <div><dt>Razón social</dt><dd>{created.providerName}</dd></div>
            {created.providerRuc && <div><dt>RUC</dt><dd>{created.providerRuc}</dd></div>}
            {created.providerAddress && <div><dt>Domicilio</dt><dd>{created.providerAddress}</dd></div>}
          </dl>
        </section>
        <section>
          <h3>2. Identificación del consumidor reclamante</h3>
          <dl>
            <div><dt>Nombre</dt><dd>{created.consumerName}</dd></div>
            {created.consumerDocument && <div><dt>Documento</dt><dd>{created.consumerDocument}</dd></div>}
            <div><dt>Domicilio</dt><dd>{created.consumerAddress}</dd></div>
            <div><dt>Correo</dt><dd>{created.consumerEmail}</dd></div>
            {created.consumerPhone && <div><dt>Teléfono</dt><dd>{created.consumerPhone}</dd></div>}
          </dl>
        </section>
        <section>
          <h3>3. Identificación del bien contratado</h3>
          <dl>
            <div><dt>Producto o servicio</dt><dd>{created.productServiceDescription}</dd></div>
            {created.orderNumber && <div><dt>Pedido</dt><dd>{created.orderNumber}</dd></div>}
            {created.amount != null && <div><dt>Monto reclamado</dt><dd>S/ {Number(created.amount).toFixed(2)}</dd></div>}
          </dl>
        </section>
        <section>
          <h3>4. Detalle de lo ocurrido</h3>
          <p>{created.detail}</p>
          <h3>5. Pedido del consumidor</h3>
          <p>{created.consumerRequest}</p>
        </section>
        <footer>
          <span>Registrada el {formatDate(created.createdAt)}</span>
          <span>Plazo máximo de respuesta: {formatDay(created.responseDueDate)}</span>
        </footer>
      </article> : <form className="store-complaint-form" onSubmit={submit} noValidate>
        <div className="store-complaint-form-heading"><span className="store-kicker">NUEVA ATENCIÓN</span><h2>Cuéntanos qué ocurrió</h2><p>Te responderemos dentro del plazo legal aplicable.</p></div>
        <div className="store-choice-grid" role="group" aria-label="Tipo de atención"><label className={`store-choice${type === 'RECLAMO' ? ' is-selected' : ''}`}><input type="radio" name="type" checked={type === 'RECLAMO'} onChange={() => setType('RECLAMO')} /><span><strong>Reclamo</strong><small>Disconformidad con el producto o servicio.</small></span></label><label className={`store-choice${type === 'QUEJA' ? ' is-selected' : ''}`}><input type="radio" name="type" checked={type === 'QUEJA'} onChange={() => setType('QUEJA')} /><span><strong>Queja</strong><small>Malestar por la atención recibida.</small></span></label></div>
        <div className="store-complaint-fields">
          <label className="template-field"><span>Nombre completo *</span><input required maxLength={150} value={form.consumerName} onChange={(event) => update('consumerName', event.target.value)} /></label>
          <label className="template-field"><span>DNI, CE o RUC <small>(opcional)</small></span><input maxLength={20} value={form.consumerDocument} onChange={(event) => update('consumerDocument', event.target.value.replace(/[^a-zA-Z0-9 .-]/g, '').slice(0, 20))} /></label>
          <label className="template-field"><span>Correo electrónico *</span><input required type="email" maxLength={150} autoComplete="email" value={form.consumerEmail} onChange={(event) => update('consumerEmail', event.target.value)} /></label>
          <label className="template-field"><span>Teléfono <small>(opcional)</small></span><input maxLength={20} inputMode="tel" value={form.consumerPhone} onChange={(event) => update('consumerPhone', event.target.value.replace(/[^0-9+() -]/g, '').slice(0, 20))} /></label>
          <label className="template-field store-field-wide"><span>Domicilio *</span><input required maxLength={255} autoComplete="street-address" value={form.consumerAddress} onChange={(event) => update('consumerAddress', event.target.value)} placeholder="Calle, número, distrito y provincia" /><small>Exigido por el Anexo II del D.S. 011-2011-PCM.</small></label>
          <label className="template-field"><span>Número de pedido <small>(opcional)</small></span><input maxLength={30} value={form.orderNumber} onChange={(event) => update('orderNumber', event.target.value.replace(/[^a-zA-Z0-9-]/g, '').slice(0, 30))} placeholder="Ej. PED-00000027" /></label>
          <label className="template-field"><span>Producto o servicio *</span><input required maxLength={255} value={form.productServiceDescription} onChange={(event) => update('productServiceDescription', event.target.value)} /></label>
          <label className="template-field"><span>Monto involucrado <small>(opcional, S/)</small></span><input type="number" min="0" step="0.01" inputMode="decimal" value={form.amount} onChange={(event) => update('amount', event.target.value)} /></label>
          <label className="template-field store-field-wide"><span>Detalle de lo ocurrido *</span><textarea required maxLength={5000} rows={5} value={form.detail} onChange={(event) => update('detail', event.target.value)} /></label>
          <label className="template-field store-field-wide"><span>Pedido del consumidor *</span><textarea required maxLength={3000} rows={3} value={form.consumerRequest} onChange={(event) => update('consumerRequest', event.target.value)} placeholder="Indica qué solución solicitas." /></label>
        </div>
        {error && <p className="store-form-error" role="alert">{error}</p>}<button className="store-complaint-submit" type="submit" disabled={saving}>{saving ? 'Registrando…' : 'Registrar en el libro'}</button><p className="store-complaint-footnote">Al registrar recibirás tu constancia, con el código de la hoja y el plazo de respuesta, lista para descargar o imprimir. Tus datos se usarán para atender esta solicitud, conforme a la <a href="/politica-privacidad">Política de Privacidad</a>.</p>
      </form>}
      <aside className="store-complaint-aside">
        <div className="store-info-card"><span className="store-kicker">SEGUIMIENTO</span><h2>Consulta tu código</h2><p>Ingresa el número que aparece al finalizar el registro para ver si ya existe una respuesta.</p><form onSubmit={consult}><label className="template-field"><span>Código de hoja</span><input value={lookup} onChange={(event) => setLookup(event.target.value)} placeholder="RC-00000001" /></label><button className="store-lookup-submit" type="submit" disabled={lookingUp}>{lookingUp ? 'Consultando…' : 'Consultar estado'}</button></form>{lookupError && <p className="store-form-error" role="alert">{lookupError}</p>}{lookupResult && <div className="store-complaint-status"><strong>{lookupResult.entryNumber}</strong><span className={`store-status-pill is-${lookupResult.status.toLowerCase()}`}>{statusLabel[lookupResult.status]}</span><small>Registrado el {formatDate(lookupResult.createdAt)}</small>{lookupResult.response && <p><strong>Respuesta:</strong> {lookupResult.response}</p>}</div>}</div>
        {created && <div className="store-complaint-success" role="status"><span className="store-kicker">REGISTRO CONFIRMADO</span><h2>Tu solicitud fue registrada</h2><p>Este es tu código de seguimiento:</p><strong>{created.entryNumber}</strong><div className="store-complaint-success-actions"><button type="button" onClick={() => navigator.clipboard?.writeText(created.entryNumber)}>Copiar código</button><button type="button" className="store-complaint-print" onClick={() => window.print()}>Descargar constancia</button></div><small>Guarda o imprime la constancia: es tu copia de la hoja. Responderemos a más tardar el {formatDay(created.responseDueDate)}.</small></div>}
        <div className="store-info-card"><span className="store-kicker">IMPORTANTE</span><p>El Libro de Reclamaciones es un canal de atención. No reemplaza el comprobante de pago: conserva tu boleta, factura o constancia interna de venta cuando corresponda.</p></div>
      </aside>
    </div>
  </StoreShell>;
}
