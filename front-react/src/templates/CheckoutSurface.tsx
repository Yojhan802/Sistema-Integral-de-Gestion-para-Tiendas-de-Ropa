import type { FormEvent, MouseEvent } from 'react';
import type { BillingOptions, CartItem, CustomerSession, OrderBillingDocumentType, PaymentMethod, PaymentProvider, StoreTemplate } from '../types';
import type { formatCurrency } from '../utils';
import { digitsOnly, legalNameInput, personNameInput } from '../services/validation';

interface UbigeoOption { id: string; nombre: string; }

export interface CheckoutSurfaceProps {
  template: StoreTemplate;
  session: CustomerSession;
  items: CartItem[];
  departments: UbigeoOption[];
  provinces: UbigeoOption[];
  districts: UbigeoOption[];
  departmentId: string;
  provinceId: string;
  district: string;
  setDepartmentId: (value: string) => void;
  setProvinceId: (value: string) => void;
  setDistrict: (value: string) => void;
  providers: PaymentProvider[];
  selectedMethodId: number | null;
  selectedMethod?: PaymentMethod;
  selectedProvider: string;
  setSelectedMethodId: (value: number) => void;
  setSelectedProvider: (value: string) => void;
  availableMethods: PaymentMethod[];
  delivery: number;
  billingOptions: BillingOptions;
  billingDocumentType: OrderBillingDocumentType;
  setBillingDocumentType: (value: OrderBillingDocumentType) => void;
  error: string;
  submitting: boolean;
  acceptedTerms: boolean;
  setAcceptedTerms: (value: boolean) => void;
  taxNotice: string | null;
  setProofFile: (file: File | null) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  imageUrl: (value?: string | null) => string;
  formatCurrency: typeof formatCurrency;
}

function goLegal(event: MouseEvent<HTMLAnchorElement>, path: string) {
  event.preventDefault();
  window.history.pushState({}, '', path);
  window.dispatchEvent(new PopStateEvent('popstate'));
}

export function CheckoutSurface({ template, session, items, departments, provinces, districts, departmentId, provinceId, district, setDepartmentId, setProvinceId, setDistrict, providers, selectedMethodId, selectedMethod, selectedProvider, setSelectedMethodId, setSelectedProvider, availableMethods, delivery, billingOptions, billingDocumentType, setBillingDocumentType, error, submitting, acceptedTerms, setAcceptedTerms, taxNotice, setProofFile, onSubmit, imageUrl, formatCurrency }: CheckoutSurfaceProps) {
  const variant = template.toLowerCase();
  return <form className={`template-checkout template-checkout-${variant}`} id="checkout-form" onSubmit={onSubmit} data-template-surface="checkout">
    <div className="template-checkout-eyebrow">01 / ENTREGA Y PAGO</div>
    <p className="template-checkout-intro">Datos de quien recibe el envío — la courier los exige para registrar el paquete.</p>
    <div className="template-checkout-layout">
      <section className="template-checkout-delivery">
        <div className="template-panel-heading"><span className="template-panel-kicker">INFORMACIÓN</span><h2>Datos de entrega</h2></div>
        <div className="template-form-grid template-checkout-recipient-grid">
          <label className="template-field"><span>DNI <small>8 dígitos, solo números</small></span><input required maxLength={8} pattern="[0-9]{8}" inputMode="numeric" name="recipientDni" onChange={(event) => { event.currentTarget.value = digitsOnly(event.currentTarget.value, 8); }} /></label>
          <label className="template-field"><span>Nombres <small>solo letras</small></span><input required maxLength={100} defaultValue={session.customer.fullName.split(' ')[0]} name="recipientFirstName" onChange={(event) => { event.currentTarget.value = personNameInput(event.currentTarget.value, 100); }} /></label>
          <label className="template-field"><span>Apellido paterno <small>solo letras</small></span><input required maxLength={60} name="recipientLastNamePaterno" onChange={(event) => { event.currentTarget.value = personNameInput(event.currentTarget.value, 60); }} /></label>
          <label className="template-field"><span>Apellido materno <small>solo letras</small></span><input required maxLength={60} name="recipientLastNameMaterno" onChange={(event) => { event.currentTarget.value = personNameInput(event.currentTarget.value, 60); }} /></label>
        </div>
        <label className="template-field"><span>Teléfono <small>9 dígitos, comienza en 9</small></span><input required maxLength={9} pattern="9[0-9]{8}" inputMode="numeric" defaultValue={session.customer.phone || ''} name="phone" onChange={(event) => { event.currentTarget.value = digitsOnly(event.currentTarget.value, 9); }} /></label>
        <label className="template-field"><span>Dirección <small>máximo 255 caracteres</small></span><input required maxLength={255} placeholder="Av./Jr./Calle, número, referencia" name="address" /></label>
        <div className="template-form-grid template-checkout-location-grid">
          <label className="template-field"><span>Departamento</span><select required value={departmentId} onChange={(event) => { setDepartmentId(event.target.value); setProvinceId(''); setDistrict(''); }}><option value="">Selecciona…</option>{departments.map((item) => <option value={item.id} key={item.id}>{item.nombre}</option>)}</select></label>
          <label className="template-field"><span>Provincia</span><select required disabled={!departmentId} value={provinceId} onChange={(event) => { setProvinceId(event.target.value); setDistrict(''); }}><option value="">{departmentId ? 'Selecciona…' : 'Elige un departamento primero'}</option>{provinces.map((item) => <option value={item.id} key={item.id}>{item.nombre}</option>)}</select></label>
          <label className="template-field template-field-wide"><span>Distrito</span><select required disabled={!provinceId} value={district} onChange={(event) => setDistrict(event.target.value)}><option value="">{provinceId ? 'Selecciona…' : 'Elige una provincia primero'}</option>{districts.map((item) => <option value={item.nombre} key={item.id}>{item.nombre}</option>)}</select></label>
        </div>
        <label className="template-field"><span>Notas <small>opcional, máximo 255 caracteres</small></span><textarea maxLength={255} name="notes" rows={3} /></label>
        <fieldset className="template-billing-block">
          <legend>Comprobante de venta</legend>
          {billingOptions.available ? <>
            <p className="template-billing-hint">Elige cómo deseas recibir tu comprobante electrónico.</p>
            <label className="template-field template-billing-selector"><span>Tipo de comprobante</span><select name="billingDocumentType" value={billingDocumentType} onChange={(event) => setBillingDocumentType(event.target.value as OrderBillingDocumentType)}>{billingOptions.receiptAvailable && <option value="BOLETA">Boleta electr&oacute;nica</option>}{billingOptions.invoiceAvailable && <option value="FACTURA">Factura electr&oacute;nica</option>}</select><small>Usamos boleta por defecto. Si compras para una empresa, elige factura.</small></label>
            {billingDocumentType === 'FACTURA' && <div className="template-billing-invoice-fields"><label className="template-field"><span>RUC <small>11 dígitos, solo números</small></span><input required maxLength={11} pattern="[0-9]{11}" inputMode="numeric" name="billingDocumentNumber" onChange={(event) => { event.currentTarget.value = digitsOnly(event.currentTarget.value, 11); }} /></label><label className="template-field"><span>Razón social <small>nombre legal de la empresa</small></span><input required maxLength={150} name="billingName" onChange={(event) => { event.currentTarget.value = legalNameInput(event.currentTarget.value); }} /></label></div>}
          </> : <><input type="hidden" name="billingDocumentType" value="TICKET" /><p className="template-billing-hint">Esta tienda entrega una constancia interna de la venta. La facturación electrónica no está disponible.</p></>}
        </fieldset>
      </section>
      <aside className="template-checkout-order" id="order-summary">
        <div className="template-panel-heading"><span className="template-panel-kicker">TU COMPRA</span><h2>Resumen</h2></div>
        <div className="template-order-lines">{items.map((item) => <div className="template-order-line" key={item.variantId}><span>{item.productName} <small>({item.variantLabel || 'Producto'}) × {item.quantity}</small></span><strong>{formatCurrency(item.unitPrice * item.quantity)}</strong></div>)}</div>
        <div className="template-total-line"><span>Subtotal</span><strong>{formatCurrency(items.reduce((total, item) => total + item.unitPrice * item.quantity, 0))}</strong></div>
        <div className="template-total-line"><span>Envío</span><strong>{delivery ? formatCurrency(delivery) : 'Gratis'}</strong></div>
        <div className="template-grand-total"><span>Total</span><strong>{formatCurrency(items.reduce((total, item) => total + item.unitPrice * item.quantity, 0) + delivery)}</strong></div>
        {taxNotice && <p className="template-tax-notice">{taxNotice}</p>}
        <div className="template-payment-block"><span className="template-payment-title">Método de pago</span><div className="template-payment-options">{availableMethods.map((method) => <label className={`template-payment-option${selectedMethodId === method.id ? ' is-selected' : ''}`} key={method.id}><input type="radio" name="paymentMethod" checked={selectedMethodId === method.id} onChange={() => setSelectedMethodId(method.id)} /><span><strong>{method.name}</strong>{method.code === 'CONTRAENTREGA' && <small> · envío gratis</small>}{method.instructions && <small>{method.instructions}</small>}</span></label>)}</div>
          {selectedMethod?.qrImageUrl && <div className="template-payment-qr"><img src={imageUrl(selectedMethod.qrImageUrl)} alt={`QR de ${selectedMethod.name}`} onError={(event) => { event.currentTarget.hidden = true; }} />{selectedMethod.accountHolder && <span>{selectedMethod.accountHolder}</span>}{selectedMethod.accountNumber && <span>{selectedMethod.accountNumber}</span>}</div>}
          {selectedMethod?.accountNumber && !selectedMethod.qrImageUrl && <p className="template-payment-account"><strong>{selectedMethod.accountHolder}</strong> — {selectedMethod.accountNumber}</p>}
          {selectedMethod?.type === 'CARD' && <label className="template-field template-provider-field"><span>Pasarela</span><select required name="provider" value={selectedProvider} onChange={(event) => setSelectedProvider(event.target.value)}><option value="">Selecciona una pasarela</option>{providers.map((provider) => <option value={provider.provider} key={provider.provider}>{provider.displayName || provider.provider}</option>)}</select><small>El pago se procesa con las credenciales configuradas por la empresa.</small></label>}
        </div>
        {selectedMethod?.requiresReference && <label className="template-field"><span>Número de operación <small>máximo 50 caracteres</small></span><input required maxLength={50} name="paymentReference" /></label>}
        {selectedMethod?.type === 'DIGITAL_WALLET' && <label className="template-field"><span>Comprobante de pago <small>opcional</small></span><input accept="image/png,image/jpeg,image/webp" type="file" onChange={(event) => setProofFile(event.target.files?.[0] || null)} /><small>Puedes subirlo ahora o después desde Mis pedidos.</small></label>}
        {/* Sin aceptación expresa no hay contratación válida a distancia: el botón
            queda deshabilitado en vez de fallar al enviar, para no perder los datos. */}
        <label className="template-terms-accept">
          <input type="checkbox" name="acceptedTerms" checked={acceptedTerms} onChange={(event) => setAcceptedTerms(event.target.checked)} />
          <span>He leído y acepto los <a href="/terminos-condiciones" onClick={(event) => goLegal(event, '/terminos-condiciones')}>Términos y Condiciones</a>, la <a href="/politica-privacidad" onClick={(event) => goLegal(event, '/politica-privacidad')}>Política de Privacidad</a> y la <a href="/cambios-devoluciones" onClick={(event) => goLegal(event, '/cambios-devoluciones')}>Política de Cambios y Devoluciones</a>.</span>
        </label>
        {error && <div className="template-error" role="alert">{error}</div>}
        <button className="template-submit" disabled={submitting || !acceptedTerms} type="submit">{submitting ? 'Procesando…' : 'Confirmar pedido'}</button>
      </aside>
    </div>
  </form>;
}
