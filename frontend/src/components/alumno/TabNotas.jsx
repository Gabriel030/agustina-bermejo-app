import { Fragment } from 'react';
import { useQueries } from '@tanstack/react-query';
import { Lock } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { StatusPill } from '@/components/StatusPill';
import { Paginador } from '@/components/catalogo/Paginador';
import { EstadoColumnFilter } from '@/components/alumno/EstadoColumnFilter';
import { AnioColumnFilter } from '@/components/alumno/AnioColumnFilter';
import { AnioGroupHeaderRow } from '@/components/alumno/AnioGroupHeaderRow';
import { SortableColumnHeader } from '@/components/alumno/SortableColumnHeader';
import { apiFetch } from '@/api/client';
import { useGrillaDeMaterias } from '@/hooks/useGrillaDeMaterias';
import { COLUMNAS_NOTAS, FINAL_GRADE_TYPE_LABEL, mapearNotasAColumnas } from '@/lib/notas';

const ESTADO_PILL = {
  'en-curso': { label: 'En curso', tone: 'accent' },
  regular: { label: 'Regular · falta final', tone: 'warning' },
  aprobada: { label: 'Aprobada', tone: 'success' },
  disponible: { label: 'Disponible', tone: 'accent' },
  bloqueada: { label: 'Bloqueada', tone: 'neutral' },
};

// Pestaña "Notas" de Mis carreras (Épica 2 Fase 2) — SOLO LECTURA: la grilla
// completa de notas, agrupada por año curricular, sin ninguna fila
// clickeable. Es la contraparte de TabMaterias (esa sí navega, materia por
// materia) — decisión de UX explícita del usuario: mezclar "ver notas" con
// "navegar a un detalle" generaba una navegación confusa. Mismos filtros
// (estado, año) que TabMaterias — pedido explícito del usuario.
export function TabNotas({ carreraId }) {
  const { pagina, page, setPage, totalPages, cargando, estado, cambiarEstado, anio, cambiarAnio, anios, toggleOrden, estadoDe, hayOrdenExplicito } =
    useGrillaDeMaterias(carreraId);

  // Solo las filas con una inscripción real tienen grade-items que pedir —
  // "disponible"/"bloqueada" nunca tuvieron un SectionEnrollment, no hay nada que fetchear.
  const notasQueries = useQueries({
    queries: pagina.map((fila) => ({
      queryKey: ['me', 'section-enrollments', fila.inscripcion?.id, 'grade-items'],
      queryFn: () => apiFetch(`/me/section-enrollments/${fila.inscripcion.id}/grade-items`),
      enabled: !!fila.inscripcion,
    })),
  });

  let anioAnterior;

  return (
    <div className="flex min-h-full flex-1 flex-col gap-5">
      <GlassCard className="flex flex-col p-1">
        {/* El header (con los filtros de Año/Estado embebidos) se renderiza SIEMPRE,
            nunca solo cuando hay resultados — si no, filtrar a una combinación sin
            coincidencias hacía desaparecer la tabla ENTERA junto con los controles
            para poder cambiar el filtro, dejando al usuario sin forma de volver
            atrás sin recargar la página. Bug real reportado por el usuario. */}
        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] border-collapse text-sm">
            <thead>
              <tr>
                <th className="border-b border-border px-4 py-2.5 text-left text-xs font-semibold text-ink-faint">Código</th>
                <th className="border-b border-border px-4 py-2.5 text-left">
                  <SortableColumnHeader label="Materia" sortId="subjectName" sortState={estadoDe('subjectName')} onToggle={toggleOrden} />
                </th>
                <th className="border-b border-border px-3 py-2.5 text-left">
                  <SortableColumnHeader
                    label="Año"
                    sortId="curricularYear"
                    sortState={estadoDe('curricularYear')}
                    onToggle={toggleOrden}
                    extra={<AnioColumnFilter anios={anios} value={anio} onChange={cambiarAnio} />}
                  />
                </th>
                {COLUMNAS_NOTAS.map((columna) => (
                  <th key={columna.key} className="border-b border-border px-3 py-2.5 text-left text-xs font-semibold text-ink-faint">
                    {columna.label}
                  </th>
                ))}
                <th className="border-b border-border px-3 py-2.5 text-left">
                  <SortableColumnHeader
                    label="Estado"
                    sortId="estado"
                    sortState={estadoDe('estado')}
                    onToggle={toggleOrden}
                    extra={<EstadoColumnFilter value={estado} onChange={cambiarEstado} />}
                  />
                </th>
              </tr>
            </thead>
            <tbody>
              {cargando ? (
                <tr>
                  <td colSpan={COLUMNAS_NOTAS.length + 4} className="p-4">
                    <div className="flex flex-col gap-2">
                      <Skeleton className="h-10" />
                      <Skeleton className="h-10" />
                      <Skeleton className="h-10" />
                    </div>
                  </td>
                </tr>
              ) : pagina.length === 0 ? (
                <tr>
                  <td colSpan={COLUMNAS_NOTAS.length + 4}>
                    <EmptyState title="Todavía no tenés notas" description="Acá vas a ver tus notas apenas tus docentes las carguen." />
                  </td>
                </tr>
              ) : (
                pagina.map((fila, i) => {
                  const pill = ESTADO_PILL[fila.estado];
                  const tieneNotas = fila.estado === 'en-curso' || fila.estado === 'regular' || fila.estado === 'aprobada';
                  const query = notasQueries[i];
                  const { columnas } = tieneNotas ? mapearNotasAColumnas(query?.data) : { columnas: {} };
                  const nuevoGrupo = !hayOrdenExplicito && fila.curricularYear !== anioAnterior;
                  anioAnterior = fila.curricularYear;

                  return (
                    <Fragment key={fila.key}>
                      {nuevoGrupo && <AnioGroupHeaderRow anio={fila.curricularYear} colSpan={COLUMNAS_NOTAS.length + 4} />}
                      <tr className={`border-b border-border last:border-b-0 ${fila.estado === 'bloqueada' ? 'opacity-60' : ''}`}>
                        <td className="px-4 py-3 text-[13px] font-semibold text-ink-faint">{fila.subjectCode ?? '—'}</td>
                        <td className="px-4 py-3">
                          <span className="text-[13.5px] font-semibold text-ink">{fila.subjectName}</span>
                        </td>
                        <td className="px-3 py-3 text-[13px] text-ink-faint">{fila.curricularYear ? `${fila.curricularYear}°` : '—'}</td>
                        {fila.estado === 'bloqueada' ? (
                          <td colSpan={COLUMNAS_NOTAS.length} className="px-3 py-3 text-[13px] text-ink-faint">
                            {fila.motivoBloqueo}
                          </td>
                        ) : (
                          COLUMNAS_NOTAS.map((columna) => (
                            <td key={columna.key} className="px-3 py-3 font-semibold text-ink">
                              {query?.isLoading ? (
                                <Skeleton className="h-4 w-8" />
                              ) : (
                                (columnas[columna.key] ?? <span className="font-normal text-ink-faint">—</span>)
                              )}
                            </td>
                          ))
                        )}
                        <td className="px-3 py-3">
                          <div className="flex flex-col gap-0.5">
                            <StatusPill tone={pill.tone} dot={fila.estado === 'bloqueada'}>
                              {fila.estado === 'bloqueada' && <Lock className="h-3 w-3" />}
                              {pill.label}
                            </StatusPill>
                            {fila.estado === 'aprobada' && fila.inscripcion?.finalGradeType && (
                              <span className="text-[11px] text-ink-faint">{FINAL_GRADE_TYPE_LABEL[fila.inscripcion.finalGradeType]}</span>
                            )}
                          </div>
                        </td>
                      </tr>
                    </Fragment>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </GlassCard>

      <Paginador page={page} totalPages={totalPages} onChange={setPage} />
    </div>
  );
}
