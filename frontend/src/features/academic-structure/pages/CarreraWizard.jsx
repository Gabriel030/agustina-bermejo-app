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
import { useCreateProgram } from '@/features/academic-structure/hooks/usePrograms';
import { useUnsavedChangesGuard } from '@/features/academic-structure/components/UnsavedChangesGuard';
import { FormField, SelectField } from '@/features/academic-structure/components/FormField';
import { TEACHING_MODE_OPTIONS } from '@/lib/teachingMode';
import {
  countErrors,
  describeSaveError,
  fieldProps,
  invalidFieldsSummary,
  validateProgramFields,
} from '@/features/academic-structure/lib/offeringForm';

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

function estadoInicial() {
  return {
    paso: 'datos',
    pasosVisitados: ['datos'],
    showErrors: false,
    name: '',
    totalYears: '',
    level: '',
    teachingMode: '',
    totalCreditsRequired: '',
    price: '',
    enrollmentFee: '',
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
    case 'SHOW_ERRORS':
      return { ...state, showErrors: true };
    case 'IR_A_PASO':
      return { ...state, paso: action.paso, pasosVisitados: visitado(state, action.paso) };
    default:
      return state;
  }
}

function visibleBasicErrors(state) {
  const found = validateProgramFields(state, { requireCredits: false });
  return Object.fromEntries(
    Object.entries({ name: found.name, totalYears: found.totalYears }).filter(([, v]) => v),
  );
}

function Paso1Datos({ state, dispatch }) {
  const errors = state.showErrors ? visibleBasicErrors(state) : {};

  function handleContinuar() {
    if (countErrors(visibleBasicErrors(state)) > 0) {
      dispatch({ type: 'SHOW_ERRORS' });
      return;
    }
    dispatch({ type: 'IR_A_PASO', paso: 'contenido' });
  }

  return (
    <GlassCard className="flex flex-col gap-5 p-6">
      <FormField id="program-name" label="Nombre de la carrera" required error={errors.name}>
        <Input
          {...fieldProps('program-name', errors.name, true)}
          value={state.name}
          onChange={(e) => dispatch({ type: 'SET', campo: 'name', valor: e.target.value })}
          placeholder="Ej. Licenciatura en Enfermería"
        />
      </FormField>

      <FormField id="program-years" label="Duración (años)" required error={errors.totalYears}>
        <Input
          {...fieldProps('program-years', errors.totalYears, true)}
          type="number"
          min={1}
          max={10}
          value={state.totalYears}
          onChange={(e) => dispatch({ type: 'SET', campo: 'totalYears', valor: e.target.value })}
          placeholder="Ej. 3"
        />
      </FormField>

      <SelectField
        id="program-level"
        label="Nivel"
        value={state.level}
        onChange={(v) => dispatch({ type: 'SET', campo: 'level', valor: v })}
        options={LEVEL_OPTIONS}
      />

      <SelectField
        id="program-teaching-mode"
        label="Modalidad"
        value={state.teachingMode}
        onChange={(v) => dispatch({ type: 'SET', campo: 'teachingMode', valor: v })}
        options={TEACHING_MODE_OPTIONS}
      />

      {countErrors(errors) > 0 && (
        <p role="alert" className="text-size-13 text-danger">
          {invalidFieldsSummary(errors)}
        </p>
      )}

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
  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState(null);
  const navigate = useNavigate();

  async function handleCrear() {
    const found = validateProgramFields(state, { requireCredits: true, requirePrices: true });
    setErrors(found);
    setSubmitError(null);
    if (countErrors(found) > 0) return;
    try {
      const body = {
        name: state.name.trim(),
        totalYears: parseInt(state.totalYears),
        ...(state.level && { level: state.level }),
        ...(state.teachingMode && { teachingMode: state.teachingMode }),
        totalCreditsRequired: parseInt(state.totalCreditsRequired),
        price: { amount: String(parseFloat(state.price)), currency: 'ARS' },
        enrollmentFeeAmount: { amount: String(parseFloat(state.enrollmentFee)), currency: 'ARS' },
        ...(state.totalHoursRequired && { totalHoursRequired: parseInt(state.totalHoursRequired) }),
        ...(state.graduationProfile.trim() && { graduationProfile: state.graduationProfile.trim() }),
        ...(state.admissionRequirements.trim() && { admissionRequirements: state.admissionRequirements.trim() }),
      };
      const result = await createProgram.mutateAsync(body);
      toast.success('Carrera creada');
      navigate(`/administrativo/gestion-academica/carreras/${result.id}`);
    } catch (err) {
      setSubmitError(describeSaveError(err, 'No se pudo crear la carrera. Intentá de nuevo.').message);
    }
  }

  return (
    <GlassCard className="flex flex-col gap-5 p-6">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <FormField id="program-credits" label="Créditos requeridos" required error={errors.totalCreditsRequired}>
          <Input
            {...fieldProps('program-credits', errors.totalCreditsRequired, true)}
            type="number"
            min={0}
            value={state.totalCreditsRequired}
            onChange={(e) => dispatch({ type: 'SET', campo: 'totalCreditsRequired', valor: e.target.value })}
            placeholder="Ej. 180"
          />
        </FormField>
        <FormField id="program-price" label="Cuota mensual" required error={errors.price}>
          <Input
            {...fieldProps('program-price', errors.price, true)}
            type="number"
            min={0}
            step={0.01}
            value={state.price}
            onChange={(e) => dispatch({ type: 'SET', campo: 'price', valor: e.target.value })}
            placeholder="Ej. 48000"
          />
        </FormField>
        <FormField id="program-enrollment-fee" label="Matrícula" required error={errors.enrollmentFee}>
          <Input
            {...fieldProps('program-enrollment-fee', errors.enrollmentFee, true)}
            type="number"
            min={0}
            step={0.01}
            value={state.enrollmentFee}
            onChange={(e) => dispatch({ type: 'SET', campo: 'enrollmentFee', valor: e.target.value })}
            placeholder="Ej. 95000"
          />
        </FormField>
        <FormField id="program-hours" label="Horas requeridas" error={errors.totalHoursRequired}>
          <Input
            {...fieldProps('program-hours', errors.totalHoursRequired)}
            type="number"
            min={0}
            value={state.totalHoursRequired}
            onChange={(e) => dispatch({ type: 'SET', campo: 'totalHoursRequired', valor: e.target.value })}
            placeholder="Ej. 2400"
          />
        </FormField>
      </div>

      <FormField id="program-profile" label="Perfil del egresado">
        <Textarea
          id="program-profile"
          rows={4}
          value={state.graduationProfile}
          onChange={(e) => dispatch({ type: 'SET', campo: 'graduationProfile', valor: e.target.value })}
          placeholder="Describí las competencias y habilidades del egresado…"
        />
      </FormField>

      <FormField id="program-admission" label="Requisitos de admisión">
        <Textarea
          id="program-admission"
          rows={3}
          value={state.admissionRequirements}
          onChange={(e) => dispatch({ type: 'SET', campo: 'admissionRequirements', valor: e.target.value })}
          placeholder="Ej. Título secundario completo…"
        />
      </FormField>

      {countErrors(errors) > 0 && (
        <p role="alert" className="text-size-13 text-danger">
          {invalidFieldsSummary(errors)}
        </p>
      )}
      {submitError && (
        <p role="alert" className="text-size-13 text-danger">
          {submitError}
        </p>
      )}

      <div className="flex items-center justify-between">
        <Button variant="outline" onClick={() => dispatch({ type: 'IR_A_PASO', paso: 'datos' })}>
          Atrás
        </Button>
        <Button variant="gradient" loading={createProgram.isPending} onClick={handleCrear}>
          Crear carrera
        </Button>
      </div>
    </GlassCard>
  );
}

const FIELD_KEYS = [
  'name',
  'totalYears',
  'level',
  'teachingMode',
  'totalCreditsRequired',
  'price',
  'enrollmentFee',
  'totalHoursRequired',
  'graduationProfile',
  'admissionRequirements',
];

export function CarreraWizard() {
  const [state, dispatch] = useReducer(reducer, null, estadoInicial);
  const initial = estadoInicial();
  const dirty = FIELD_KEYS.some((key) => state[key] !== initial[key]);
  const { guardDialog } = useUnsavedChangesGuard(dirty);

  function handleStepClick(paso) {
    if (paso !== 'datos' && countErrors(visibleBasicErrors(state)) > 0) {
      dispatch({ type: 'IR_A_PASO', paso: 'datos' });
      dispatch({ type: 'SHOW_ERRORS' });
      return;
    }
    dispatch({ type: 'IR_A_PASO', paso });
  }

  return (
    <PageContainer>
      <div className="flex flex-col gap-4">
        <div>
          <Link
            to="/administrativo/gestion-academica?section=carreras"
            className="mb-1 flex items-center gap-1 text-size-12 text-ink-faint hover:text-ink"
          >
            <ChevronLeft className="h-3.5 w-3.5" />
            Carreras
          </Link>
          <h1 className="font-heading text-xl font-bold text-ink">Nueva carrera</h1>
        </div>

        <Stepper
          pasos={PASOS}
          pasoActual={state.paso}
          pasosVisitados={state.pasosVisitados}
          onStepClick={handleStepClick}
        />

        {state.paso === 'datos' && <Paso1Datos state={state} dispatch={dispatch} />}
        {state.paso === 'contenido' && <Paso2Contenido state={state} dispatch={dispatch} />}
        {guardDialog}
      </div>
    </PageContainer>
  );
}
