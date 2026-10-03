import { useId, useReducer, useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { ChevronLeft, Plus, CheckCircle2 } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Stepper } from '@/components/Stepper';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { useProgramas, useCursos, useProgramaPlanDeEstudio, useCursoTemario } from '@/hooks/useCatalogo';
import { useTerminos } from '@/features/oferta-academica/hooks/useTerminos';
import { useInstructores } from '@/features/oferta-academica/hooks/useInstructores';
import { useClassrooms } from '@/features/oferta-academica/hooks/useClassrooms';
import {
  useCycle,
  useCicloSecciones,
  useCreateCiclo,
  useAddSeccion,
  useAsignarInstructorSeccion,
  useSubmitCiclo,
} from '@/features/oferta-academica/hooks/useCiclos';
import { RejectionReasonNotice } from '@/features/oferta-academica/components/RejectionReasonNotice';

const PASOS = [
  { key: 'periodo', label: 'Período' },
  { key: 'comisiones', label: 'Comisiones' },
  { key: 'resumen', label: 'Resumen' },
];

const DIAS = [
  { id: 'MONDAY', label: 'Lun' },
  { id: 'TUESDAY', label: 'Mar' },
  { id: 'WEDNESDAY', label: 'Mié' },
  { id: 'THURSDAY', label: 'Jue' },
  { id: 'FRIDAY', label: 'Vie' },
  { id: 'SATURDAY', label: 'Sáb' },
];

const DAY_NAMES = {
  MONDAY: 'Lunes',
  TUESDAY: 'Martes',
  WEDNESDAY: 'Miércoles',
  THURSDAY: 'Jueves',
  FRIDAY: 'Viernes',
  SATURDAY: 'Sábado',
  SUNDAY: 'Domingo',
};

const SECTION_ERROR_MESSAGES = {
  SECTION_SCHEDULE_CONFLICT: 'El aula ya está ocupada ese día y en ese horario durante este período.',
  SECTION_CAPACITY_EXCEEDS_CLASSROOM: 'El cupo supera la capacidad del aula.',
  CYCLE_NOT_EDITABLE: 'Este ciclo ya fue enviado a aprobación y no admite comisiones nuevas.',
};

const SUBMIT_ERROR_MESSAGES = {
  CYCLE_INCOMPLETE: 'No se pudo enviar a aprobación. Verificá que todas las comisiones tengan docente titular.',
  CYCLE_NOT_EDITABLE: 'Este ciclo ya fue enviado a aprobación y no se puede modificar.',
};

const NOT_EDITABLE_MESSAGE = 'Este ciclo ya fue enviado a aprobación y no se puede modificar.';

function sectionCountLabel(count) {
  return `${count} ${count === 1 ? 'comisión' : 'comisiones'}`;
}

function formatTime(time) {
  return time?.slice(0, 5) ?? '—';
}

function leadInstructorName(section) {
  return section.instructors?.find((i) => i.role === 'LEAD')?.name ?? 'Sin titular';
}

function cycleDetailPath(tipo, programaId, cursoId) {
  return tipo === 'programa'
    ? `/preceptor/oferta-academica/carreras/${programaId}`
    : `/preceptor/oferta-academica/cursos/${cursoId}`;
}

function estadoInicial() {
  return {
    paso: 'periodo',
    pasosVisitados: ['periodo'],
    termId: '',
    tipo: 'programa',
    programaId: '',
    cursoId: '',
    cicloId: null,
    termName: '',
    entityName: '',
  };
}

function initialStateFor(cycle) {
  if (!cycle) return estadoInicial();
  return {
    paso: 'comisiones',
    pasosVisitados: ['periodo', 'comisiones'],
    termId: String(cycle.termId),
    tipo: cycle.programId ? 'programa' : 'curso',
    programaId: cycle.programId ? String(cycle.programId) : '',
    cursoId: cycle.courseId ? String(cycle.courseId) : '',
    cicloId: cycle.id,
    termName: cycle.termName,
    entityName: cycle.programName ?? cycle.courseName ?? '',
  };
}

function visitado(state, paso) {
  return state.pasosVisitados.includes(paso)
    ? state.pasosVisitados
    : [...state.pasosVisitados, paso];
}

function reducer(state, action) {
  switch (action.type) {
    case 'SET':
      return { ...state, [action.campo]: action.valor };
    case 'IR_A_COMISIONES':
      return {
        ...state,
        cicloId: action.cycle.id,
        termName: action.cycle.termName,
        entityName: action.cycle.programName ?? action.cycle.courseName ?? '',
        paso: 'comisiones',
        pasosVisitados: visitado(state, 'comisiones'),
      };
    case 'IR_A_RESUMEN':
      return { ...state, paso: 'resumen', pasosVisitados: visitado(state, 'resumen') };
    case 'IR_A_PASO':
      return { ...state, paso: action.paso };
    default:
      return state;
  }
}

function CampoSelect({ label, value, onChange, children, required }) {
  const id = useId();
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">{label}</label>
      <select
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required={required}
        className="w-full rounded-xl border border-surface-border bg-surface/50 px-3 py-2 text-[13.5px] text-ink outline-none focus:border-accent focus:ring-1 focus:ring-accent/30"
      >
        {children}
      </select>
    </div>
  );
}

function ReadOnlyField({ label, value }) {
  const id = useId();
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">{label}</label>
      <Input id={id} value={value} readOnly className="cursor-default" />
    </div>
  );
}

function CreatedCyclePeriod({ state, dispatch }) {
  return (
    <GlassCard className="flex flex-col gap-5 p-6">
      <div className="flex flex-col gap-4">
        <ReadOnlyField label="Período académico" value={state.termName} />
        <ReadOnlyField label={state.tipo === 'programa' ? 'Carrera' : 'Curso'} value={state.entityName} />
      </div>
      <Button variant="gradient" onClick={() => dispatch({ type: 'IR_A_PASO', paso: 'comisiones' })}>
        Continuar
      </Button>
    </GlassCard>
  );
}

function Paso1Periodo({ state, dispatch, onCycleCreated }) {
  const { data: terminos, isLoading: loadingTerminos } = useTerminos();
  const { data: programas, isLoading: loadingProgramas } = useProgramas();
  const { data: cursos, isLoading: loadingCursos } = useCursos();
  const createCiclo = useCreateCiclo();
  const [error, setError] = useState(null);

  const lista = state.tipo === 'programa' ? programas : cursos;
  const loadingLista = state.tipo === 'programa' ? loadingProgramas : loadingCursos;

  async function handleContinuar() {
    setError(null);
    if (!state.termId || !(state.tipo === 'programa' ? state.programaId : state.cursoId)) {
      setError({ message: 'Completá todos los campos antes de continuar.' });
      return;
    }
    const body = { termId: Number(state.termId) };
    if (state.tipo === 'programa') body.programId = Number(state.programaId);
    else body.courseId = Number(state.cursoId);

    createCiclo.mutate(body, {
      onSuccess: (ciclo) => {
        dispatch({ type: 'IR_A_COMISIONES', cycle: ciclo });
        onCycleCreated(ciclo.id);
      },
      onError: (err) => setError(
        err.code === 'CYCLE_ALREADY_IN_PROGRESS'
          ? { message: 'Ya hay un ciclo en construcción o en revisión para este período. Podés retomarlo desde el detalle.', alreadyInProgress: true }
          : { message: 'No se pudo crear el ciclo. Probá de nuevo.' },
      ),
    });
  }

  if (state.cicloId) return <CreatedCyclePeriod state={state} dispatch={dispatch} />;

  if (loadingTerminos) return <Skeleton className="h-[200px] w-full rounded-2xl" />;

  const entidadId = state.tipo === 'programa' ? state.programaId : state.cursoId;
  const campoEntidad = state.tipo === 'programa' ? 'programaId' : 'cursoId';

  return (
    <GlassCard className="flex flex-col gap-5 p-6">
      <div className="flex flex-col gap-4">
        <CampoSelect label="Período académico" value={state.termId} onChange={(v) => dispatch({ type: 'SET', campo: 'termId', valor: v })} required>
          <option value="">Seleccioná un período…</option>
          {(terminos ?? []).map((t) => <option key={t.id} value={t.id}>{t.description}</option>)}
        </CampoSelect>

        <div className="flex flex-col gap-1.5">
          <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">Tipo</label>
          <div className="flex gap-2">
            {[{ value: 'programa', label: 'Carrera' }, { value: 'curso', label: 'Curso' }].map((op) => (
              <button
                key={op.value}
                type="button"
                onClick={() => { dispatch({ type: 'SET', campo: 'tipo', valor: op.value }); dispatch({ type: 'SET', campo: 'programaId', valor: '' }); dispatch({ type: 'SET', campo: 'cursoId', valor: '' }); }}
                className={`rounded-lg px-4 py-2 text-[13px] font-medium transition-colors ${state.tipo === op.value ? 'bg-accent-soft text-brand' : 'bg-neutral-bg/60 text-ink-soft hover:bg-neutral-bg'}`}
              >
                {op.label}
              </button>
            ))}
          </div>
        </div>

        {loadingLista ? (
          <Skeleton className="h-10 w-full rounded-xl" />
        ) : (
          <CampoSelect
            label={state.tipo === 'programa' ? 'Carrera' : 'Curso'}
            value={entidadId}
            onChange={(v) => dispatch({ type: 'SET', campo: campoEntidad, valor: v })}
            required
          >
            <option value="">Seleccioná…</option>
            {(lista ?? []).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
          </CampoSelect>
        )}
      </div>

      {error && (
        <p className="text-[13px] text-danger">
          {error.message}
          {error.alreadyInProgress && (
            <>
              {' '}
              <Link to={cycleDetailPath(state.tipo, state.programaId, state.cursoId)} className="font-medium underline">
                {state.tipo === 'programa' ? 'Ir a la carrera' : 'Ir al curso'}
              </Link>
            </>
          )}
        </p>
      )}

      <Button variant="gradient" onClick={handleContinuar} disabled={createCiclo.isPending}>
        {createCiclo.isPending ? 'Creando…' : 'Crear borrador y continuar'}
      </Button>
    </GlassCard>
  );
}

function SeccionFormInline({ subjectId, courseSubjectId, subjectName, cicloId, tipoCiclo }) {
  const fieldId = useId();
  const [nombre, setNombre] = useState('');
  const [cupo, setCupo] = useState('');
  const [instructorId, setInstructorId] = useState('');
  const [dia, setDia] = useState('MONDAY');
  const [horaInicio, setHoraInicio] = useState('08:00');
  const [horaFin, setHoraFin] = useState('12:00');
  const [aulaId, setAulaId] = useState('');
  const [error, setError] = useState(null);
  const [abierto, setAbierto] = useState(false);

  const { data: instructores } = useInstructores();
  const { data: aulas } = useClassrooms();
  const addSeccion = useAddSeccion(cicloId);
  const asignarInstructor = useAsignarInstructorSeccion(tipoCiclo);

  async function handleAgregar() {
    setError(null);
    if (!nombre.trim() || !cupo || !instructorId || !aulaId) {
      setError('Completá nombre, cupo, docente y aula.');
      return;
    }
    if (horaFin <= horaInicio) {
      setError('La hora de fin tiene que ser posterior a la de inicio.');
      return;
    }
    const classroom = aulas?.find((a) => String(a.id) === aulaId);
    if (classroom && Number(cupo) > classroom.capacity) {
      setError(`El cupo supera la capacidad del aula (${classroom.capacity} lugares).`);
      return;
    }

    const sectionBody = {
      name: nombre.trim(),
      capacity: Number(cupo),
      slots: [{ classroomId: Number(aulaId), dayOfWeek: dia, startTime: `${horaInicio}:00`, endTime: `${horaFin}:00` }],
    };
    if (subjectId) sectionBody.subjectId = subjectId;
    else sectionBody.courseSubjectId = courseSubjectId;

    addSeccion.mutate(sectionBody, {
      onSuccess: (sectionId) => {
        asignarInstructor.mutate({ sectionId, instructorId: Number(instructorId) }, {
          onSuccess: () => {
            setNombre(''); setCupo(''); setInstructorId(''); setAulaId(''); setAbierto(false);
          },
          onError: () => setError('Sección creada pero no se pudo asignar el docente. Podés asignarlo después.'),
        });
      },
      onError: (err) => setError(SECTION_ERROR_MESSAGES[err.code] ?? 'No se pudo agregar la comisión. Probá de nuevo.'),
    });
  }

  if (!abierto) {
    return (
      <button
        type="button"
        onClick={() => setAbierto(true)}
        className="flex items-center gap-1.5 rounded-xl px-3 py-2 text-[13px] text-brand hover:bg-accent-soft/50 transition-colors"
      >
        <Plus className="h-3.5 w-3.5" />
        Agregar comisión para {subjectName}
      </button>
    );
  }

  return (
    <div className="rounded-2xl border border-surface-border bg-surface/30 p-4">
      <p className="mb-3 text-[12px] font-semibold uppercase tracking-wide text-ink-faint">{subjectName}</p>
      <div className="grid grid-cols-2 gap-3">
        <div className="flex flex-col gap-1">
          <label htmlFor={`${fieldId}-name`} className="text-[11px] font-medium uppercase tracking-wide text-ink-faint">Nombre / turno</label>
          <Input id={`${fieldId}-name`} placeholder="Ej: A, Turno mañana…" value={nombre} onChange={(e) => setNombre(e.target.value)} />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor={`${fieldId}-capacity`} className="text-[11px] font-medium uppercase tracking-wide text-ink-faint">Cupo</label>
          <Input id={`${fieldId}-capacity`} type="number" min="1" placeholder="Ej: 30" value={cupo} onChange={(e) => setCupo(e.target.value)} />
        </div>
      </div>

      <div className="mt-3 flex flex-col gap-1">
        <label htmlFor={`${fieldId}-instructor`} className="text-[11px] font-medium uppercase tracking-wide text-ink-faint">Docente titular</label>
        <select
          id={`${fieldId}-instructor`}
          value={instructorId}
          onChange={(e) => setInstructorId(e.target.value)}
          className="w-full rounded-xl border border-surface-border bg-surface/50 px-3 py-2 text-[13.5px] text-ink outline-none focus:border-accent"
        >
          <option value="">Seleccioná un docente…</option>
          {(instructores ?? []).map((i) => <option key={i.id} value={i.id}>{i.name}</option>)}
        </select>
      </div>

      <div className="mt-3 flex flex-col gap-1">
        <label className="text-[11px] font-medium uppercase tracking-wide text-ink-faint">Día</label>
        <div className="flex flex-wrap gap-1.5">
          {DIAS.map((d) => (
            <button
              key={d.id}
              type="button"
              onClick={() => setDia(d.id)}
              className={`rounded-lg px-3 py-1 text-[12px] transition-colors ${dia === d.id ? 'bg-accent-soft font-medium text-brand' : 'bg-neutral-bg/60 text-ink-soft hover:bg-neutral-bg'}`}
            >
              {d.label}
            </button>
          ))}
        </div>
      </div>

      <div className="mt-3 grid grid-cols-2 gap-3">
        <div className="flex flex-col gap-1">
          <label htmlFor={`${fieldId}-start`} className="text-[11px] font-medium uppercase tracking-wide text-ink-faint">Horario inicio</label>
          <Input id={`${fieldId}-start`} type="time" value={horaInicio} onChange={(e) => setHoraInicio(e.target.value)} />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor={`${fieldId}-end`} className="text-[11px] font-medium uppercase tracking-wide text-ink-faint">Horario fin</label>
          <Input id={`${fieldId}-end`} type="time" value={horaFin} onChange={(e) => setHoraFin(e.target.value)} />
        </div>
      </div>

      <div className="mt-3 flex flex-col gap-1">
        <label htmlFor={`${fieldId}-classroom`} className="text-[11px] font-medium uppercase tracking-wide text-ink-faint">Aula</label>
        <select
          id={`${fieldId}-classroom`}
          value={aulaId}
          onChange={(e) => setAulaId(e.target.value)}
          className="w-full rounded-xl border border-surface-border bg-surface/50 px-3 py-2 text-[13.5px] text-ink outline-none focus:border-accent"
        >
          <option value="">Seleccioná un aula…</option>
          {(aulas ?? []).map((a) => <option key={a.id} value={a.id}>{a.name} ({a.capacity} lugares)</option>)}
        </select>
      </div>

      {error && <p className="mt-2 text-[12px] text-danger">{error}</p>}

      <div className="mt-4 flex justify-end gap-2">
        <Button variant="outline" size="sm" onClick={() => { setAbierto(false); setError(null); }}>Cancelar</Button>
        <Button variant="gradient" size="sm" onClick={handleAgregar} disabled={addSeccion.isPending || asignarInstructor.isPending}>
          {addSeccion.isPending || asignarInstructor.isPending ? 'Guardando…' : 'Agregar comisión'}
        </Button>
      </div>
    </div>
  );
}

function Paso2Comisiones({ state, dispatch }) {
  const esProgramaCiclo = state.tipo === 'programa';
  const { data: materiasPrograma, isLoading: loadingPrograma } = useProgramaPlanDeEstudio(esProgramaCiclo ? state.programaId : null);
  const { data: materiasTemario, isLoading: loadingTemario } = useCursoTemario(!esProgramaCiclo ? state.cursoId : null);
  const { data: cycleSections, isLoading: loadingSections } = useCicloSecciones(state.cicloId);
  const materias = esProgramaCiclo ? materiasPrograma : materiasTemario;
  const isLoading = (esProgramaCiclo ? loadingPrograma : loadingTemario) || loadingSections;
  const sections = cycleSections ?? [];

  const seccionesPorMateria = {};
  for (const s of sections) {
    if (!seccionesPorMateria[s.subjectName]) seccionesPorMateria[s.subjectName] = [];
    seccionesPorMateria[s.subjectName].push(s);
  }

  if (isLoading) return <Skeleton className="h-[200px] w-full rounded-2xl" />;
  if (!materias?.length) return <EmptyState title="Esta carrera/curso no tiene materias cargadas." className="py-8" />;

  return (
    <div className="flex flex-col gap-4">
      <GlassCard className="flex flex-col gap-4 p-4">
        <p className="text-[13px] text-ink-faint">
          Agregá las comisiones con docente, aula y horario. Podés dejar materias sin comisión si no aplican este período.
        </p>

        <div className="flex flex-col gap-3">
          {materias.map((m) => {
            const subjectName = esProgramaCiclo ? m.subjectName : m.name;
            const subjectId = esProgramaCiclo ? m.subjectId : null;
            const courseSubjectId = esProgramaCiclo ? null : m.id;
            const secciones = seccionesPorMateria[subjectName] ?? [];

            return (
              <div key={m.id} className="flex flex-col gap-2 rounded-2xl border border-surface-border px-4 py-3">
                <div className="flex items-center justify-between">
                  <span className="font-heading text-[13.5px] font-semibold text-ink">{subjectName}</span>
                  {secciones.length > 0 && (
                    <span className="rounded-full bg-success-soft px-2.5 py-0.5 text-[11px] font-medium text-success">
                      {sectionCountLabel(secciones.length)}
                    </span>
                  )}
                </div>

                {secciones.map((s) => (
                  <div key={s.id} className="flex flex-wrap items-center gap-2 rounded-xl bg-success-soft/40 px-3 py-1.5 text-[12px] text-ink-soft">
                    <CheckCircle2 className="h-3.5 w-3.5 shrink-0 text-success" />
                    <span className="font-medium text-ink">{s.name}</span>
                    <span>· {leadInstructorName(s)}</span>
                    {s.slots?.map((slot) => (
                      <span key={`${slot.dayOfWeek}-${slot.startTime}`}>· {slot.classroomName}</span>
                    ))}
                  </div>
                ))}

                <SeccionFormInline
                  subjectId={subjectId}
                  courseSubjectId={courseSubjectId}
                  subjectName={subjectName}
                  cicloId={state.cicloId}
                  tipoCiclo={state.tipo}
                />
              </div>
            );
          })}
        </div>
      </GlassCard>

      <div className="flex justify-between">
        <Button variant="outline" onClick={() => dispatch({ type: 'IR_A_PASO', paso: 'periodo' })}>
          Volver
        </Button>
        <Button
          variant="gradient"
          onClick={() => dispatch({ type: 'IR_A_RESUMEN' })}
          disabled={sections.length === 0}
        >
          Ver resumen ({sectionCountLabel(sections.length)})
        </Button>
      </div>
    </div>
  );
}

function Paso3Resumen({ state, dispatch }) {
  const navigate = useNavigate();
  const submitCiclo = useSubmitCiclo();
  const [error, setError] = useState(null);
  const { data: cycleSections } = useCicloSecciones(state.cicloId);
  const sections = cycleSections ?? [];

  function handleSubmit() {
    setError(null);
    submitCiclo.mutate(state.cicloId, {
      onSuccess: () => navigate(cycleDetailPath(state.tipo, state.programaId, state.cursoId)),
      onError: (err) => setError(SUBMIT_ERROR_MESSAGES[err.code] ?? 'No se pudo enviar a aprobación. Probá de nuevo.'),
    });
  }

  return (
    <div className="flex flex-col gap-4">
      <GlassCard className="flex flex-col gap-4 p-5">
        <div className="grid grid-cols-2 gap-x-8 gap-y-3">
          <div>
            <p className="text-[11px] font-medium uppercase tracking-wide text-ink-faint">Período</p>
            <p className="mt-0.5 text-[14px] font-medium text-ink">{state.termName || '—'}</p>
          </div>
          <div>
            <p className="text-[11px] font-medium uppercase tracking-wide text-ink-faint">{state.tipo === 'programa' ? 'Carrera' : 'Curso'}</p>
            <p className="mt-0.5 text-[14px] font-medium text-ink">{state.entityName || '—'}</p>
          </div>
        </div>

        <div>
          <p className="mb-2 text-[12px] font-medium uppercase tracking-wide text-ink-faint">
            Comisiones ({sections.length})
          </p>
          <div className="flex flex-col gap-2">
            {sections.map((s) => (
              <div key={s.id} className="rounded-xl border border-surface-border px-3 py-2.5">
                <div className="flex items-baseline justify-between">
                  <span className="font-heading text-[13px] font-semibold text-ink">{s.subjectName}</span>
                  <span className="text-[12px] text-ink-faint">{s.name}</span>
                </div>
                <p className="mt-0.5 text-[12px] text-ink-soft">{leadInstructorName(s)}</p>
                {s.slots?.map((slot) => (
                  <p key={`${slot.dayOfWeek}-${slot.startTime}`} className="text-[12px] text-ink-soft">
                    {slot.classroomName} · {DAY_NAMES[slot.dayOfWeek] ?? slot.dayOfWeek} {formatTime(slot.startTime)}–{formatTime(slot.endTime)}
                  </p>
                ))}
              </div>
            ))}
          </div>
        </div>
      </GlassCard>

      {error && <p className="text-[13px] text-danger">{error}</p>}

      <div className="flex justify-between">
        <Button variant="outline" onClick={() => dispatch({ type: 'IR_A_PASO', paso: 'comisiones' })}>
          Volver a comisiones
        </Button>
        <Button variant="gradient" onClick={handleSubmit} disabled={submitCiclo.isPending}>
          {submitCiclo.isPending ? 'Enviando…' : 'Enviar a aprobación'}
        </Button>
      </div>
    </div>
  );
}

function CycleWizard({ resumedCycle, onCycleCreated }) {
  const [state, dispatch] = useReducer(reducer, resumedCycle, initialStateFor);

  return (
    <>
      <GlassCard className="p-4">
        <Stepper
          pasos={PASOS}
          pasoActual={state.paso}
          pasosVisitados={state.pasosVisitados}
          onStepClick={(paso) => dispatch({ type: 'IR_A_PASO', paso })}
        />
      </GlassCard>

      <RejectionReasonNotice cycle={resumedCycle} />

      {state.paso === 'periodo' && <Paso1Periodo state={state} dispatch={dispatch} onCycleCreated={onCycleCreated} />}
      {state.paso === 'comisiones' && <Paso2Comisiones state={state} dispatch={dispatch} />}
      {state.paso === 'resumen' && <Paso3Resumen state={state} dispatch={dispatch} />}
    </>
  );
}

function ResumedCycleGate({ cycleId, onCycleCreated }) {
  const { data: cycle, isLoading, isError } = useCycle(cycleId);

  if (isLoading) return <Skeleton className="h-[200px] w-full rounded-2xl" />;
  if (isError || !cycle) {
    return (
      <GlassCard className="p-6">
        <EmptyState title="No se encontró el ciclo." />
      </GlassCard>
    );
  }
  if (cycle.status !== 'DRAFT' && cycle.status !== 'REJECTED') {
    return (
      <GlassCard className="p-6">
        <EmptyState
          title={NOT_EDITABLE_MESSAGE}
          action={
            <Button variant="outline" asChild>
              <Link to={cycleDetailPath(cycle.programId ? 'programa' : 'curso', cycle.programId, cycle.courseId)}>
                Ver el ciclo
              </Link>
            </Button>
          }
        />
      </GlassCard>
    );
  }
  return <CycleWizard resumedCycle={cycle} onCycleCreated={onCycleCreated} />;
}

export function PreceptorNuevoCiclo() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [initialCycleId] = useState(() => searchParams.get('cicloId'));

  function handleCycleCreated(cycleId) {
    setSearchParams({ cicloId: String(cycleId) }, { replace: true });
  }

  return (
    <PageContainer>
      <div className="flex flex-col gap-5">
        <div>
          <Link
            to="/preceptor/oferta-academica"
            className="mb-1 flex items-center gap-1 text-[12px] text-ink-faint hover:text-ink"
          >
            <ChevronLeft className="h-3.5 w-3.5" />
            Oferta académica
          </Link>
          <h1 className="font-heading text-xl font-bold text-ink">Alta de ciclo académico</h1>
        </div>

        {initialCycleId
          ? <ResumedCycleGate cycleId={initialCycleId} onCycleCreated={handleCycleCreated} />
          : <CycleWizard resumedCycle={null} onCycleCreated={handleCycleCreated} />}
      </div>
    </PageContainer>
  );
}
