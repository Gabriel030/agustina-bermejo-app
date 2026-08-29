import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMateriasDelPlan } from '@/hooks/useMateriasDelPlan';
import { useOrdenamiento } from '@/hooks/useOrdenamiento';
import { aniosDisponibles, ORDEN_ESTADO } from '@/lib/notas';
import { construirComparador } from '@/lib/ordenamiento';

const ESTADO_A_FILTRO = { aprobadas: 'aprobada', 'en-curso': 'en-curso', regulares: 'regular', bloqueadas: 'bloqueada' };
const POR_PAGINA = 10;

// Campos ordenables comunes a las 4 pestañas de materia (Materias/Notas/
// Asistencia/Plan de estudio) — `criteriosExtra` permite sumar campos propios
// de una tabla puntual (ej. "credits" en Plan de estudio) sin duplicar esta
// lista en cada Tab*.jsx.
const CRITERIOS_BASE = [
  { id: 'subjectName', accessor: (f) => f.subjectName },
  { id: 'curricularYear', accessor: (f) => f.curricularYear },
  { id: 'estado', accessor: (f) => ORDEN_ESTADO[f.estado] },
];

// Estado compartido de filtros (estado + año), ordenamiento (1 o 2 criterios
// encadenados, ver useOrdenamiento.js) y paginación para las 4 grillas de
// materia (TabMaterias.jsx, TabNotas.jsx, TabAsistencia.jsx,
// TabPlanDeEstudio.jsx) — mismo criterio en las cuatro, pedido explícito del
// usuario. Todo en la URL (?estado=&anio=&sort=) para que sea
// compartible/recargable, igual que el resto de los filtros de la app.
// `comparadorDefault` reordena la lista de base cuando NO hay un orden
// explícito elegido por el usuario (ver hayOrdenExplicito más abajo) — por
// default es el orden que ya trae useMateriasDelPlan (año, estado, nombre).
// Plan de estudio pasa uno propio (año, cuatrimestre, código) porque ahí el
// estado de cursada no pinta el orden natural del plan.
export function useGrillaDeMaterias(carreraId, criteriosExtra = [], comparadorDefault) {
  const [searchParams, setSearchParams] = useSearchParams();
  const estado = searchParams.get('estado') ?? 'todas';
  const anio = searchParams.get('anio') ?? 'todos';
  const [page, setPage] = useState(1);

  const { filas: filasSinOrdenPropio, cargando } = useMateriasDelPlan(carreraId);
  const filas = comparadorDefault ? [...filasSinOrdenPropio].sort(comparadorDefault) : filasSinOrdenPropio;
  const anios = aniosDisponibles(filas);
  const { criterios, toggleOrden: toggleOrdenBase, estadoDe } = useOrdenamiento();
  function toggleOrden(id) {
    setPage(1);
    toggleOrdenBase(id);
  }

  const filtroEstado = ESTADO_A_FILTRO[estado];
  let filasFiltradas = filtroEstado ? filas.filter((f) => f.estado === filtroEstado) : filas;
  if (anio !== 'todos') {
    filasFiltradas = filasFiltradas.filter((f) => String(f.curricularYear) === anio);
  }

  // Sin un orden explícito elegido por el usuario, se respeta el orden por default
  // que ya trae useMateriasDelPlan (agrupado por año). Con un orden explícito, se
  // aplica ese comparador y se muestra como lista plana (ver hayOrdenExplicito,
  // que cada Tab*.jsx usa para no dibujar los separadores de año en ese caso).
  const hayOrdenExplicito = criterios.length > 0;
  const filasOrdenadas = hayOrdenExplicito
    ? [...filasFiltradas].sort(construirComparador(criterios, [...CRITERIOS_BASE, ...criteriosExtra]))
    : filasFiltradas;

  const totalPages = Math.max(1, Math.ceil(filasOrdenadas.length / POR_PAGINA));
  const pagina = filasOrdenadas.slice((page - 1) * POR_PAGINA, page * POR_PAGINA);

  function cambiarEstado(nuevoEstado) {
    setPage(1);
    if (nuevoEstado === 'todas') {
      searchParams.delete('estado');
    } else {
      searchParams.set('estado', nuevoEstado);
    }
    setSearchParams(searchParams, { replace: true });
  }

  function cambiarAnio(nuevoAnio) {
    setPage(1);
    if (nuevoAnio === 'todos') {
      searchParams.delete('anio');
    } else {
      searchParams.set('anio', nuevoAnio);
    }
    setSearchParams(searchParams, { replace: true });
  }

  return {
    pagina,
    page,
    setPage,
    totalPages,
    cargando,
    estado,
    cambiarEstado,
    anio,
    cambiarAnio,
    anios,
    totalFilas: filasOrdenadas.length,
    toggleOrden,
    estadoDe,
    hayOrdenExplicito,
  };
}
