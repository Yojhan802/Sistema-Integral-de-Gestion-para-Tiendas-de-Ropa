import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { AdminShell } from './AdminPagesV2';
import { EmptyState, ErrorState, LoadingState } from '../components/States';
import { ApiError, api, getStaffSession } from '../services/api';
import { showToast } from '../components/ToastHost';

type Permission = { id: number; code: string; module: string; description?: string | null };
type Role = { id: number; code: string; name: string; description?: string | null; isSystem: boolean; hierarchyLevel: number; permisos: Permission[] };

function Dialog({ title, subtitle, children, onClose }: { title: string; subtitle?: string; children: ReactNode; onClose: () => void }) {
  useEffect(() => { const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose(); }; window.addEventListener('keydown', onKeyDown); return () => window.removeEventListener('keydown', onKeyDown); }, [onClose]);
  return <div className="react-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) onClose(); }}><section className="react-dialog react-dialog-small" role="dialog" aria-modal="true" aria-labelledby="react-role-dialog-title"><div className="react-dialog-header"><div><h2 id="react-role-dialog-title">{title}</h2>{subtitle && <p className="field-hint">{subtitle}</p>}</div><button className="btn btn-ghost btn-sm" type="button" aria-label="Cerrar" onClick={onClose}>×</button></div>{children}</section></div>;
}

function RoleFormDialog({ role, onClose, onSaved }: { role?: Role; onClose: () => void; onSaved: (role: Role) => void }) {
  const isEdit = Boolean(role);
  const [code, setCode] = useState(role?.code || '');
  const [name, setName] = useState(role?.name || '');
  const [description, setDescription] = useState(role?.description || '');
  const [hierarchyLevel, setHierarchyLevel] = useState(String(role?.hierarchyLevel ?? 0));
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedCode = code.trim().toUpperCase();
    const level = Number(hierarchyLevel);
    if (!isEdit && !/^[A-Z_]{1,30}$/.test(normalizedCode)) { setError('El código solo puede contener mayúsculas y guiones bajos.'); return; }
    if (!name.trim()) { setError('Ingresa el nombre del rol.'); return; }
    if (!Number.isInteger(level) || level < 0 || level > 100) { setError('El techo de asignación debe estar entre 0 y 100.'); return; }
    setSaving(true);
    try { const saved = isEdit ? await api.put<Role>(`/roles/${role!.id}`, { name: name.trim(), description: description.trim() || null, hierarchyLevel: level }, { auth: 'staff' }) : await api.post<Role>('/roles', { code: normalizedCode, name: name.trim(), description: description.trim() || null, hierarchyLevel: level }, { auth: 'staff' }); showToast(isEdit ? 'Rol actualizado correctamente.' : 'Rol creado correctamente.'); onSaved(saved); onClose(); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'No se pudo guardar el rol.'); } finally { setSaving(false); }
  }
  return <Dialog title={isEdit ? 'Editar rol' : 'Nuevo rol'} subtitle={isEdit ? role?.code : 'El techo limita los roles que este rol puede asignar.'} onClose={onClose}><form className="react-role-form" onSubmit={submit} noValidate>{error && <div className="alert alert-danger" role="alert">{error}</div>}<div className="form-grid">{!isEdit && <label className="field field-span-2"><span className="field-label">Código</span><input className="input mono" required maxLength={30} value={code} onChange={(event) => setCode(event.target.value.toUpperCase().replace(/[^A-Z_]/g, ''))} placeholder="EJ: SUPERVISOR" /></label>}<label className="field field-span-2"><span className="field-label">Nombre</span><input className="input" required maxLength={60} value={name} onChange={(event) => setName(event.target.value)} /></label><label className="field field-span-2"><span className="field-label">Descripción</span><textarea className="input" maxLength={255} rows={2} value={description} onChange={(event) => setDescription(event.target.value)} /></label><label className="field field-span-2"><span className="field-label">Techo de asignación (0–100)</span><input className="input" type="number" min={0} max={100} step={1} value={hierarchyLevel} onChange={(event) => setHierarchyLevel(event.target.value)} /><small className="field-hint">Administrador = 100, Supervisor = 50, Vendedor/Almacenero = 10.</small></label></div><div className="react-dialog-actions"><button className="btn btn-secondary" type="button" onClick={onClose}>Cancelar</button><button className="btn btn-primary" type="submit" disabled={saving}>{saving ? 'Guardando…' : isEdit ? 'Guardar cambios' : 'Crear rol'}</button></div></form></Dialog>;
}

export function AdminRolesPage() {
  const canManage = getStaffSession()?.user.permissions.includes('ROLES_GESTIONAR') ?? false;
  const [roles, setRoles] = useState<Role[]>([]);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [savingPermissions, setSavingPermissions] = useState(false);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState<Role | null | undefined>(undefined);
  const [selectedPermissionIds, setSelectedPermissionIds] = useState<number[]>([]);
  const selectedRole = roles.find((role) => role.id === selectedId) || null;
  const permissionsByModule = useMemo(() => permissions.reduce<Record<string, Permission[]>>((groups, permission) => { (groups[permission.module] ||= []).push(permission); return groups; }, {}), [permissions]);

  useEffect(() => {
    let mounted = true;
    Promise.all([api.get<Role[]>('/roles', { auth: 'staff' }), canManage ? api.get<Permission[]>('/permissions', { auth: 'staff' }) : Promise.resolve([] as Permission[])]).then(([roleRows, permissionRows]) => { if (!mounted) return; setRoles(roleRows); setPermissions(permissionRows); setSelectedId((current) => current ?? roleRows[0]?.id ?? null); }).catch((reason) => { if (mounted) setError(reason instanceof ApiError ? reason.message : 'No se pudieron cargar roles y permisos.'); }).finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; };
  }, [canManage]);

  useEffect(() => { setSelectedPermissionIds(selectedRole?.permisos?.map((permission) => permission.id) || []); }, [selectedRole]);

  function saveRole(role: Role) { setRoles((current) => current.some((item) => item.id === role.id) ? current.map((item) => item.id === role.id ? role : item) : [...current, role]); setSelectedId(role.id); }
  function togglePermission(id: number) { setSelectedPermissionIds((current) => current.includes(id) ? current.filter((value) => value !== id) : [...current, id]); }
  async function savePermissions(event: FormEvent<HTMLFormElement>) { event.preventDefault(); if (!selectedRole || selectedRole.isSystem) return; setSavingPermissions(true); try { const saved = await api.put<Role>(`/roles/${selectedRole.id}/permissions`, { permissionIds: selectedPermissionIds }, { auth: 'staff' }); saveRole(saved); showToast('Permisos actualizados correctamente.'); } catch (reason) { showToast(reason instanceof ApiError ? reason.message : 'No se pudieron guardar los permisos.', 'Error', 'error'); } finally { setSavingPermissions(false); } }

  return <AdminShell title="Roles y permisos" description="Define qué puede hacer cada rol dentro del sistema." activePage="/admin/usuarios"><div className="page-actions react-roles-actions"><a className="btn btn-secondary" href="/admin/usuarios" onClick={(event) => { event.preventDefault(); window.history.pushState({}, '', '/admin/usuarios'); window.dispatchEvent(new PopStateEvent('popstate')); }}>Volver a usuarios</a>{canManage && <button className="btn btn-primary" type="button" onClick={() => setEditing(null)}>+ Nuevo rol</button>}</div>{error ? <ErrorState message={error} /> : loading ? <LoadingState label="Cargando roles…" /> : <div className="react-roles-layout"><section className="table-card react-roles-list"><div className="react-roles-list-inner">{roles.length ? roles.map((role) => <button className={`react-role-list-item${role.id === selectedId ? ' is-selected' : ''}`} type="button" key={role.id} onClick={() => setSelectedId(role.id)}><span>{role.name}</span>{role.isSystem && <span className="badge badge-neutral">Sistema</span>}</button>) : <EmptyState>No hay roles disponibles.</EmptyState>}</div></section><section className="table-card react-role-detail">{selectedRole ? <><div className="react-role-detail-head"><div><h2>{selectedRole.name}</h2><p className="mono">{selectedRole.code}</p>{selectedRole.description && <p className="react-role-description">{selectedRole.description}</p>}<p className="field-hint">Techo de asignación: <strong>{selectedRole.hierarchyLevel}</strong></p></div>{canManage && <button className="btn btn-secondary btn-sm" type="button" onClick={() => setEditing(selectedRole)}>Editar datos</button>}</div><form onSubmit={savePermissions}><div className="react-permission-groups">{Object.entries(permissionsByModule).length ? Object.entries(permissionsByModule).map(([module, modulePermissions]) => <fieldset key={module}><legend>{module}</legend>{modulePermissions.map((permission) => <label className="checkbox-field" key={permission.id}><input type="checkbox" checked={selectedPermissionIds.includes(permission.id)} disabled={!canManage || selectedRole.isSystem || permission.code === 'USUARIOS_CAMBIAR_CONTRASENA' || permission.code === 'USUARIOS_RESETEAR_CONTRASENA'} onChange={() => togglePermission(permission.id)} /><span>{permission.description || permission.code}<small>{permission.code}</small></span></label>)}</fieldset>) : <p className="field-hint">No tienes permiso para administrar el catálogo de permisos. Puedes consultar los roles disponibles.</p>}</div>{canManage && <div className="react-dialog-actions"><button className="btn btn-primary" type="submit" disabled={savingPermissions || selectedRole.isSystem}>{selectedRole.isSystem ? 'Rol de sistema' : savingPermissions ? 'Guardando…' : 'Guardar permisos'}</button></div>}</form></> : <EmptyState>Selecciona un rol para ver sus permisos.</EmptyState>}</section></div>}{editing !== undefined && <RoleFormDialog role={editing || undefined} onClose={() => setEditing(undefined)} onSaved={saveRole} />}</AdminShell>;
}
