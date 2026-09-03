import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { motion } from 'motion/react';
import { AdminShell } from './AdminPagesV2';
import { ApiError, api, getStaffSession, imageUrl } from '../services/api';
import { EmptyState, ErrorState, LoadingState } from '../components/States';
import { showToast } from '../components/ToastHost';
import type { Page, PaymentMethod, StoreConfig } from '../types';
import { decimalInput, digitsOnly, legalNameInput, validateCheckoutBilling } from '../services/validation';
import { formatCurrency, formatDate } from '../utils';

type Tab = 'sale' | 'history' | 'returns' | 'promoters';
type Variant = { variantId: number; productName: string; variantLabel?: string; sku: string; effectivePrice?: number; price?: number; stock: number; status?: string };
type ComboItem = { selectorType: string; productName?: string | null; categoryName?: string | null; brandName?: string | null; quantity: number };
type Combo = { id: number; name: string; price: number; status: string; items: ComboItem[] };
type Promotion = { id: number; name: string; discountType: string; discountValue: number };
type Line = Variant & { quantity: number; comboId?: number | null; comboName?: string | null; comboPrice?: number | null; promotionId?: number | null; promotionName?: string | null; promotionDiscount?: number };
type Customer = { id: number; fullName: string; docNumber?: string | null; phone?: string | null };
type Promoter = { id: number; name: string; status: string };
type SaleSummary = { id: number; saleNumber: string; customerName?: string | null; sellerName?: string | null; total: number; status: string; createdAt: string };
type Sale = SaleSummary & { promoterName?: string | null; subtotal: number; discountAmount: number; items: Array<{ variantId: number; productName: string; variantSku: string; variantLabel?: string; quantity: number; unitPrice: number; subtotal: number }>; payments: Array<{ paymentMethodName: string; amount: number; reference?: string | null }>; billingDocumentType?: 'TICKET' | 'BOLETA' | 'FACTURA'; billingDocumentNumber?: string | null; billingName?: string | null };
type Returnable = { saleDetailId: number; productName: string; variantSku: string; quantityReturnable: number };
type ReturnRow = { id: number; returnNumber: string; saleNumber: string; totalAmount: number; refundMethodName: string; reason: string; username: string; createdAt: string };

const statusLabel: Record<string, string> = { COMPLETED: 'Completada', CANCELLED: 'Anulada', PARTIALLY_RETURNED: 'Devuelta parcialmente', RETURNED: 'Devuelta' };
const statusClass: Record<string, string> = { COMPLETED: 'badge-success', CANCELLED: 'badge-danger', PARTIALLY_RETURNED: 'badge-warning', RETURNED: 'badge-warning' };
const can = (permission: string) => getStaffSession()?.user.permissions.includes(permission) ?? false;
const reasonOf = (value: unknown, fallback: string) => value instanceof ApiError ? value.message : fallback;
type TicketSettings = StoreConfig & { ruc?: string | null; address?: string | null; phone?: string | null; ticketFooter?: string | null; igvRate?: number | null };

function ticketEscape(value: unknown) {
  return String(value ?? '').replace(/[&<>"']/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[character] || character));
}

export function printSaleTicket(sale: Sale, settings: TicketSettings | null) {
  const popup = window.open('', `ticket-${sale.id}`, 'width=420,height=760');
  if (!popup) {
    showToast('Permite ventanas emergentes para imprimir el ticket.', 'Ticket no disponible', 'info');
    return;
  }
  popup.document.open();
  popup.document.write('<p style="font-family:sans-serif;padding:24px">Cargando ticket…</p>');
  popup.document.close();
  void (async () => {
    const resolvedSettings = settings ?? await api.get<TicketSettings>('/settings/company', { auth: 'staff' }).catch(() => null);
    if (popup.closed) return;
    printSaleTicketContent(popup, sale, resolvedSettings);
  })();
}

function printSaleTicketContent(popup: Window, sale: Sale, settings: TicketSettings | null) {
  const items = sale.items.map((item) => `<tr><td>${item.quantity}</td><td>${ticketEscape(item.productName)}<br><span class="muted">${ticketEscape(item.variantLabel || item.variantSku)}</span></td><td class="right">${ticketEscape(formatCurrency(item.subtotal))}</td></tr>`).join('');
  const payments = sale.payments.map((payment) => `<div class="row"><span>${ticketEscape(payment.paymentMethodName)}${payment.reference ? ` (${ticketEscape(payment.reference)})` : ''}</span><span>${ticketEscape(formatCurrency(payment.amount))}</span></div>`).join('');
  const logo = settings?.logoUrl ? `<img class="logo" src="${ticketEscape(imageUrl(settings.logoUrl))}" alt="" />` : '';
  const igvRate = settings?.igvRate != null ? Number(settings.igvRate) : null;
  const igvIncluded = igvRate ? sale.total - sale.total / (1 + igvRate) : null;
  popup.document.open();
  popup.document.write(`<!doctype html><html lang="es"><head><meta charset="utf-8"><title>${ticketEscape(sale.saleNumber)} - ${ticketEscape(settings?.name || 'Qynex')}</title><style>@page{size:80mm auto;margin:4mm}*{box-sizing:border-box}body{font-family:"Courier New",Consolas,monospace;font-size:12px;color:#111;margin:0;padding:16px;width:320px}.center{text-align:center}.muted{color:#555;font-size:11px}.logo{max-width:120px;max-height:60px;margin:0 auto 8px;display:block}h1{font-size:14px;margin:0 0 2px}hr{border:0;border-top:1px dashed #999;margin:8px 0}.row{display:flex;justify-content:space-between;gap:12px;padding:2px 0}table{width:100%;border-collapse:collapse}th{text-align:left;font-size:11px;border-bottom:1px dashed #999;padding-bottom:4px}td{vertical-align:top;padding:4px 0;font-size:12px}.right{text-align:right;white-space:nowrap}.total{font-weight:700;font-size:14px}.footer{margin-top:12px;text-align:center;white-space:pre-line}.actions{margin-top:16px;text-align:center}.actions button{font:inherit;font-size:13px;padding:8px 16px;cursor:pointer}@media print{.actions{display:none}}</style></head><body><div class="center">${logo}<h1>${ticketEscape(settings?.name || 'Qynex Shop')}</h1>${settings?.ruc ? `<div class="muted">RUC ${ticketEscape(settings.ruc)}</div>` : ''}${settings?.address ? `<div class="muted">${ticketEscape(settings.address)}</div>` : ''}${settings?.phone ? `<div class="muted">${ticketEscape(settings.phone)}</div>` : ''}</div><hr><div class="row"><span>N° venta</span><span>${ticketEscape(sale.saleNumber)}</span></div><div class="row"><span>Fecha</span><span>${ticketEscape(formatDate(sale.createdAt))}</span></div><div class="row"><span>Vendedor</span><span>${ticketEscape(sale.sellerName || '—')}</span></div>${sale.customerName ? `<div class="row"><span>Cliente</span><span>${ticketEscape(sale.customerName)}</span></div>` : ''}<hr><table><thead><tr><th>Cant</th><th>Descripción</th><th class="right">Importe</th></tr></thead><tbody>${items}</tbody></table><hr><div class="row"><span>Subtotal</span><span>${ticketEscape(formatCurrency(sale.subtotal))}</span></div>${sale.discountAmount > 0 ? `<div class="row"><span>Descuento</span><span>-${ticketEscape(formatCurrency(sale.discountAmount))}</span></div>` : ''}${igvIncluded != null ? `<div class="row muted"><span>IGV incluido (${((igvRate ?? 0) * 100).toFixed(0)}%)</span><span>${ticketEscape(formatCurrency(igvIncluded))}</span></div>` : ''}<div class="row total"><span>TOTAL</span><span>${ticketEscape(formatCurrency(sale.total))}</span></div><hr>${payments}${settings?.ticketFooter ? `<div class="footer">${ticketEscape(settings.ticketFooter)}</div>` : ''}<div class="center actions"><button type="button" onclick="window.print()">Imprimir</button> <button type="button" onclick="window.close()">Cerrar</button></div></body></html>`);
  popup.document.close();
  const internalNote = popup.document.createElement('div');
  internalNote.textContent = 'CONSTANCIA INTERNA DE VENTA · NO REEMPLAZA COMPROBANTE DE PAGO';
  internalNote.style.cssText = 'margin:10px 0;padding:6px 4px;border:1px solid #999;text-align:center;font-size:10px;font-weight:700;line-height:1.35;';
  popup.document.querySelector('.center')?.appendChild(internalNote);
  const internalTaxRow = popup.document.querySelector<HTMLElement>('.row.muted');
  if (internalTaxRow) internalTaxRow.firstElementChild!.textContent = internalTaxRow.firstElementChild!.textContent?.replace('IGV incluido', 'Impuesto referencial') || 'Impuesto referencial';
  popup.focus();
}

export function AdminPosPageV2() {
  const [tab, setTab] = useState<Tab>('sale');
  const [cash, setCash] = useState<{ id: number; cashRegisterName: string } | null>(null);
  const [loadingCash, setLoadingCash] = useState(true);
  const [cashError, setCashError] = useState('');
  const [electronicInvoicingEnabled, setElectronicInvoicingEnabled] = useState<boolean | null>(null);
  const [registeredSale, setRegisteredSale] = useState<Sale | null>(null);
  const refreshCash = () => { setLoadingCash(true); setCashError(''); api.get<{ id: number; cashRegisterName: string }>('/cash-registers/sessions/current', { auth: 'staff' }).then(setCash).catch((error) => { if (error instanceof ApiError && error.status === 404) setCash(null); else setCashError(reasonOf(error, 'No se pudo consultar la caja.')); }).finally(() => setLoadingCash(false)); };
  useEffect(() => { refreshCash(); }, []);
  useEffect(() => { api.get<{ electronicInvoicingEnabled?: boolean }>('/settings/company', { auth: 'staff' }).then((settings) => setElectronicInvoicingEnabled(settings?.electronicInvoicingEnabled === true)).catch(() => setElectronicInvoicingEnabled(false)); }, []);
  return <AdminShell title="Ventas / POS" description="Ventas, combos, promociones, cobros y devoluciones." activePage="/admin/pos">
    <div className="react-pos-tabs" role="tablist" aria-label="Operaciones de ventas">{([['sale', 'Nueva venta'], ['history', 'Historial'], ['returns', 'Devoluciones'], ['promoters', 'Promotores']] as [Tab, string][]).map(([key, label]) => <button className="tab" type="button" role="tab" aria-selected={tab === key} key={key} onClick={() => setTab(key)}>{label}</button>)}</div>
    {cashError && <ErrorState message={cashError} />}
    {tab === 'sale' && (loadingCash ? <LoadingState label="Consultando caja..." /> : electronicInvoicingEnabled === null ? <LoadingState label="Consultando configuración..." /> : <SalePanel cash={cash} refreshCash={refreshCash} electronicInvoicingEnabled={electronicInvoicingEnabled} onSaleRegistered={setRegisteredSale} />)}
    {tab === 'history' && <HistoryPanel electronicInvoicingEnabled={electronicInvoicingEnabled === true} />}
    {tab === 'returns' && <ReturnsPanel />}
    {tab === 'promoters' && <PromotersPanel />}
    {registeredSale && <TicketDialog sale={registeredSale} onClose={() => setRegisteredSale(null)} canIssueDocuments={electronicInvoicingEnabled === true} />}
  </AdminShell>;
}

function SalePanel({ cash, refreshCash, electronicInvoicingEnabled, onSaleRegistered }: { cash: { id: number; cashRegisterName: string } | null; refreshCash: () => void; electronicInvoicingEnabled: boolean; onSaleRegistered: (sale: Sale) => void }) {
  const [lines, setLines] = useState<Line[]>([]); const [search, setSearch] = useState(''); const [results, setResults] = useState<Variant[]>([]); const [customer, setCustomer] = useState<Customer | null>(null); const [customerSearch, setCustomerSearch] = useState(''); const [customerResults, setCustomerResults] = useState<Customer[]>([]); const [combos, setCombos] = useState<Combo[]>([]); const [promotions, setPromotions] = useState<Promotion[]>([]); const [selectedPromo, setSelectedPromo] = useState<Line | null>(null); const [dialog, setDialog] = useState<'open-cash' | 'combo' | 'combo-items' | 'payment' | 'promotion' | null>(null); const [combo, setCombo] = useState<Combo | null>(null); const [error, setError] = useState('');
  const subtotal = useMemo(() => lines.reduce((sum, line) => sum + Number(line.effectivePrice ?? line.price ?? 0) * line.quantity, 0), [lines]);
  const discount = useMemo(() => { let total = lines.filter((line) => !line.comboId).reduce((sum, line) => sum + (line.promotionDiscount || 0), 0); [...new Set(lines.filter((line) => line.comboId).map((line) => line.comboId))].forEach((id) => { const group = lines.filter((line) => line.comboId === id); if (group[0]?.comboPrice != null) total += Math.max(0, group.reduce((sum, line) => sum + Number(line.effectivePrice ?? line.price ?? 0) * line.quantity, 0) - group[0].comboPrice); }); return total; }, [lines]);
  const total = Math.max(0, subtotal - discount);
  useEffect(() => { if (search.trim().length < 2) { setResults([]); return; } const timer = window.setTimeout(() => { api.get<Variant[]>('/variants/search', { auth: 'staff', query: { q: search.trim() } }).then((items) => setResults(items || [])).catch(() => setResults([])); }, 250); return () => window.clearTimeout(timer); }, [search]);
  useEffect(() => { if (customerSearch.trim().length < 2) { setCustomerResults([]); return; } const timer = window.setTimeout(() => { api.get<Customer[]>('/customers/search', { auth: 'staff', query: { q: customerSearch.trim() } }).then((items) => setCustomerResults(items || [])).catch(() => setCustomerResults([])); }, 250); return () => window.clearTimeout(timer); }, [customerSearch]);
  function add(item: Variant, selectedCombo?: Combo) { if (item.status && item.status !== 'ACTIVE') { setError('La variante esta inactiva.'); return; } if (item.stock < 1) { setError('La variante no tiene stock disponible.'); return; } setLines((current) => { const existing = !selectedCombo && current.find((line) => line.variantId === item.variantId && !line.comboId); if (existing) return current.map((line) => line === existing ? { ...line, quantity: Math.min(line.quantity + 1, item.stock) } : line); return [...current, { ...item, quantity: 1, comboId: selectedCombo?.id || null, comboName: selectedCombo?.name || null, comboPrice: selectedCombo?.price || null, promotionDiscount: 0 }]; }); setError(''); }
  async function scan(event: FormEvent) { event.preventDefault(); if (!search.trim()) return; try { add(await api.get<Variant>(`/variants/barcode/${encodeURIComponent(search.trim())}`, { auth: 'staff' })); setSearch(''); setResults([]); } catch { /* la lista textual permanece disponible */ } }
  async function openCombos() { try { const all = await api.get<Combo[]>('/combos', { auth: 'staff' }); setCombos((all || []).filter((item) => item.status === 'ACTIVE')); setDialog('combo'); } catch (error) { setError(reasonOf(error, 'No se pudieron cargar los combos.')); } }
  async function openPromotions(line: Line) { try { setPromotions(await api.get<Promotion[]>('/promotions/applicable', { auth: 'staff', query: { variantId: line.variantId } }) || []); setSelectedPromo(line); setDialog('promotion'); } catch (error) { setError(reasonOf(error, 'No se pudieron cargar las promociones.')); } }
  async function createSale(payload: { payments: Array<{ paymentMethodId: number; amount: number; reference: string | null }>; promoterId: number | null; billingDocumentType: 'TICKET' | 'BOLETA' | 'FACTURA'; billingDocumentNumber: string | null; billingName: string | null }) { if (!cash) throw new Error('No hay una caja abierta.'); const sale = await api.post<Sale>('/sales', { customerId: customer?.id || null, promoterId: payload.promoterId, cashSessionId: cash.id, discountAmount: discount, notes: null, billingDocumentType: payload.billingDocumentType, billingDocumentNumber: payload.billingDocumentNumber, billingName: payload.billingName, items: lines.map((line) => ({ variantId: line.variantId, quantity: line.quantity, discountAmount: line.promotionDiscount || 0, comboId: line.comboId || null, promotionId: line.promotionId || null })), payments: payload.payments }, { auth: 'staff' }); setDialog(null); onSaleRegistered(sale); setLines([]); setCustomer(null); showToast(`Venta registrada · ${sale.saleNumber}`, formatCurrency(sale.total)); refreshCash(); }
  if (!cash) return <section className="card react-pos-closed"><span className="field-hint">CAJA REQUERIDA</span><h2>Abre una caja para comenzar</h2><p>La venta presencial necesita una sesion activa para registrar el arqueo.</p>{can('CAJA_ABRIR') ? <button className="btn btn-primary" type="button" onClick={() => setDialog('open-cash')}>Abrir caja</button> : <p className="field-hint">No tienes permiso para abrir cajas.</p>}{dialog === 'open-cash' && <OpenCashDialog onClose={() => setDialog(null)} onSaved={() => { setDialog(null); refreshCash(); }} />}</section>;
  const comboIds = [...new Set(lines.filter((line) => line.comboId).map((line) => line.comboId))];
  return <><div className="react-pos-cash-banner"><span><strong>{cash.cashRegisterName}</strong><small>Caja abierta y lista para vender</small></span><span className="badge badge-success">Sesion activa</span></div><div className="react-pos-layout"><section className="card react-pos-products"><div className="card-header"><div><span className="field-hint">MOSTRADOR</span><h2>Agregar productos</h2></div>{can('COMBOS_CONSULTAR') && <button className="btn btn-secondary btn-sm" type="button" onClick={() => void openCombos()}>+ Combo</button>}</div><form className="react-pos-search" onSubmit={scan}><input className="input" autoComplete="off" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar producto, SKU o escanear codigo..." aria-label="Buscar producto o codigo de barras" /><button className="btn btn-secondary" type="submit">Buscar</button>{results.length > 0 && <div className="react-pos-results">{results.map((item) => <button type="button" key={item.variantId} onClick={() => { add(item); setSearch(''); setResults([]); }}><span><strong>{item.productName}</strong><small>{item.variantLabel || 'Producto'} · {item.sku}</small></span><span><strong>{formatCurrency(Number(item.effectivePrice ?? item.price ?? 0))}</strong><small>Stock: {item.stock}</small></span></button>)}</div>}</form><label className="field react-pos-customer"><span className="field-label">Cliente (opcional)</span>{customer ? <span className="react-pos-selected"><strong>{customer.fullName}</strong><button className="btn btn-ghost btn-sm" type="button" onClick={() => setCustomer(null)}>Cambiar</button></span> : <div className="react-pos-customer-search"><input className="input" value={customerSearch} onChange={(event) => setCustomerSearch(event.target.value)} placeholder="Buscar por nombre, DNI o telefono..." />{customerResults.length > 0 && <div className="react-pos-results">{customerResults.map((item) => <button type="button" key={item.id} onClick={() => { setCustomer(item); setCustomerSearch(''); setCustomerResults([]); }}><span><strong>{item.fullName}</strong><small>{item.docNumber || item.phone || 'Sin documento'}</small></span></button>)}</div>}</div>}</label></section><section className="card react-pos-cart"><div className="card-header"><div><span className="field-hint">VENTA ACTUAL</span><h2>Detalle</h2></div>{lines.length > 0 && <button className="btn btn-ghost btn-sm" type="button" onClick={() => setLines([])}>Vaciar</button>}</div>{error && <div className="alert alert-danger" role="alert">{error}</div>}{lines.length ? <div className="react-pos-lines">{lines.filter((line) => !line.comboId).map((line) => <PosLine key={line.variantId} line={line} onRemove={() => setLines((current) => current.filter((item) => item !== line))} onQuantity={(quantity) => setLines((current) => current.map((item) => item === line ? { ...item, quantity: Math.max(1, Math.min(quantity, item.stock)) } : item))} onPromotion={can('PROMOCIONES_APLICAR') ? () => void openPromotions(line) : undefined} />)}{comboIds.map((id) => { const group = lines.filter((line) => line.comboId === id); return <article className="react-pos-combo" key={id}><div className="react-pos-combo-header"><span className="badge badge-info">Combo · {group[0].comboName}</span><button className="btn btn-ghost btn-sm" type="button" onClick={() => setLines((current) => current.filter((line) => line.comboId !== id))}>Quitar combo</button></div>{group.map((line) => <div className="react-pos-combo-item" key={line.variantId}><span>{line.productName}<small>{line.variantLabel || line.sku}</small></span></div>)}<div className="react-pos-combo-price"><s>{formatCurrency(group.reduce((sum, line) => sum + Number(line.effectivePrice ?? line.price ?? 0), 0))}</s><strong>{formatCurrency(group[0].comboPrice || 0)}</strong></div></article>; })}</div> : <EmptyState>Busca una variante o agrega un combo para comenzar.</EmptyState>}<div className="react-pos-breakdown"><span>Subtotal <strong>{formatCurrency(subtotal)}</strong></span><span>Descuentos <strong>- {formatCurrency(discount)}</strong></span></div><div className="react-pos-total"><span>Total</span><strong>{formatCurrency(total)}</strong></div><button className="btn btn-primary btn-lg btn-block" type="button" disabled={!lines.length} onClick={() => setDialog('payment')}>Cobrar venta</button></section></div>{dialog === 'open-cash' && <OpenCashDialog onClose={() => setDialog(null)} onSaved={() => { setDialog(null); refreshCash(); }} />}{dialog === 'combo' && <ChoiceDialog title="Elegir combo" subtitle="Selecciona una oferta activa." items={combos.map((item) => ({ id: item.id, label: item.name, detail: item.items.map(comboDescription).join(' + '), value: formatCurrency(item.price) }))} onClose={() => setDialog(null)} onSelect={(id) => { setCombo(combos.find((item) => item.id === id) || null); setDialog('combo-items'); }} />}{dialog === 'combo-items' && combo && <ComboItemsDialog combo={combo} onClose={() => setDialog(null)} onConfirm={(selected) => { selected.forEach((item) => add(item, combo)); setDialog(null); }} />}{dialog === 'promotion' && selectedPromo && <ChoiceDialog title="Aplicar promocion" subtitle={selectedPromo.productName} items={[...(selectedPromo.promotionId ? [{ id: 0, label: 'Quitar promocion aplicada', detail: '', value: '' }] : []), ...promotions.map((item) => ({ id: item.id, label: item.name, detail: item.discountType === 'PERCENTAGE' ? `${item.discountValue}% de descuento` : `${formatCurrency(item.discountValue)} de descuento`, value: '' }))]} onClose={() => setDialog(null)} onSelect={(id) => { const promo = promotions.find((item) => item.id === id); const gross = Number(selectedPromo.effectivePrice ?? selectedPromo.price ?? 0) * selectedPromo.quantity; const amount = promo ? promo.discountType === 'PERCENTAGE' ? gross * promo.discountValue / 100 : Math.min(gross, promo.discountValue) : 0; setLines((current) => current.map((line) => line === selectedPromo ? { ...line, promotionId: promo?.id || null, promotionName: promo?.name || null, promotionDiscount: Math.round(amount * 100) / 100 } : line)); setDialog(null); }} />}{dialog === 'payment' && <PaymentDialog total={total} onClose={() => setDialog(null)} onConfirm={createSale} />}</>;
}

function PosLine({ line, onQuantity, onRemove, onPromotion }: { line: Line; onQuantity: (value: number) => void; onRemove: () => void; onPromotion?: () => void }) { const amount = Number(line.effectivePrice ?? line.price ?? 0) * line.quantity - (line.promotionDiscount || 0); return <motion.article className="react-pos-line" layout><div><strong>{line.productName}</strong><small>{line.variantLabel || 'Producto'} · {line.sku}</small>{line.promotionName && <span className="badge badge-success">{line.promotionName} · -{formatCurrency(line.promotionDiscount)}</span>}</div><div className="react-pos-line-controls"><input className="input" type="number" min="1" max={line.stock} value={line.quantity} onChange={(event) => onQuantity(Number(event.target.value))} aria-label={`Cantidad de ${line.productName}`} /><strong>{formatCurrency(amount)}</strong><button className="btn btn-ghost btn-sm" type="button" onClick={onRemove}>Quitar</button></div>{onPromotion && <button className="btn btn-ghost btn-sm react-pos-promotion-button" type="button" onClick={onPromotion}>{line.promotionName ? 'Cambiar promocion' : '+ Promocion'}</button>}</motion.article>; }
function comboDescription(item: ComboItem) { return item.selectorType === 'CATEGORY' ? `${item.quantity} x cualquier producto de ${item.categoryName || 'la categoria'}` : `${item.quantity} x ${item.productName || 'producto'}`; }

function ChoiceDialog({ title, subtitle, items, onClose, onSelect }: { title: string; subtitle?: string; items: Array<{ id: number; label: string; detail: string; value: string }>; onClose: () => void; onSelect: (id: number) => void }) { return <Dialog title={title} subtitle={subtitle} error="" onClose={onClose} actions={<button className="btn btn-secondary" type="button" onClick={onClose}>Cerrar</button>}><div className="react-pos-choice-list">{items.length ? items.map((item) => <button type="button" key={item.id} className={item.id === 0 ? 'react-pos-choice-danger' : ''} onClick={() => onSelect(item.id)}><span><strong>{item.label}</strong><small>{item.detail}</small></span><strong>{item.value}</strong></button>) : <EmptyState>No hay opciones disponibles.</EmptyState>}</div></Dialog>; }
function ComboItemsDialog({ combo, onClose, onConfirm }: { combo: Combo; onClose: () => void; onConfirm: (items: Variant[]) => void }) { const slots = combo.items.flatMap((item) => Array.from({ length: item.quantity }, () => item)); const [selected, setSelected] = useState<Array<Variant | null>>(() => slots.map(() => null)); const [active, setActive] = useState<number | null>(null); const [term, setTerm] = useState(''); const [results, setResults] = useState<Variant[]>([]); const [error, setError] = useState(''); useEffect(() => { if (active == null || term.trim().length < 2) { setResults([]); return; } const timer = window.setTimeout(() => { api.get<Variant[]>('/variants/search', { auth: 'staff', query: { q: term.trim() } }).then((items) => setResults(items || [])).catch(() => setResults([])); }, 250); return () => window.clearTimeout(timer); }, [active, term]); function confirm() { if (selected.some((item) => !item)) { setError('Elige una variante para cada linea del combo.'); return; } if ((selected as Variant[]).some((item) => item.stock < 1)) { setError('Una de las variantes no tiene stock disponible.'); return; } onConfirm(selected as Variant[]); } return <Dialog title={combo.name} subtitle={`Precio del combo: ${formatCurrency(combo.price)}`} error={error} onClose={onClose} actions={<><button className="btn btn-secondary" type="button" onClick={onClose}>Cancelar</button><button className="btn btn-primary" type="button" onClick={confirm}>Agregar al carrito</button></>}>{<div className="react-pos-combo-slots">{slots.map((slot, index) => <div className="react-pos-combo-slot" key={index}><span className="field-label">{slot.selectorType === 'CATEGORY' ? `Cualquier producto de ${slot.categoryName || 'la categoria'}` : slot.productName || 'Producto'}</span>{selected[index] ? <span className="react-pos-selected"><strong>{selected[index]?.productName} · {selected[index]?.variantLabel}</strong><button className="btn btn-ghost btn-sm" type="button" onClick={() => setSelected((current) => current.map((item, position) => position === index ? null : item))}>Cambiar</button></span> : <div className="react-pos-customer-search"><input className="input" value={active === index ? term : ''} onFocus={() => { setActive(index); setTerm(''); }} onChange={(event) => { setActive(index); setTerm(event.target.value); }} placeholder="Buscar variante..." />{active === index && results.length > 0 && <div className="react-pos-results">{results.map((item) => <button type="button" key={item.variantId} onClick={() => { setSelected((current) => current.map((value, position) => position === index ? item : value)); setActive(null); setTerm(''); setResults([]); }}><span><strong>{item.productName}</strong><small>{item.variantLabel || item.sku}</small></span><span>Stock: {item.stock}</span></button>)}</div>}</div>}</div>)}</div>}</Dialog>; }

function PaymentDialog({ total, onClose, onConfirm }: { total: number; onClose: () => void; onConfirm: (value: { payments: Array<{ paymentMethodId: number; amount: number; reference: string | null }>; promoterId: number | null; billingDocumentType: 'TICKET' | 'BOLETA' | 'FACTURA'; billingDocumentNumber: string | null; billingName: string | null }) => Promise<void> }) {
  const [methods, setMethods] = useState<PaymentMethod[]>([]);
  const [promoters, setPromoters] = useState<Promoter[]>([]);
  const [amounts, setAmounts] = useState<Record<number, string>>({});
  const [references, setReferences] = useState<Record<number, string>>({});
  const [qr, setQr] = useState<Record<number, boolean>>({});
  const [promoter, setPromoter] = useState('');
  const [electronicInvoicingEnabled, setElectronicInvoicingEnabled] = useState<boolean | null>(null);
  const [billingType, setBillingType] = useState<'TICKET' | 'BOLETA' | 'FACTURA'>('TICKET');
  const [billingNumber, setBillingNumber] = useState('');
  const [billingName, setBillingName] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    api.get<{ electronicInvoicingEnabled?: boolean }>('/settings/company', { auth: 'staff' })
      .then((settings) => {
        const enabled = settings?.electronicInvoicingEnabled === true;
        setElectronicInvoicingEnabled(enabled);
        if (enabled) setBillingType('BOLETA');
      })
      .catch(() => setElectronicInvoicingEnabled(false));
  }, []);

  useEffect(() => {
    Promise.all([
      api.get<PaymentMethod[]>('/payment-methods', { auth: 'staff' }),
      api.get<Promoter[]>('/promoters', { auth: 'staff' }).catch(() => []),
    ]).then(([allMethods, allPromoters]) => {
      setMethods((allMethods || []).filter((item) => item.status === 'ACTIVE'));
      setPromoters((allPromoters || []).filter((item) => item.status === 'ACTIVE'));
    }).catch((reason) => setError(reasonOf(reason, 'No se pudieron cargar los metodos de pago.')));
  }, []);

  const paid = methods.reduce((sum, item) => sum + (Number(amounts[item.id]) || 0), 0);
  const remaining = Math.round((total - paid) * 100) / 100;

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (electronicInvoicingEnabled === null) {
      setError('Espera a que cargue la configuracion de comprobantes.');
      return;
    }
    if (remaining !== 0) {
      setError(`El monto restante debe ser S/ 0.00. Actualmente: ${formatCurrency(remaining)}.`);
      return;
    }
    const payments = methods.map((method) => ({
      paymentMethodId: method.id,
      amount: Number(amounts[method.id]) || 0,
      reference: method.requiresReference ? references[method.id]?.trim() || null : null,
    })).filter((item) => item.amount > 0);
    if (!payments.length) {
      setError('Ingresa al menos un monto.');
      return;
    }
    const inheritedCustomerDocument = billingNumber.trim();
    const billingError = validateCheckoutBilling({
      type: billingType,
      number: billingType === 'FACTURA' ? billingNumber : inheritedCustomerDocument,
      name: billingName,
      electronicInvoicingAvailable: electronicInvoicingEnabled === true,
    });
    if (billingError) {
      setError(billingError);
      return;
    }
    if (billingType === 'BOLETA' && total > 700 && !inheritedCustomerDocument) {
      setError('La boleta por importes mayores a S/ 700 requiere identificar al adquirente.');
      return;
    }
    setSaving(true);
    try {
      await onConfirm({
        payments,
        promoterId: promoter ? Number(promoter) : null,
        billingDocumentType: billingType,
        billingDocumentNumber: billingType === 'TICKET' ? null : inheritedCustomerDocument || null,
        billingName: billingType === 'FACTURA' ? billingName.trim() : null,
      });
    } catch (reason) {
      setError(reasonOf(reason, 'No se pudo registrar la venta.'));
    } finally {
      setSaving(false);
    }
  }

  return <Dialog title="Cobrar venta" subtitle={`Total a pagar: ${formatCurrency(total)}`} error={error} onClose={onClose} actions={<><button className="btn btn-secondary" type="button" onClick={onClose}>Cancelar</button><button className="btn btn-primary" type="submit" form="pos-payment-form" disabled={saving || !methods.length || electronicInvoicingEnabled === null}>{saving ? 'Registrando...' : electronicInvoicingEnabled === null ? 'Cargando...' : 'Confirmar venta'}</button></>}>
    <form id="pos-payment-form" className="react-payment-form" onSubmit={submit} noValidate>
      {promoters.length > 0 && <label className="field"><span className="field-label">Promotor (opcional)</span><select className="select" value={promoter} onChange={(event) => setPromoter(event.target.value)}><option value="">Ninguno</option>{promoters.map((item) => <option value={item.id} key={item.id}>{item.name}</option>)}</select></label>}
      <fieldset className="react-billing-block">
        <legend>Comprobante</legend>
        <p className="field-hint">{electronicInvoicingEnabled ? 'Elige boleta o factura para esta venta.' : 'Esta venta generara un ticket interno; no reemplaza un comprobante SUNAT.'}</p>
        {electronicInvoicingEnabled ? <div className="react-billing-options"><label className={billingType === 'BOLETA' ? 'is-selected' : ''}><input type="radio" name="posBillingType" checked={billingType === 'BOLETA'} onChange={() => setBillingType('BOLETA')} />Boleta</label><label className={billingType === 'FACTURA' ? 'is-selected' : ''}><input type="radio" name="posBillingType" checked={billingType === 'FACTURA'} onChange={() => setBillingType('FACTURA')} />Factura</label></div> : <input type="hidden" name="posBillingType" value="TICKET" />}
        {billingType === 'FACTURA' && <div className="react-billing-fields"><label className="field"><span className="field-label">RUC</span><input className="input" required maxLength={11} inputMode="numeric" value={billingNumber} onChange={(event) => setBillingNumber(digitsOnly(event.target.value, 11))} /></label><label className="field"><span className="field-label">Razon social</span><input className="input" required maxLength={150} value={billingName} onChange={(event) => setBillingName(legalNameInput(event.target.value))} /></label></div>}
        {billingType === 'BOLETA' && electronicInvoicingEnabled && <label className="field"><span className="field-label">Documento del adquirente (opcional hasta S/ 700)</span><input className="input" maxLength={15} value={billingNumber} onChange={(event) => setBillingNumber(event.target.value.replace(/[^A-Za-z0-9]/g, '').slice(0, 15))} placeholder="DNI, RUC o CE" /></label>}
      </fieldset>
      <div className="react-payment-list">{methods.map((method) => <div className="react-payment-row" key={method.id}><div><strong>{method.name}</strong>{method.requiresReference && <input className="input" maxLength={50} value={references[method.id] || ''} onChange={(event) => setReferences((current) => ({ ...current, [method.id]: event.target.value }))} placeholder="Numero de operacion" />}{method.qrImageUrl && <><button type="button" className="btn btn-ghost btn-sm" onClick={() => setQr((current) => ({ ...current, [method.id]: !current[method.id] }))}>{qr[method.id] ? 'Ocultar QR' : 'Mostrar QR'}</button>{qr[method.id] && <div className="react-payment-qr"><img src={imageUrl(method.qrImageUrl)} alt={`QR de ${method.name}`} />{method.accountHolder && <span>{method.accountHolder}</span>}{method.accountNumber && <span>{method.accountNumber}</span>}</div>}</>}</div><input className="input react-payment-amount" inputMode="decimal" value={amounts[method.id] || ''} onChange={(event) => setAmounts((current) => ({ ...current, [method.id]: decimalInput(event.target.value) }))} placeholder="0.00" aria-label={`Monto ${method.name}`} /><button className="btn btn-ghost btn-sm" type="button" onClick={() => setAmounts((current) => ({ ...current, [method.id]: Math.max(0, (Number(current[method.id]) || 0) + remaining).toFixed(2) }))}>Todo</button></div>)}</div>
      <div className={`react-payment-remaining${remaining === 0 ? ' is-complete' : ''}`}><span>Restante</span><strong>{formatCurrency(remaining)}</strong></div>
    </form>
  </Dialog>;
}

function TicketDialog({ sale, onClose, canIssueDocuments }: { sale: Sale; onClose: () => void; canIssueDocuments: boolean }) {
  const [settings, setSettings] = useState<(StoreConfig & { ruc?: string | null; address?: string | null; phone?: string | null; ticketFooter?: string | null }) | null>(null);
  useEffect(() => { api.get<typeof settings>('/settings/company', { auth: 'staff' }).then(setSettings).catch(() => setSettings(null)); }, []);
  const billingLabel = sale.billingDocumentType === 'FACTURA' ? 'Factura electronica' : sale.billingDocumentType === 'BOLETA' ? 'Boleta electronica' : 'Ticket interno';
  return <Dialog title="¡Venta registrada!" subtitle={sale.saleNumber} error="" onClose={onClose} actions={<>{!canIssueDocuments && <button className="btn btn-secondary" type="button" onClick={() => printSaleTicket(sale, settings)}>Imprimir ticket</button>}{canIssueDocuments && <a className="btn btn-secondary" href={`/admin/comprobantes?saleId=${sale.id}`}>Emitir boleta o factura</a>}<button className="btn btn-primary" type="button" onClick={() => { onClose(); window.setTimeout(() => document.querySelector<HTMLInputElement>('.react-pos-search input')?.focus(), 0); }}>Nueva venta</button></>}> 
    <div className="react-sale-success" data-sale-registered="true">
      <div className="react-sale-success-lines">{sale.items.map((item, index) => <div key={`${item.variantId}-${index}`}><span>{item.quantity} × {item.productName} <small>({item.variantLabel || item.variantSku})</small></span><strong>{formatCurrency(item.subtotal)}</strong></div>)}</div>
      <div className="react-sale-success-total"><span>Total</span><strong>{formatCurrency(sale.total)}</strong></div>
      <p className="field-hint">Comprobante: {billingLabel}{sale.billingDocumentNumber ? ` · ${sale.billingDocumentNumber}` : ''}{sale.billingName ? ` · ${sale.billingName}` : ''}</p>
    </div>
  </Dialog>;
}

function HistoryPanel({ electronicInvoicingEnabled }: { electronicInvoicingEnabled: boolean }) { const [rows, setRows] = useState<SaleSummary[]>([]); const [selected, setSelected] = useState<number | null>(null); const [page, setPage] = useState(0); const [totalPages, setTotalPages] = useState(0); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const load = async () => { setLoading(true); try { const result = await api.get<Page<SaleSummary>>('/sales', { auth: 'staff', query: { page, size: 20, sort: 'createdAt,desc' } }); setRows(result.content || []); setTotalPages(result.totalPages || 0); } catch (reason) { setError(reasonOf(reason, 'No se pudo cargar el historial.')); } finally { setLoading(false); } }; useEffect(() => { void load(); }, [page]); return <><section className="table-card react-pos-table"><div className="card-header"><div><span className="field-hint">OPERACIONES</span><h2>Historial de ventas</h2></div></div>{error && <ErrorState message={error} />}{loading ? <LoadingState label="Cargando ventas..." /> : rows.length ? <div className="table-scroll"><table className="data-table"><thead><tr><th>Venta</th><th>Fecha</th><th>Cliente</th><th>Vendedor</th><th>Total</th><th>Estado</th><th></th></tr></thead><tbody>{rows.map((row) => <tr key={row.id}><td data-label="Venta" className="table-cell-primary">{row.saleNumber}</td><td data-label="Fecha">{formatDate(row.createdAt)}</td><td data-label="Cliente">{row.customerName || 'Venta mostrador'}</td><td data-label="Vendedor">{row.sellerName || '-'}</td><td data-label="Total" className="mono">{formatCurrency(row.total)}</td><td data-label="Estado"><span className={`badge ${statusClass[row.status] || 'badge-neutral'}`}>{statusLabel[row.status] || row.status}</span></td><td data-label="Acciones"><button className="btn btn-secondary btn-sm" type="button" onClick={() => setSelected(row.id)}>Ver</button></td></tr>)}</tbody></table></div> : <EmptyState>Sin ventas registradas.</EmptyState>}{totalPages > 0 && <div className="pagination-bar"><button className="btn btn-secondary btn-sm" disabled={page === 0} type="button" onClick={() => setPage((value) => value - 1)}>Anterior</button><span>Pagina {page + 1} de {totalPages}</span><button className="btn btn-secondary btn-sm" disabled={page + 1 >= totalPages} type="button" onClick={() => setPage((value) => value + 1)}>Siguiente</button></div>}</section>{selected && <SaleDetailWithReturn saleId={selected} electronicInvoicingEnabled={electronicInvoicingEnabled} onClose={() => setSelected(null)} onChanged={() => { setSelected(null); void load(); }} />}</>; }
function SaleDetail({ saleId, onClose, onChanged }: { saleId: number; onClose: () => void; onChanged: () => void }) { const [sale, setSale] = useState<Sale | null>(null); const [error, setError] = useState(''); const [reason, setReason] = useState(''); const [confirming, setConfirming] = useState(false); useEffect(() => { api.get<Sale>(`/sales/${saleId}`, { auth: 'staff' }).then(setSale).catch((value) => setError(reasonOf(value, 'No se pudo cargar el detalle.'))); }, [saleId]); async function cancel() { if (!reason.trim()) { setError('Ingresa el motivo de anulacion.'); return; } setConfirming(true); try { await api.post(`/sales/${saleId}/cancel`, { reason: reason.trim() }, { auth: 'staff' }); showToast('Venta anulada correctamente.'); onChanged(); } catch (value) { setError(reasonOf(value, 'No se pudo anular la venta.')); } finally { setConfirming(false); } } return <Dialog title={sale ? `Venta ${sale.saleNumber}` : 'Detalle de venta'} subtitle={sale ? formatDate(sale.createdAt) : undefined} error={error} onClose={onClose} actions={<button className="btn btn-secondary" type="button" onClick={onClose}>Cerrar</button>}>{!sale ? <LoadingState label="Cargando detalle..." /> : <div className="react-sale-detail"><div className="react-sale-lines">{sale.items.map((item, index) => <div className="react-sale-line" key={`${item.variantId}-${index}`}><span><strong>{item.productName}</strong><small>{item.variantLabel || item.variantSku} x {item.quantity}</small></span><strong>{formatCurrency(item.subtotal)}</strong></div>)}</div><div className="react-sale-totals"><span>Subtotal <strong>{formatCurrency(sale.subtotal)}</strong></span><span>Descuento <strong>{formatCurrency(sale.discountAmount)}</strong></span><span>Total <strong>{formatCurrency(sale.total)}</strong></span></div><p className="field-hint">Cliente: {sale.customerName || 'Venta mostrador'} · Pagos: {sale.payments.map((item) => item.paymentMethodName).join(', ')}</p>{sale.status === 'COMPLETED' && can('VENTAS_ANULAR') && <div className="react-inline-danger"><label className="field"><span className="field-label">Motivo de anulacion</span><textarea className="input" maxLength={255} rows={2} value={reason} onChange={(event) => setReason(event.target.value)} /></label><button className="btn btn-danger" type="button" disabled={confirming} onClick={() => void cancel()}>{confirming ? 'Anulando...' : 'Anular venta'}</button></div>}{sale.status === 'COMPLETED' && can('VENTAS_DEVOLVER') && <p className="field-hint">La devolucion se gestiona desde el flujo de Devoluciones y conserva validacion de cantidades y reembolso.</p>}</div>}</Dialog>; }

function ReturnsPanel() { const [rows, setRows] = useState<ReturnRow[]>([]); const [page, setPage] = useState(0); const [totalPages, setTotalPages] = useState(0); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); useEffect(() => { setLoading(true); api.get<Page<ReturnRow>>('/returns', { auth: 'staff', query: { page, size: 20, sort: 'createdAt,desc' } }).then((result) => { setRows(result.content || []); setTotalPages(result.totalPages || 0); }).catch((reason) => setError(reasonOf(reason, 'No se pudieron cargar las devoluciones.'))).finally(() => setLoading(false)); }, [page]); return <section className="table-card react-pos-table"><div className="card-header"><div><span className="field-hint">DEVOLUCIONES</span><h2>Devoluciones registradas</h2></div></div>{error ? <ErrorState message={error} /> : loading ? <LoadingState label="Cargando devoluciones..." /> : rows.length ? <div className="table-scroll"><table className="data-table"><thead><tr><th>Nro. devolucion</th><th>Venta</th><th>Fecha</th><th>Motivo</th><th>Reembolso</th><th>Total</th><th>Usuario</th></tr></thead><tbody>{rows.map((row) => <tr key={row.id}><td data-label="Devolucion" className="table-cell-primary">{row.returnNumber}</td><td data-label="Venta">{row.saleNumber}</td><td data-label="Fecha">{formatDate(row.createdAt)}</td><td data-label="Motivo">{row.reason}</td><td data-label="Reembolso">{row.refundMethodName}</td><td data-label="Total" className="mono">{formatCurrency(row.totalAmount)}</td><td data-label="Usuario">{row.username}</td></tr>)}</tbody></table></div> : <EmptyState>Sin devoluciones registradas.</EmptyState>}{totalPages > 0 && <div className="pagination-bar"><button className="btn btn-secondary btn-sm" disabled={page === 0} type="button" onClick={() => setPage((value) => value - 1)}>Anterior</button><span>Pagina {page + 1} de {totalPages}</span><button className="btn btn-secondary btn-sm" disabled={page + 1 >= totalPages} type="button" onClick={() => setPage((value) => value + 1)}>Siguiente</button></div>}</section>; }


function Dialog({ title, subtitle, error, actions, onClose, children }: { title: string; subtitle?: string; error: string; actions: ReactNode; onClose: () => void; children: ReactNode }) { return <div className="react-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><section className="react-dialog" role="dialog" aria-modal="true" aria-labelledby="pos-dialog-title"><div className="react-dialog-header"><div><span className="field-hint">VENTAS</span><h2 id="pos-dialog-title">{title}</h2>{subtitle && <p className="field-hint">{subtitle}</p>}</div><button className="btn btn-ghost" type="button" aria-label="Cerrar" onClick={onClose}>X</button></div>{error && <div className="alert alert-danger" role="alert">{error}</div>}{children}<div className="react-dialog-actions">{actions}</div></section></div>; }
function OpenCashDialog({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) { const [registers, setRegisters] = useState<Array<{ id: number; name: string; status: string }>>([]); const [registerId, setRegisterId] = useState(''); const [amount, setAmount] = useState('0'); const [error, setError] = useState(''); const [saving, setSaving] = useState(false); useEffect(() => { api.get<Array<{ id: number; name: string; status: string }>>('/cash-registers', { auth: 'staff' }).then((items) => setRegisters((items || []).filter((item) => item.status === 'ACTIVE'))).catch((reason) => setError(reasonOf(reason, 'No se pudieron cargar las cajas.'))); }, []); async function submit(event: FormEvent) { event.preventDefault(); const openingAmount = Number(amount); if (!registerId || !Number.isFinite(openingAmount) || openingAmount < 0) { setError('Selecciona una caja e ingresa un monto valido.'); return; } setSaving(true); try { await api.post('/cash-registers/sessions', { cashRegisterId: Number(registerId), openingAmount }, { auth: 'staff' }); showToast('Caja abierta correctamente.'); onSaved(); } catch (reason) { setError(reasonOf(reason, 'No se pudo abrir la caja.')); } finally { setSaving(false); } } return <Dialog title="Abrir caja" subtitle="Confirma la caja y el monto inicial." error={error} onClose={onClose} actions={<><button className="btn btn-secondary" type="button" onClick={onClose}>Cancelar</button><button className="btn btn-primary" type="submit" form="pos-open-form" disabled={saving}>{saving ? 'Guardando...' : 'Abrir caja'}</button></>}><form id="pos-open-form" className="form-grid" onSubmit={submit} noValidate><label className="field field-span-2"><span className="field-label">Caja</span><select className="select" required value={registerId} onChange={(event) => setRegisterId(event.target.value)}><option value="">Selecciona una caja</option>{registers.map((item) => <option value={item.id} key={item.id}>{item.name}</option>)}</select></label><label className="field field-span-2"><span className="field-label">Monto de apertura (S/)</span><input className="input" inputMode="decimal" required value={amount} onChange={(event) => setAmount(decimalInput(event.target.value))} /></label></form></Dialog>; }
// audit marker: POS React includes combo, promotion and payment QR flows.

function PromotersPanel() {
  const [rows, setRows] = useState<Promoter[]>([]);
  const [name, setName] = useState('');
  const [editing, setEditing] = useState<Promoter | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const canManage = can('PROMOTORES_GESTIONAR');

  async function load() {
    setLoading(true);
    try {
      setRows(await api.get<Promoter[]>('/promoters', { auth: 'staff' }) || []);
    } catch (value) {
      setError(reasonOf(value, 'No se pudieron cargar los promotores.'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, []);

  function reset() {
    setEditing(null);
    setName('');
    setError('');
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!canManage) return;
    const value = name.trim();
    if (!value || value.length > 120) {
      setError('Ingresa un nombre de promotor de hasta 120 caracteres.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      if (editing) await api.put(`/promoters/${editing.id}`, { name: value }, { auth: 'staff' });
      else await api.post('/promoters', { name: value }, { auth: 'staff' });
      showToast(editing ? 'Promotor actualizado correctamente.' : 'Promotor creado correctamente.');
      reset();
      await load();
    } catch (reason) {
      setError(reasonOf(reason, 'No se pudo guardar el promotor.'));
    } finally {
      setSaving(false);
    }
  }

  async function toggle(row: Promoter) {
    if (!canManage) return;
    try {
      await api.patch(`/promoters/${row.id}/status`, { status: row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' }, { auth: 'staff' });
      showToast(row.status === 'ACTIVE' ? 'Promotor desactivado.' : 'Promotor activado.');
      await load();
    } catch (reason) {
      setError(reasonOf(reason, 'No se pudo cambiar el estado.'));
    }
  }

  return <>
    <section className="card react-promoter-create">
      <div><span className="field-hint">COMISIONES</span><h2>{editing ? 'Editar promotor' : 'Nuevo promotor'}</h2><p>Asocia un promotor al cobrar una venta para conservar la trazabilidad comercial.</p></div>
      {canManage ? <form onSubmit={save}><input className="input" required maxLength={120} value={name} onChange={(event) => setName(event.target.value.slice(0, 120))} placeholder="Nombre completo" /><button className="btn btn-primary" disabled={saving}>{saving ? 'Guardando...' : editing ? 'Guardar cambios' : 'Agregar promotor'}</button>{editing && <button className="btn btn-secondary" type="button" onClick={reset}>Cancelar</button>}</form> : <p className="field-hint">No tienes permiso para gestionar promotores.</p>}
    </section>
    <section className="table-card react-pos-table">
      <div className="card-header"><div><span className="field-hint">COMISIONES</span><h2>Promotores activos e historicos</h2></div></div>
      {error && <ErrorState message={error} />}
      {loading ? <LoadingState label="Cargando promotores..." /> : rows.length ? <div className="table-scroll"><table className="data-table"><thead><tr><th>Nombre</th><th>Estado</th>{canManage && <th>Acciones</th>}</tr></thead><tbody>{rows.map((row) => <tr key={row.id}><td data-label="Nombre" className="table-cell-primary">{row.name}</td><td data-label="Estado"><span className={`badge ${row.status === 'ACTIVE' ? 'badge-success' : 'badge-neutral'}`}>{row.status === 'ACTIVE' ? 'Activo' : 'Inactivo'}</span></td>{canManage && <td data-label="Acciones"><button className="btn btn-secondary btn-sm" type="button" onClick={() => { setEditing(row); setName(row.name); setError(''); }}>Editar</button> <button className="btn btn-ghost btn-sm" type="button" onClick={() => void toggle(row)}>{row.status === 'ACTIVE' ? 'Desactivar' : 'Activar'}</button></td>}</tr>)}</tbody></table></div> : <EmptyState>Sin promotores registrados.</EmptyState>}
    </section>
  </>;
}

function SaleDetailWithReturn({ saleId, onClose, onChanged, electronicInvoicingEnabled }: { saleId: number; onClose: () => void; onChanged: () => void; electronicInvoicingEnabled: boolean }) { const [sale, setSale] = useState<Sale | null>(null); const [returning, setReturning] = useState(false); const [cancelling, setCancelling] = useState(false); const [cancelReason, setCancelReason] = useState(''); const [error, setError] = useState(''); useEffect(() => { api.get<Sale>('/sales/' + saleId, { auth: 'staff' }).then(setSale).catch((value) => setError(reasonOf(value, 'No se pudo cargar el detalle.'))); }, [saleId]); async function cancelSale() { if (!cancelReason.trim()) { setError('Ingresa el motivo de anulacion.'); return; } if (!window.confirm('Se anulara la venta ' + sale?.saleNumber + '. Si tiene comprobante aceptado, se gestionara la nota de credito fiscal antes de completar la anulacion. Confirma para continuar.')) return; try { setError(''); await api.post('/sales/' + saleId + '/cancel', { reason: cancelReason.trim() }, { auth: 'staff' }); showToast('Venta anulada correctamente.'); onChanged(); } catch (value) { setError(reasonOf(value, 'No se pudo anular la venta.')); } } const canReturn = sale && (sale.status === 'COMPLETED' || sale.status === 'PARTIALLY_RETURNED') && can('VENTAS_DEVOLVER'); const canCancel = sale?.status === 'COMPLETED' && can('VENTAS_ANULAR'); const seller = sale?.sellerName ? 'Vendedor: ' + sale.sellerName : ''; const promoter = sale?.promoterName ? ' · Promotor: ' + sale.promoterName : ''; return <Dialog title={sale ? 'Venta ' + sale.saleNumber : 'Detalle de venta'} subtitle={sale ? formatDate(sale.createdAt) + ' · ' + seller + promoter : undefined} error={error} onClose={onClose} actions={<><button className="btn btn-secondary" type="button" onClick={onClose}>Cerrar</button>{sale && !electronicInvoicingEnabled && <button className="btn btn-secondary" type="button" onClick={() => printSaleTicket(sale, null)}>Imprimir ticket</button>}{electronicInvoicingEnabled && sale?.status === 'COMPLETED' && <a className="btn btn-secondary" href={'/admin/comprobantes?saleId=' + saleId}>Comprobante</a>}{canReturn && <button className="btn btn-secondary" type="button" onClick={() => setReturning(true)}>Devolver</button>}{canCancel && <button className="btn btn-danger" type="button" onClick={() => setCancelling(true)}>Anular venta</button>}</>}>{!sale ? <LoadingState label="Cargando detalle..." /> : <div className="react-sale-detail"><div className="react-sale-lines">{sale.items.map((item, index) => <div className="react-sale-line" key={item.variantId + '-' + index}><span><strong>{item.productName}</strong><small>{item.variantLabel || item.variantSku} × {item.quantity}</small></span><strong>{formatCurrency(item.subtotal)}</strong></div>)}</div><div className="react-sale-totals"><span>Subtotal <strong>{formatCurrency(sale.subtotal)}</strong></span><span>Descuento <strong>{formatCurrency(sale.discountAmount)}</strong></span><span>Total <strong>{formatCurrency(sale.total)}</strong></span></div><div className="react-sale-payments"><strong>Pagos</strong>{sale.payments.map((payment, index) => <div key={payment.paymentMethodName + '-' + index}><span>{payment.paymentMethodName}{payment.reference ? ' · Ref. ' + payment.reference : ''}</span><strong>{formatCurrency(payment.amount)}</strong></div>)}</div><p className="field-hint">Cliente: {sale.customerName || 'Venta mostrador'}{sale.status !== 'COMPLETED' ? ' · Estado: ' + (statusLabel[sale.status] || sale.status) : ''}</p>{canReturn && <p className="field-hint">La devolución valida cantidades, stock y reembolso. Si existe un comprobante aceptado, el backend gestionará la nota de crédito fiscal correspondiente.</p>}{canCancel && <p className="field-hint">La anulación devuelve stock y revierte caja. Si existe un comprobante aceptado, el backend exigirá una nota de crédito aceptada antes de anular la venta.</p>}</div>}{cancelling && <div className='react-inline-danger'><label className='field'><span className='field-label'>Motivo de anulacion</span><textarea className='input' maxLength={255} rows={2} value={cancelReason} onChange={(event) => setCancelReason(event.target.value)} /></label><div className='react-sale-actions'><button className='btn btn-secondary' type='button' onClick={() => setCancelling(false)}>Cancelar</button><button className='btn btn-danger' type='button' onClick={() => void cancelSale()}>Confirmar anulacion</button></div></div>}{returning && <ReturnDialog saleId={saleId} electronicInvoicingEnabled={electronicInvoicingEnabled} onClose={() => setReturning(false)} onSaved={onChanged} />}</Dialog>; }

function ReturnDialog({ saleId, onClose, onSaved, electronicInvoicingEnabled }: { saleId: number; onClose: () => void; onSaved: () => void; electronicInvoicingEnabled: boolean }) { const [items, setItems] = useState<Array<{ saleDetailId: number; productName: string; variantSku: string; quantityReturnable: number }>>([]); const [methods, setMethods] = useState<PaymentMethod[]>([]); const [quantities, setQuantities] = useState<Record<number, string>>({}); const [restock, setRestock] = useState<Record<number, boolean>>({}); const [reason, setReason] = useState(''); const [methodId, setMethodId] = useState(''); const [error, setError] = useState(''); const [saving, setSaving] = useState(false); useEffect(() => { Promise.all([api.get<Array<{ saleDetailId: number; productName: string; variantSku: string; quantityReturnable: number }>>(`/sales/${saleId}/returnable-items`, { auth: 'staff' }), api.get<PaymentMethod[]>('/payment-methods', { auth: 'staff' })]).then(([available, paymentMethods]) => { setItems((available || []).filter((item) => item.quantityReturnable > 0)); setMethods((paymentMethods || []).filter((item) => item.status === 'ACTIVE')); }).catch((value) => setError(reasonOf(value, 'No se pudo preparar la devolucion.'))); }, [saleId]); async function submit(event: FormEvent) { event.preventDefault(); const selected = items.map((item) => ({ saleDetailId: item.saleDetailId, quantity: Number(quantities[item.saleDetailId]) || 0, restock: restock[item.saleDetailId] ?? true })).filter((item) => item.quantity > 0); if (!selected.length || !reason.trim() || !methodId) { setError('Selecciona articulos, motivo y metodo de reembolso.'); return; } if (selected.some((item) => item.quantity > (items.find((row) => row.saleDetailId === item.saleDetailId)?.quantityReturnable || 0))) { setError('La cantidad supera lo disponible para devolver.'); return; } const confirmation = electronicInvoicingEnabled ? 'Se registrará la devolución y, si la venta tiene un comprobante aceptado, se solicitará la nota de crédito fiscal correspondiente. ¿Deseas continuar?' : 'Se registrará únicamente la devolución interna de la venta y el reembolso indicado. ¿Deseas continuar?'; if (!window.confirm(confirmation)) return; setSaving(true); try { await api.post('/returns', { saleId, reason: reason.trim(), refundMethodId: Number(methodId), items: selected }, { auth: 'staff' }); showToast('Devolucion registrada correctamente.'); onSaved(); } catch (value) { setError(reasonOf(value, 'No se pudo registrar la devolucion.')); } finally { setSaving(false); } } return <div className="react-dialog-backdrop react-dialog-nested" role="presentation"><section className="react-dialog" role="dialog" aria-modal="true" aria-labelledby="pos-return-title"><div className="react-dialog-header"><div><span className="field-hint">DEVOLUCION</span><h2 id="pos-return-title">Registrar devolucion</h2></div><button className="btn btn-ghost" type="button" onClick={onClose}>X</button></div>{error && <div className="alert alert-danger" role="alert">{error}</div>}<form className="form-grid" onSubmit={submit} noValidate><div className="react-return-items">{items.map((item) => <div className="react-return-item" key={item.saleDetailId}><div><strong>{item.productName}</strong><small>{item.variantSku} · disponibles: {item.quantityReturnable}</small></div><input className="input" type="number" min="0" max={item.quantityReturnable} value={quantities[item.saleDetailId] || ''} onChange={(event) => setQuantities((current) => ({ ...current, [item.saleDetailId]: event.target.value.replace(/\D/g, '') }))} /><label className="checkbox-field"><input type="checkbox" checked={restock[item.saleDetailId] ?? true} onChange={(event) => setRestock((current) => ({ ...current, [item.saleDetailId]: event.target.checked }))} />Reingresar a stock</label></div>)}</div><label className="field"><span className="field-label">Motivo</span><textarea className="input" required maxLength={255} value={reason} onChange={(event) => setReason(event.target.value)} /></label><label className="field"><span className="field-label">Metodo de reembolso</span><select className="select" required value={methodId} onChange={(event) => setMethodId(event.target.value)}><option value="">Selecciona...</option>{methods.map((item) => <option value={item.id} key={item.id}>{item.name}</option>)}</select></label><div className="react-dialog-actions"><button className="btn btn-secondary" type="button" onClick={onClose}>Cancelar</button><button className="btn btn-primary" disabled={saving}>{saving ? 'Guardando...' : 'Confirmar devolucion'}</button></div></form></section></div>; }
