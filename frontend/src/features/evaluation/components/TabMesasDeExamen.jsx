import { useState } from 'react';
import { ChevronDown, ChevronRight, MapPin } from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useMisInscripciones } from '@/hooks/useAlumno';
import {
  useMesasDisponibles,
  useMisInscripcionesAMesas,
  useInscribirmeAMesa,
  useDarmeDeBajaDeMesa,
} from '@/features/evaluation/hooks/useStudentExams';
import { TIPOLOGIA_LABEL } from '@/lib/notas';

const SUB_TABS = [
  { id: 'para-rendir', label: 'Para rendir' },
  { id: 'mis-inscripciones', label: 'Mis inscripciones' },
];

function formatearFecha(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('es-AR', { day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

// Lista de llamados disponibles para UNA materia Regular — el fetch de mesas es lazy (solo
// cuando se expande esa materia, mismo criterio que SeccionAsistencia). `misInscripciones`
// (ya cargadas por el padre) son las que ese alumno tiene para ESTA materia puntual — pedido
// explícito del usuario: la fila tiene que mostrar directo si ya está inscripto (sin ir a otra
// pestaña) y bloquear los demás llamados hasta que se dé de baja del que tiene activo.
const REGISTRATION_ERROR_MESSAGES = {
  STUDENT_FREE_BY_ABSENCES: 'Superaste el máximo de inasistencias: justificalas para poder inscribirte al final.',
};

export function LlamadosDisponibles({ subjectId, programSectionEnrollmentId, misInscripciones }) {
  const { data: mesas, isLoading } = useMesasDisponibles(subjectId);
  const inscribirme = useInscribirmeAMesa();
  const darmeDeBaja = useDarmeDeBajaDeMesa();
  const [registrationError, setRegistrationError] = useState(null);

  if (isLoading) {
    return (
      <div className="flex flex-col gap-2 border-t border-border p-4">
        <Skeleton className="h-12" />
      </div>
    );
  }

  if (!mesas || mesas.length === 0) {
    return (
      <div className="border-t border-border p-4">
        <EmptyState title="No hay llamados abiertos todavía" description="Cuando Preceptoría abra una mesa para esta materia, la vas a ver acá." />
      </div>
    );
  }

  // Inscripción activa (inscripto, todavía sin nota) a CUALQUIERA de los llamados de esta
  // materia — solo puede haber una a la vez (backend lo garantiza), bloquea a las demás.
  const activa = misInscripciones.find((mia) => mia.status === 'REGISTERED' && mia.score == null);

  async function inscribirseA(mesaId) {
    setRegistrationError(null);
    try {
      await inscribirme.mutateAsync({ examBoardId: mesaId, programSectionEnrollmentId });
    } catch (err) {
      setRegistrationError({
        examBoardId: mesaId,
        message: REGISTRATION_ERROR_MESSAGES[err.code] ?? 'No pudimos inscribirte, probá de nuevo.',
      });
    }
  }

  return (
    <div className="flex flex-col gap-2 border-t border-border p-4">
      {mesas.map((mesa) => {
        const miInscripcion = activa?.examBoard.id === mesa.id ? activa : null;
        const bloqueadaPorOtra = !!activa && !miInscripcion;
        return (
          <div
            key={mesa.id}
            className={`flex flex-col gap-2 rounded-xl border border-border px-4 py-3 sm:flex-row sm:items-center sm:justify-between ${
              bloqueadaPorOtra ? 'opacity-50' : ''
            }`}
          >
            <div>
              <div className="text-[13.5px] font-semibold text-ink">{formatearFecha(mesa.examDate)}</div>
              {mesa.classroom && (
                <div className="flex items-center gap-1 text-xs text-ink-faint">
                  <MapPin className="h-3 w-3" /> {mesa.classroom}
                </div>
              )}
            </div>
            {miInscripcion ? (
              <div className="flex items-center gap-2">
                <StatusPill tone="accent">Inscripto</StatusPill>
                <Button
                  variant="outline"
                  className="text-danger"
                  onClick={() => darmeDeBaja.mutate(miInscripcion.id)}
                  disabled={darmeDeBaja.isPending}
                >
                  {darmeDeBaja.isPending ? 'Dando de baja…' : 'Darme de baja'}
                </Button>
              </div>
            ) : (
              <div className="flex flex-col items-end gap-1">
                <Button variant="gradient" onClick={() => inscribirseA(mesa.id)} disabled={inscribirme.isPending || bloqueadaPorOtra}>
                  {bloqueadaPorOtra ? 'Ya estás inscripto a otro llamado' : inscribirme.isPending ? 'Inscribiendo…' : 'Inscribirme a este llamado'}
                </Button>
                {registrationError?.examBoardId === mesa.id && (
                  <span className="text-[11px] font-medium text-danger">{registrationError.message}</span>
                )}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

// Pestaña "Mesas de examen" (Épica 2 Fase 2) — dos sub-pestañas:
// - "Para rendir": materias donde el alumno está Regular (pendiente de final), cada una
//   desplegable para ver los llamados abiertos e inscribirse — mismo patrón de acordeón lazy
//   que Asistencia. Muestra año/cuatrimestre de la materia (pedido explícito del usuario) para
//   que quede claro de qué instancia del plan se trata.
// - "Mis inscripciones": historial completo de mesas a las que se inscribió (todas las
//   materias), con la nota si ya rindió — vista de repaso, no reemplaza el estado inline de
//   "Para rendir". La fecha/llamado en que rindió queda registrada automáticamente porque el
//   GradeItem del final siempre se carga contra un examBoardId puntual (ver
//   GradeItemService/ExamBoardRegistrationService en el backend) — no hace falta un campo
//   nuevo para trackearlo, ya viene de la mesa concreta que devuelve el endpoint.
// Antes esta pestaña era un placeholder ("fase posterior del roadmap") — al construirla se
// encontró que el backend no tenía forma de listar mesas disponibles ni las inscripciones
// propias del alumno; ambos endpoints se agregaron para esto (GET /me/exam-boards?subjectId=,
// GET /me/exam-board-registrations).
export function TabMesasDeExamen({ carreraId }) {
  const { data: inscripciones, isLoading: cargandoInscripciones } = useMisInscripciones();
  const { data: misMesas, isLoading: cargandoMesas } = useMisInscripcionesAMesas();
  const darmeDeBaja = useDarmeDeBajaDeMesa();
  const [subTab, setSubTab] = useState('para-rendir');
  const [expandidoId, setExpandidoId] = useState(null);

  const regulares = (inscripciones ?? []).filter((i) => (!carreraId || i.programEnrollmentId === carreraId) && i.status === 'REGULAR');

  function toggleFila(id) {
    setExpandidoId((actual) => (actual === id ? null : id));
  }

  return (
    <div className="flex flex-col gap-4">
      <Tabs value={subTab} onValueChange={setSubTab}>
        <TabsList variant="segmented">
          {SUB_TABS.map(({ id, label }) => (
            <TabsTrigger key={id} value={id}>{label}</TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      {subTab === 'para-rendir' ? (
        cargandoInscripciones || cargandoMesas ? (
          <div className="flex flex-col gap-2">
            <Skeleton className="h-16" />
          </div>
        ) : regulares.length === 0 ? (
          <div className="rounded-xl border border-border bg-surface p-8">
            <EmptyState
              title="No tenés materias pendientes de final"
              description="Cuando termines de cursar una materia y quedes Regular, la vas a ver acá para inscribirte a un llamado."
            />
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            {regulares.map((inscripcion) => {
              const abierto = expandidoId === inscripcion.id;
              const misInscripcionesDeEstaMateria = (misMesas ?? []).filter((mia) => mia.examBoard.subjectId === inscripcion.subjectId);
              return (
                <div key={inscripcion.id}>
                  <button
                    type="button"
                    onClick={() => toggleFila(inscripcion.id)}
                    className="flex w-full items-center gap-3 rounded-xl border border-border bg-surface px-4 py-3 text-left transition-colors hover:bg-neutral-bg"
                  >
                    {abierto ? <ChevronDown className="h-4 w-4 shrink-0 text-ink-faint" /> : <ChevronRight className="h-4 w-4 shrink-0 text-ink-faint" />}
                    <span className="text-[13px] font-semibold text-ink-faint">{inscripcion.subjectCode ?? '—'}</span>
                    <span className="flex-1 text-[13.5px] font-semibold text-ink">{inscripcion.subjectName}</span>
                    {inscripcion.curricularYear != null && (
                      <span className="hidden text-xs text-ink-faint sm:inline">
                        {inscripcion.curricularYear}° año{inscripcion.curricularTerm && ` · ${TIPOLOGIA_LABEL[inscripcion.curricularTerm]}`}
                      </span>
                    )}
                    <StatusPill tone="warning">Regular · falta final</StatusPill>
                  </button>
                  {abierto && (
                    <LlamadosDisponibles
                      subjectId={inscripcion.subjectId}
                      programSectionEnrollmentId={inscripcion.id}
                      misInscripciones={misInscripcionesDeEstaMateria}
                    />
                  )}
                </div>
              );
            })}
          </div>
        )
      ) : cargandoMesas ? (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-16" />
        </div>
      ) : !misMesas || misMesas.length === 0 ? (
        <div className="rounded-xl border border-border bg-surface p-8">
          <EmptyState title="Todavía no te inscribiste a ninguna mesa" description="Cuando te inscribas a un llamado, lo vas a ver acá." />
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {misMesas.map((mia) => (
            <div key={mia.id} className="flex flex-col gap-2 rounded-xl border border-border bg-surface px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <div className="text-[13.5px] font-semibold text-ink">{mia.examBoard.subjectName}</div>
                <div className="text-xs text-ink-faint">
                  {formatearFecha(mia.examBoard.examDate)}
                  {mia.examBoard.classroom && ` · ${mia.examBoard.classroom}`}
                </div>
              </div>
              <div className="flex items-center gap-3">
                {mia.score != null && <span className="font-heading text-base font-bold text-ink">{mia.score}</span>}
                {/* El status de la inscripción a mesa nunca pasa a GRADED en el backend — "ya
                    rindió" se deriva de si hay nota, no del status. */}
                <StatusPill tone={mia.status === 'WITHDRAWN' ? 'neutral' : mia.score != null ? 'success' : 'accent'}>
                  {mia.status === 'WITHDRAWN' ? 'De baja' : mia.score != null ? 'Rendido' : 'Inscripto'}
                </StatusPill>
                {mia.status === 'REGISTERED' && mia.score == null && (
                  <Button variant="outline" className="text-danger" onClick={() => darmeDeBaja.mutate(mia.id)} disabled={darmeDeBaja.isPending}>
                    Darme de baja
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
