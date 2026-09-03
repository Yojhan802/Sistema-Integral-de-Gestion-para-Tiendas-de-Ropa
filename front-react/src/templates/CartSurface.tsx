import type { CartItem, StoreTemplate } from '../types';

export interface CartSurfaceProps {
  template: StoreTemplate;
  items: CartItem[];
  formatCurrency: (value: number) => string;
  imageUrl: (value?: string | null) => string;
  onDecrease: (item: CartItem) => void;
  onIncrease: (item: CartItem) => void;
  onRemove: (item: CartItem) => void;
  taxNotice: string | null;
}

export function CartSurface({ template, items, formatCurrency, imageUrl, onDecrease, onIncrease, onRemove, taxNotice }: CartSurfaceProps) {
  const subtotal = items.reduce((total, item) => total + item.unitPrice * item.quantity, 0);
  return <div className={`template-cart template-cart-${template.toLowerCase()}`} data-template-surface="cart">
    <section className="template-cart-items" id="cart-items" aria-label="Productos del carrito">
      {items.map((item) => <article className="template-cart-item" key={item.variantId}>
        <img src={imageUrl(item.imageUrl)} alt={item.productName} />
        <div className="template-cart-product"><span className="template-cart-meta">{item.productName}</span><h2>{item.productName}</h2><span className="template-cart-variant">{item.variantLabel || 'Producto'}</span><span className="template-cart-unit-price">{formatCurrency(item.unitPrice)}</span><div className="template-cart-controls"><button type="button" onClick={() => onDecrease(item)} aria-label={`Reducir cantidad de ${item.productName}`}>−</button><span aria-live="polite">{item.quantity}</span><button type="button" onClick={() => onIncrease(item)} aria-label={`Aumentar cantidad de ${item.productName}`}>+</button></div><button className="template-cart-remove" type="button" onClick={() => onRemove(item)}>Quitar</button></div>
        <strong className="template-cart-line-total">{formatCurrency(item.unitPrice * item.quantity)}</strong>
      </article>)}
    </section>
    <aside className="template-cart-summary" id="cart-summary"><div className="template-total-line"><span>Subtotal</span><strong>{formatCurrency(subtotal)}</strong></div><p className="template-summary-note">El costo de envío se calcula en el siguiente paso.</p><div className="template-grand-total"><span>Total</span><strong>{formatCurrency(subtotal)}</strong></div>{taxNotice && <p className="template-tax-notice">{taxNotice}</p>}<a className="template-submit" href="/checkout">Continuar al pago</a></aside>
  </div>;
}
