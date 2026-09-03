import { useState } from 'react';
import type { Product } from '../types';
import { imageUrl } from '../services/api';
import { formatCurrency, resolveProductImage } from '../utils';
import { motion } from 'motion/react';

export function ProductCard({ product }: { product: Product }) {
  const [failed, setFailed] = useState(false);
  const primary = resolveProductImage(product);
  const secondary = product.images?.find((entry) => entry.imageUrl && entry.imageUrl !== primary)?.imageUrl;
  const sale = product.promoPrice != null;
  const price = sale ? product.promoPrice : product.price;
  return <motion.a className="store-product-card" href={`/producto?id=${encodeURIComponent(product.id)}`} whileHover={{ y: -5 }} whileTap={{ scale: .985 }} transition={{ duration: .22 }}>
    <div className="store-product-image">
      <img src={failed ? imageUrl() : imageUrl(primary)} alt={product.name} loading="lazy" onError={() => setFailed(true)} />
      {secondary && <img className="store-product-image-secondary" src={imageUrl(secondary)} alt="" loading="lazy" />}
      {sale && <span className="store-product-tag">-{Math.round((1 - Number(price) / product.price) * 100)}%</span>}
    </div>
    <div className="store-product-body"><span className="store-product-meta">{product.brandName || product.categoryName || ''}</span><span className="store-product-name">{product.name}</span><span className="store-product-price">{sale && <del className="price-old">{formatCurrency(product.price)}</del>}<span>{formatCurrency(price)}</span></span>{!product.inStock && <span className="badge badge-neutral">Agotado</span>}</div>
  </motion.a>;
}
