import { useEffect, useState } from 'react';
import { ApiError, api } from '../services/api';
import { showToast } from '../components/ToastHost';
import { ErrorState, LoadingState } from './States';

export type ModuloCode = string;

export type Modulo = {
  code: ModuloCode;
  nombre: string;
  descripcion: string;
  tipo: 'NUCLEO' | 'OPCIONAL' | 'LEGAL';
  contratado: boolean;
  incluidoPorDependencia: boolean;
  bloqueado: boolean;
  motivoBloqueo?: string | null;
  precioMensual: number;
  precioLista: number;
  requiere: ModuloCode[];
};

export type ModulosTenant = {
  tenantId: number;
  empresa: string;
  plan: string;
  modulos: Modulo[];
  totalMensual: number;
  presets: Record<string, ModuloCode[]>;
};

const planLabels: Record<string, string> = {
  STARTER: 'Básico', PROFESIONAL: 'Profesional', ECOMMERCE: 'Ecommerce', IA: 'IA',
};

const soles = (valor: number) => `S/ ${Number(valor || 0).toFixed(2)}`;

export type Seleccion = Record<ModuloCode, number>;

/** Devuelve la selección tras marcar o desmarcar un módulo, con sus dependencias. */
export function alternarModulo(catalogo: Modulo[], seleccion: Seleccion, modulo: Modulo): Seleccion {
  const siguiente = { ...seleccion };
  if (siguiente[modulo.code] !== undefined) {
    delete siguiente[modulo.code];
    return siguiente;
  }
  siguiente[modulo.code] = Number(modulo.precioLista);
  // Las dependencias entran a cero: el cliente paga el módulo que pidió, no la
  // infraestructura que ese módulo necesita.
  dependenciasDe(catalogo, modulo.code).forEach((dependencia) => {
    if (siguiente[dependencia] === undefined) siguiente[dependencia] = 0;
  });
  if (modulo.code === 'TIENDA' && siguiente.RECLAMOS === undefined) siguiente.RECLAMOS = 0;
  return siguiente;
}

export function totalDe(seleccion: Seleccion) {
  return Object.values(seleccion).reduce((suma, precio) => suma + Number(precio || 0), 0);
}

export function seleccionDePreset(catalogo: Modulo[], codigos: ModuloCode[]): Seleccion {
  const porCodigo = new Map(catalogo.map((m) => [m.code, m]));
  return Object.fromEntries(codigos.map((code) => [code, Number(porCodigo.get(code)?.precioLista ?? 0)]));
}

/**
 * Lista de módulos con sus dependencias bloqueadas y el total en vivo. La usan el alta y
 * la edición: el comportamiento tiene que ser idéntico en ambas.
 */
export function ModulePicker({ catalogo, presets, seleccion, onChange }: {
  catalogo: Modulo[]; presets: Record<string, ModuloCode[]>;
  seleccion: Seleccion; onChange: (siguiente: Seleccion) => void;
}) {
  const activos = new Set(Object.keys(seleccion));
  const dependientesDe = (code: ModuloCode) => catalogo
    .filter((m) => activos.has(m.code) && m.code !== code && requiereTransitivamente(catalogo, m.code, code))
    .map((m) => m.nombre);
  const bloqueoDe = (modulo: Modulo): string | null => {
    if (modulo.tipo === 'NUCLEO') return 'El sistema no funciona sin este módulo';
    if (!activos.has(modulo.code)) return null;
    if (modulo.tipo === 'LEGAL' && activos.has('TIENDA')) return 'Obligatorio por ley mientras venda por internet';
    const dependientes = dependientesDe(modulo.code);
    return dependientes.length ? `Lo necesitan: ${dependientes.join(', ')}` : null;
  };

  return <>
    <div className="react-modules-presets">
      <span className="field-label">Partir de un plan</span>
      <div className="react-modules-preset-buttons">
        {Object.keys(presets || {}).map((plan) => <button className="btn btn-secondary btn-sm" type="button" key={plan}
          onClick={() => onChange(seleccionDePreset(catalogo, presets[plan] ?? []))}>{planLabels[plan] || plan}</button>)}
        <button className="btn btn-ghost btn-sm" type="button" onClick={() => onChange({})}>Vaciar</button>
      </div>
    </div>

    <ul className="react-modules-list">
      {catalogo.map((modulo) => {
        const activo = activos.has(modulo.code);
        const motivo = bloqueoDe(modulo);
        const porDependencia = activo && Boolean(dependientesDe(modulo.code).length);
        return <li className={`react-module-row${activo ? ' is-active' : ''}`} key={modulo.code}>
          <label className="react-module-check">
            <input type="checkbox" checked={activo} disabled={Boolean(motivo)}
              onChange={() => onChange(alternarModulo(catalogo, seleccion, modulo))} />
            <span>
              <strong>{modulo.nombre}</strong>
              <small>{modulo.descripcion}</small>
              {motivo && <small className="react-module-locked">{motivo}</small>}
            </span>
          </label>
          <div className="react-module-price">
            {activo ? <>
              <label className="sr-only" htmlFor={`precio-${modulo.code}`}>Precio de {modulo.nombre}</label>
              <input id={`precio-${modulo.code}`} className="input" type="number" min="0" step="0.01"
                value={seleccion[modulo.code] ?? 0}
                onChange={(event) => onChange({ ...seleccion, [modulo.code]: Number(event.target.value) })} />
              {porDependencia && <small>incluido</small>}
            </> : <small className="react-module-list-price">lista {soles(modulo.precioLista)}</small>}
          </div>
        </li>;
      })}
    </ul>
  </>;
}

/**
 * Editor del paquete contratado por una empresa.
 *
 * <p>El cierre de dependencias lo hace el servidor; aquí solo se refleja para que el
 * operador entienda por qué un módulo no se puede quitar. El total se recalcula en vivo
 * porque el flujo real es al revés de lo habitual: se parte del presupuesto del cliente
 * y se va recortando hasta que entra.
 */
type CambioPaquete = {
  fecha: string; usuario?: string | null;
  totalAnterior: number; totalNuevo: number;
  agregados: string[]; quitados: string[];
};

/** Historial de quién movió el paquete y cuándo: sustenta una factura discutida. */
function HistorialPaquete({ cambios }: { cambios: CambioPaquete[] }) {
  if (!cambios.length) return null;
  const fecha = (valor: string) => new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(valor));
  return <details className="react-modules-history">
    <summary>Historial de cambios ({cambios.length})</summary>
    <ul>
      {cambios.map((cambio, indice) => {
        const subio = Number(cambio.totalNuevo) > Number(cambio.totalAnterior);
        return <li key={`${cambio.fecha}-${indice}`}>
          <div className="react-history-head">
            <span>{fecha(cambio.fecha)}</span>
            <strong className={subio ? 'is-subida' : 'is-bajada'}>
              {soles(cambio.totalAnterior)} → {soles(cambio.totalNuevo)}
            </strong>
          </div>
          <small>{cambio.usuario ? `Por ${cambio.usuario}` : 'Cambio automático'}</small>
          {cambio.agregados.length > 0 && <small className="react-history-add">+ {cambio.agregados.join(', ')}</small>}
          {cambio.quitados.length > 0 && <small className="react-history-remove">− {cambio.quitados.join(', ')}</small>}
        </li>;
      })}
    </ul>
  </details>;
}

export function ModulesDialog({ tenantId, onClose }: { tenantId: number; onClose: () => void }) {
  const [estado, setEstado] = useState<ModulosTenant | null>(null);
  const [historial, setHistorial] = useState<CambioPaquete[]>([]);
  const [seleccion, setSeleccion] = useState<Record<ModuloCode, number>>({});
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let vivo = true;
    api.get<ModulosTenant>(`/platform/tenants/${tenantId}/modules`, { auth: 'staff' })
      .then((respuesta) => {
        if (!vivo) return;
        setEstado(respuesta);
        setSeleccion(Object.fromEntries(respuesta.modulos.filter((m) => m.contratado)
          .map((m) => [m.code, Number(m.precioMensual)])));
      })
      .catch((razon) => { if (vivo) setError(razon instanceof ApiError ? razon.message : 'No se pudieron cargar los módulos.'); })
      .finally(() => { if (vivo) setCargando(false); });
    api.get<CambioPaquete[]>(`/platform/tenants/${tenantId}/modules/history`, { auth: 'staff' })
      .then((respuesta) => { if (vivo) setHistorial(respuesta ?? []); })
      .catch(() => undefined);
    return () => { vivo = false; };
  }, [tenantId]);

  const catalogo = estado?.modulos ?? [];

  const total = totalDe(seleccion);

  async function guardar() {
    setGuardando(true); setError('');
    try {
      const actualizado = await api.put<ModulosTenant>(`/platform/tenants/${tenantId}/modules`,
        { modulos: Object.entries(seleccion).map(([code, precioMensual]) => ({ code, precioMensual })) },
        { auth: 'staff' });
      setEstado(actualizado);
      setSeleccion(Object.fromEntries(actualizado.modulos.filter((m) => m.contratado)
        .map((m) => [m.code, Number(m.precioMensual)])));
      showToast(`Paquete guardado: ${soles(actualizado.totalMensual)} al mes.`, 'Módulos actualizados', 'info');
      // El cambio recién guardado tiene que aparecer sin recargar el diálogo.
      api.get<CambioPaquete[]>(`/platform/tenants/${tenantId}/modules/history`, { auth: 'staff' })
        .then((respuesta) => setHistorial(respuesta ?? [])).catch(() => undefined);
    } catch (razon) {
      setError(razon instanceof ApiError ? razon.message : 'No se pudo guardar el paquete.');
    } finally { setGuardando(false); }
  }

  return <div className="react-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) onClose(); }}>
    <section className="react-dialog react-dialog-wide react-modules-dialog" role="dialog" aria-modal="true" aria-labelledby="react-modules-title">
      <div className="react-dialog-header">
        <div>
          <h2 id="react-modules-title">Módulos de {estado?.empresa || 'la empresa'}</h2>
          <p className="field-hint">Marca solo lo que el cliente va a usar. Las dependencias se activan solas y no se cobran.</p>
        </div>
        <button className="btn btn-ghost btn-sm" type="button" aria-label="Cerrar" onClick={onClose}>×</button>
      </div>

      {cargando ? <LoadingState label="Cargando módulos…" /> : !estado ? <ErrorState message={error || 'No se pudo cargar.'} /> : <>
        <ModulePicker catalogo={catalogo} presets={estado.presets} seleccion={seleccion} onChange={setSeleccion} />

        <HistorialPaquete cambios={historial} />

        {error && <div className="alert alert-danger" role="alert">{error}</div>}

        <div className="react-modules-footer">
          <div className="react-modules-total">
            <span>Total mensual</span>
            <strong>{soles(total)}</strong>
          </div>
          <div className="react-dialog-actions">
            <button className="btn btn-secondary" type="button" onClick={onClose}>Cerrar</button>
            <button className="btn btn-primary" type="button" disabled={guardando} onClick={guardar}>{guardando ? 'Guardando…' : 'Guardar paquete'}</button>
          </div>
        </div>
      </>}
    </section>
  </div>;
}

/** Dependencias directas e indirectas declaradas por el catálogo del servidor. */
function dependenciasDe(catalogo: Modulo[], code: ModuloCode, vistos = new Set<ModuloCode>()): ModuloCode[] {
  const modulo = catalogo.find((m) => m.code === code);
  if (!modulo) return [];
  modulo.requiere.forEach((requisito) => {
    if (vistos.has(requisito)) return;
    vistos.add(requisito);
    dependenciasDe(catalogo, requisito, vistos);
  });
  return [...vistos];
}

function requiereTransitivamente(catalogo: Modulo[], code: ModuloCode, objetivo: ModuloCode): boolean {
  return dependenciasDe(catalogo, code).includes(objetivo);
}
