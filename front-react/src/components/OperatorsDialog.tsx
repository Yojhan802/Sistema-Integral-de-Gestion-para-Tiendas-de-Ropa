import { useEffect, useState } from 'react';
import { ApiError, api, getStaffSession } from '../services/api';
import { showToast } from './ToastHost';
import { EmptyState, ErrorState, LoadingState } from './States';

type UsuarioEmpresa = { id: number; username: string; fullName: string; status: string; platformOperator: boolean };

/**
 * Concede o retira el acceso al módulo Empresas.
 *
 * <p>No es un permiso de rol a propósito: un Administrador de tienda no debe poder darse
 * de alta empresas a sí mismo. Hasta ahora esta marca solo se podía cambiar con un UPDATE
 * a mano en la base de datos.
 */
export function OperatorsDialog({ tenantId, empresa, onClose }: { tenantId: number; empresa: string; onClose: () => void }) {
  const [usuarios, setUsuarios] = useState<UsuarioEmpresa[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [guardandoId, setGuardandoId] = useState<number | null>(null);
  const propioId = getStaffSession()?.user.id;

  useEffect(() => {
    let vivo = true;
    api.get<UsuarioEmpresa[]>(`/platform/tenants/${tenantId}/users`, { auth: 'staff' })
      .then((respuesta) => { if (vivo) setUsuarios(respuesta ?? []); })
      .catch((razon) => { if (vivo) setError(razon instanceof ApiError ? razon.message : 'No se pudieron cargar los usuarios.'); })
      .finally(() => { if (vivo) setCargando(false); });
    return () => { vivo = false; };
  }, [tenantId]);

  async function alternar(usuario: UsuarioEmpresa) {
    setGuardandoId(usuario.id); setError('');
    try {
      const actualizados = await api.patch<UsuarioEmpresa[]>(
        `/platform/tenants/${tenantId}/users/${usuario.id}/operator`,
        { operador: !usuario.platformOperator }, { auth: 'staff' });
      setUsuarios(actualizados ?? []);
      showToast(`${usuario.username} ${usuario.platformOperator ? 'ya no administra' : 'ahora administra'} la plataforma.`,
        'Acceso actualizado', 'info');
    } catch (razon) {
      setError(razon instanceof ApiError ? razon.message : 'No se pudo cambiar el acceso.');
    } finally { setGuardandoId(null); }
  }

  return <div className="react-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) onClose(); }}>
    <section className="react-dialog react-operators-dialog" role="dialog" aria-modal="true" aria-labelledby="react-operators-title">
      <div className="react-dialog-header">
        <div>
          <h2 id="react-operators-title">Acceso a Empresas · {empresa}</h2>
          <p className="field-hint">Quien lo tenga podrá dar de alta empresas y editar los módulos de todas.</p>
        </div>
        <button className="btn btn-ghost btn-sm" type="button" aria-label="Cerrar" onClick={onClose}>×</button>
      </div>

      {cargando ? <LoadingState label="Cargando usuarios…" />
        : !usuarios.length ? <EmptyState>Esta empresa no tiene usuarios.</EmptyState>
        : <ul className="react-operators-list">
            {usuarios.map((usuario) => <li key={usuario.id}>
              <label className="react-module-check">
                <input type="checkbox" checked={usuario.platformOperator} disabled={guardandoId === usuario.id}
                  onChange={() => alternar(usuario)} />
                <span>
                  <strong>{usuario.fullName || usuario.username}</strong>
                  <small className="mono">{usuario.username}{usuario.id === propioId ? ' · tú' : ''}</small>
                  {usuario.status !== 'ACTIVE' && <small className="react-module-locked">Usuario {usuario.status.toLowerCase()}</small>}
                </span>
              </label>
            </li>)}
          </ul>}

      {error && <div className="alert alert-danger" role="alert">{error}</div>}
      <div className="react-dialog-actions"><button className="btn btn-secondary" type="button" onClick={onClose}>Cerrar</button></div>
    </section>
  </div>;
}
