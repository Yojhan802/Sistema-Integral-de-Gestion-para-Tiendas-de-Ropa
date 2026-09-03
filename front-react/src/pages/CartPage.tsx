import { useEffect, useState } from 'react';
import { imageUrl } from '../services/api';
import { getCart, removeFromCart, updateCartQuantity } from '../services/cart';
import { formatCurrency } from '../utils';
import { EmptyState } from '../components/States';
import { StoreShell } from '../components/StoreShell';
import { useStoreConfig, useStoreTemplate } from '../components/TemplateProvider';
import { CartSurface } from '../templates/CartSurface';
import { igvNotice } from '../services/legal';
import type { CartItem } from '../types';

export function CartPage() {
  const [items, setItems] = useState<CartItem[]>(getCart());
  const template = useStoreTemplate();
  const config = useStoreConfig();

  useEffect(() => {
    const handler = () => setItems(getCart());
    window.addEventListener('qynex-cart-change', handler);
    return () => window.removeEventListener('qynex-cart-change', handler);
  }, []);

  return <StoreShell active="carrito">
    <div className="store-page-heading">
      <span className="store-kicker">TU COMPRA</span>
      <h1>Tu carrito</h1>
      <p>Revisa tus productos antes de continuar.</p>
    </div>
    {items.length === 0 ? <EmptyState>
      <p>Tu carrito est&aacute; vac&iacute;o.</p>
      {/* El legado ofrecía salida hacia el catálogo; el rewrite la había perdido. */}
      <a className="template-submit store-empty-cta" href="/" onClick={(event) => { event.preventDefault(); window.history.pushState({}, '', '/'); window.dispatchEvent(new PopStateEvent('popstate')); }}>Ver cat&aacute;logo</a>
    </EmptyState> : <CartSurface
      template={template}
      items={items}
      formatCurrency={formatCurrency}
      imageUrl={imageUrl}
      onDecrease={(item) => setItems(updateCartQuantity(item.variantId, item.quantity - 1))}
      onIncrease={(item) => setItems(updateCartQuantity(item.variantId, item.quantity + 1))}
      onRemove={(item) => setItems(removeFromCart(item.variantId))}
      taxNotice={igvNotice(config)}
    />}
  </StoreShell>;
}
