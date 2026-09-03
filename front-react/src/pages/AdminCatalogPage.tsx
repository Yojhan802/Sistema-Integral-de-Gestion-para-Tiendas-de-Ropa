import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { AdminShell } from './AdminPagesV2';
import { EmptyState, ErrorState, LoadingState } from '../components/States';
import { ApiError, api, getStaffSession, imageUrl } from '../services/api';
import { showToast } from '../components/ToastHost';

type Status = 'ACTIVE' | 'INACTIVE' | string;
type Category = { id: number; name: string; slug?: string; imageUrl?: string | null; status: Status };
type Subcategory = { id: number; categoryId: number; categoryName: string; name: string; slug?: string; status: Status };
type Brand = { id: number; name: string; status: Status };
type AttributeValue = { id: number; attributeId: number; value: string; hexCode?: string | null; sortOrder: number; status: Status };
type Attribute = { id: number; name: string; inputType: 'LIST' | 'SWATCH' | string; status: Status; values: AttributeValue[] };
type Catalog = { categories: Category[]; subcategories: Subcategory[]; brands: Brand[]; attributes: Attribute[] };
type Tab = 'categorias' | 'subcategorias' | 'marcas' | `attribute:${number}`;
type FormKind = 'category' | 'subcategory' | 'brand' | 'value';

function statusLabel(status: string) { return status === 'ACTIVE' ? 'Activo' : 'Inactivo'; }
function statusClass(status: string) { return status === 'ACTIVE' ? 'badge-success' : 'badge-neutral'; }

function Dialog({ title, subtitle, children, onClose, wide = false }: { title: string; subtitle?: string; children: ReactNode; onClose: () => void; wide?: boolean }) {
  useEffect(() => { const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose(); }; window.addEventListener('keydown', onKeyDown); return () => window.removeEventListener('keydown', onKeyDown); }, [onClose]);
  return <div className="react-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) onClose(); }}><section className={`react-dialog${wide ? ' react-dialog-wide' : ''}`} role="dialog" aria-modal="true" aria-labelledby="react-catalog-dialog-title"><div className="react-dialog-header"><div><h2 id="react-catalog-dialog-title">{title}</h2>{subtitle && <p className="field-hint">{subtitle}</p>}</div><button className="btn btn-ghost btn-sm" type="button" aria-label="Cerrar" onClick={onClose}>×</button></div>{children}</section></div>;
}

function CatalogFormDialog({ kind, item, categories, attribute, onClose, onSaved }: { kind: FormKind; item?: Category | Subcategory | Brand | AttributeValue; categories: Category[]; attribute?: Attribute; onClose: () => void; onSaved: () => void }) {
  const isEdit = Boolean(item);
  const [name, setName] = useState(kind === 'category' || kind === 'brand' ? (item as Category | Brand | undefined)?.name || '' : kind === 'subcategory' ? (item as Subcategory | undefined)?.name || '' : (item as AttributeValue | undefined)?.value || '');
  const [categoryId, setCategoryId] = useState(String((item as Subcategory | undefined)?.categoryId || categories.find((category) => category.status === 'ACTIVE')?.id || ''));
  const [hexCode, setHexCode] = useState((item as AttributeValue | undefined)?.hexCode || '#000000');
  const [sortOrder, setSortOrder] = useState(String((item as AttributeValue | undefined)?.sortOrder ?? 0));
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const label = kind === 'category' ? 'categoría' : kind === 'subcategory' ? 'subcategoría' : kind === 'brand' ? 'marca' : 'valor';
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!name.trim()) { setError('Ingresa un nombre.'); return; }
    if (kind === 'subcategory' && !categoryId) { setError('Selecciona una categoría.'); return; }
    const order = Number(sortOrder);
    if (kind === 'value' && (!Number.isInteger(order) || order < 0)) { setError('El orden debe ser un número entero igual o mayor que 0.'); return; }
    setSaving(true);
    try {
      const payload = kind === 'subcategory' ? { categoryId: Number(categoryId), name: name.trim() } : kind === 'value' ? { value: name.trim(), hexCode: attribute?.inputType === 'SWATCH' ? hexCode : null, sortOrder: order } : { name: name.trim() };
      if (kind === 'category') isEdit ? await api.put(`/categories/${(item as Category).id}`, payload, { auth: 'staff' }) : await api.post('/categories', payload, { auth: 'staff' });
      if (kind === 'subcategory') isEdit ? await api.put(`/subcategories/${(item as Subcategory).id}`, payload, { auth: 'staff' }) : await api.post('/subcategories', payload, { auth: 'staff' });
      if (kind === 'brand') isEdit ? await api.put(`/brands/${(item as Brand).id}`, payload, { auth: 'staff' }) : await api.post('/brands', payload, { auth: 'staff' });
      if (kind === 'value') isEdit ? await api.put(`/attributes/values/${(item as AttributeValue).id}`, payload, { auth: 'staff' }) : await api.post(`/attributes/${attribute!.id}/values`, payload, { auth: 'staff' });
      showToast(`${label[0].toUpperCase()}${label.slice(1)} ${isEdit ? 'actualizada' : 'creada'} correctamente.`);
      onSaved();
      onClose();
    } catch (reason) { setError(reason instanceof ApiError ? reason.message : `No se pudo guardar la ${label}.`); } finally { setSaving(false); }
  }
  return <Dialog title={`${isEdit ? 'Editar' : 'Nueva'} ${label}`} onClose={onClose}><form className="react-catalog-form" onSubmit={submit} noValidate>{error && <div className="alert alert-danger" role="alert">{error}</div>}<div className="form-grid">{kind === 'subcategory' && <label className="field field-span-2"><span className="field-label">Categoría</span><select className="select" required value={categoryId} onChange={(event) => setCategoryId(event.target.value)}><option value="">Selecciona…</option>{categories.filter((category) => category.status === 'ACTIVE' || String(category.id) === categoryId).map((category) => <option value={category.id} key={category.id}>{category.name}</option>)}</select></label>}<label className="field field-span-2"><span className="field-label">{kind === 'value' ? 'Nombre del valor' : 'Nombre'}</span><input className="input" required maxLength={kind === 'value' ? 40 : 80} value={name} onChange={(event) => setName(event.target.value)} /></label>{kind === 'value' && attribute?.inputType === 'SWATCH' && <label className="field field-span-2"><span className="field-label">Color</span><input className="react-color-input" type="color" value={hexCode} onChange={(event) => setHexCode(event.target.value)} /></label>}{kind === 'value' && <label className="field field-span-2"><span className="field-label">Orden</span><input className="input" type="number" min={0} step={1} value={sortOrder} onChange={(event) => setSortOrder(event.target.value)} /></label>}</div><div className="react-dialog-actions"><button className="btn btn-secondary" type="button" onClick={onClose}>Cancelar</button><button className="btn btn-primary" type="submit" disabled={saving}>{saving ? 'Guardando…' : isEdit ? 'Guardar cambios' : 'Crear'}</button></div></form></Dialog>;
}

function AttributeDialog({ onClose, onSaved }: { onClose: () => void; onSaved: (id: number) => void }) {
  const [name, setName] = useState('');
  const [inputType, setInputType] = useState<'LIST' | 'SWATCH'>('LIST');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); if (!name.trim()) { setError('Ingresa el nombre del atributo.'); return; } setSaving(true); try { const created = await api.post<Attribute>('/attributes', { name: name.trim(), inputType }, { auth: 'staff' }); showToast('Atributo creado correctamente.'); onSaved(created.id); onClose(); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'No se pudo crear el atributo.'); } finally { setSaving(false); } }
  return <Dialog title="Nuevo atributo" subtitle="Por ejemplo, Color, Talla, Material o Voltaje." onClose={onClose}><form className="react-catalog-form" onSubmit={submit} noValidate>{error && <div className="alert alert-danger" role="alert">{error}</div>}<label className="field"><span className="field-label">Nombre</span><input className="input" required maxLength={40} value={name} onChange={(event) => setName(event.target.value)} placeholder="Ej. Talla" /></label><label className="field"><span className="field-label">Tipo</span><select className="select" value={inputType} onChange={(event) => setInputType(event.target.value as 'LIST' | 'SWATCH')}><option value="LIST">Lista de texto</option><option value="SWATCH">Muestra de color</option></select></label><div className="react-dialog-actions"><button className="btn btn-secondary" type="button" onClick={onClose}>Cancelar</button><button className="btn btn-primary" type="submit" disabled={saving}>{saving ? 'Guardando…' : 'Crear atributo'}</button></div></form></Dialog>;
}

function CategoryImageDialog({ category, onClose, onSaved }: { category: Category; onClose: () => void; onSaved: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  useEffect(() => { if (!file) { setPreview(category.imageUrl ? imageUrl(category.imageUrl) : ''); return; } const url = URL.createObjectURL(file); setPreview(url); return () => URL.revokeObjectURL(url); }, [category.imageUrl, file]);
  async function saveImage(event: FormEvent<HTMLFormElement>) { event.preventDefault(); if (!file) { setError('Selecciona una imagen.'); return; } setSaving(true); try { const body = new FormData(); body.append('file', file); await api.post(`/categories/${category.id}/image`, body, { auth: 'staff' }); showToast('Imagen de categoría actualizada.'); onSaved(); onClose(); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'No se pudo actualizar la imagen.'); } finally { setSaving(false); } }
  async function deleteImage() { if (!window.confirm(`¿Quitar la imagen de ${category.name}?`)) return; try { await api.delete(`/categories/${category.id}/image`, { auth: 'staff' }); showToast('Imagen de categoría eliminada.'); onSaved(); onClose(); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'No se pudo eliminar la imagen.'); } }
  return <Dialog title={`Imagen de ${category.name}`} subtitle="Usa una imagen horizontal o cuadrada para las tarjetas de categorías." onClose={onClose}><form className="react-catalog-form" onSubmit={saveImage} noValidate>{error && <div className="alert alert-danger" role="alert">{error}</div>}<label className="field"><span className="field-label">Archivo de imagen</span><input className="input" type="file" accept="image/png,image/jpeg,image/webp,image/svg+xml" onChange={(event) => setFile(event.target.files?.[0] || null)} /></label><div className="react-category-image-preview">{preview ? <img src={preview} alt={`Vista previa de ${category.name}`} /> : <span className="field-hint">Todavía no hay imagen.</span>}</div><div className="react-dialog-actions">{category.imageUrl && <button className="btn btn-ghost danger-action" type="button" onClick={() => void deleteImage()}>Quitar imagen</button>}<button className="btn btn-secondary" type="button" onClick={onClose}>Cancelar</button><button className="btn btn-primary" type="submit" disabled={saving || !file}>{saving ? 'Guardando…' : 'Guardar imagen'}</button></div></form></Dialog>;
}

export function AdminCatalogPage() {
  const canEdit = getStaffSession()?.user.permissions.includes('CONFIGURACION_EDITAR') ?? false;
  const [catalog, setCatalog] = useState<Catalog>({ categories: [], subcategories: [], brands: [], attributes: [] });
  const [tab, setTab] = useState<Tab>('categorias');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [dialog, setDialog] = useState<{ kind: FormKind; item?: Category | Subcategory | Brand | AttributeValue; attribute?: Attribute } | null>(null);
  const [attributeDialog, setAttributeDialog] = useState(false);
  const [imageCategory, setImageCategory] = useState<Category | null>(null);
  const activeAttribute = tab.startsWith('attribute:') ? catalog.attributes.find((attribute) => attribute.id === Number(tab.slice(10))) : undefined;
  const activeLabel = tab === 'categorias' ? 'categoría' : tab === 'subcategorias' ? 'subcategoría' : tab === 'marcas' ? 'marca' : activeAttribute?.name || 'valor';

  async function loadCatalog() { setLoading(true); setError(''); try { const [categories, subcategories, brands, attributes] = await Promise.all([api.get<Category[]>('/categories', { auth: 'staff' }), api.get<Subcategory[]>('/subcategories', { auth: 'staff' }), api.get<Brand[]>('/brands', { auth: 'staff' }), api.get<Attribute[]>('/attributes', { auth: 'staff' })]); setCatalog({ categories, subcategories, brands, attributes }); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'No se pudo cargar el catálogo.'); } finally { setLoading(false); } }
  useEffect(() => { void loadCatalog(); }, []);
  const items = useMemo(() => tab === 'categorias' ? catalog.categories : tab === 'subcategorias' ? catalog.subcategories : tab === 'marcas' ? catalog.brands : activeAttribute?.values || [], [activeAttribute, catalog, tab]);

  async function toggleStatus(kind: 'category' | 'subcategory' | 'brand' | 'value', item: Category | Subcategory | Brand | AttributeValue) { const next = item.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'; const path = kind === 'category' ? `/categories/${item.id}/status` : kind === 'subcategory' ? `/subcategories/${item.id}/status` : kind === 'brand' ? `/brands/${item.id}/status` : `/attributes/values/${item.id}/status`; try { await api.patch(path, { status: next }, { auth: 'staff' }); showToast('Estado actualizado correctamente.'); await loadCatalog(); } catch (reason) { showToast(reason instanceof ApiError ? reason.message : 'No se pudo actualizar el estado.', 'Error', 'error'); } }

  function renderRows() {
    if (!items.length) return <tr><td colSpan={tab === 'categorias' ? 4 : tab.startsWith('attribute:') && activeAttribute?.inputType === 'SWATCH' ? 6 : tab === 'marcas' ? 3 : 4}><EmptyState>Todavía no hay {activeLabel}s registradas.</EmptyState></td></tr>;
    return items.map((item) => {
      const isCategory = tab === 'categorias'; const isSubcategory = tab === 'subcategorias'; const isBrand = tab === 'marcas'; const kind: FormKind = isCategory ? 'category' : isSubcategory ? 'subcategory' : isBrand ? 'brand' : 'value';
      const attributeValue = item as AttributeValue;
      return <tr key={item.id}>{isCategory ? <><td data-label="Nombre" className="table-cell-primary">{(item as Category).name}</td><td data-label="Imagen">{(item as Category).imageUrl ? <img className="react-catalog-thumbnail" src={imageUrl((item as Category).imageUrl)} alt="" /> : <span className="table-cell-muted">Sin imagen</span>}</td></> : isSubcategory ? <><td data-label="Categoría">{(item as Subcategory).categoryName}</td><td data-label="Subcategoría" className="table-cell-primary">{(item as Subcategory).name}</td></> : isBrand ? <td data-label="Nombre" className="table-cell-primary">{(item as Brand).name}</td> : activeAttribute?.inputType === 'SWATCH' ? <><td data-label="Color"><span className="react-color-swatch" style={{ background: attributeValue.hexCode || '#000000' }} /></td><td data-label="Nombre" className="table-cell-primary">{attributeValue.value}</td><td data-label="Código" className="mono">{attributeValue.hexCode || '—'}</td><td data-label="Orden">{attributeValue.sortOrder}</td></> : <><td data-label="Nombre" className="table-cell-primary">{attributeValue.value}</td><td data-label="Orden">{attributeValue.sortOrder}</td></>}<td data-label="Estado"><span className={`badge ${statusClass(item.status)}`}>{statusLabel(item.status)}</span></td><td data-label="Acciones"><div className="table-actions">{isCategory && canEdit && <button className="btn btn-ghost btn-sm" type="button" onClick={() => setImageCategory(item as Category)}>Imagen</button>}{canEdit && <><button className="btn btn-ghost btn-sm" type="button" onClick={() => setDialog({ kind, item, attribute: activeAttribute })}>Editar</button><button className="btn btn-ghost btn-sm" type="button" onClick={() => void toggleStatus(kind, item)}>{item.status === 'ACTIVE' ? 'Desactivar' : 'Activar'}</button></>}</div></td></tr>;
    });
  }

  return <AdminShell title="Catálogo" description="Categorías, subcategorías, marcas y atributos disponibles para tus productos." activePage="/admin/productos"><div className="page-actions react-catalog-actions"><a className="btn btn-secondary" href="/admin/productos" onClick={(event) => { event.preventDefault(); window.history.pushState({}, '', '/admin/productos'); window.dispatchEvent(new PopStateEvent('popstate')); }}>Volver a productos</a>{canEdit && <button className="btn btn-primary" type="button" onClick={() => setDialog(tab === 'categorias' ? { kind: 'category' } : tab === 'subcategorias' ? { kind: 'subcategory' } : tab === 'marcas' ? { kind: 'brand' } : { kind: 'value', attribute: activeAttribute })}>+ Nueva {activeLabel}</button>}</div><div className="tabs react-catalog-tabs" role="tablist" aria-label="Secciones del catálogo"><button className="tab" type="button" role="tab" aria-selected={tab === 'categorias'} onClick={() => setTab('categorias')}>Categorías</button><button className="tab" type="button" role="tab" aria-selected={tab === 'subcategorias'} onClick={() => setTab('subcategorias')}>Subcategorías</button><button className="tab" type="button" role="tab" aria-selected={tab === 'marcas'} onClick={() => setTab('marcas')}>Marcas</button>{catalog.attributes.map((attribute) => <button className="tab" type="button" role="tab" aria-selected={tab === `attribute:${attribute.id}`} key={attribute.id} onClick={() => setTab(`attribute:${attribute.id}`)}>{attribute.name}</button>)}{canEdit && <button className="tab react-catalog-new-attribute" type="button" onClick={() => setAttributeDialog(true)}>+ Atributo</button>}</div>{error ? <ErrorState message={error} /> : loading ? <LoadingState label="Cargando catálogo…" /> : <section className="table-card react-catalog-table"><div className="table-scroll"><table className="data-table"><thead><tr>{tab === 'categorias' ? <><th>Nombre</th><th>Imagen</th></> : tab === 'subcategorias' ? <><th>Categoría</th><th>Subcategoría</th></> : tab === 'marcas' ? <th>Nombre</th> : activeAttribute?.inputType === 'SWATCH' ? <><th /><th>Nombre</th><th>Código</th><th>Orden</th></> : <><th>Nombre</th><th>Orden</th></>}<th>Estado</th><th /></tr></thead><tbody>{renderRows()}</tbody></table></div></section>}{dialog && <CatalogFormDialog kind={dialog.kind} item={dialog.item} categories={catalog.categories} attribute={dialog.attribute} onClose={() => setDialog(null)} onSaved={() => void loadCatalog()} />}{attributeDialog && <AttributeDialog onClose={() => setAttributeDialog(false)} onSaved={(id) => { setTab(`attribute:${id}`); void loadCatalog(); }} />}{imageCategory && <CategoryImageDialog category={imageCategory} onClose={() => setImageCategory(null)} onSaved={() => void loadCatalog()} />}</AdminShell>;
}
