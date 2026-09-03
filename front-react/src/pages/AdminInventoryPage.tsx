import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { motion } from 'motion/react';
import { AdminShell } from './AdminPagesV2';
import { ApiError, api, getStaffSession } from '../services/api';
import { EmptyState, ErrorState, LoadingState } from '../components/States';
import { showToast } from '../components/ToastHost';
import type { Page } from '../types';
import { formatDate } from '../utils';

type Tab = 'stock' | 'low-stock' | 'out-of-stock' | 'movements';
type InventoryItem = { variantId: number; productName: string; sku: string; barcode?: string | null; variantLabel: string; stock: number; minStock: number; status: string };
type Movement = { id: number; variantId: number; variantSku: string; productName: string; warehouseName?: string | null; type: string; quantity: number; stockBefore: number; stockAfter: number; reason?: string | null; username: string; createdAt: string };
type VariantSearchResult = { variantId: number; productName: string; sku: string; stock: number; effectivePrice?: number };

const movementLabels: Record<string, { label: string; className: string }> = {
  ENTRADA: { label: 'Entrada', className: 'badge-success' },
  SALIDA: { label: 'Salida', className: 'badge-warning' },
  VENTA: { label: 'Venta', className: 'badge-info' },
  DEVOLUCION: { label: 'Devolución', className: 'badge-info' },
  AJUSTE: { label: 'Ajuste', className: 'badge-neutral' },
  MERMA: { label: 'Merma', className: 'badge-danger' },
};

function can(permission: string) { return getStaffSession()?.user.permissions.includes(permission) ?? false; }
function errorMessage(reason: unknown, fallback: string) { return reason instanceof ApiError ? reason.message : fallback; }
function statusText(status: string) { return status === 'ACTIVE' ? 'Activo' : 'Inactivo'; }

export function AdminInventoryPage() {
  const [tab, setTab] = useState<Tab>('stock');
  const [search, setSearch] = useState('');
  const [movementType, setMovementType] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<InventoryItem[] | Movement[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [operation, setOperation] = useState<'entry' | 'exit' | 'adjustment' | null>(null);

  const load = async () => {
    setLoading(true); setError('');
    try {
      if (tab === 'stock') {
        const result = await api.get<Page<InventoryItem>>('/inventory', { auth: 'staff', query: { search: search.trim() || undefined, page, size: 20 } });
        setData(result.content || []); setTotalPages(result.totalPages || 0);
      } else if (tab === 'low-stock') {
        setData(await api.get<InventoryItem[]>('/inventory/low-stock', { auth: 'staff' }) || []); setTotalPages(0);
      } else if (tab === 'out-of-stock') {
        setData(await api.get<InventoryItem[]>('/inventory/out-of-stock', { auth: 'staff' }) || []); setTotalPages(0);
      } else {
        const result = await api.get<Page<Movement>>('/inventory/movements', { auth: 'staff', query: { type: movementType || undefined, page, size: 20, sort: 'createdAt,desc' } });
        setData(result.content || []); setTotalPages(result.totalPages || 0);
      }
    } catch (reason) { setError(errorMessage(reason, 'No se pudo cargar el inventario.')); setData([]); }
    finally { setLoading(false); }
  };

  useEffect(() => { void load(); }, [tab, search, movementType, page]);
  function changeTab(next: Tab) { setTab(next); setPage(0); setError(''); }
  const inventory = data as InventoryItem[];
  const movements = data as Movement[];
  const title = useMemo(() => ({ stock: 'Stock general', 'low-stock': 'Stock bajo', 'out-of-stock': 'Agotados', movements: 'Movimientos' }[tab]), [tab]);

  return <AdminShell title="Inventario" description="Todo cambio de stock queda registrado como un movimiento; nunca se edita directamente." activePage="/admin/inventario">
    <div className="page-actions react-inventory-actions">
      {can('INVENTARIO_AJUSTAR') && <button className="btn btn-secondary" type="button" onClick={() => setOperation('adjustment')}>Ajuste</button>}
      {can('INVENTARIO_SALIDA') && <button className="btn btn-secondary" type="button" onClick={() => setOperation('exit')}>Salida</button>}
      {can('INVENTARIO_ENTRADA') && <button className="btn btn-primary" type="button" onClick={() => setOperation('entry')}>＋ Entrada</button>}
    </div>
    <div className="tabs react-inventory-tabs" role="tablist" aria-label="Vistas de inventario">
      {(['stock', 'low-stock', 'out-of-stock', 'movements'] as Tab[]).map((item) => <button className="tab" type="button" role="tab" key={item} aria-selected={tab === item} onClick={() => changeTab(item)}>{({ stock: 'Stock general', 'low-stock': 'Stock bajo', 'out-of-stock': 'Agotados', movements: 'Movimientos' }[item])}</button>)}
    </div>
    {tab === 'stock' && <div className="filter-bar react-inventory-filters"><label className="topbar-search"><span aria-hidden="true">⌕</span><input type="search" value={search} onChange={(event) => { setSearch(event.target.value); setPage(0); }} placeholder="Buscar por SKU, código de barras o producto…" aria-label="Buscar en inventario" /></label></div>}
    {tab === 'movements' && <div className="filter-bar react-inventory-filters"><select className="select" value={movementType} onChange={(event) => { setMovementType(event.target.value); setPage(0); }} aria-label="Filtrar por tipo de movimiento"><option value="">Todos los tipos</option><option value="ENTRADA">Entrada</option><option value="SALIDA">Salida</option><option value="VENTA">Venta</option><option value="DEVOLUCION">Devolución</option><option value="AJUSTE">Ajuste</option><option value="MERMA">Merma</option></select></div>}
    {error && <ErrorState message={error} />}
    <section className="table-card react-inventory-table"><div className="card-header"><div><span className="field-hint">INVENTARIO</span><h2>{title}</h2></div>{!loading && <span className="badge badge-neutral">{data.length} en esta página</span>}</div>{loading ? <LoadingState label="Cargando inventario…" /> : tab === 'movements' ? <MovementTable rows={movements} /> : <InventoryTable tab={tab} rows={inventory} />}{!loading && totalPages > 0 && <div className="pagination-bar"><button className="btn btn-secondary btn-sm" type="button" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Anterior</button><span>Página {page + 1} de {totalPages}</span><button className="btn btn-secondary btn-sm" type="button" disabled={page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)}>Siguiente</button></div>}</section>
    {operation && <InventoryOperationDialog operation={operation} onClose={() => setOperation(null)} onSaved={() => { setOperation(null); void load(); }} />}
  </AdminShell>;
}

function InventoryTable({ rows, tab }: { rows: InventoryItem[]; tab: Tab }) {
  if (!rows.length) return <EmptyState>{tab === 'low-stock' ? 'No hay variantes con stock bajo.' : tab === 'out-of-stock' ? 'No hay variantes agotadas.' : 'No se encontraron variantes.'}</EmptyState>;
  return <div className="table-scroll"><table className="data-table"><thead><tr><th>Producto</th><th>Variante</th><th>SKU</th>{tab !== 'low-stock' && <th>Código de barras</th>}<th>Stock</th><th>Mínimo</th>{tab === 'low-stock' ? <th>Faltan</th> : <th>Estado</th>}</tr></thead><tbody>{rows.map((item, index) => <motion.tr key={item.variantId} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: Math.min(index * .02, .2) }}><td data-label="Producto" className="table-cell-primary">{item.productName}</td><td data-label="Variante">{item.variantLabel}</td><td data-label="SKU" className="mono">{item.sku}</td>{tab !== 'low-stock' && <td data-label="Código de barras" className="mono">{item.barcode || '—'}</td>}<td data-label="Stock" className={item.stock === 0 ? 'react-stock-empty' : item.stock <= item.minStock ? 'react-stock-warning' : ''}>{item.stock}</td><td data-label="Mínimo">{item.minStock}</td>{tab === 'low-stock' ? <td data-label="Faltan">{Math.max(item.minStock - item.stock, 0)}</td> : <td data-label="Estado"><span className={`badge ${item.status === 'ACTIVE' ? 'badge-success' : 'badge-neutral'}`}>{statusText(item.status)}</span></td>}</motion.tr>)}</tbody></table></div>;
}

function MovementTable({ rows }: { rows: Movement[] }) {
  if (!rows.length) return <EmptyState>Sin movimientos registrados.</EmptyState>;
  return <div className="table-scroll"><table className="data-table"><thead><tr><th>Fecha</th><th>Tipo</th><th>Producto</th><th>Cantidad</th><th>Stock antes → después</th><th>Motivo</th><th>Usuario</th></tr></thead><tbody>{rows.map((item, index) => { const meta = movementLabels[item.type] || { label: item.type, className: 'badge-neutral' }; return <motion.tr key={item.id} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: Math.min(index * .02, .2) }}><td data-label="Fecha" className="table-cell-muted">{formatDate(item.createdAt)}</td><td data-label="Tipo"><span className={`badge ${meta.className}`}>{meta.label}</span></td><td data-label="Producto">{item.productName} <span className="table-cell-muted mono">{item.variantSku}</span></td><td data-label="Cantidad" className={item.quantity > 0 ? 'react-stock-positive mono' : 'react-stock-negative mono'}>{item.quantity > 0 ? '+' : ''}{item.quantity}</td><td data-label="Stock" className="mono">{item.stockBefore} → {item.stockAfter}</td><td data-label="Motivo">{item.reason || '—'}</td><td data-label="Usuario">{item.username}</td></motion.tr>; })}</tbody></table></div>;
}

function InventoryOperationDialog({ operation, onClose, onSaved }: { operation: 'entry' | 'exit' | 'adjustment'; onClose: () => void; onSaved: () => void }) {
  const [query, setQuery] = useState(''); const [results, setResults] = useState<VariantSearchResult[]>([]); const [selected, setSelected] = useState<VariantSearchResult | null>(null); const [quantity, setQuantity] = useState(''); const [reason, setReason] = useState(''); const [error, setError] = useState(''); const [saving, setSaving] = useState(false);
  useEffect(() => { if (query.trim().length < 2) { setResults([]); return; } const timer = window.setTimeout(() => { api.get<VariantSearchResult[]>('/variants/search', { auth: 'staff', query: { q: query.trim() } }).then(setResults).catch(() => setResults([])); }, 300); return () => window.clearTimeout(timer); }, [query]);
  const isAdjustment = operation === 'adjustment'; const isEntry = operation === 'entry'; const actionLabel = isAdjustment ? 'Registrar ajuste' : isEntry ? 'Registrar entrada' : 'Registrar salida';
  async function submit(event: FormEvent) { event.preventDefault(); setError(''); if (!selected) { setError('Selecciona una variante.'); return; } const value = Number(quantity); if (!Number.isInteger(value) || (isAdjustment ? value < 0 : value < 1)) { setError(isAdjustment ? 'El stock real debe ser un entero mayor o igual a 0.' : 'La cantidad debe ser un entero mayor o igual a 1.'); return; } if (!reason.trim() && !isEntry) { setError('Ingresa el motivo.'); return; } setSaving(true); try { const path = isAdjustment ? '/inventory/adjustment' : isEntry ? '/inventory/entry' : '/inventory/exit'; const body = isAdjustment ? { variantId: selected.variantId, newStock: value, reason: reason.trim() } : { variantId: selected.variantId, quantity: value, reason: reason.trim() || undefined }; await api.post(path, body, { auth: 'staff' }); showToast(`${actionLabel} correctamente.`); onSaved(); } catch (reasonValue) { setError(errorMessage(reasonValue, `No se pudo registrar ${isAdjustment ? 'el ajuste' : 'el movimiento'}.`)); } finally { setSaving(false); } }
  return <div className="react-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><section className="react-dialog react-dialog-small" role="dialog" aria-modal="true" aria-labelledby="inventory-dialog-title"><div className="react-dialog-header"><div><span className="field-hint">OPERACIÓN DE STOCK</span><h2 id="inventory-dialog-title">{actionLabel}</h2></div><button className="btn btn-ghost" type="button" onClick={onClose} aria-label="Cerrar">×</button></div>{error && <div className="alert alert-danger" role="alert">{error}</div>}<form className="form-grid" onSubmit={submit} noValidate><div className="field field-span-2"><label className="field-label" htmlFor="inventory-variant-search">Variante</label>{selected ? <div className="react-selected-variant"><div><strong>{selected.productName}</strong><span className="field-hint">{selected.sku} · Stock actual: {selected.stock}</span></div><button className="btn btn-ghost btn-sm" type="button" onClick={() => setSelected(null)}>Cambiar</button></div> : <div className="react-variant-search"><input className="input" id="inventory-variant-search" autoComplete="off" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar por SKU, código o producto…" />{results.length > 0 && <div className="react-variant-results">{results.map((item) => <button type="button" key={item.variantId} onClick={() => { setSelected(item); setQuery(''); setResults([]); }}><strong>{item.productName} · {item.sku}</strong><small>Stock: {item.stock}</small></button>)}</div>}</div>}</div><label className="field"><span className="field-label">{isAdjustment ? 'Stock real contado' : 'Cantidad'}</span><input className="input" required inputMode="numeric" value={quantity} onChange={(event) => setQuantity(event.target.value.replace(/\D/g, '').slice(0, 7))} min={isAdjustment ? 0 : 1} /></label><label className="field"><span className="field-label">Motivo {!isEntry && <small>obligatorio</small>}{isEntry && <small>opcional</small>}</span><input className="input" required={!isEntry} maxLength={255} value={reason} onChange={(event) => setReason(event.target.value.slice(0, 255))} placeholder={isAdjustment ? 'Recuento físico' : isEntry ? 'Compra o reposición' : 'Traslado o muestra'} /></label><div className="react-dialog-actions"><button className="btn btn-secondary" type="button" onClick={onClose}>Cancelar</button><button className="btn btn-primary" disabled={saving}>{saving ? 'Guardando…' : actionLabel}</button></div></form></section></div>;
}
