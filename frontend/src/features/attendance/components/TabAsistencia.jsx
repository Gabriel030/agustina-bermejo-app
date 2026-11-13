import { Button } from '@/components/ui/button';
import { useState } from 'react';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { StatusPill } from '@/components/StatusPill';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useMisInscripciones } from '@/features/enrollment';
import { ESTADOS_EN_CURSO } from '@/lib/notas';
import { SeccionAsistencia } from '@/features/attendance/components/SeccionAsistencia';

const SUB_TABS = [
  { id: 'en-curso', label: 'En curso' },
  { id: 'cursadas', label: 'Cursadas' },
];

const ESTADO_PILL = {
  REGULAR: { label: 'Regular · falta final', tone: 'warning' },
  IN_PROGRESS: { label: 'En curso', tone: 'accent' },
  WAITLISTED: { label: 'En lista de espera', tone: 'warning' },
  WAITLIST_EXPIRED: { label: 'Lista de espera vencida', tone: 'neutral' },
  ENROLLED: { label: 'Inscripto', tone: 'accent' },
  APPROVED: { label: 'Aprobada', tone: 'success' },
  FAILED: { label: 'Desaprobada', tone: 'danger' },
  WITHDRAWN: { label: 'De baja', tone: 'neutral' },
};

// Pestaña "Asistencia" de Mis carreras (Épica 2 Fase 2) — rediseñada a pedido
// explícito del usuario sobre la versión anterior, que traía el plan de estudio
// COMPLETO (incluidas materias bloqueadas/disponibles, que un alumno nunca
// puede tener asistencia) y disparaba un fetch de asistencia por cada fila
// visible con useQueries, aunque solo las "en curso" tuvieran algo útil que
// mostrar ahí. Criterio nuevo:
// - Dos sub-pestañas En curso/Cursadas. Las dos filtran EN EL CLIENTE la misma
//   lista ya cacheada de /me/section-enrollments (useMisInscripciones, la
//   comparten Materias/Notas) — cambiar de sub-pestaña no dispara un fetch
//   nuevo al servidor, ese costo ya está pagado.
// - Cada fila despliega inline (acordeón, una sola abierta a la vez) el
//   detalle de asistencia — SeccionAsistencia (torta + calendario por mes) ya
//   pide sus propios datos recién al montarse, así que el fetch pesado
//   (listado de clases + riesgo) solo se paga para la materia que el alumno
//   realmente abre, no para todas las que están en curso.
export function TabAsistencia({ carreraId }) {
  const { data: inscripciones, isLoading, isError, error, refetch } = useMisInscripciones();
  const [subTab, setSubTab] = useState('en-curso');
  const [expandidoId, setExpandidoId] = useState(null);

  const deEstaCarrera = (inscripciones ?? []).filter((i) => !carreraId || i.programEnrollmentId === carreraId);
  const enCurso = deEstaCarrera.filter((i) => ESTADOS_EN_CURSO.has(i.status));
  const cursadas = deEstaCarrera.filter((i) => !ESTADOS_EN_CURSO.has(i.status));
  const lista = subTab === 'en-curso' ? enCurso : cursadas;

  function cambiarSubTab(id) {
    setSubTab(id);
    setExpandidoId(null);
  }

  function toggleFila(id) {
    setExpandidoId((actual) => (actual === id ? null : id));
  }

  return (
    <div className="flex flex-col gap-4">
      <Tabs value={subTab} onValueChange={cambiarSubTab}>
        <TabsList variant="segmented">
          {SUB_TABS.map(({ id, label }) => (
            <TabsTrigger key={id} value={id}>{label}</TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      {isLoading ? (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-16" />
          <Skeleton className="h-16" />
        </div>
      ) : isError ? (
        <LoadErrorState error={error} onRetry={refetch} resource="tus materias" bare />
      ) : lista.length === 0 ? (
        <div className="rounded-xl border border-border bg-surface p-8">
          <EmptyState
            title={subTab === 'en-curso' ? 'No tenés materias en curso' : 'Todavía no cursaste ninguna materia'}
            description={
              subTab === 'en-curso'
                ? 'Cuando te inscribas a una comisión, la vas a ver acá con su asistencia.'
                : 'Las materias que termines de cursar van a aparecer acá con su historial de asistencia.'
            }
          />
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {lista.map((inscripcion) => {
            const abierto = expandidoId === inscripcion.id;
            const pill = ESTADO_PILL[inscripcion.status] ?? { label: inscripcion.status, tone: 'neutral' };
            return (
              <div key={inscripcion.id}>
                <Button
                  variant="row-card"
                  size="row"
                  type="button"
                  onClick={() => toggleFila(inscripcion.id)}
                  className="gap-3 justify-start"
                  >
                  {abierto ? <ChevronDown className="h-4 w-4 shrink-0 text-ink-faint" /> : <ChevronRight className="h-4 w-4 shrink-0 text-ink-faint" />}
                  <span className="text-size-13 font-semibold text-ink-faint">{inscripcion.subjectCode ?? '—'}</span>
                  <span className="flex-1 text-size-13-5 font-semibold text-ink">{inscripcion.subjectName}</span>
                  {inscripcion.curricularYear != null && (
                    <span className="hidden text-xs text-ink-faint sm:inline">{inscripcion.curricularYear}° año</span>
                  )}
                  <StatusPill tone={pill.tone}>{pill.label}</StatusPill>
                </Button>
                {abierto && (
                  <div className="mt-2">
                    <SeccionAsistencia sectionEnrollmentId={inscripcion.id} />
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
