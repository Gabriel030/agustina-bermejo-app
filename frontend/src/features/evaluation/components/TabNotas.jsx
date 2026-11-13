import { StablePageHeight } from '@/components/StablePageHeight';
import { Fragment } from 'react';
import { useQueries } from '@tanstack/react-query';
import { Lock } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { StatusPill } from '@/components/StatusPill';
import { Paginador } from '@/components/Paginador';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import { EstadoColumnFilter } from '@/components/alumno/EstadoColumnFilter';
import { AnioColumnFilter } from '@/components/alumno/AnioColumnFilter';
import { AnioGroupHeaderRow } from '@/components/alumno/AnioGroupHeaderRow';
import { SortableColumnHeader } from '@/components/alumno/SortableColumnHeader';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { apiFetch } from '@/api/client';
import { useGrillaDeMaterias } from '@/hooks/useGrillaDeMaterias';
import { COLUMNAS_NOTAS, FINAL_GRADE_TYPE_LABEL, mapearNotasAColumnas } from '@/lib/notas';

function GradeWithCorrection({ score, correction }) {
  if (!correction) return score;
  const previous = String(correction.previousScore).replace('.', ',');
  return (
    <div className="flex flex-col items-start gap-0.5">
      <span>
        {String(score).replace('.', ',')} <span className="text-xs font-normal text-ink-faint">{`antes ${previous}`}</span>
      </span>
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>
            <span tabIndex={0} className="w-fit rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
              <StatusPill tone="accent">Corregida</StatusPill>
            </span>
          </TooltipTrigger>
          <TooltipContent>{`Antes: ${previous}. Motivo: ${correction.reason}`}</TooltipContent>
        </Tooltip>
      </TooltipProvider>
    </div>
  );
}

const ESTADO_PILL = {
  'en-curso': { label: 'En curso', tone: 'accent' },
  regular: { label: 'Regular · falta final', tone: 'warning' },
  aprobada: { label: 'Aprobada', tone: 'success' },
  desaprobada: { label: 'Desaprobada', tone: 'danger' },
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
  const { pagina, page, setPage, totalPages, cargando, fallo, recargar, estado, cambiarEstado, anio, cambiarAnio, anios, toggleOrden, estadoDe, hayOrdenExplicito } =
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
    <div className="flex flex-1 flex-col gap-5">
      <StablePageHeight resetKey={`${estado}-${anio}`}>
      <GlassCard className="flex flex-col p-1">
        {/* El header (con los filtros de Año/Estado embebidos) se renderiza SIEMPRE,
            nunca solo cuando hay resultados — si no, filtrar a una combinación sin
            coincidencias hacía desaparecer la tabla ENTERA junto con los controles
            para poder cambiar el filtro, dejando al usuario sin forma de volver
            atrás sin recargar la página. Bug real reportado por el usuario. */}
        <div className="overflow-x-auto">
        <Table className="min-w-[640px]">
          <TableHeader>
            <TableRow>
              <TableHead>Código</TableHead>
              <TableHead>
                <SortableColumnHeader label="Materia" sortId="subjectName" sortState={estadoDe('subjectName')} onToggle={toggleOrden} />
              </TableHead>
              <TableHead>
                <SortableColumnHeader
                  label="Año"
                  sortId="curricularYear"
                  sortState={estadoDe('curricularYear')}
                  onToggle={toggleOrden}
                  extra={<AnioColumnFilter anios={anios} value={anio} onChange={cambiarAnio} />}
                />
              </TableHead>
              {COLUMNAS_NOTAS.map((columna) => (
                <TableHead key={columna.key}>{columna.label}</TableHead>
              ))}
              <TableHead>
                <SortableColumnHeader
                  label="Estado"
                  sortId="estado"
                  sortState={estadoDe('estado')}
                  onToggle={toggleOrden}
                  extra={<EstadoColumnFilter value={estado} onChange={cambiarEstado} />}
                />
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {cargando ? (
              <TableRow>
                <TableCell colSpan={COLUMNAS_NOTAS.length + 4} className="p-4">
                  <div className="flex flex-col gap-2">
                    <Skeleton className="h-10" />
                    <Skeleton className="h-10" />
                    <Skeleton className="h-10" />
                  </div>
                </TableCell>
              </TableRow>
            ) : fallo ? (
              <TableRow>
                <TableCell colSpan={COLUMNAS_NOTAS.length + 4}>
                  <LoadErrorState onRetry={recargar} resource="tus notas" bare />
                </TableCell>
              </TableRow>
            ) : pagina.length === 0 ? (
              <TableRow>
                <TableCell colSpan={COLUMNAS_NOTAS.length + 4}>
                  <EmptyState title="Todavía no tenés notas" description="Acá vas a ver tus notas apenas tus docentes las carguen." />
                </TableCell>
              </TableRow>
            ) : (
              pagina.map((fila, i) => {
                const pill = ESTADO_PILL[fila.estado];
                const tieneNotas = !!fila.inscripcion;
                const query = notasQueries[i];
                const { columnas, correcciones } = tieneNotas ? mapearNotasAColumnas(query?.data) : { columnas: {}, correcciones: {} };
                const nuevoGrupo = !hayOrdenExplicito && fila.curricularYear !== anioAnterior;
                anioAnterior = fila.curricularYear;

                return (
                  <Fragment key={fila.key}>
                    {nuevoGrupo && <AnioGroupHeaderRow anio={fila.curricularYear} colSpan={COLUMNAS_NOTAS.length + 4} />}
                    <TableRow className={fila.estado === 'bloqueada' ? 'opacity-60' : ''}>
                      <TableCell className="font-semibold text-ink-faint">{fila.subjectCode ?? '—'}</TableCell>
                      <TableCell>
                        <span className="text-size-13-5 font-semibold text-ink">{fila.subjectName}</span>
                      </TableCell>
                      <TableCell className="text-ink-faint">{fila.curricularYear ? `${fila.curricularYear}°` : '—'}</TableCell>
                      {fila.estado === 'bloqueada' ? (
                        <TableCell colSpan={COLUMNAS_NOTAS.length} className="text-ink-faint">
                          {fila.motivoBloqueo}
                        </TableCell>
                      ) : (
                        COLUMNAS_NOTAS.map((columna) => (
                          <TableCell key={columna.key} className="font-semibold text-ink">
                            {query?.isLoading ? (
                              <Skeleton className="h-4 w-8" />
                            ) : (
                              (columnas[columna.key] == null ? (
                                <span className="font-normal text-ink-faint">—</span>
                              ) : (
                                <GradeWithCorrection score={columnas[columna.key]} correction={correcciones[columna.key]} />
                              ))
                            )}
                          </TableCell>
                        ))
                      )}
                      <TableCell>
                        <div className="flex flex-col gap-0.5">
                          <StatusPill tone={pill.tone} dot={fila.estado === 'bloqueada'}>
                            {fila.estado === 'bloqueada' && <Lock className="h-3 w-3" />}
                            {pill.label}
                          </StatusPill>
                          {fila.estado === 'aprobada' && fila.inscripcion?.finalGradeType && (
                            <span className="text-size-11 text-ink-faint">{FINAL_GRADE_TYPE_LABEL[fila.inscripcion.finalGradeType]}</span>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  </Fragment>
                );
              })
            )}
          </TableBody>
        </Table>
        </div>
      </GlassCard>
      </StablePageHeight>

      <Paginador page={page} totalPages={totalPages} onChange={setPage} />
    </div>
  );
}
