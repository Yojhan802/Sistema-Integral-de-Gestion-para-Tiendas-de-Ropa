import { useEffect, useMemo, useState } from 'react';
import { storeApi, imageUrl } from '../services/api';
import { addToCart } from '../services/cart';
import type { Product, ProductAttribute } from '../types';
import { formatCurrency } from '../utils';
import { ErrorState, LoadingState } from '../components/States';
import { StoreShell } from '../components/StoreShell';
import { showToast } from '../components/ToastHost';
import { connectCatalogUpdates } from '../services/live';
import { useStoreConfig } from '../components/TemplateProvider';
import { igvNotice } from '../services/legal';

type ProductVariant = NonNullable<Product['variants']>[number];

function attributeGroups(product: Product) {
  const first = product.variants?.find((variant) => variant.attributes?.length)?.attributes ?? [];
  return first.map((attribute) => {
    const values = new Map<number, ProductAttribute>();
    product.variants?.forEach((variant) => variant.attributes?.filter((item) => item.attributeId === attribute.attributeId)
      .forEach((item) => values.set(item.attributeValueId, item)));
    return { ...attribute, values: [...values.values()] };
  });
}

function hasAttributeValue(variant: ProductVariant, attributeId: number, attributeValueId: number) {
  return variant.attributes?.some((item) => item.attributeId === attributeId && item.attributeValueId === attributeValueId) ?? false;
}

function selectionFromVariant(variant?: ProductVariant): Record<number, number> {
  return Object.fromEntries((variant?.attributes ?? []).map((item) => [item.attributeId, item.attributeValueId])) as Record<number, number>;
}

export function ProductPage() {
  const id = useMemo(() => new URLSearchParams(window.location.search).get('id'), []);
  const taxNotice = igvNotice(useStoreConfig());
  const [product, setProduct] = useState<Product | null>(null);
  const [selectedAttributes, setSelectedAttributes] = useState<Record<number, number>>({});
  const [selectedVariantId, setSelectedVariantId] = useState<number | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [gallery, setGallery] = useState(0);
  const [message, setMessage] = useState('');
  const [imageFailed, setImageFailed] = useState(false);
  const [catalogRevision, setCatalogRevision] = useState(0);

  useEffect(() => {
    const stream = connectCatalogUpdates(() => setCatalogRevision((value) => value + 1));
    return () => stream.close();
  }, []);

  useEffect(() => {
    if (!id) return;
    storeApi.get<Product>(`/store/catalog/products/${id}`).then((value) => {
      const initialVariant = value.variants?.find((variant) => variant.inStock !== false) ?? value.variants?.[0];
      setProduct(value);
      setSelectedAttributes(selectionFromVariant(initialVariant));
      setSelectedVariantId(initialVariant?.variantId ?? null);
      setQuantity(1);
      setGallery(0);
      setImageFailed(false);
      setMessage('');
    }).catch((error) => setMessage(error instanceof Error ? error.message : 'No se pudo cargar el producto.'));
  }, [id, catalogRevision]);

  const groups = useMemo(() => product ? attributeGroups(product) : [], [product]);
  const selectedVariant = useMemo(() => product?.variants?.find((variant) => {
    if (!groups.length) return variant.variantId === selectedVariantId;
    return groups.every((group) => selectedAttributes[group.attributeId] !== undefined
      && hasAttributeValue(variant, group.attributeId, selectedAttributes[group.attributeId]));
  }), [groups, product, selectedAttributes, selectedVariantId]);
  const images = product ? [...new Set([product.imageUrl, ...[...(product.images ?? [])]
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)).map((entry) => entry.imageUrl)]
    .filter(Boolean) as string[])] : [];
  const price = selectedVariant?.price ?? product?.promoPrice ?? product?.price ?? 0;
  const canBuy = product ? (product.variants?.length
    ? Boolean(selectedVariant?.variantId && selectedVariant.inStock !== false)
    : product.inStock !== false) : false;

  if (!product && !message) return <StoreShell><LoadingState label="Cargando producto..." /></StoreShell>;
  if (!product) return <StoreShell><ErrorState message={message || 'No se pudo cargar el producto'} /></StoreShell>;

  const chooseAttribute = (group: { attributeId: number }, value: number, index: number) => {
    setSelectedAttributes((current) => {
      const next = { ...current, [group.attributeId]: value };
      groups.slice(index + 1).forEach((item) => delete next[item.attributeId]);
      return next;
    });
    setMessage('');
  };

  return <StoreShell>
    <div className="store-detail store-product-detail">
      <div className="store-detail-gallery store-product-gallery">
        <div className="store-product-image store-detail-main-image store-product-detail-main">
          <img src={imageFailed ? imageUrl() : imageUrl(images[gallery])} alt={product.name} onError={() => setImageFailed(true)} />
        </div>
        {images.length > 1 && <div className="store-detail-thumbnails store-product-thumbs" role="list" aria-label="Imagenes del producto">
          {images.map((src, index) => <button type="button" key={`${src}-${index}`} className={`store-detail-thumbnail ${gallery === index ? 'is-selected is-active' : ''}`} onClick={() => { setGallery(index); setImageFailed(false); }} aria-label={`Ver imagen ${index + 1}`}>
            <img src={imageUrl(src)} alt="" onError={(event) => { event.currentTarget.src = imageUrl(); }} />
          </button>)}
        </div>}
      </div>
      <section className="store-detail-panel store-product-info">
        <span className="store-product-meta">{product.brandName || product.categoryName || ''}</span>
        <h1>{product.name}</h1>
        <div className="store-product-price store-product-detail-price">{formatCurrency(price)}</div>
        {/* Precio final: el comprador debe ver que el impuesto ya está incluido. */}
        {taxNotice && <p className="store-product-tax-notice">{taxNotice}</p>}
        {product.description && <p className="store-product-description">{product.description}</p>}
        {(product.material || product.fit) && <div className="store-product-specs"><span>{product.material && <>Material: <strong>{product.material}</strong></>}</span><span>{product.fit && <>Calce: <strong>{product.fit}</strong></>}</span></div>}
        {groups.length ? groups.map((group, index) => <div className="field store-variant-picker" key={group.attributeId}>
          <div className="field-label-row"><span className="field-label">{group.attributeName}</span>{index === 0 && product.sizeGuideImageUrl && <a href={imageUrl(product.sizeGuideImageUrl)} target="_blank" rel="noopener">Guia de tallas</a>}</div>
          <div className="store-swatches" aria-label={`Opciones de ${group.attributeName}`}>
            {group.values.map((value) => {
              const compatibleWithPrevious = (variant: ProductVariant) => index === 0 || groups.slice(0, index).every((previous) => {
                const selectedValue = selectedAttributes[previous.attributeId];
                return selectedValue === undefined || hasAttributeValue(variant, previous.attributeId, selectedValue);
              });
              const available = product.variants?.some((variant) => hasAttributeValue(variant, group.attributeId, value.attributeValueId)
                && compatibleWithPrevious(variant) && variant.inStock !== false) ?? false;
              const selected = selectedAttributes[group.attributeId] === value.attributeValueId;
              return <button type="button" className={`store-swatch ${selected ? 'is-selected' : ''} ${!available ? 'is-sold-out' : ''}`} key={value.attributeValueId} disabled={!available} aria-pressed={selected} aria-label={`${value.value}${available ? '' : ' - Agotado'}`} onClick={() => chooseAttribute(group, value.attributeValueId, index)}>{value.inputType === 'SWATCH' && value.hexCode && <span className="store-swatch-dot" style={{ backgroundColor: value.hexCode }} aria-hidden="true" />}<span className="store-swatch-label">{value.value}</span>{!available && <small className="store-swatch-status">Agotado</small>}</button>;
            })}
          </div>
        </div>) : product.variants?.length ? <fieldset className="store-variant-picker"><legend>Elige una variante</legend><div className="store-swatches">{product.variants.map((variant) => { const available = variant.inStock !== false; const selected = selectedVariant?.variantId === variant.variantId; return <button type="button" className={`store-swatch ${selected ? 'is-selected' : ''} ${!available ? 'is-sold-out' : ''}`} key={variant.variantId} disabled={!available} aria-pressed={selected} aria-label={`${variant.variantLabel || `Opcion ${variant.variantId}`}${available ? '' : ' - Agotado'}`} onClick={() => { setSelectedVariantId(variant.variantId); setMessage(''); }}><span className="store-swatch-label">{variant.variantLabel || `Opcion ${variant.variantId}`}</span>{!available && <small className="store-swatch-status">Agotado</small>}</button>; })}</div></fieldset> : null}
        {!canBuy && product.variants?.length ? <p className="store-stock-message" role="status">La combinacion seleccionada esta agotada.</p> : null}
        <div className="store-purchase-row"><label>Cantidad<input className="input" type="number" min="1" max="99" value={quantity} onChange={(event) => setQuantity(Math.max(1, Number(event.target.value) || 1))} /></label><button className="btn btn-primary" type="button" disabled={!canBuy} onClick={() => { const variantId = selectedVariant?.variantId ?? product.id; addToCart({ variantId, productId: product.id, productName: product.name, variantLabel: selectedVariant?.variantLabel, unitPrice: Number(price), imageUrl: product.imageUrl }, quantity); setMessage('Producto agregado al carrito'); showToast('Producto agregado al carrito.'); }}>Agregar al carrito</button></div>
        {message && <p className="store-inline-success" role="status">{message}</p>}
      </section>
    </div>
  </StoreShell>;
}
