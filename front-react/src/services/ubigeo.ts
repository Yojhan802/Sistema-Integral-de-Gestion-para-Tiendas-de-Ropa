import data from '../../../front/tienda/js/store/data/peru-ubigeo.json';

interface UbigeoItem { id: string; nombre: string; }
interface UbigeoData {
  departamentos: UbigeoItem[];
  provincias: Record<string, UbigeoItem[]>;
  distritos: Record<string, UbigeoItem[]>;
}

const ubigeo = data as UbigeoData;

export function getDepartamentos() { return ubigeo.departamentos; }
export function getProvincias(departamentoId: string) { return ubigeo.provincias[departamentoId] ?? []; }
export function getDistritos(provinciaId: string) { return ubigeo.distritos[provinciaId] ?? []; }
