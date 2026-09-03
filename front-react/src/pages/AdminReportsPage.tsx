import { motion } from 'motion/react';
import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { ErrorState, LoadingState, EmptyState } from '../components/States';
import { AdminShell } from './AdminPagesV2';
import { ApiError, api, getStaffSession } from '../services/api';
import { formatCurrency, formatDate } from '../utils';

type Range = { from: string; to: string };
type Summary = { count: number; total: number };
type LabelRow = { label: string; total: number; percentage?: number | null };
type ProductRow = { productName: string; units: number; total: number };
type PromoterRow = { promoterName: string; salesCount: number; total: number };
type CashSession = {
  sessionId: number;
  registerName: string;
  openedByUsername: string;
  closedByUsername?: string | null;
  openedAt?: string | null;
  closedAt?: string | null;
  expectedAmount: number;
  countedAmount: number;
  difference: number;
};
type SalesReport = {
  summary: Summary;
  categories: LabelRow[];
  paymentMethods: LabelRow[];
  products: ProductRow[];
  sellers: LabelRow[];
  promoters: PromoterRow[];
  digitalPayments: LabelRow[];
};

const emptySalesReport: SalesReport = {
  summary: { count: 0, total: 0 },
  categories: [],
  paymentMethods: [],
  products: [],
  sellers: [],
  promoters: [],
  digitalPayments: [],
};

function localIsoDate(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function rangeForMonth() {
  const today = new Date();
  return { from: `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-01`, to: localIsoDate(today) };
}

function rangeForYear() {
  const today = new Date();
  return { from: `${today.getFullYear()}-01-01`, to: localIsoDate(today) };
}

function percent(value?: number | null) {
  return value == null ? '—' : `${Number(value).toFixed(1)}%`;
}

function CsvCell({ value }: { value: unknown }) {
  return String(value ?? '').replace(/"/g, '""');
}

function downloadCsv(filename: string, rows: unknown[][]) {
  const csv = `\uFEFF${rows.map((row) => row.map((value) => `"${CsvCell({ value })}"`).join(',')).join('\r\n')}`;
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

function BarList({ rows, labelKey = 'label', valueKey = 'total' }: { rows: Array<Record<string, unknown>>; labelKey?: string; valueKey?: string }) {
  const max = Math.max(...rows.map((row) => Number(row[valueKey]) || 0), 1);
  if (!rows.length) return <EmptyState>Sin datos en este período.</EmptyState>;
  return <div className="viz-bars">{rows.slice(0, 10).map((row, index) => {
    const value = Number(row[valueKey]) || 0;
    return <div className="viz-bar-row" key={`${String(row[labelKey])}-${index}`}><span className="viz-bar-label">{String(row[labelKey] || 'Sin nombre')}</span><span className="viz-bar-track"><span className="viz-bar-fill" style={{ width: `${Math.max(value ? 4 : 0, (value / max) * 100)}%`, background: 'var(--brand-accent)' }} /></span><strong className="viz-bar-value">{formatCurrency(value)}</strong></div>;
  })}</div>;
}

function PaymentBars({ rows }: { rows: LabelRow[] }) {
  const max = Math.max(...rows.map((row) => Number(row.total) || 0), 1);
  if (!rows.length) return <EmptyState>Sin ventas en este período.</EmptyState>;
  return <div className="react-report-payments">{rows.map((row, index) => <div className="react-report-payment" key={`${row.label}-${index}`}><div className="react-report-payment-head"><span>{row.label}</span><strong>{formatCurrency(row.total)}</strong></div><div className="react-report-payment-track"><span style={{ width: `${Math.max(row.total ? 4 : 0, (Number(row.total) / max) * 100)}%` }} /></div><small>{percent(row.percentage)}</small></div>)}</div>;
}

function ReportCard({ title, children }: { title: string; children: React.ReactNode }) {
  return <motion.section className="card react-report-card" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: .22 }}><div className="card-header"><h2>{title}</h2></div>{children}</motion.section>;
}

export function AdminReportsPage() {
  const [tab, setTab] = useState<'ventas' | 'caja'>('ventas');
  const [range, setRange] = useState<Range>({ from: '', to: '' });
  const [draftRange, setDraftRange] = useState<Range>({ from: '', to: '' });
  const [sales, setSales] = useState<SalesReport>(emptySalesReport);
  const [cash, setCash] = useState<CashSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState('');
  const [assistantLoading, setAssistantLoading] = useState(false);
  const canExport = getStaffSession()?.user.permissions.includes('REPORTES_EXPORTAR') ?? false;

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    setError('');
    const query = { from: range.from || undefined, to: range.to || undefined };
    const request = tab === 'ventas'
      ? Promise.all([
        api.get<Summary>('/reports/sales/summary', { auth: 'staff', query }),
        api.get<LabelRow[]>('/reports/sales/by-category', { auth: 'staff', query }),
        api.get<LabelRow[]>('/reports/sales/by-payment-method', { auth: 'staff', query }),
        api.get<ProductRow[]>('/reports/products/top-selling', { auth: 'staff', query: { ...query, limit: 10 } }),
        api.get<LabelRow[]>('/reports/sales/by-seller', { auth: 'staff', query }),
        api.get<PromoterRow[]>('/reports/sales/by-promoter', { auth: 'staff', query }),
        api.get<LabelRow[]>('/reports/payments/non-cash', { auth: 'staff', query }),
      ]).then(([summary, categories, paymentMethods, products, sellers, promoters, digitalPayments]) => {
        if (mounted) setSales({ summary, categories, paymentMethods, products, sellers, promoters, digitalPayments });
      })
      : api.get<CashSession[]>('/reports/cash/sessions', { auth: 'staff', query }).then((rows) => { if (mounted) setCash(rows); });
    request.catch((reason) => { if (mounted) setError(reason instanceof ApiError ? reason.message : `No se pudo cargar el reporte de ${tab}.`); }).finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; };
  }, [range, tab]);

  const activeData = useMemo(() => tab === 'ventas' ? sales : cash, [cash, sales, tab]);

  function applyRange(next: Range) {
    setDraftRange(next);
    setRange(next);
  }

  function exportReport() {
    const suffix = range.from || range.to ? `-${range.from || 'inicio'}-a-${range.to || 'fin'}` : '';
    const filename = `reporte-${tab}${suffix}.csv`;
    if (tab === 'ventas') {
      const rows: unknown[][] = [
        [`Reporte de ventas${suffix}`], [], ['Resumen'], ['Ventas', sales.summary.count], ['Total vendido', sales.summary.total], [],
        ['Pagos digitales'], ['Método', 'Total', '% del período'], ...sales.digitalPayments.map((row) => [row.label, row.total, percent(row.percentage)]), [],
        ['Ventas por categoría'], ['Categoría', 'Total', '% del período'], ...sales.categories.map((row) => [row.label, row.total, percent(row.percentage)]), [],
        ['Ventas por método de pago'], ['Método', 'Total', '% del período'], ...sales.paymentMethods.map((row) => [row.label, row.total, percent(row.percentage)]), [],
        ['Productos más vendidos'], ['Producto', 'Unidades', 'Total'], ...sales.products.map((row) => [row.productName, row.units, row.total]), [],
        ['Ventas por vendedor'], ['Vendedor', 'Total', '% del período'], ...sales.sellers.map((row) => [row.label, row.total, percent(row.percentage)]), [],
        ['Ventas por promotor'], ['Promotor', 'Ventas', 'Total'], ...sales.promoters.map((row) => [row.promoterName, row.salesCount, row.total]),
      ];
      downloadCsv(filename, rows);
    } else {
      downloadCsv(filename, [['Caja', 'Abierta por', 'Cerrada por', 'Cierre', 'Esperado', 'Contado', 'Diferencia'], ...cash.map((row) => [row.registerName, row.openedByUsername, row.closedByUsername || '', row.closedAt || '', row.expectedAmount, row.countedAmount, row.difference])]);
    }
  }

  async function askAssistant(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const prompt = question.trim();
    if (!prompt || assistantLoading) return;
    setAssistantLoading(true);
    setAnswer('');
    try {
      const response = await api.post<{ respuesta: string }>('/reports/assistant/ask', { pregunta: prompt, datos: JSON.stringify(activeData) }, { auth: 'staff' });
      setAnswer(response.respuesta);
    } catch (reason) {
      setAnswer(reason instanceof ApiError ? reason.message : 'No se pudo responder la pregunta.');
    } finally {
      setAssistantLoading(false);
    }
  }

  return <AdminShell title="Reportes" description="Ventas, productos y caja. Los filtros de fecha aplican a todo lo de abajo." activePage="/admin/reportes">
    <div className="page-actions react-reports-actions">
      <button className="btn btn-ghost btn-sm" type="button" onClick={() => applyRange({ from: localIsoDate(), to: localIsoDate() })}>Hoy</button>
      <button className="btn btn-ghost btn-sm" type="button" onClick={() => applyRange(rangeForMonth())}>Este mes</button>
      <button className="btn btn-ghost btn-sm" type="button" onClick={() => applyRange(rangeForYear())}>Este año</button>
      <input className="input" type="date" aria-label="Desde" value={draftRange.from} onChange={(event) => setDraftRange((current) => ({ ...current, from: event.target.value }))} />
      <span className="react-report-range-separator" aria-hidden="true">–</span>
      <input className="input" type="date" aria-label="Hasta" value={draftRange.to} onChange={(event) => setDraftRange((current) => ({ ...current, to: event.target.value }))} />
      <button className="btn btn-secondary" type="button" onClick={() => setRange(draftRange)}>Aplicar</button>
      {canExport && <button className="btn btn-primary" type="button" onClick={exportReport}>Exportar CSV</button>}
    </div>
    <div className="tabs react-report-tabs" role="tablist" aria-label="Tipo de reporte"><button className="tab" type="button" role="tab" aria-selected={tab === 'ventas'} onClick={() => setTab('ventas')}>Ventas</button><button className="tab" type="button" role="tab" aria-selected={tab === 'caja'} onClick={() => setTab('caja')}>Caja</button></div>
    <section className="card react-report-assistant"><div className="card-header"><div><h2>Pregúntale a tus datos</h2><p>Consulta el reporte activo y el rango seleccionado.</p></div></div><form onSubmit={askAssistant}><input className="input" type="text" maxLength={300} value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="Ej.: ¿cuál fue mi producto más vendido?" /><button className="btn btn-primary" type="submit" disabled={assistantLoading}>{assistantLoading ? 'Pensando…' : 'Preguntar'}</button></form>{answer && <p className="react-report-answer" role="status">{answer}</p>}</section>
    {error ? <ErrorState message={error} /> : loading ? <LoadingState label="Cargando reporte…" /> : tab === 'ventas' ? <>
      <section className="stat-grid react-report-stats"><article className="card stat-card"><span className="stat-label">Ventas en el período</span><strong className="stat-value">{sales.summary.count}</strong></article><article className="card stat-card"><span className="stat-label">Total vendido</span><strong className="stat-value">{formatCurrency(sales.summary.total)}</strong></article></section>
      <div className="chart-grid"><ReportCard title="Ventas por categoría"><BarList rows={sales.categories as unknown as Array<Record<string, unknown>>} /></ReportCard><ReportCard title="Métodos de pago"><PaymentBars rows={sales.paymentMethods} /></ReportCard></div>
      <div className="chart-grid-secondary"><ReportCard title="Productos más vendidos"><BarList rows={sales.products.map((row) => ({ label: row.productName, total: row.total }))} /></ReportCard><ReportCard title="Ventas por vendedor"><BarList rows={sales.sellers as unknown as Array<Record<string, unknown>>} /></ReportCard></div>
      {sales.promoters.length > 0 && <section className="card react-report-table-card"><div className="card-header"><div><h2>Ventas por promotor</h2><p>Cuántas ventas ayudó a cerrar cada promotor.</p></div></div><div className="table-scroll"><table className="data-table"><thead><tr><th>Promotor</th><th>Ventas</th><th>Total</th></tr></thead><tbody>{sales.promoters.map((row) => <tr key={row.promoterName}><td data-label="Promotor" className="table-cell-primary">{row.promoterName}</td><td data-label="Ventas">{row.salesCount}</td><td data-label="Total" className="mono">{formatCurrency(row.total)}</td></tr>)}</tbody></table></div></section>}
      {sales.digitalPayments.length > 0 && <section className="card react-report-table-card"><div className="card-header"><div><h2>Pagos digitales</h2><p>Yape, Plin, transferencia y otros pagos no efectivos.</p></div></div><div className="table-scroll"><table className="data-table"><thead><tr><th>Método</th><th>Total</th><th>% del período</th></tr></thead><tbody>{sales.digitalPayments.map((row) => <tr key={row.label}><td data-label="Método" className="table-cell-primary">{row.label}</td><td data-label="Total" className="mono">{formatCurrency(row.total)}</td><td data-label="Porcentaje">{percent(row.percentage)}</td></tr>)}</tbody></table></div></section>}
    </> : <section className="table-card react-report-table-card"><div className="table-scroll"><table className="data-table"><thead><tr><th>Caja</th><th>Abierta por</th><th>Cerrada por</th><th>Cierre</th><th>Esperado</th><th>Contado</th><th>Diferencia</th></tr></thead><tbody>{cash.length ? cash.map((row) => <tr key={row.sessionId}><td data-label="Caja" className="table-cell-primary">{row.registerName}</td><td data-label="Abierta por">{row.openedByUsername}</td><td data-label="Cerrada por">{row.closedByUsername || '—'}</td><td data-label="Cierre" className="table-cell-muted">{formatDate(row.closedAt)}</td><td data-label="Esperado" className="mono">{formatCurrency(row.expectedAmount)}</td><td data-label="Contado" className="mono">{formatCurrency(row.countedAmount)}</td><td data-label="Diferencia" className={`mono ${row.difference === 0 ? 'react-stock-positive' : 'react-stock-negative'}`}>{formatCurrency(row.difference)}</td></tr>) : <tr><td colSpan={7}><EmptyState>Sin cierres de caja en este período.</EmptyState></td></tr>}</tbody></table></div></section>}
  </AdminShell>;
}
