import type { ElectronicDocument, Order, StoreTemplate } from '../types';

export interface OrdersSurfaceProps {
  template: StoreTemplate;
  orders: Order[];
  selected: Order | null;
  labels: Record<string, string>;
  formatCurrency: (value: number) => string;
  formatDate: (value: string) => string;
  imageUrl: (value?: string | null) => string;
  onOpen: (order: Order) => void;
  onClose: () => void;
  uploading: boolean;
  onUploadProof: (file: File) => void;
  documents: ElectronicDocument[];
  documentsLoading: boolean;
  onDownloadDocument: (document: ElectronicDocument, resource: 'pdf' | 'xml' | 'cdr') => void;
}

function statusClass(status: string) {
  if (status === 'CONFIRMED') return 'is-confirmed';
  if (status === 'CANCELLED') return 'is-cancelled';
  return 'is-pending';
}

function documentLabel(type: string) {
  if (type === 'FACTURA') return 'Factura electrónica';
  if (type === 'BOLETA') return 'Boleta electrónica';
  if (type === 'NOTA_CREDITO') return 'Nota de crédito electrónica';
  if (type === 'NOTA_DEBITO') return 'Nota de débito electrónica';
  return 'Documento electrónico';
}

function documentStatusLabel(status: string) {
  if (status === 'ACCEPTED') return 'Aceptado por el proveedor';
  if (status === 'PENDING' || status === 'SENT') return 'En validación del proveedor';
  if (status === 'REJECTED') return 'Rechazado por el proveedor';
  if (status === 'ERROR') return 'Requiere revisión';
  return 'Preparando emisión';
}

export function OrdersSurface({ template, orders, selected, labels, formatCurrency, formatDate, imageUrl, onOpen, onClose, uploading, onUploadProof, documents, documentsLoading, onDownloadDocument }: OrdersSurfaceProps) {
  return <div className={`template-orders template-orders-${template.toLowerCase()}`} data-template-surface="orders">
    <div className="template-orders-list" aria-label="Lista de pedidos">
      {orders.map((order) => <button className="template-order-card" type="button" key={order.id} onClick={() => onOpen(order)}>
        <span className="template-order-card-main"><strong>{order.orderNumber}</strong><small>{formatDate(order.createdAt)}</small></span>
        <span className={`template-order-status ${statusClass(order.status)}`}>{labels[order.status] || order.status}</span>
        <strong className="template-order-card-total">{formatCurrency(order.total)}</strong>
      </button>)}
    </div>
    {selected && <div className="template-order-drawer" role="dialog" aria-modal="true" aria-label={`Detalle ${selected.orderNumber}`}>
      <div className="template-order-drawer-panel">
        <button className="template-dialog-close" type="button" onClick={onClose} aria-label="Cerrar">&times;</button>
        <span className="template-panel-kicker">DETALLE DEL PEDIDO</span>
        <h2>{selected.orderNumber}</h2>
        <div className="template-order-detail-meta">
          <p><strong>Entrega:</strong> {[selected.address, selected.district, selected.province, selected.department].filter(Boolean).join(', ') || 'No registrada'}</p>
          <p><strong>M&eacute;todo de pago:</strong> {selected.paymentMethodName || 'No registrado'}</p>
          <p><strong>Comprobante solicitado:</strong> {selected.billingDocumentType === 'FACTURA' ? 'Factura electr&oacute;nica' : selected.billingDocumentType === 'BOLETA' ? 'Boleta electr&oacute;nica' : 'Constancia interna de venta'}</p>
          {selected.billingDocumentNumber && <p><strong>Documento:</strong> {selected.billingDocumentNumber}{selected.billingName ? ` · ${selected.billingName}` : ''}</p>}
          {selected.confirmedAt && <p><strong>Confirmado:</strong> {formatDate(selected.confirmedAt)}</p>}
          {selected.status === 'CANCELLED' && selected.cancellationReason && <p><strong>Motivo de anulaci&oacute;n:</strong> {selected.cancellationReason}</p>}
        </div>
        <div className="template-order-lines">
          {selected.items?.map((item, index) => <div className="template-order-line" key={`${item.productName}-${index}`}><span>{item.productName} ({item.variantLabel || 'Producto'}) &times; {item.quantity}</span><strong>{formatCurrency(item.subtotal)}</strong></div>)}
        </div>
        <div className="template-total-line"><span>Subtotal</span><strong>{formatCurrency(selected.subtotal ?? selected.total)}</strong></div>
        <div className="template-total-line"><span>Envi&oacute;</span><strong>{selected.shippingCost ? formatCurrency(selected.shippingCost) : 'Gratis'}</strong></div>
        <div className="template-grand-total"><span>Total</span><strong>{formatCurrency(selected.total)}</strong></div>
        <div className="template-order-proof">
          {selected.paymentProofUrl ? <><span className="template-field-label">Comprobante de pago</span><a href={imageUrl(selected.paymentProofUrl)} target="_blank" rel="noopener"><img className="template-proof-image" src={imageUrl(selected.paymentProofUrl)} alt="Comprobante de pago" /></a></> : selected.status === 'PENDING_PAYMENT' && <label className="template-field"><span>Comprobante de pago (opcional)</span><input type="file" accept="image/png,image/jpeg,image/webp" disabled={uploading} onChange={(event) => { const file = event.target.files?.[0]; if (file) onUploadProof(file); }} />{uploading && <small>Subiendo comprobante...</small>}</label>}
        </div>
        <section className="template-order-documents" aria-label="Comprobantes electrónicos">
          <span className="template-field-label">Comprobantes electrónicos</span>
          {documentsLoading ? <small>Consultando el estado del proveedor...</small> : documents.length === 0 ? <small>{selected.billingDocumentType === 'TICKET' ? 'Esta compra tiene una constancia interna de venta.' : 'El comprobante todavía no está disponible.'}</small> : documents.map((document) => <article className="template-order-document" key={document.id}>
            <div className="template-order-document-heading"><strong>{documentLabel(document.documentType)}</strong><span className={`template-document-status is-${document.status.toLowerCase()}`}>{documentStatusLabel(document.status)}</span></div>
            <small>{document.series && document.documentNumber ? `${document.series}-${document.documentNumber}` : 'Número pendiente'} · {formatCurrency(document.amount)}</small>
            {document.status === 'ACCEPTED' ? <div className="template-document-actions"><button type="button" onClick={() => onDownloadDocument(document, 'pdf')}>PDF</button><button type="button" onClick={() => onDownloadDocument(document, 'xml')}>XML</button><button type="button" onClick={() => onDownloadDocument(document, 'cdr')}>CDR</button></div> : document.cdrMessage && <small className="template-document-message">{document.cdrMessage}</small>}
          </article>)}
        </section>
      </div>
    </div>}
  </div>;
}
