import { useReducer, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { ChevronLeft } from 'lucide-react';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Stepper } from '@/components/Stepper';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { LoadErrorState } from '@/components/LoadErrorState';
import { useCreateCourse } from '@/features/academic-structure/hooks/useCourses';
import { useTerminos } from '@/features/academic-structure/hooks/useTerminos';
import { FormField, SelectField } from '@/features/academic-structure/components/FormField';
import {
  countErrors,
  describeSaveError,
  fieldProps,
  invalidFieldsSummary,
  validateCourseBasics,
  validateCourseDetail,
} from '@/features/academic-structure/lib/offeringForm';

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

function Paso1Datos({ state, dispatch }) {
  const { data: terminos, isLoading: loadingTerms, isError: termsFailed, error: termsError, refetch: refetchTerms } = useTerminos();
  const [errors, setErrors] = useState({});

  const termOptions = [
    { value: '', label: 'Seleccionar período…' },
    ...(terminos ?? []).map((t) => ({
      value: String(t.id),
      label: t.description ? `${t.year} — ${t.description}` : String(t.year),
    })),
  ];

  function handleContinuar() {
    const found = validateCourseBasics(state);
    setErrors(found);
    if (countErrors(found) > 0) return;
    dispatch({ type: 'IR_A_PASO', paso: 'detalle' });
  }

  return (
    <GlassCard className="flex flex-col gap-5 p-6">
      <FormField id="course-name" label="Nombre del curso" required error={errors.name}>
        <Input
          {...fieldProps('course-name', errors.name, true)}
          value={state.name}
          onChange={(e) => dispatch({ type: 'SET', campo: 'name', valor: e.target.value })}
          placeholder="Ej. Curso de Excel Avanzado"
        />
      </FormField>

      {termsFailed ? (
        <LoadErrorState error={termsError} onRetry={refetchTerms} resource="los períodos académicos" bare />
      ) : (
        <SelectField
          id="course-term"
          label="Período académico"
          required
          error={errors.termId}
          value={state.termId}
          onChange={(v) => dispatch({ type: 'SET', campo: 'termId', valor: v })}
          options={loadingTerms ? [{ value: '', label: 'Cargando…' }] : termOptions}
        />
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <FormField id="course-capacity" label="Cupo" required error={errors.capacity}>
          <Input
            {...fieldProps('course-capacity', errors.capacity, true)}
            type="number"
            min={1}
            value={state.capacity}
            onChange={(e) => dispatch({ type: 'SET', campo: 'capacity', valor: e.target.value })}
            placeholder="Ej. 30"
          />
        </FormField>
        <FormField id="course-price" label="Precio" error={errors.price}>
          <Input
            {...fieldProps('course-price', errors.price)}
            type="number"
            min={0}
            step={0.01}
            value={state.price}
            onChange={(e) => dispatch({ type: 'SET', campo: 'price', valor: e.target.value })}
            placeholder="Ej. 15000"
          />
        </FormField>
      </div>

      <SelectField
        id="course-teaching-mode"
        label="Modalidad"
        value={state.teachingMode}
        onChange={(v) => dispatch({ type: 'SET', campo: 'teachingMode', valor: v })}
        options={TEACHING_MODE_OPTIONS}
      />

      {countErrors(errors) > 0 && (
        <p role="alert" className="text-[13px] text-danger">
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

function Paso2Detalle({ state, dispatch }) {
  const createCourse = useCreateCourse();
  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState(null);
  const navigate = useNavigate();

  async function handleCrear() {
    const found = validateCourseDetail(state);
    setErrors(found);
    setSubmitError(null);
    if (countErrors(found) > 0) return;
    try {
      const body = {
        name: state.name.trim(),
        termId: parseInt(state.termId),
        capacity: parseInt(state.capacity),
        ...(state.price && { price: { amount: String(parseFloat(state.price)), currency: 'ARS' } }),
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
      const failure = describeSaveError(err, 'No se pudo crear el curso. Revisá los datos e intentá de nuevo.');
      if (failure.field === 'registrationEnd') setErrors({ registrationEnd: failure.message });
      else setSubmitError(failure.message);
    }
  }

  return (
    <GlassCard className="flex flex-col gap-5 p-6">
      <FormField id="course-schedule" label="Horario">
        <Input
          id="course-schedule"
          value={state.schedule}
          onChange={(e) => dispatch({ type: 'SET', campo: 'schedule', valor: e.target.value })}
          placeholder="Ej. Lunes y miércoles 18–20 hs"
        />
      </FormField>

      <FormField id="course-duration" label="Duración (meses)" required error={errors.durationMonths}>
        <Input
          {...fieldProps('course-duration', errors.durationMonths, true)}
          type="number"
          min={1}
          value={state.durationMonths}
          onChange={(e) => dispatch({ type: 'SET', campo: 'durationMonths', valor: e.target.value })}
          placeholder="Ej. 3"
        />
      </FormField>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <FormField id="course-registration-start" label="Inicio de inscripción">
          <Input
            id="course-registration-start"
            type="datetime-local"
            value={state.registrationStart}
            onChange={(e) => dispatch({ type: 'SET', campo: 'registrationStart', valor: e.target.value })}
          />
        </FormField>
        <FormField id="course-registration-end" label="Cierre de inscripción" error={errors.registrationEnd}>
          <Input
            {...fieldProps('course-registration-end', errors.registrationEnd)}
            type="datetime-local"
            value={state.registrationEnd}
            onChange={(e) => dispatch({ type: 'SET', campo: 'registrationEnd', valor: e.target.value })}
          />
        </FormField>
      </div>

      {countErrors(errors) > 0 && (
        <p role="alert" className="text-[13px] text-danger">
          {invalidFieldsSummary(errors)}
        </p>
      )}
      {submitError && (
        <p role="alert" className="text-[13px] text-danger">
          {submitError}
        </p>
      )}

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
