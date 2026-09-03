import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { AdminShell } from './AdminPagesV2';
import { EmptyState, ErrorState, LoadingState } from '../components/States';
import { ApiError, api, getStaffSession } from '../services/api';
import { showToast } from '../components/ToastHost';
import type { Page } from '../types';
import { formatDate } from '../utils';

type UserRole = { id: number; code: string; name: string; hierarchyLevel: number };
type UserRow = { id: number; username: string; email?: string | null; fullName: string; dni?: string | null; phone?: string | null; status: 'ACTIVE' | 'INACTIVE' | 'BLOCKED' | string; lastLoginAt?: string | null; roles: UserRole[]; createdAt?: string | null };
type Role = UserRole & { description?: string | null; isSystem: boolean; permisos?: Array<{ id: number; code: string; description?: string | null }> };
type UserAction = { status: string; label: string; kind?: 'status' | 'reset' };

const nextStatus: Record<string, { status: string; label: string }> = { ACTIVE: { status: 'INACTIVE', label: 'Desactivar' }, INACTIVE: { status: 'ACTIVE', label: 'Activar' }, BLOCKED: { status: 'ACTIVE', label: 'Desbloquear' } };

function statusLabel(status: string) { return status === 'ACTIVE' ? 'Activo' : status === 'BLOCKED' ? 'Bloqueado' : 'Inactivo'; }
function statusClass(status: string) { return status === 'ACTIVE' ? 'badge-success' : status === 'BLOCKED' ? 'badge-danger' : 'badge-neutral'; }

function Dialog({ title, subtitle, children, onClose, small = false }: { title: string; subtitle?: string; children: ReactNode; onClose: () => void; small?: boolean }) {
  useEffect(() => { const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose(); }; window.addEventListener('keydown', onKeyDown); return () => window.removeEventListener('keydown', onKeyDown); }, [onClose]);
  return <div className="react-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) onClose(); }}><section className={`react-dialog${small ? ' react-dialog-small' : ''}`} role="dialog" aria-modal="true" aria-labelledby="react-user-dialog-title"><div className="react-dialog-header"><div><h2 id="react-user-dialog-title">{title}</h2>{subtitle && <p className="field-hint">{subtitle}</p>}</div><button className="btn btn-ghost btn-sm" type="button" aria-label="Cerrar" onClick={onClose}>×</button></div>{children}</section></div>;
}

function UserDialog({ user, onClose, onSaved }: { user: UserRow | null; onClose: () => void; onSaved: () => void }) {
  const isEdit = Boolean(user);
  const [roles, setRoles] = useState<Role[]>([]);
  const [rolesLoading, setRolesLoading] = useState(true);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [username, setUsername] = useState(user?.username || '');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState(user?.fullName || '');
  const [email, setEmail] = useState(user?.email || '');
  const [phone, setPhone] = useState(user?.phone || '');
  const [selectedRoleIds, setSelectedRoleIds] = useState<number[]>(() => user?.roles.map((role) => role.id) || []);
  const session = getStaffSession();
  const actorRoleCodes = session?.user.roles || [];
  const ceiling = roles.length && actorRoleCodes.length ? Math.max(...roles.filter((role) => actorRoleCodes.includes(role.code)).map((role) => role.hierarchyLevel), 0) : Infinity;

  useEffect(() => { let mounted = true; api.get<Role[]>('/roles', { auth: 'staff' }).then((response) => { if (mounted) setRoles(response); }).catch((reason) => { if (mounted) setError(reason instanceof ApiError ? reason.message : 'No se pudieron cargar los roles.'); }).finally(() => { if (mounted) setRolesLoading(false); }); return () => { mounted = false; }; }, []);

  function toggleRole(role: Role) {
    if (role.hierarchyLevel > ceiling && !selectedRoleIds.includes(role.id)) return;
    setSelectedRoleIds((current) => current.includes(role.id) ? current.filter((id) => id !== role.id) : [...current, role.id]);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    if (selectedRoleIds.length === 0) { setError('Selecciona al menos un rol.'); return; }
    if (!isEdit && !/^[a-zA-Z0-9._-]{4,50}$/.test(username.trim())) { setError('El usuario debe tener entre 4 y 50 caracteres: letras, números, punto, guion o guion bajo.'); return; }
    if (!isEdit && !/^(?=.*[A-Za-z])(?=.*\d).{8,60}$/.test(password)) { setError('La contraseña debe tener entre 8 y 60 caracteres, con al menos una letra y un número.'); return; }
    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) { setError('Ingresa un correo electrónico válido.'); return; }
    setSaving(true);
    try {
      const payload = { email: email.trim() || null, fullName: fullName.trim(), phone: phone.trim() || null, dni: user?.dni || null, roleIds: selectedRoleIds };
      const saved = isEdit ? await api.put<UserRow>(`/users/${user!.id}`, payload, { auth: 'staff' }) : await api.post<UserRow>('/users', { ...payload, username: username.trim(), password }, { auth: 'staff' });
      showToast(isEdit ? 'Usuario actualizado correctamente.' : `Usuario ${saved.fullName} creado correctamente.`);
      onSaved();
      onClose();
    } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'No se pudo guardar el usuario.'); } finally { setSaving(false); }
  }

  return <Dialog title={isEdit ? 'Editar usuario' : 'Nuevo usuario'} subtitle={isEdit ? user?.username : 'La contraseña inicial deberá cambiarse al primer ingreso.'} onClose={onClose}><form className="react-user-form" onSubmit={submit} noValidate>{error && <div className="alert alert-danger" role="alert">{error}</div>}<div className="form-grid"><label className="field field-span-2"><span className="field-label">Usuario</span><input className="input" required minLength={4} maxLength={50} pattern="[a-zA-Z0-9._-]+" disabled={isEdit} value={username} onChange={(event) => setUsername(event.target.value)} /></label>{!isEdit && <label className="field field-span-2"><span className="field-label">Contraseña inicial</span><input className="input" type="password" required minLength={8} maxLength={60} autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} /><small className="field-hint">Mínimo 8 caracteres, con letras y números.</small></label>}<label className="field field-span-2"><span className="field-label">Nombre completo</span><input className="input" required maxLength={120} value={fullName} onChange={(event) => setFullName(event.target.value)} /></label><label className="field"><span className="field-label">Email</span><input className="input" type="email" maxLength={120} value={email} onChange={(event) => setEmail(event.target.value)} /></label><label className="field"><span className="field-label">Teléfono</span><input className="input" maxLength={20} inputMode="tel" value={phone} onChange={(event) => setPhone(event.target.value)} /></label></div><fieldset className="react-user-roles"><legend className="field-label">Roles</legend>{rolesLoading ? <LoadingState label="Cargando roles…" /> : <div className="react-role-options">{roles.map((role) => { const checked = selectedRoleIds.includes(role.id); const disabled = role.hierarchyLevel > ceiling && !checked; return <label className={`checkbox-field${disabled ? ' is-disabled' : ''}`} title={disabled ? 'Supera tu nivel de autorización' : undefined} key={role.id}><input type="checkbox" checked={checked} disabled={disabled} onChange={() => toggleRole(role)} /> <span>{role.name}<small>{role.code} · nivel {role.hierarchyLevel}</small></span></label>; })}</div>}</fieldset><div className="react-dialog-actions"><button className="btn btn-secondary" type="button" onClick={onClose}>Cancelar</button><button className="btn btn-primary" type="submit" disabled={saving || rolesLoading}>{saving ? 'Guardando…' : isEdit ? 'Guardar cambios' : 'Crear usuario'}</button></div></form></Dialog>;
}

function ConfirmDialog({ user, action, onClose, onConfirm, busy }: { user: UserRow; action: UserAction; onClose: () => void; onConfirm: () => void; busy: boolean }) {
  return <Dialog title={`${action.label} usuario`} subtitle={user.username} onClose={onClose} small><p>¿Seguro que deseas {action.label.toLowerCase()} a <strong>{user.fullName}</strong>?</p><div className="react-dialog-actions"><button className="btn btn-secondary" type="button" onClick={onClose}>Cancelar</button><button className={`btn ${action.status === 'ACTIVE' ? 'btn-primary' : 'btn-danger'}`} type="button" disabled={busy} onClick={onConfirm}>{busy ? 'Procesando…' : action.label}</button></div></Dialog>;
}

function PasswordDialog({ user, password, onClose }: { user: UserRow; password: string; onClose: () => void }) {
  return <Dialog title="Contraseña temporal generada" subtitle={user.username} onClose={onClose} small><p>Comparte esta contraseña con <strong>{user.fullName}</strong> por un canal seguro. No volverá a mostrarse.</p><div className="react-temporary-password mono">{password}</div><div className="react-dialog-actions"><button className="btn btn-primary" type="button" onClick={onClose}>Entendido</button></div></Dialog>;
}

export function AdminUsersPage() {
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [pageNumber, setPageNumber] = useState(0);
  const [page, setPage] = useState<Page<UserRow> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [editing, setEditing] = useState<UserRow | null | undefined>(undefined);
  const [confirming, setConfirming] = useState<{ user: UserRow; action: UserAction } | null>(null);
  const [changing, setChanging] = useState(false);
  const [temporary, setTemporary] = useState<{ user: UserRow; password: string } | null>(null);
  const session = getStaffSession();
  const permissions = session?.user.permissions || [];
  const canCreate = permissions.includes('USUARIOS_CREAR');
  const canEdit = permissions.includes('USUARIOS_EDITAR');
  const canBlock = permissions.includes('USUARIOS_BLOQUEAR');
  const canReset = permissions.includes('USUARIOS_RESETEAR_CONTRASENA');

  useEffect(() => {
    let mounted = true;
    const timer = window.setTimeout(() => {
      setLoading(true);
      setError('');
      api.get<Page<UserRow>>('/users', { auth: 'staff', query: { search: search.trim() || undefined, status: status || undefined, page: pageNumber, size: 20, sort: 'fullName,asc' } }).then((response) => { if (mounted) setPage(response); }).catch((reason) => { if (mounted) setError(reason instanceof ApiError ? reason.message : 'No se pudieron cargar los usuarios.'); }).finally(() => { if (mounted) setLoading(false); });
    }, search ? 300 : 0);
    return () => { mounted = false; window.clearTimeout(timer); };
  }, [pageNumber, reloadKey, search, status]);

  const rows = useMemo(() => page?.content || [], [page]);

  async function changeStatus() {
    if (!confirming) return;
    setChanging(true);
    try {
      if (confirming.action.kind === 'reset') {
        const response = await api.post<{ temporaryPassword: string }>(`/users/${confirming.user.id}/reset-password`, {}, { auth: 'staff' });
        setConfirming(null);
        setTemporary({ user: confirming.user, password: response.temporaryPassword });
      } else {
        await api.patch<UserRow>(`/users/${confirming.user.id}/status`, { status: confirming.action.status }, { auth: 'staff' });
        showToast(`Estado de ${confirming.user.fullName} actualizado.`);
        setConfirming(null);
        setPage((current) => current ? { ...current, content: current.content.map((row) => row.id === confirming.user.id ? { ...row, status: confirming.action.status } : row) } : current);
      }
    } catch (reason) { showToast(reason instanceof ApiError ? reason.message : confirming.action.kind === 'reset' ? 'No se pudo resetear la contraseña.' : 'No se pudo actualizar el estado.', 'Error', 'error'); } finally { setChanging(false); }
  }

  function resetPassword(user: UserRow) {
    setConfirming({ user, action: { status: 'RESET', label: 'Resetear contraseña', kind: 'reset' } });
  }

  return <AdminShell title="Usuarios" description="Cuentas del personal, sus roles y su estado de acceso." activePage="/admin/usuarios"><div className="page-actions react-users-actions"><a className="btn btn-secondary" href="/admin/roles" onClick={(event) => { event.preventDefault(); window.history.pushState({}, '', '/admin/roles'); window.dispatchEvent(new PopStateEvent('popstate')); }}>Gestionar roles</a>{canCreate && <button className="btn btn-primary" type="button" onClick={() => setEditing(null)}>+ Nuevo usuario</button>}</div><section className="filter-bar react-users-filters"><label className="topbar-search"><span aria-hidden="true">⌕</span><input type="search" value={search} onChange={(event) => { setSearch(event.target.value); setPageNumber(0); }} placeholder="Buscar por usuario o nombre…" aria-label="Buscar usuarios" /></label><select className="select" value={status} onChange={(event) => { setStatus(event.target.value); setPageNumber(0); }} aria-label="Filtrar por estado"><option value="">Todos los estados</option><option value="ACTIVE">Activo</option><option value="INACTIVE">Inactivo</option><option value="BLOCKED">Bloqueado</option></select></section><section className="table-card react-users-table">{error ? <ErrorState message={error} /> : loading ? <LoadingState label="Cargando usuarios…" /> : rows.length ? <div className="table-scroll"><table className="data-table"><thead><tr><th>Usuario</th><th>Roles</th><th>Último acceso</th><th>Estado</th><th /></tr></thead><tbody>{rows.map((user) => { const action = nextStatus[user.status] || nextStatus.ACTIVE; const isCurrent = user.id === session?.user.id; return <tr key={user.id}><td data-label="Usuario"><div className="table-cell-primary">{user.fullName}</div><div className="table-cell-muted mono">{user.username}</div></td><td data-label="Roles"><div className="react-user-role-list">{user.roles?.map((role) => <span className="badge badge-neutral" key={role.id}>{role.name}</span>) || '—'}</div></td><td data-label="Último acceso" className="table-cell-muted">{formatDate(user.lastLoginAt)}</td><td data-label="Estado"><span className={`badge ${statusClass(user.status)}`}>{statusLabel(user.status)}</span></td><td data-label="Acciones"><div className="table-actions">{canEdit && <button className="btn btn-ghost btn-sm" type="button" onClick={() => setEditing(user)}>Editar</button>}{canReset && <button className="btn btn-ghost btn-sm" type="button" onClick={() => void resetPassword(user)}>Resetear contraseña</button>}{canBlock && <button className="btn btn-ghost btn-sm" type="button" disabled={isCurrent} title={isCurrent ? 'No puedes cambiar tu propio estado' : undefined} onClick={() => setConfirming({ user, action })}>{action.label}</button>}</div></td></tr>; })}</tbody></table></div> : <EmptyState>No se encontraron usuarios.</EmptyState>}{page && (page.totalPages || 0) > 1 && <div className="pagination-bar"><button className="btn btn-secondary btn-sm" type="button" disabled={pageNumber === 0} onClick={() => setPageNumber((value) => value - 1)}>Anterior</button><span>Página {pageNumber + 1} de {page.totalPages}</span><button className="btn btn-secondary btn-sm" type="button" disabled={pageNumber + 1 >= (page.totalPages || 0)} onClick={() => setPageNumber((value) => value + 1)}>Siguiente</button></div>}</section>{editing !== undefined && <UserDialog user={editing} onClose={() => setEditing(undefined)} onSaved={() => { setPageNumber(0); setReloadKey((value) => value + 1); }} />}{confirming && <ConfirmDialog user={confirming.user} action={confirming.action} busy={changing} onClose={() => setConfirming(null)} onConfirm={() => void changeStatus()} />}{temporary && <PasswordDialog user={temporary.user} password={temporary.password} onClose={() => setTemporary(null)} />}</AdminShell>;
}
