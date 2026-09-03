import { useEffect, useState } from 'react';
import { AdminShell } from './AdminPagesV2';
import { EmptyState, ErrorState, LoadingState } from '../components/States';
import { ApiError, api } from '../services/api';
import type { Page } from '../types';
import { formatDate } from '../utils';

type AuditResult = 'SUCCESS' | 'DENIED' | 'FAILURE' | string;
type AuditSummary = { id: number; userId?: number | null; username?: string | null; action: string; entity: string; entityId?: number | null; result: AuditResult; createdAt: string };
type AuditDetail = AuditSummary & { oldValue?: string | null; newValue?: string | null; ipAddress?: string | null; userAgent?: string | null };
type UserOption = { id: number; fullName: string };
type AuditFilters = { userId: string; action: string; entity: string; result: string; from: string; to: string };

const initialFilters: AuditFilters = { userId: '', action: '', entity: '', result: '', from: '', to: '' };
const resultLabels: Record<string, string> = { SUCCESS: 'Éxito', DENIED: 'Denegado', FAILURE: 'Falla' };

function resultClass(result: string) {
  return result === 'SUCCESS' ? 'badge-success' : result === 'DENIED' ? 'badge-warning' : result === 'FAILURE' ? 'badge-danger' : 'badge-neutral';
}

function resultLabel(result: string) {
  return resultLabels[result] || result;
}

function parseJson(value?: string | null) {
  if (!value) return '';
  try { return JSON.stringify(JSON.parse(value), null, 2); } catch { return value; }
}

function PageControls({ page, onPage }: { page: Page<unknown>; onPage: (value: number) => void }) {
  const current = page.number ?? 0;
  const total = page.totalPages ?? 0;
  if (total <= 1) return null;
  return <div className="pagination-bar"><button className="btn btn-secondary btn-sm" type="button" disabled={current === 0} onClick={() => onPage(current - 1)}>Anterior</button><span>Página {current + 1} de {total}</span><button className="btn btn-secondary btn-sm" type="button" disabled={current + 1 >= total} onClick={() => onPage(current + 1)}>Siguiente</button></div>;
}

function AuditDetailDialog({ entry, onClose }: { entry: AuditDetail; onClose: () => void }) {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose]);
  const oldValue = parseJson(entry.oldValue);
  const newValue = parseJson(entry.newValue);
  return <div className="react-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) onClose(); }}><section className="react-dialog react-dialog-wide react-audit-dialog" role="dialog" aria-modal="true" aria-labelledby="audit-detail-title"><div className="react-dialog-header"><div><span className="field-hint">DETALLE DE AUDITORÍA</span><h2 id="audit-detail-title">Registro #{entry.id}</h2></div><button className="btn btn-ghost btn-sm" type="button" aria-label="Cerrar detalle" onClick={onClose}>×</button></div><div className="react-audit-meta"><p><strong>Usuario</strong><span>{entry.username || '—'}{entry.userId ? ` (#${entry.userId})` : ''}</span></p><p><strong>Acción</strong><span className="mono">{entry.action}</span></p><p><strong>Entidad</strong><span>{entry.entity}{entry.entityId ? ` #${entry.entityId}` : ''}</span></p><p><strong>Resultado</strong><span><span className={`badge ${resultClass(entry.result)}`}>{resultLabel(entry.result)}</span></span></p><p><strong>Fecha</strong><span>{formatDate(entry.createdAt)}</span></p><p><strong>IP</strong><span className="mono">{entry.ipAddress || '—'}</span></p>{entry.userAgent && <p className="react-audit-meta-wide"><strong>Navegador</strong><span>{entry.userAgent}</span></p>}</div>{oldValue && <div className="react-audit-json"><span className="field-label">Valor anterior</span><pre>{oldValue}</pre></div>}{newValue && <div className="react-audit-json"><span className="field-label">Valor nuevo</span><pre>{newValue}</pre></div>}{!oldValue && !newValue && <EmptyState>Esta acción no guardó un detalle adicional.</EmptyState>}<div className="react-dialog-actions"><button className="btn btn-secondary" type="button" onClick={onClose}>Cerrar</button></div></section></div>;
}

export function AdminAuditPage() {
  const [filters, setFilters] = useState<AuditFilters>(initialFilters);
  const [pageNumber, setPageNumber] = useState(0);
  const [users, setUsers] = useState<UserOption[]>([]);
  const [page, setPage] = useState<Page<AuditSummary> | null>(null);
  const [selected, setSelected] = useState<AuditDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let mounted = true;
    api.get<Page<UserOption>>('/users', { auth: 'staff', query: { size: 100, sort: 'fullName,asc' } }).then((response) => { if (mounted) setUsers(response.content || []); }).catch(() => undefined);
    return () => { mounted = false; };
  }, []);

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    setError('');
    const query = { userId: filters.userId ? Number(filters.userId) : undefined, action: filters.action || undefined, entity: filters.entity || undefined, result: filters.result || undefined, from: filters.from || undefined, to: filters.to || undefined, page: pageNumber, size: 20, sort: 'createdAt,desc' };
    api.get<Page<AuditSummary>>('/audit', { auth: 'staff', query }).then((response) => { if (mounted) setPage(response); }).catch((reason) => { if (mounted) setError(reason instanceof ApiError ? reason.message : 'No se pudo cargar la auditoría.'); }).finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; };
  }, [filters, pageNumber]);

  function updateFilter<K extends keyof AuditFilters>(key: K, value: AuditFilters[K]) {
    setFilters((current) => ({ ...current, [key]: value }));
    setPageNumber(0);
  }

  async function openDetail(id: number) {
    try { setSelected(await api.get<AuditDetail>(`/audit/${id}`, { auth: 'staff' })); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'No se pudo cargar el detalle.'); }
  }

  return <AdminShell title="Auditoría" description="Registro de operaciones sensibles del sistema. Solo lectura: no se puede editar ni borrar desde aquí." activePage="/admin/auditoria">
    <section className="filter-bar react-audit-filters"><label className="field"><span className="field-label">Acción</span><input className="input" value={filters.action} onChange={(event) => updateFilter('action', event.target.value)} placeholder="Ej.: CREADO" /></label><label className="field"><span className="field-label">Entidad</span><input className="input" value={filters.entity} onChange={(event) => updateFilter('entity', event.target.value)} placeholder="Ej.: PRODUCTO" /></label><label className="field"><span className="field-label">Usuario</span><select className="select" value={filters.userId} onChange={(event) => updateFilter('userId', event.target.value)}><option value="">Todos los usuarios</option>{users.map((user) => <option value={user.id} key={user.id}>{user.fullName}</option>)}</select></label><label className="field"><span className="field-label">Resultado</span><select className="select" value={filters.result} onChange={(event) => updateFilter('result', event.target.value)}><option value="">Todos los resultados</option><option value="SUCCESS">Éxito</option><option value="DENIED">Denegado</option><option value="FAILURE">Falla</option></select></label><label className="field"><span className="field-label">Desde</span><input className="input" type="datetime-local" value={filters.from} onChange={(event) => updateFilter('from', event.target.value)} /></label><label className="field"><span className="field-label">Hasta</span><input className="input" type="datetime-local" value={filters.to} onChange={(event) => updateFilter('to', event.target.value)} /></label></section>
    <section className="table-card react-audit-table">{error ? <ErrorState message={error} /> : loading ? <LoadingState label="Cargando auditoría…" /> : page?.content?.length ? <div className="table-scroll"><table className="data-table"><thead><tr><th>Fecha</th><th>Usuario</th><th>Acción</th><th>Entidad</th><th>Resultado</th><th /></tr></thead><tbody>{page.content.map((entry) => <tr key={entry.id}><td data-label="Fecha" className="table-cell-muted">{formatDate(entry.createdAt)}</td><td data-label="Usuario">{entry.username || '—'}</td><td data-label="Acción" className="mono">{entry.action}</td><td data-label="Entidad">{entry.entity}{entry.entityId ? ` #${entry.entityId}` : ''}</td><td data-label="Resultado"><span className={`badge ${resultClass(entry.result)}`}>{resultLabel(entry.result)}</span></td><td data-label="Acciones"><button className="btn btn-ghost btn-sm" type="button" onClick={() => void openDetail(entry.id)}>Ver detalle</button></td></tr>)}</tbody></table></div> : <EmptyState>No se encontraron registros.</EmptyState>}{page && <PageControls page={page} onPage={setPageNumber} />}</section>
    {selected && <AuditDetailDialog entry={selected} onClose={() => setSelected(null)} />}
  </AdminShell>;
}
