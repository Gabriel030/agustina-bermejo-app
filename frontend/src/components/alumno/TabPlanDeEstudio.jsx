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

const ESTADO_PILL = {
  'en-curso': { label: 'En curso', tone: 'accent' },
  regular: { label: 'Regular · falta final', tone: 'warning' },
  aprobada: { label: 'Aprobada', tone: 'success' },
  disponible: { label: 'Disponible', tone: 'accent' },
  bloqueada: { label: 'Bloqueada', tone: 'neutral' },
};

// Tipología = Cuatrimestral o Anual (si se cursa en un solo cuatrimestre o
// todo el año lectivo) — CurricularTerm.FIRST/SECOND son los dos casos
// "Cuatrimestral", distinguidos entre paréntesis; ANNUAL es la única
// tipología "Anual". Antes esta columna mostraba "1er/2do cuatrimestre"
// como si esa fuera la tipología, confundiendo el CUÁNDO con el QUÉ TIPO.
const TIPOLOGIA = { FIRST: 'Cuatrimestral (1°)', SECOND: 'Cuatrimestral (2°)', ANNUAL: 'Anual' };

// Horas semanales — MOCK, no existe ese dato en el backend todavía (ProgramSubject
// no tiene un campo de carga horaria). Determinístico por créditos, para que la
// demo sea estable: más créditos, más horas — es una relación razonable aunque el
// número puntual sea de ejemplo.
function horasSemanalesMock(credits) {
  if (credits == null) return null;
  return Math.max(2, Math.round(credits / 2));
}

// Pestaña "Plan de estudio" de Mis carreras (Épica 2 Fase 2) — materias por año con
// horas semanales (mock), créditos, correlativas y tipología. Correlativas: solo los
// requisitos INMEDIATOS/explícitos de cada materia (GET /program-subjects/{id}/prerequisites,
// dato real) — nunca la cadena completa de materias previas, pedido explícito del
// usuario ("no voy a poner todas las materias anteriores... sino las que pusimos
// como requisito específico"). Mismos filtros/agrupamiento por año y estado que el
// resto de las pestañas de Mis carreras.
// Criterio de orden propio de esta pestaña (además de Materia/Año/Estado, que
// ya trae useGrillaDeMaterias) — "Créditos" solo tiene sentido acá.
const CRITERIOS_EXTRA = [{ id: 'credits', accessor: (f) => f.credits }];

// Orden natural del plan de estudio (sin un orden explícito elegido por el
// usuario) — pedido explícito: año, después cuatrimestre (las cuatrimestrales
// antes que las anuales del mismo año, y entre las cuatrimestrales 1° antes
// que 2°), y recién dentro de ese segmento, por código. El orden por estado
// que usan Materias/Notas no tiene sentido acá: esta pestaña muestra el plan
// tal cual está diseñado, no el progreso del alumno sobre él.
const RANGO_CUATRIMESTRE = { FIRST: 0, SECOND: 1, ANNUAL: 2 };

function compararPlanDeEstudio(a, b) {
  const porAnio = (a.curricularYear ?? Infinity) - (b.curricularYear ?? Infinity);
  if (porAnio !== 0) return porAnio;
  const porCuatrimestre = (RANGO_CUATRIMESTRE[a.curricularTerm] ?? 99) - (RANGO_CUATRIMESTRE[b.curricularTerm] ?? 99);
  if (porCuatrimestre !== 0) return porCuatrimestre;
  return (a.subjectCode ?? '').localeCompare(b.subjectCode ?? '');
}

export function TabPlanDeEstudio({ carreraId }) {
  const {
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
    toggleOrden,
    estadoDe,
    hayOrdenExplicito,
  } = useGrillaDeMaterias(carreraId, CRITERIOS_EXTRA, compararPlanDeEstudio);

  const correlativasQueries = useQueries({
    queries: pagina.map((fila) => ({
      queryKey: ['program-subjects', fila.programSubjectId, 'prerequisites'],
      queryFn: () => apiFetch(`/program-subjects/${fila.programSubjectId}/prerequisites`),
      enabled: !!fila.programSubjectId,
    })),
  });

  let anioAnterior;

  return (
    <div className="flex flex-col gap-5">
      <GlassCard className="flex flex-col p-1">
        {/* El header (con los filtros de Año/Estado embebidos) se renderiza SIEMPRE —
            ver comentario del mismo patrón en TabMaterias.jsx/TabNotas.jsx/TabAsistencia.jsx. */}
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] border-collapse text-sm">
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
                <th className="border-b border-border px-3 py-2.5 text-left text-xs font-semibold text-ink-faint">Hs. semanales</th>
                <th className="border-b border-border px-3 py-2.5 text-left">
                  <SortableColumnHeader label="Créditos" sortId="credits" sortState={estadoDe('credits')} onToggle={toggleOrden} />
                </th>
                <th className="border-b border-border px-3 py-2.5 text-left text-xs font-semibold text-ink-faint">Correlativas</th>
                <th className="border-b border-border px-3 py-2.5 text-left text-xs font-semibold text-ink-faint">Tipología</th>
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
                  <td colSpan={8} className="p-4">
                    <div className="flex flex-col gap-2">
                      <Skeleton className="h-10" />
                      <Skeleton className="h-10" />
                      <Skeleton className="h-10" />
                    </div>
                  </td>
                </tr>
              ) : pagina.length === 0 ? (
                <tr>
                  <td colSpan={8}>
                    <EmptyState title="No hay materias para mostrar" description="Probá con otro filtro." />
                  </td>
                </tr>
              ) : (
                pagina.map((fila, i) => {
                  const pill = ESTADO_PILL[fila.estado];
                  const nuevoGrupo = !hayOrdenExplicito && fila.curricularYear !== anioAnterior;
                  anioAnterior = fila.curricularYear;
                  const query = correlativasQueries[i];
                  const correlativas = query?.data ?? [];

                  return (
                    <Fragment key={fila.key}>
                      {nuevoGrupo && <AnioGroupHeaderRow anio={fila.curricularYear} colSpan={8} />}
                      <tr className={`border-b border-border last:border-b-0 ${fila.estado === 'bloqueada' ? 'opacity-60' : ''}`}>
                        <td className="px-4 py-3 text-[13px] font-semibold text-ink-faint">{fila.subjectCode ?? '—'}</td>
                        <td className="px-4 py-3 text-[13.5px] font-semibold text-ink">{fila.subjectName}</td>
                        <td className="px-3 py-3 text-[13px] text-ink-faint">{fila.curricularYear ? `${fila.curricularYear}°` : '—'}</td>
                        <td className="px-3 py-3 text-[13px] text-ink-faint">{horasSemanalesMock(fila.credits) ?? '—'}</td>
                        <td className="px-3 py-3 text-[13px] text-ink-faint">{fila.credits ?? '—'}</td>
                        <td className="px-3 py-3 text-[13px] text-ink-faint">
                          {query?.isLoading ? (
                            <Skeleton className="h-4 w-24" />
                          ) : correlativas.length === 0 ? (
                            'Sin correlativas'
                          ) : (
                            // "Nombre + código" — pedido explícito del usuario, para que la
                            // correlativa quede inequívoca aunque haya materias con nombres
                            // parecidos.
                            correlativas.map((c) => `${c.prerequisiteSubjectName} (${c.prerequisiteSubjectCode})`).join(', ')
                          )}
                        </td>
                        <td className="px-3 py-3 text-[13px] text-ink-faint">{TIPOLOGIA[fila.curricularTerm] ?? '—'}</td>
                        <td className="px-3 py-3">
                          <StatusPill tone={pill.tone} dot={fila.estado === 'bloqueada'}>
                            {fila.estado === 'bloqueada' && <Lock className="h-3 w-3" />}
                            {pill.label}
                          </StatusPill>
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
