import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { PlatformShell } from '../components/PlatformShell';
import { EmptyState, ErrorState, LoadingState } from '../components/States';
import { ApiError, api, getStaffSession } from '../services/api';
import { showToast } from '../components/ToastHost';
import { ModulePicker, ModulesDialog, seleccionDePreset, totalDe, type Modulo, type ModuloCode, type Seleccion } from '../components/ModulesDialog';
import { OperatorsDialog } from '../components/OperatorsDialog';
import { RenewDialog } from '../components/RenewDialog';

type Tenant = { id: number; slug: string; name: string; ruc?: string | null; address?: string | null; phone?: string | null; email?: string | null; businessVertical: 'CLOTHING' | 'GENERAL' | string; plan: 'STARTER' | 'PROFESIONAL' | 'ECOMMERCE' | 'IA' | string; subscriptionStatus: 'ACTIVA' | 'SUSPENDIDA' | string; billable?: boolean; nextPaymentDue?: string | null; ownerUsername?: string | null; activeUsers: number; monthlyTotal?: number | null; moduleCount?: number; updatedAt?: string | null };
type CreatedTenant = { tenant: Tenant; ownerUsername: string; temporaryPassword: string };

const planLabels: Record<string, string> = { STARTER: 'Básico', PROFESIONAL: 'Profesional', ECOMMERCE: 'Ecommerce', IA: 'IA' };
const soles = (valor: number) => `S/ ${Number(valor || 0).toFixed(2)}`;

/**
 * Ingresos agregados del listado visible. Se separa lo activo de lo suspendido porque una
 * suscripción suspendida sigue teniendo paquete pero no está facturando: sumarlas juntas
 * daría una cifra que no corresponde con lo que entra al mes.
 */
function ResumenIngresos({ rows }: { rows: Tenant[] }) {
  // La tienda propia y el demo se excluyen: incluirlas infla el ingreso y hace que el
  // promedio por empresa deje de significar nada.
  const facturables = rows.filter((t) => t.billable !== false);
  const noFacturables = rows.length - facturables.length;
  const activas = facturables.filter((t) => t.subscriptionStatus === 'ACTIVA');
  const facturando = activas.reduce((suma, t) => suma + Number(t.monthlyTotal || 0), 0);
  const enRiesgo = facturables.filter((t) => t.subscriptionStatus !== 'ACTIVA')
    .reduce((suma, t) => suma + Number(t.monthlyTotal || 0), 0);
  const promedio = activas.length ? facturando / activas.length : 0;
  return <section className="react-revenue-strip" aria-label="Ingresos por suscripciones">
    <div><span>Ingreso mensual activo</span><strong>{soles(facturando)}</strong><small>{activas.length} empresa{activas.length === 1 ? '' : 's'} facturando</small></div>
    <div><span>Promedio por empresa</span><strong>{soles(promedio)}</strong><small>solo suscripciones activas</small></div>
    <div className={enRiesgo > 0 ? 'is-riesgo' : ''}><span>Suspendido</span><strong>{soles(enRiesgo)}</strong>
      <small>{facturables.length - activas.length} sin facturar{noFacturables ? ` · ${noFacturables} no facturable${noFacturables === 1 ? '' : 's'} fuera del cálculo` : ''}</small></div>
  </section>;
}

function Dialog({ title, subtitle, children, onClose, wide = false }: { title: string; subtitle?: string; children: ReactNode; onClose: () => void; wide?: boolean }) {
  useEffect(() => { const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose(); }; window.addEventListener('keydown', onKeyDown); return () => window.removeEventListener('keydown', onKeyDown); }, [onClose]);
  return <div className="react-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) onClose(); }}><section className={`react-dialog${wide ? ' react-dialog-wide' : ''}`} role="dialog" aria-modal="true" aria-labelledby="react-company-dialog-title"><div className="react-dialog-header"><div><h2 id="react-company-dialog-title">{title}</h2>{subtitle && <p className="field-hint">{subtitle}</p>}</div><button className="btn btn-ghost btn-sm" type="button" aria-label="Cerrar" onClick={onClose}>×</button></div>{children}</section></div>;
}

function CompanyDialog({ tenant, onClose, onCreated, onSaved }: { tenant?: Tenant; onClose: () => void; onCreated: (response: CreatedTenant) => void; onSaved: () => void }) {
  const isEdit = Boolean(tenant);
  const [name, setName] = useState(tenant?.name || '');
  const [slug, setSlug] = useState('');
  const [ruc, setRuc] = useState(tenant?.ruc || '');
  const [email, setEmail] = useState(tenant?.email || '');
  const [address, setAddress] = useState(tenant?.address || '');
  const [phone, setPhone] = useState(tenant?.phone || '');
  const [vertical, setVertical] = useState(tenant?.businessVertical || 'CLOTHING');
  const [plan, setPlan] = useState(tenant?.plan || 'STARTER');
  const [nextPaymentDue, setNextPaymentDue] = useState(tenant?.nextPaymentDue || '');
  const [subscriptionStatus, setSubscriptionStatus] = useState(tenant?.subscriptionStatus || 'ACTIVA');
  const [ownerUsername, setOwnerUsername] = useState('');
  const [ownerEmail, setOwnerEmail] = useState('');
  // Solo en el alta: el paquete se elige aquí mismo, en vez de crear la empresa y
  // entrar después a editar módulos.
  const [catalogo, setCatalogo] = useState<Modulo[]>([]);
  const [presets, setPresets] = useState<Record<string, ModuloCode[]>>({});
  const [paquete, setPaquete] = useState<Seleccion>({});
  // El costo de implementación cubre el primer mes y se registra como el primer pago.
  const [costoImplementacion, setCostoImplementacion] = useState('');
  const [billable, setBillable] = useState(tenant?.billable !== false);
  const [ownerFullName, setOwnerFullName] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (isEdit) return;
    let vivo = true;
    api.get<{ modulos: Modulo[]; presets: Record<string, ModuloCode[]> }>('/platform/tenants/modules/catalog', { auth: 'staff' })
      .then((respuesta) => {
        if (!vivo || !respuesta) return;
        setCatalogo(respuesta.modulos);
        setPresets(respuesta.presets);
        setPaquete(seleccionDePreset(respuesta.modulos, respuesta.presets[plan] ?? []));
      })
      .catch(() => undefined);
    return () => { vivo = false; };
    // El plan solo siembra el paquete al abrir; después se ajusta a mano y no debe
    // pisarse cada vez que cambie el selector.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isEdit]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedRuc = ruc.trim();
    if (!name.trim()) { setError('Ingresa la razón social o nombre.'); return; }
    if (normalizedRuc && !/^\d{11}$/.test(normalizedRuc)) { setError('El RUC debe contener exactamente 11 dígitos o quedar vacío.'); return; }
    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) { setError('Ingresa un correo de empresa válido.'); return; }
    if (!isEdit && !/^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])?$/.test(slug.trim())) { setError('El subdominio debe usar minúsculas, números y guiones; debe tener entre 3 y 63 caracteres.'); return; }
    if (!isEdit && !/^[A-Za-z0-9._-]{4,50}$/.test(ownerUsername.trim())) { setError('El usuario administrador debe tener entre 4 y 50 caracteres válidos.'); return; }
    if (!isEdit && !ownerFullName.trim()) { setError('Ingresa el nombre del administrador inicial.'); return; }
    setSaving(true);
    try {
      const base = { name: name.trim(), ruc: normalizedRuc || null, email: email.trim() || null, address: address.trim() || null, phone: phone.trim() || null, businessVertical: vertical, plan, nextPaymentDue: nextPaymentDue || null, billable };
      if (isEdit) { await api.put(`/platform/tenants/${tenant!.id}`, { ...base, subscriptionStatus }, { auth: 'staff' }); showToast('Empresa actualizada correctamente.'); onSaved(); } else { const created = await api.post<CreatedTenant>('/platform/tenants', { ...base, slug: slug.trim(), ownerUsername: ownerUsername.trim(), ownerEmail: ownerEmail.trim() || null, ownerFullName: ownerFullName.trim(), modulos: Object.entries(paquete).map(([code, precioMensual]) => ({ code, precioMensual })), costoImplementacion: costoImplementacion.trim() ? Number(costoImplementacion) : null }, { auth: 'staff' }); onCreated(created); }
      onClose();
    } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'No se pudo guardar la empresa.'); } finally { setSaving(false); }
  }
  return <Dialog title={isEdit ? 'Editar empresa' : 'Nueva empresa'} subtitle={isEdit ? tenant?.slug : 'Se creará un tenant aislado con su administrador inicial.'} onClose={onClose} wide><form className="react-company-form" onSubmit={submit} noValidate>{error && <div className="alert alert-danger" role="alert">{error}</div>}<div className="form-grid"><label className="field field-span-2"><span className="field-label">Razón social / nombre</span><input className="input" required maxLength={150} value={name} onChange={(event) => setName(event.target.value)} /></label>{!isEdit && <label className="field field-span-2"><span className="field-label">Subdominio</span><input className="input mono" required maxLength={63} value={slug} onChange={(event) => setSlug(event.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''))} placeholder="mi-tienda" /><small className="field-hint">Se usará como mi-tienda.tudominio.com.</small></label>}<label className="field"><span className="field-label">RUC</span><input className="input" maxLength={11} inputMode="numeric" value={ruc} onChange={(event) => setRuc(event.target.value.replace(/\D/g, '').slice(0, 11))} placeholder="11 dígitos" /></label><label className="field"><span className="field-label">Correo de empresa</span><input className="input" type="email" maxLength={120} value={email} onChange={(event) => setEmail(event.target.value)} /></label><label className="field field-span-2"><span className="field-label">Dirección</span><input className="input" maxLength={255} value={address} onChange={(event) => setAddress(event.target.value)} /></label><label className="field"><span className="field-label">Teléfono</span><input className="input" maxLength={20} inputMode="tel" value={phone} onChange={(event) => setPhone(event.target.value)} /></label><label className="field"><span className="field-label">Rubro</span><select className="select" value={vertical} onChange={(event) => setVertical(event.target.value)}><option value="CLOTHING">Ropa</option><option value="GENERAL">General / otro</option></select></label><label className="field"><span className="field-label">Plan</span><select className="select" value={plan} onChange={(event) => { setPlan(event.target.value); if (!isEdit) setPaquete(seleccionDePreset(catalogo, presets[event.target.value] ?? [])); }}>{Object.entries(planLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label><label className="field"><span className="field-label">{isEdit ? 'Próximo pago' : 'Cubierto hasta'}</span><input className="input" type="date" value={nextPaymentDue || ''} onChange={(event) => setNextPaymentDue(event.target.value)} />{!isEdit && <small className="field-hint">Si lo dejas vacío, la implementación cubre un mes desde hoy.</small>}</label>{!isEdit && <label className="field"><span className="field-label">Costo de implementación</span><input className="input" type="number" min="0" step="0.01" value={costoImplementacion} onChange={(event) => setCostoImplementacion(event.target.value)} placeholder={totalDe(paquete).toFixed(2)} /><small className="field-hint">Cubre el primer mes y queda como el primer pago. Vacío = el total del paquete.</small></label>}<label className="field field-span-2 checkbox-field"><input type="checkbox" checked={billable} onChange={(event) => setBillable(event.target.checked)} /><span>Cuenta como ingreso<small className="field-hint"> — desmárcalo para tu propia tienda o el demo: sigue funcionando igual, pero no suma al ingreso mensual.</small></span></label>{isEdit && <label className="field"><span className="field-label">Suscripción</span><select className="select" value={subscriptionStatus} onChange={(event) => setSubscriptionStatus(event.target.value)}><option value="ACTIVA">Activa</option><option value="SUSPENDIDA">Suspendida</option></select></label>}{!isEdit && <><div className="field field-span-2 react-company-section-heading"><h3>Administrador inicial</h3><p className="field-hint">La contraseña temporal se muestra una sola vez al finalizar.</p></div><label className="field"><span className="field-label">Usuario</span><input className="input" required maxLength={50} value={ownerUsername} onChange={(event) => setOwnerUsername(event.target.value)} placeholder="admin.tienda" /></label><label className="field"><span className="field-label">Correo del administrador</span><input className="input" type="email" maxLength={120} value={ownerEmail} onChange={(event) => setOwnerEmail(event.target.value)} /></label><label className="field field-span-2"><span className="field-label">Nombre completo</span><input className="input" required maxLength={120} value={ownerFullName} onChange={(event) => setOwnerFullName(event.target.value)} /></label></>}</div>{!isEdit && catalogo.length > 0 && <div className="react-company-modules"><div className="react-company-section-heading"><h3>Paquete contratado</h3><p className="field-hint">El plan es solo el punto de partida: ajusta los módulos al presupuesto del cliente.</p></div><ModulePicker catalogo={catalogo} presets={presets} seleccion={paquete} onChange={setPaquete} /><div className="react-modules-total"><span>Total mensual</span><strong>S/ {totalDe(paquete).toFixed(2)}</strong></div></div>}<div className="react-dialog-actions"><button className="btn btn-secondary" type="button" onClick={onClose}>Cancelar</button><button className="btn btn-primary" type="submit" disabled={saving}>{saving ? 'Guardando…' : isEdit ? 'Guardar cambios' : 'Crear empresa'}</button></div></form></Dialog>;
}

function CredentialsDialog({ created, onClose }: { created: CreatedTenant; onClose: () => void }) {
  return <Dialog title="Empresa creada" subtitle={created.tenant.name} onClose={onClose} wide><div className="alert alert-success" role="status">Guarda estas credenciales y entrégalas al administrador por un canal seguro. La contraseña temporal deberá cambiarse al ingresar.</div><dl className="react-company-credentials"><div><dt>Subdominio</dt><dd className="mono">{created.tenant.slug}</dd></div><div><dt>Usuario</dt><dd className="mono">{created.ownerUsername}</dd></div><div><dt>Contraseña temporal</dt><dd className="react-temporary-password mono">{created.temporaryPassword}</dd></div></dl><div className="react-dialog-actions"><button className="btn btn-primary" type="button" onClick={onClose}>Entendido</button></div></Dialog>;
}

export function AdminCompaniesPage() {
  const canManage = getStaffSession()?.user.permissions.includes('PLATAFORMA_EMPRESAS_GESTIONAR') ?? false;
  const [rows, setRows] = useState<Tenant[]>([]);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [editing, setEditing] = useState<Tenant | null | undefined>(undefined);
  const [created, setCreated] = useState<CreatedTenant | null>(null);
  const [modulesOf, setModulesOf] = useState<Tenant | null>(null);
  const [operatorsOf, setOperatorsOf] = useState<Tenant | null>(null);
  const [renewing, setRenewing] = useState<Tenant | null>(null);
  useEffect(() => {
    if (!canManage) { setLoading(false); setError('No tienes permiso para gestionar empresas.'); return; }
    let mounted = true;
    const timer = window.setTimeout(() => {
      setLoading(true); setError('');
      api.get<Tenant[]>('/platform/tenants', { auth: 'staff', query: { search: search.trim() || undefined, status: status || undefined } }).then((response) => { if (mounted) setRows(response); }).catch((reason) => { if (mounted) setError(reason instanceof ApiError ? reason.message : 'No se pudieron cargar las empresas.'); }).finally(() => { if (mounted) setLoading(false); });
    }, search ? 300 : 0);
    return () => { mounted = false; window.clearTimeout(timer); };
  }, [canManage, reloadKey, search, status]);
  return <PlatformShell title="Empresas" description="Altas, paquetes de módulos, accesos y cobros de las empresas que usan la plataforma." activePage="/plataforma/empresas"><div className="page-actions react-companies-actions">{canManage && <button className="btn btn-primary" type="button" onClick={() => setEditing(null)}>+ Nueva empresa</button>}</div><section className="filter-bar react-companies-filters"><label className="topbar-search"><span aria-hidden="true">⌕</span><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar por empresa o subdominio…" aria-label="Buscar empresas" /></label><select className="select" value={status} onChange={(event) => setStatus(event.target.value)} aria-label="Filtrar por suscripción"><option value="">Todos los estados</option><option value="ACTIVA">Activa</option><option value="SUSPENDIDA">Suspendida</option></select></section>{!error && !loading && rows.length > 0 && <ResumenIngresos rows={rows} />}<section className="table-card react-companies-table">{error ? <ErrorState message={error} /> : loading ? <LoadingState label="Cargando empresas…" /> : rows.length ? <div className="table-scroll"><table className="data-table"><thead><tr><th>Empresa</th><th>Acceso</th><th>Plan</th><th>Suscripción</th><th>Usuarios</th><th>Mensual</th><th>Paquete</th><th /></tr></thead><tbody>{rows.map((tenant) => <tr key={tenant.id}><td data-label="Empresa"><div className="table-cell-primary">{tenant.name}</div><div className="table-cell-muted mono">{tenant.ruc || 'Sin RUC'}</div></td><td data-label="Acceso"><div className="table-cell-primary mono">{tenant.slug}</div><div className="table-cell-muted">{tenant.ownerUsername || 'Sin administrador'}</div></td><td data-label="Plan"><span className="badge badge-neutral">{planLabels[tenant.plan] || tenant.plan}</span></td><td data-label="Suscripción"><span className={`badge ${tenant.subscriptionStatus === 'ACTIVA' ? 'badge-success' : 'badge-danger'}`}>{tenant.subscriptionStatus === 'ACTIVA' ? 'Activa' : 'Suspendida'}</span>{tenant.nextPaymentDue && <div className="table-cell-muted">{tenant.nextPaymentDue}</div>}<button className="btn btn-ghost btn-sm react-renew-button" type="button" onClick={() => setRenewing(tenant)}>Renovar</button></td><td data-label="Usuarios">{tenant.activeUsers}</td><td data-label="Mensual"><div className="table-cell-primary mono">{soles(Number(tenant.monthlyTotal || 0))}</div><div className="table-cell-muted">{tenant.moduleCount ?? 0} módulos</div>{tenant.billable === false && <span className="badge badge-neutral">No facturable</span>}</td><td data-label="Paquete"><div className="react-companies-package"><button className="btn btn-ghost btn-sm" type="button" onClick={() => setModulesOf(tenant)}>Módulos</button><button className="btn btn-ghost btn-sm" type="button" onClick={() => setOperatorsOf(tenant)}>Acceso</button></div></td><td data-label="Acciones"><button className="btn btn-ghost btn-sm" type="button" onClick={() => setEditing(tenant)}>Editar</button></td></tr>)}</tbody></table></div> : <EmptyState>No se encontraron empresas.</EmptyState>}</section>{editing !== undefined && <CompanyDialog tenant={editing || undefined} onClose={() => setEditing(undefined)} onCreated={(response) => { setCreated(response); setReloadKey((value) => value + 1); }} onSaved={() => setReloadKey((value) => value + 1)} />}{created && <CredentialsDialog created={created} onClose={() => setCreated(null)} />}{modulesOf && <ModulesDialog tenantId={modulesOf.id} onClose={() => { setModulesOf(null); setReloadKey((value) => value + 1); }} />}{operatorsOf && <OperatorsDialog tenantId={operatorsOf.id} empresa={operatorsOf.name} onClose={() => setOperatorsOf(null)} />}{renewing && <RenewDialog tenantId={renewing.id} empresa={renewing.name} vence={renewing.nextPaymentDue} totalPaquete={renewing.monthlyTotal ?? null} onClose={() => setRenewing(null)} onRenovado={() => setReloadKey((value) => value + 1)} />}</PlatformShell>;
}
