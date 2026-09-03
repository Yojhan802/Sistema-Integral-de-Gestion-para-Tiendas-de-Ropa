import { useEffect, useState, type ReactNode } from 'react';
import { AdminShell } from './AdminPagesV2';
import { printSaleTicket } from './AdminPosPageV2';
import { ApiError, api, getStaffSession, imageUrl } from '../services/api';
import { EmptyState, ErrorState, LoadingState } from '../components/States';
import { showToast } from '../components/ToastHost';
import type { Page } from '../types';
import { formatCurrency, formatDate } from '../utils';

type OrderStatus = 'PENDING_PAYMENT' | 'CONFIRMED' | 'CANCELLED' | string;
type OrderSummary = { id: number; orderNumber: string; customerName: string; total: number; status: OrderStatus; createdAt: string };
type OrderItem = { variantId: number; productName: string; variantSku: string; variantLabel?: string; quantity: number; unitPrice: number; subtotal: number };
type OrderDetail = OrderSummary & {
  customerId?: number | null; subtotal: number; shippingCost: number; paymentMethodId?: number | null; paymentMethodName: string;
  paymentReference?: string | null; paymentProofUrl?: string | null; recipientDni: string; recipientFirstName: string;
  recipientLastNamePaterno: string; recipientLastNameMaterno: string; phone: string; address: string; department: string;
  province: string; district: string; notes?: string | null; confirmedAt?: string | null; confirmedByUsername?: string | null;
  cancelledAt?: string | null; cancellationReason?: string | null; saleId?: number | null; items: OrderItem[];
  billingDocumentType?: 'TICKET' | 'BOLETA' | 'FACTURA'; billingDocumentNumber?: string | null; billingName?: string | null;
};
type TicketSale = { id: number; saleNumber: string; customerName?: string | null; sellerName?: string | null; total: number; status: string; createdAt: string; subtotal: number; discountAmount: number; items: Array<{ variantId: number; productName: string; variantSku: string; variantLabel?: string; quantity: number; unitPrice: number; subtotal: number }>; payments: Array<{ paymentMethodName: string; amount: number; reference?: string | null }>; billingDocumentType?: 'TICKET' | 'BOLETA' | 'FACTURA'; billingDocumentNumber?: string | null; billingName?: string | null };

const statusLabels: Record<string, string> = { PENDING_PAYMENT: 'Pendiente de pago', CONFIRMED: 'Confirmado', CANCELLED: 'Anulado' };
const statusClasses: Record<string, string> = { PENDING_PAYMENT: 'badge-warning', CONFIRMED: 'badge-success', CANCELLED: 'badge-danger' };
const messageOf = (reason: unknown, fallback: string) => reason instanceof ApiError ? reason.message : fallback;
const can = (permission: string) => getStaffSession()?.user.permissions.includes(permission) ?? false;

export function AdminOrdersPage() {
  const [rows, setRows] = useState<OrderSummary[]>([]);
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<number | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [electronicInvoicingEnabled, setElectronicInvoicingEnabled] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    api.get<Page<OrderSummary>>('/orders', { auth: 'staff', query: { status: status || undefined, page, size: 20, sort: 'createdAt,desc' } })
      .then((result) => { if (!active) return; setRows(result.content || []); setTotalPages(result.totalPages || 0); })
      .catch((reason) => { if (active) setError(messageOf(reason, 'No se pudieron cargar los pedidos.')); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [status, page, reloadKey]);

  useEffect(() => {
    const refreshFromStream = () => setReloadKey((value) => value + 1);
    window.addEventListener('qynex:order-created', refreshFromStream);
    return () => window.removeEventListener('qynex:order-created', refreshFromStream);
  }, []);

  useEffect(() => {
    api.get<{ electronicInvoicingEnabled?: boolean }>('/settings/company', { auth: 'staff' })
      .then((settings) => setElectronicInvoicingEnabled(settings?.electronicInvoicingEnabled === true))
      .catch(() => setElectronicInvoicingEnabled(false));
  }, []);

  function changeStatus(value: string) { setStatus(value); setPage(0); }
  function refresh() { setReloadKey((value) => value + 1); }

  return <AdminShell title="Pedidos" description="Pedidos hechos desde la tienda online. El pago se confirma manualmente antes de descontar stock." activePage="/admin/pedidos">
    <section className="filter-bar react-orders-filter">
      <label className="field"><span className="field-label">Estado</span><select className="select" aria-label="Filtrar por estado" value={status} onChange={(event) => changeStatus(event.target.value)}><option value="">Todos los estados</option><option value="PENDING_PAYMENT">Pendiente de pago</option><option value="CONFIRMED">Confirmado</option><option value="CANCELLED">Anulado</option></select></label>
    </section>
    <section className="table-card react-orders-table">
      {error ? <ErrorState message={error} /> : loading ? <LoadingState label="Cargando pedidos…" /> : rows.length ? <div className="table-scroll"><table className="data-table"><thead><tr><th>N.º pedido</th><th>Cliente</th><th>Total</th><th>Estado</th><th>Fecha</th><th>Acciones</th></tr></thead><tbody>{rows.map((order) => <tr key={order.id}><td data-label="N.º pedido" className="table-cell-primary mono">{order.orderNumber}</td><td data-label="Cliente">{order.customerName || 'Cliente de tienda'}</td><td data-label="Total" className="mono">{formatCurrency(order.total)}</td><td data-label="Estado"><span className={`badge ${statusClasses[order.status] || 'badge-neutral'}`}>{statusLabels[order.status] || order.status}</span></td><td data-label="Fecha" className="table-cell-muted">{formatDate(order.createdAt)}</td><td data-label="Acciones"><button className="btn btn-ghost btn-sm" type="button" onClick={() => setSelected(order.id)}>Ver detalle</button></td></tr>)}</tbody></table></div> : <EmptyState>No se encontraron pedidos.</EmptyState>}
      {totalPages > 0 && <div className="pagination-bar"><button className="btn btn-secondary btn-sm" type="button" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Anterior</button><span>Página {page + 1} de {totalPages}</span><button className="btn btn-secondary btn-sm" type="button" disabled={page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)}>Siguiente</button></div>}
    </section>
    {selected !== null && <OrderDetailDialog orderId={selected} electronicInvoicingEnabled={electronicInvoicingEnabled} onClose={() => setSelected(null)} onChanged={refresh} />}
  </AdminShell>;
}

function OrderDetailDialog({ orderId, electronicInvoicingEnabled, onClose, onChanged }: { orderId: number; electronicInvoicingEnabled: boolean; onClose: () => void; onChanged: () => void }) {
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState('');
  const [printing, setPrinting] = useState(false);

  useEffect(() => { setOrder(null); setError(''); api.get<OrderDetail>(`/orders/${orderId}`, { auth: 'staff' }).then(setOrder).catch((value) => setError(messageOf(value, 'No se pudo cargar el detalle del pedido.'))); }, [orderId]);

  async function confirmPayment() {
    if (!order || !window.confirm(`¿Confirmas que recibiste el pago del pedido ${order.orderNumber}? Esta acción generará la venta y descontará el stock retenido.`)) return;
    setSaving(true); setError('');
    try {
      const confirmed = await api.post<OrderDetail>(`/orders/${order.id}/confirm`, {}, { auth: 'staff' });
      setOrder(confirmed); showToast(`Pago confirmado · ${confirmed.orderNumber}`);
      if (confirmed.saleId && !electronicInvoicingEnabled) {
        const sale = await api.get<TicketSale>(`/sales/${confirmed.saleId}`, { auth: 'staff' });
        printSaleTicket(sale, null);
      } else if (confirmed.saleId && confirmed.billingDocumentType !== 'TICKET') {
        showToast('Pago confirmado. Emite el comprobante solicitado desde este pedido.', 'Comprobante pendiente', 'info');
      }
      onChanged();
    } catch (value) { setError(messageOf(value, 'No se pudo confirmar el pago.')); } finally { setSaving(false); }
  }

  async function cancelOrder() {
    if (!order || !reason.trim()) { setError('Ingresa el motivo de la cancelación.'); return; }
    setSaving(true); setError('');
    try { const updated = await api.post<OrderDetail>(`/orders/${order.id}/cancel`, { reason: reason.trim() }, { auth: 'staff' }); setOrder(updated); setCancelling(false); showToast('Pedido cancelado correctamente.'); onChanged(); } catch (value) { setError(messageOf(value, 'No se pudo cancelar el pedido.')); } finally { setSaving(false); }
  }

  async function printTicket() {
    if (!order?.saleId) return; setPrinting(true); setError('');
    try { const sale = await api.get<TicketSale>(`/sales/${order.saleId}`, { auth: 'staff' }); printSaleTicket(sale, null); } catch (value) { setError(messageOf(value, 'No se pudo cargar el ticket.')); } finally { setPrinting(false); }
  }

  const fullName = order ? [order.recipientFirstName, order.recipientLastNamePaterno, order.recipientLastNameMaterno].filter(Boolean).join(' ') : '';
  if (order && electronicInvoicingEnabled && order.billingDocumentType !== 'TICKET') {
    const requestedLabel = order.billingDocumentType === 'FACTURA' ? 'Factura electronica' : 'Boleta electronica';
    return <div className="react-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><section className="react-dialog react-dialog-wide" role="dialog" aria-modal="true" aria-labelledby="order-electronic-title"><div className="react-dialog-header"><div><span className="field-hint">PEDIDO ONLINE</span><h2 id="order-electronic-title">{order.orderNumber}</h2><p className="field-hint">{formatDate(order.createdAt)}</p></div><button className="btn btn-ghost" type="button" aria-label="Cerrar" onClick={onClose}>X</button></div>{error && <div className="alert alert-danger" role="alert">{error}</div>}<div className="react-order-detail"><div className="react-order-detail-head"><div><strong>{fullName || order.customerName || 'Cliente de tienda'}</strong><span>{[order.address, order.district, order.province, order.department].filter(Boolean).join(', ')}</span></div><span className={`badge ${statusClasses[order.status] || 'badge-neutral'}`}>{statusLabels[order.status] || order.status}</span></div><div className="react-order-items">{order.items.map((item) => <div className="react-order-item" key={`${item.variantId}-${item.variantSku}`}><span><strong>{item.productName}</strong><small>{item.variantLabel || item.variantSku} · {item.quantity} unidad(es)</small></span><strong>{formatCurrency(item.subtotal)}</strong></div>)}</div><div className="react-order-totals"><span>Subtotal <strong>{formatCurrency(order.subtotal)}</strong></span><span>Envio <strong>{order.shippingCost > 0 ? formatCurrency(order.shippingCost) : 'Gratis'}</strong></span><span>Total <strong>{formatCurrency(order.total)}</strong></span></div><div className="react-order-payment"><strong>Comprobante solicitado</strong><span>{requestedLabel}{order.billingDocumentNumber ? ` · ${order.billingDocumentNumber}` : ''}{order.billingName ? ` · ${order.billingName}` : ''}</span><span>Se emite desde el proveedor configurado después de confirmar el pago.</span></div></div><div className="react-dialog-actions">{order.saleId && can('VENTAS_CREAR') && <a className="btn btn-primary" href={`/admin/comprobantes?saleId=${order.saleId}`}>Emitir {requestedLabel.toLowerCase()}</a>}{order.status === 'PENDING_PAYMENT' && can('PEDIDOS_GESTIONAR') && <button className="btn btn-primary" type="button" disabled={saving} onClick={() => void confirmPayment()}>{saving ? 'Confirmando...' : 'Confirmar pago'}</button>}{order.status !== 'CANCELLED' && can('PEDIDOS_GESTIONAR') && !cancelling && <button className="btn btn-danger" type="button" onClick={() => setCancelling(true)}>Cancelar pedido</button>}<button className="btn btn-secondary" type="button" onClick={onClose}>Cerrar</button></div></section></div>;
  }
  return <div className="react-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><section className="react-dialog react-dialog-wide" role="dialog" aria-modal="true" aria-labelledby="order-detail-title"><div className="react-dialog-header"><div><span className="field-hint">PEDIDOS</span><h2 id="order-detail-title">{order ? order.orderNumber : 'Detalle del pedido'}</h2>{order && <p className="field-hint">Registrado {formatDate(order.createdAt)}</p>}</div><button className="btn btn-ghost" type="button" aria-label="Cerrar" onClick={onClose}>×</button></div>{error && <div className="alert alert-danger" role="alert">{error}</div>}{!order ? <LoadingState label="Cargando detalle…" /> : <div className="react-order-detail"><div className="react-order-detail-head"><div><strong>{fullName || order.customerName || 'Cliente de tienda'}</strong><span>{order.recipientDni ? `DNI ${order.recipientDni}` : 'Sin documento'} · {order.phone || 'Sin teléfono'}</span><span>{[order.address, order.district, order.province, order.department].filter(Boolean).join(', ')}</span>{order.notes && <span>Nota: {order.notes}</span>}</div><span className={`badge ${statusClasses[order.status] || 'badge-neutral'}`}>{statusLabels[order.status] || order.status}</span></div><div className="react-order-items">{order.items.map((item) => <div className="react-order-item" key={`${item.variantId}-${item.variantSku}`}><span><strong>{item.productName}</strong><small>{item.variantLabel || item.variantSku} · {item.quantity} unidad(es)</small></span><strong>{formatCurrency(item.subtotal)}</strong></div>)}</div><div className="react-order-totals"><span>Subtotal <strong>{formatCurrency(order.subtotal)}</strong></span><span>Envío <strong>{order.shippingCost > 0 ? formatCurrency(order.shippingCost) : 'Gratis'}</strong></span><span>Total <strong>{formatCurrency(order.total)}</strong></span></div><div className="react-order-payment"><strong>Pago</strong><span>{order.paymentMethodName || 'No informado'}{order.paymentReference ? ` · Ref. ${order.paymentReference}` : ''}</span>{order.paymentProofUrl && <a href={imageUrl(order.paymentProofUrl)} target="_blank" rel="noreferrer"><img src={imageUrl(order.paymentProofUrl)} alt="Comprobante de pago enviado por el cliente" /></a>}{order.confirmedByUsername && <small>Confirmado por {order.confirmedByUsername} · {formatDate(order.confirmedAt)}</small>}{order.cancellationReason && <small>Motivo de cancelación: {order.cancellationReason}</small>}</div>{cancelling && <div className="react-inline-danger"><label className="field"><span className="field-label">Motivo de cancelación</span><textarea className="input" required maxLength={255} rows={3} value={reason} onChange={(event) => setReason(event.target.value)} /></label><div className="react-sale-actions"><button className="btn btn-secondary" type="button" onClick={() => setCancelling(false)}>Volver</button><button className="btn btn-danger" type="button" disabled={saving} onClick={() => void cancelOrder()}>Confirmar cancelación</button></div></div>}</div>}{order && <div className="react-dialog-actions">{order.saleId && <button className="btn btn-secondary" type="button" disabled={printing} onClick={() => void printTicket()}>{printing ? 'Preparando ticket…' : 'Imprimir ticket'}</button>}{order.status === 'PENDING_PAYMENT' && can('PEDIDOS_GESTIONAR') && <button className="btn btn-primary" type="button" disabled={saving} onClick={() => void confirmPayment()}>{saving ? 'Confirmando…' : 'Confirmar pago'}</button>}{order.status !== 'CANCELLED' && can('PEDIDOS_GESTIONAR') && !cancelling && <button className="btn btn-danger" type="button" onClick={() => setCancelling(true)}>Cancelar pedido</button>}<button className="btn btn-secondary" type="button" onClick={onClose}>Cerrar</button></div>}</section></div>;
}
