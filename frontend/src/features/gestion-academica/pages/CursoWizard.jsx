import { useReducer, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { ChevronLeft } from 'lucide-react';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Stepper } from '@/components/Stepper';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useCreateCourse } from '@/features/gestion-academica/hooks/useCourses';
import { useTerminos } from '@/features/oferta-academica/hooks/useTerminos';

const PASOS = [
  { key: 'datos', label: 'Datos básicos' },
  { key: 'detalle', label: 'Detalle' },
];

const TEACHING_MODE_OPTIONS = [
  { value: '', label: 'Sin especificar' },
  { value: 'IN_PERSON', label: 'Presencial' },
  { value: 'ONLINE', label: 'Virtual' },
  { value: 'HYBRID', label: 'Híbrido' },
];

function estadoInicial() {
  return {
    paso: 'datos',
    pasosVisitados: ['datos'],
    name: '',
    termId: '',
    capacity: '',
    price: '',
    teachingMode: '',
    schedule: '',
    durationMonths: '',
    registrationStart: '',
    registrationEnd: '',
  };
}

const REGISTRATION_WINDOW_ERROR = 'El cierre de inscripción no puede ser anterior a la apertura.';

function courseDetailError(state) {
  if (!state.durationMonths || parseInt(state.durationMonths) < 1) {
    return 'La duración en meses es obligatoria y tiene que ser al menos 1.';
  }
  if (state.registrationStart && state.registrationEnd && state.registrationEnd < state.registrationStart) {
    return REGISTRATION_WINDOW_ERROR;
  }
  return null;
}

function visitado(state, paso) {
  return state.pasosVisitados.includes(paso) ? state.pasosVisitados : [...state.pasosVisitados, paso];
}

function reducer(state, action) {
  switch (action.type) {
    case 'SET':
      return { ...state, [action.campo]: action.valor };
    case 'IR_A_PASO':
      return { ...state, paso: action.paso, pasosVisitados: visitado(state, action.paso) };
    default:
      return state;
  }
}

function CampoSelect({ label, value, onChange, options, required }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">{label}</label>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required={required}
        className="w-full rounded-xl border border-surface-border bg-surface/50 px-3 py-2 text-[13.5px] text-ink outline-none focus:border-accent focus:ring-1 focus:ring-accent/30"
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </div>
  );
}

function Paso1Datos({ state, dispatch }) {
  const { data: terminos, isLoading: loadingTerms } = useTerminos();
  const [error, setError] = useState(null);

  const termOptions = [
    { value: '', label: 'Seleccionar período…' },
    ...(terminos ?? []).map((t) => ({
      value: String(t.id),
      label: t.description ? `${t.year} — ${t.description}` : String(t.year),
    })),
  ];

  function handleContinuar() {
    if (!state.name.trim()) {
      setError('El nombre es obligatorio.');
      return;
    }
    if (!state.termId) {
      setError('El período académico es obligatorio.');
      return;
    }
    if (!state.capacity || parseInt(state.capacity) < 1) {
      setError('El cupo debe ser al menos 1.');
      return;
    }
    setError(null);
    dispatch({ type: 'IR_A_PASO', paso: 'detalle' });
  }

  return (
    <GlassCard className="flex flex-col gap-5 p-6">
      <div className="flex flex-col gap-1.5">
        <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
          Nombre del curso <span className="text-danger">*</span>
        </label>
        <Input
          value={state.name}
          onChange={(e) => dispatch({ type: 'SET', campo: 'name', valor: e.target.value })}
          placeholder="Ej. Curso de Excel Avanzado"
        />
      </div>

      <CampoSelect
        label={
          <span>
            Período académico <span className="text-danger">*</span>
          </span>
        }
        value={state.termId}
        onChange={(v) => dispatch({ type: 'SET', campo: 'termId', valor: v })}
        options={loadingTerms ? [{ value: '', label: 'Cargando…' }] : termOptions}
        required
      />

      <div className="grid grid-cols-2 gap-4">
        <div className="flex flex-col gap-1.5">
          <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
            Cupo <span className="text-danger">*</span>
          </label>
          <Input
            type="number"
            min={1}
            value={state.capacity}
            onChange={(e) => dispatch({ type: 'SET', campo: 'capacity', valor: e.target.value })}
            placeholder="Ej. 30"
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">Precio</label>
          <Input
            type="number"
            min={0}
            step={0.01}
            value={state.price}
            onChange={(e) => dispatch({ type: 'SET', campo: 'price', valor: e.target.value })}
            placeholder="Ej. 15000"
          />
        </div>
      </div>

      <CampoSelect
        label="Modalidad"
        value={state.teachingMode}
        onChange={(v) => dispatch({ type: 'SET', campo: 'teachingMode', valor: v })}
        options={TEACHING_MODE_OPTIONS}
      />

      {error && <p className="text-[13px] text-danger">{error}</p>}

      <div className="flex justify-end">
        <Button variant="gradient" onClick={handleContinuar}>
          Continuar
        </Button>
      </div>
    </GlassCard>
  );
}

function Paso2Detalle({ state, dispatch }) {
  const createCourse = useCreateCourse();
  const [error, setError] = useState(null);
  const navigate = useNavigate();

  async function handleCrear() {
    const validationError = courseDetailError(state);
    if (validationError) {
      setError(validationError);
      return;
    }
    setError(null);
    try {
      const body = {
        name: state.name,
        termId: parseInt(state.termId),
        capacity: parseInt(state.capacity),
        ...(state.price && { price: parseFloat(state.price) }),
        ...(state.teachingMode && { teachingMode: state.teachingMode }),
        ...(state.schedule.trim() && { schedule: state.schedule.trim() }),
        ...(state.durationMonths && { durationMonths: parseInt(state.durationMonths) }),
        ...(state.registrationStart && { registrationStart: state.registrationStart }),
        ...(state.registrationEnd && { registrationEnd: state.registrationEnd }),
      };
      const result = await createCourse.mutateAsync(body);
      toast.success('Curso creado');
      navigate(`/administrativo/gestion-academica/cursos/${result.id}`);
    } catch (err) {
      setError(
        err?.code === 'INVALID_REGISTRATION_WINDOW'
          ? REGISTRATION_WINDOW_ERROR
          : 'No se pudo crear el curso. Revisá los datos e intentá de nuevo.',
      );
    }
  }

  return (
    <GlassCard className="flex flex-col gap-5 p-6">
      <div className="flex flex-col gap-1.5">
        <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">Horario</label>
        <Input
          value={state.schedule}
          onChange={(e) => dispatch({ type: 'SET', campo: 'schedule', valor: e.target.value })}
          placeholder="Ej. Lunes y miércoles 18–20 hs"
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
          Duración (meses) <span className="text-danger">*</span>
        </label>
        <Input
          type="number"
          min={1}
          value={state.durationMonths}
          onChange={(e) => dispatch({ type: 'SET', campo: 'durationMonths', valor: e.target.value })}
          placeholder="Ej. 3"
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="flex flex-col gap-1.5">
          <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
            Inicio de inscripción
          </label>
          <Input
            type="datetime-local"
            value={state.registrationStart}
            onChange={(e) => dispatch({ type: 'SET', campo: 'registrationStart', valor: e.target.value })}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
            Cierre de inscripción
          </label>
          <Input
            type="datetime-local"
            value={state.registrationEnd}
            onChange={(e) => dispatch({ type: 'SET', campo: 'registrationEnd', valor: e.target.value })}
          />
        </div>
      </div>

      {error && <p className="text-[13px] text-danger">{error}</p>}

      <div className="flex items-center justify-between">
        <Button variant="outline" onClick={() => dispatch({ type: 'IR_A_PASO', paso: 'datos' })}>
          Atrás
        </Button>
        <Button variant="gradient" loading={createCourse.isPending} onClick={handleCrear}>
          Crear curso
        </Button>
      </div>
    </GlassCard>
  );
}

export function CursoWizard() {
  const [state, dispatch] = useReducer(reducer, null, estadoInicial);

  return (
    <PageContainer>
      <div className="flex flex-col gap-4">
        <div>
          <Link
            to="/administrativo/gestion-academica?section=cursos"
            className="mb-1 flex items-center gap-1 text-[12px] text-ink-faint hover:text-ink"
          >
            <ChevronLeft className="h-3.5 w-3.5" />
            Cursos
          </Link>
          <h1 className="font-heading text-xl font-bold text-ink">Nuevo curso</h1>
        </div>

        <Stepper
          pasos={PASOS}
          pasoActual={state.paso}
          pasosVisitados={state.pasosVisitados}
          onStepClick={(key) => dispatch({ type: 'IR_A_PASO', paso: key })}
        />

        {state.paso === 'datos' && <Paso1Datos state={state} dispatch={dispatch} />}
        {state.paso === 'detalle' && <Paso2Detalle state={state} dispatch={dispatch} />}
      </div>
    </PageContainer>
  );
}
