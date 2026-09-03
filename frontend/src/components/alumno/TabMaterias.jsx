import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { InscripcionMaterias } from '@/components/alumno/InscripcionMaterias';
import { useMisInscripciones } from '@/hooks/useAlumno';
import { ESTADOS_EN_CURSO, FINAL_GRADE_TYPE_LABEL } from '@/lib/notas';

const SUB_TABS = [
  { id: 'en-curso', label: 'En curso' },
  { id: 'cursadas', label: 'Cursadas' },
];

const ESTADO_PILL = {
  REGULAR: { label: 'Regular · falta final', tone: 'warning' },
  IN_PROGRESS: { label: 'En curso', tone: 'accent' },
  WAITLISTED: { label: 'En lista de espera', tone: 'warning' },
  ENROLLED: { label: 'Inscripto', tone: 'accent' },
  APPROVED: { label: 'Aprobada', tone: 'success' },
  FAILED: { label: 'Desaprobada', tone: 'danger' },
  WITHDRAWN: { label: 'De baja', tone: 'neutral' },
};

// Pestaña "Materias" de Mis carreras (Épica 2 Fase 2) — rediseñada con el mismo
// criterio que TabAsistencia.jsx: pedido explícito del usuario, "priorizá
// mostrar las materias en las que estoy inscripto, las demás ya aparecen en
// Plan de estudio" — esta pestaña dejó de traer el plan completo (con
// "disponible"/"bloqueada"), ahora son solo las inscripciones reales del
// alumno (/me/section-enrollments), separadas en dos sub-pestañas En
// curso/Cursadas. "Cursadas" NO implica aprobada — el estado real de cada fila
// se muestra tal cual (Regular pendiente de final, Aprobada, Desaprobada, De
// baja), pedido explícito del usuario para no esconder que "cursada" y
// "aprobada" son cosas distintas.
// Encontrar materias nuevas para anotarse es un flujo aparte (+ Inscribirme a
// materias → InscripcionMaterias.jsx), que sí mira el plan completo para
// ofrecer lo "disponible" — no depende de esta lista, así que sacar
// disponible/bloqueada de acá no le afecta.
export function TabMaterias({ carreraId }) {
  const navigate = useNavigate();
  const [inscribiendo, setInscribiendo] = useState(false);
  const { data: inscripciones, isLoading } = useMisInscripciones();
  const [subTab, setSubTab] = useState('en-curso');

  if (inscribiendo) {
    return <InscripcionMaterias carreraId={carreraId} onCerrar={() => setInscribiendo(false)} />;
  }

  const deEstaCarrera = (inscripciones ?? []).filter((i) => !carreraId || i.programEnrollmentId === carreraId);
  const enCurso = deEstaCarrera.filter((i) => ESTADOS_EN_CURSO.has(i.status));
  // Un alumno puede haber cursado la misma materia más de una vez (recursada tras un FAILED o
  // WITHDRAWN) — cada intento es una fila de inscripción real distinta (attemptNumber), nunca
  // se pisan entre sí. Agrupadas por materia y ordenadas por intento para que se lean juntas
  // en vez de mezcladas con el resto — pedido explícito del usuario: "tenemos que tener
  // registro de que el alumno ya cursó una materia 2 o 3 veces... mostrarle 3 filas".
  const cursadas = deEstaCarrera
    .filter((i) => !ESTADOS_EN_CURSO.has(i.status))
    .sort((a, b) => a.subjectName.localeCompare(b.subjectName) || (a.attemptNumber ?? 1) - (b.attemptNumber ?? 1));
  const lista = subTab === 'en-curso' ? enCurso : cursadas;

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center justify-between gap-4">
        <div className="flex w-fit gap-1 rounded-xl border border-border bg-surface p-1">
          {SUB_TABS.map(({ id, label }) => (
            <button
              key={id}
              type="button"
              onClick={() => setSubTab(id)}
              className={`rounded-lg px-4 py-1.5 text-[13px] font-semibold transition-colors ${
                subTab === id ? 'bg-gradient-to-br from-brand to-accent-2 text-white' : 'text-ink-soft hover:bg-neutral-bg'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
        <Button variant="gradient" onClick={() => setInscribiendo(true)}>
          + Inscribirme a materias
        </Button>
      </div>

      {isLoading ? (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-16" />
          <Skeleton className="h-16" />
        </div>
      ) : lista.length === 0 ? (
        <div className="rounded-xl border border-border bg-surface p-8">
          <EmptyState
            title={subTab === 'en-curso' ? 'No tenés materias en curso' : 'Todavía no cursaste ninguna materia'}
            description={
              subTab === 'en-curso'
                ? 'Inscribite a una materia para empezar a cursarla.'
                : 'Las materias que termines de cursar van a aparecer acá.'
            }
          />
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {lista.map((inscripcion) => {
            const pill = ESTADO_PILL[inscripcion.status] ?? { label: inscripcion.status, tone: 'neutral' };
            return (
              <button
                key={inscripcion.id}
                type="button"
                onClick={() => navigate(`/alumno/mis-carreras/materias/${inscripcion.id}`)}
                className="flex w-full items-center gap-3 rounded-xl border border-border bg-surface px-4 py-3 text-left transition-colors hover:bg-neutral-bg"
              >
                <span className="text-[13px] font-semibold text-ink-faint">{inscripcion.subjectCode ?? '—'}</span>
                <span className="flex-1 text-[13.5px] font-semibold text-ink">
                  {inscripcion.subjectName}
                  {inscripcion.attemptNumber > 1 && (
                    <span className="ml-1.5 text-xs font-semibold text-ink-faint">· {inscripcion.attemptNumber}° intento</span>
                  )}
                </span>
                {inscripcion.curricularYear != null && (
                  <span className="hidden text-xs text-ink-faint sm:inline">{inscripcion.curricularYear}° año</span>
                )}
                <span className="hidden text-xs text-ink-faint md:inline">{inscripcion.sectionName}</span>
                <div className="flex flex-col items-end gap-0.5">
                  <StatusPill tone={pill.tone}>{pill.label}</StatusPill>
                  {inscripcion.status === 'APPROVED' && inscripcion.finalGradeType && (
                    <span className="text-[11px] text-ink-faint">{FINAL_GRADE_TYPE_LABEL[inscripcion.finalGradeType]}</span>
                  )}
                </div>
                <ChevronRight className="h-4 w-4 shrink-0 text-ink-faint" />
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
