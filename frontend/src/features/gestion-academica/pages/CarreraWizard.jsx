import { useReducer, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { ChevronLeft } from 'lucide-react';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Stepper } from '@/components/Stepper';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { useCreateProgram } from '@/features/gestion-academica/hooks/usePrograms';

const PASOS = [
  { key: 'datos', label: 'Datos básicos' },
  { key: 'contenido', label: 'Contenido académico' },
];

const LEVEL_OPTIONS = [
  { value: '', label: 'Sin especificar' },
  { value: 'UNDERGRADUATE', label: 'Pregrado' },
  { value: 'GRADUATE', label: 'Posgrado' },
  { value: 'EXTENSION', label: 'Extensión' },
  { value: 'DIPLOMA', label: 'Diplomatura' },
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
    totalYears: '',
    level: '',
    teachingMode: '',
    totalCreditsRequired: '',
    totalHoursRequired: '',
    graduationProfile: '',
    admissionRequirements: '',
  };
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
  const [error, setError] = useState(null);

  function handleContinuar() {
    if (!state.name.trim()) {
      setError('El nombre es obligatorio.');
      return;
    }
    if (!state.totalYears || parseInt(state.totalYears) < 1) {
      setError('La duración debe ser al menos 1 año.');
      return;
    }
    setError(null);
    dispatch({ type: 'IR_A_PASO', paso: 'contenido' });
  }

  return (
    <GlassCard className="flex flex-col gap-5 p-6">
      <div className="flex flex-col gap-1.5">
        <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
          Nombre de la carrera <span className="text-danger">*</span>
        </label>
        <Input
          value={state.name}
          onChange={(e) => dispatch({ type: 'SET', campo: 'name', valor: e.target.value })}
          placeholder="Ej. Licenciatura en Enfermería"
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
          Duración (años) <span className="text-danger">*</span>
        </label>
        <Input
          type="number"
          min={1}
          max={10}
          value={state.totalYears}
          onChange={(e) => dispatch({ type: 'SET', campo: 'totalYears', valor: e.target.value })}
          placeholder="Ej. 3"
        />
      </div>

      <CampoSelect
        label="Nivel"
        value={state.level}
        onChange={(v) => dispatch({ type: 'SET', campo: 'level', valor: v })}
        options={LEVEL_OPTIONS}
      />

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

function Paso2Contenido({ state, dispatch }) {
  const createProgram = useCreateProgram();
  const [error, setError] = useState(null);
  const navigate = useNavigate();

  async function handleCrear() {
    if (state.totalCreditsRequired === '') {
      setError('Los créditos requeridos son obligatorios.');
      return;
    }
    setError(null);
    try {
      const body = {
        name: state.name,
        totalYears: parseInt(state.totalYears),
        ...(state.level && { level: state.level }),
        ...(state.teachingMode && { teachingMode: state.teachingMode }),
        totalCreditsRequired: parseInt(state.totalCreditsRequired),
        ...(state.totalHoursRequired && { totalHoursRequired: parseInt(state.totalHoursRequired) }),
        ...(state.graduationProfile.trim() && { graduationProfile: state.graduationProfile.trim() }),
        ...(state.admissionRequirements.trim() && { admissionRequirements: state.admissionRequirements.trim() }),
      };
      const result = await createProgram.mutateAsync(body);
      toast.success('Carrera creada');
      navigate(`/administrativo/gestion-academica/carreras/${result.id}`);
    } catch {
      setError('No se pudo crear la carrera. Intentá de nuevo.');
    }
  }

  return (
    <GlassCard className="flex flex-col gap-5 p-6">
      <div className="grid grid-cols-2 gap-4">
        <div className="flex flex-col gap-1.5">
          <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
            Créditos requeridos <span className="text-danger">*</span>
          </label>
          <Input
            type="number"
            min={0}
            value={state.totalCreditsRequired}
            onChange={(e) => dispatch({ type: 'SET', campo: 'totalCreditsRequired', valor: e.target.value })}
            placeholder="Ej. 180"
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
            Horas requeridas
          </label>
          <Input
            type="number"
            min={0}
            value={state.totalHoursRequired}
            onChange={(e) => dispatch({ type: 'SET', campo: 'totalHoursRequired', valor: e.target.value })}
            placeholder="Ej. 2400"
          />
        </div>
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
          Perfil del egresado
        </label>
        <Textarea
          rows={4}
          value={state.graduationProfile}
          onChange={(e) => dispatch({ type: 'SET', campo: 'graduationProfile', valor: e.target.value })}
          placeholder="Describí las competencias y habilidades del egresado…"
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
          Requisitos de admisión
        </label>
        <Textarea
          rows={3}
          value={state.admissionRequirements}
          onChange={(e) => dispatch({ type: 'SET', campo: 'admissionRequirements', valor: e.target.value })}
          placeholder="Ej. Título secundario completo…"
        />
      </div>

      {error && <p className="text-[13px] text-danger">{error}</p>}

      <div className="flex items-center justify-between">
        <Button
          variant="outline"
          onClick={() => dispatch({ type: 'IR_A_PASO', paso: 'datos' })}
        >
          Atrás
        </Button>
        <Button variant="gradient" loading={createProgram.isPending} onClick={handleCrear}>
          Crear carrera
        </Button>
      </div>
    </GlassCard>
  );
}

export function CarreraWizard() {
  const [state, dispatch] = useReducer(reducer, null, estadoInicial);

  const pasoActual = PASOS.findIndex((p) => p.key === state.paso);

  return (
    <PageContainer>
      <div className="flex flex-col gap-4">
        <div>
          <Link
            to="/administrativo/gestion-academica?section=carreras"
            className="mb-1 flex items-center gap-1 text-[12px] text-ink-faint hover:text-ink"
          >
            <ChevronLeft className="h-3.5 w-3.5" />
            Carreras
          </Link>
          <h1 className="font-heading text-xl font-bold text-ink">Nueva carrera</h1>
        </div>

        <Stepper
          pasos={PASOS}
          pasoActual={pasoActual}
          pasosVisitados={state.pasosVisitados.map((k) => PASOS.findIndex((p) => p.key === k)).filter((i) => i >= 0)}
          onStepClick={(idx) => {
            const paso = PASOS[idx];
            if (state.pasosVisitados.includes(paso.key)) {
              dispatch({ type: 'IR_A_PASO', paso: paso.key });
            }
          }}
        />

        {state.paso === 'datos' && <Paso1Datos state={state} dispatch={dispatch} />}
        {state.paso === 'contenido' && <Paso2Contenido state={state} dispatch={dispatch} />}
      </div>
    </PageContainer>
  );
}
