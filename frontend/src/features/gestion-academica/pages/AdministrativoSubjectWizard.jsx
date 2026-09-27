import { useReducer } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, ArrowRight, CheckCircle2, XCircle, Clock, Send } from 'lucide-react';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { Stepper } from '@/components/Stepper';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  useSubject,
  useCreateSubject,
  useUpdateSubject,
  useSubmitForApproval,
} from '@/features/gestion-academica/hooks/useSubjects';

const STEPS = [
  { key: 'identification', label: 'Identificación' },
  { key: 'programmaticDetail', label: 'Detalle programático' },
  { key: 'teachingEvaluation', label: 'Dictado y evaluación' },
  { key: 'approval', label: 'Aprobación' },
];

function mapApprovalStatus(subject) {
  if (!subject) return 'locked';
  const map = {
    DRAFT: 'draft',
    PENDING_APPROVAL: 'pending',
    ACTIVE: 'approved',
    INACTIVE: 'approved',
    REJECTED: 'rejected',
  };
  return map[subject.status] ?? 'locked';
}

function initialState(subject) {
  return {
    step: 'identification',
    visitedSteps: ['identification'],
    savedSubjectId: subject?.id ?? null,
    approvalStatus: mapApprovalStatus(subject),
    code: subject?.code ?? '',
    name: subject?.name ?? '',
    weeklyHours: subject?.weeklyHours != null ? String(subject.weeklyHours) : '',
    weeklyHoursLab: subject?.weeklyHoursLab != null ? String(subject.weeklyHoursLab) : '',
    description: subject?.description ?? '',
    gradeMode: subject?.gradeMode ?? '',
    maxFinalExamAttempts: subject?.maxFinalExamAttempts != null ? String(subject.maxFinalExamAttempts) : '',
  };
}

function visited(state, step) {
  return { ...state, visitedSteps: [...new Set([...state.visitedSteps, step])] };
}

function reducer(state, action) {
  switch (action.type) {
    case 'GO_TO_STEP':
      return visited({ ...state, step: action.step }, action.step);
    case 'UPDATE_FIELD':
      return { ...state, [action.field]: action.value };
    case 'SAVE_SUCCESS':
      return visited(
        { ...state, savedSubjectId: action.id, approvalStatus: 'draft', step: 'approval' },
        'approval',
      );
    case 'SUBMIT_SUCCESS':
      return { ...state, approvalStatus: 'pending' };
    default:
      return state;
  }
}

function computeValidity(state) {
  const hasHours = Number(state.weeklyHours) > 0 || Number(state.weeklyHoursLab) > 0;
  return {
    identification: !!(state.code.trim() && state.name.trim() && hasHours),
    programmaticDetail: null,
    teachingEvaluation: !!state.gradeMode,
    approval: null,
  };
}

function stepperApprovalStatus(approvalStatus) {
  if (approvalStatus === 'pending') return 'pending';
  if (approvalStatus === 'approved') return 'approved';
  if (approvalStatus === 'rejected') return 'rejected';
  return null;
}

function FieldGroup({ label, required, children, hint }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-[13px] font-semibold text-ink">
        {label}
        {required && <span className="ml-1 text-danger">*</span>}
      </label>
      {children}
      {hint && <p className="text-[11px] text-ink-faint">{hint}</p>}
    </div>
  );
}

function Step1Identificacion({ state, dispatch }) {
  return (
    <div className="flex flex-col gap-5">
      <div className="grid grid-cols-2 gap-4">
        <FieldGroup label="Código" required hint="Máximo 4 caracteres">
          <Input
            value={state.code}
            onChange={(e) => dispatch({ type: 'UPDATE_FIELD', field: 'code', value: e.target.value.toUpperCase().slice(0, 4) })}
            placeholder="MAT1"
            maxLength={4}
          />
        </FieldGroup>
        <div />
      </div>
      <FieldGroup label="Nombre" required>
        <Input
          value={state.name}
          onChange={(e) => dispatch({ type: 'UPDATE_FIELD', field: 'name', value: e.target.value })}
          placeholder="Matemática I"
        />
      </FieldGroup>
      <div className="grid grid-cols-2 gap-4">
        <FieldGroup label="Horas semanales teóricas" required hint="Al menos un tipo de horas es obligatorio">
          <Input
            type="number"
            min="0"
            value={state.weeklyHours}
            onChange={(e) => dispatch({ type: 'UPDATE_FIELD', field: 'weeklyHours', value: e.target.value })}
            placeholder="3"
          />
        </FieldGroup>
        <FieldGroup label="Horas semanales prácticas">
          <Input
            type="number"
            min="0"
            value={state.weeklyHoursLab}
            onChange={(e) => dispatch({ type: 'UPDATE_FIELD', field: 'weeklyHoursLab', value: e.target.value })}
            placeholder="2"
          />
        </FieldGroup>
      </div>
    </div>
  );
}

function Step2DetalleProgramatico({ state, dispatch }) {
  return (
    <div className="flex flex-col gap-5">
      <FieldGroup label="Descripción" hint="Breve descripción del contenido y objetivos de la materia">
        <Textarea
          value={state.description}
          onChange={(e) => dispatch({ type: 'UPDATE_FIELD', field: 'description', value: e.target.value })}
          placeholder="Esta materia introduce los conceptos fundamentales de…"
          rows={5}
        />
      </FieldGroup>
    </div>
  );
}

function Step3DictadoEvaluacion({ state, dispatch }) {
  return (
    <div className="flex flex-col gap-5">
      <FieldGroup label="Modalidad de calificación" required>
        <Select
          value={state.gradeMode}
          onValueChange={(v) => dispatch({ type: 'UPDATE_FIELD', field: 'gradeMode', value: v })}
        >
          <SelectTrigger>
            <SelectValue placeholder="Seleccioná una modalidad" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="NUMERIC">Numérica (1–10)</SelectItem>
            <SelectItem value="PASS_FAIL">Aprobado / Desaprobado</SelectItem>
          </SelectContent>
        </Select>
      </FieldGroup>
      <FieldGroup label="Intentos máximos de examen final" hint="Dejá vacío para sin límite">
        <Input
          type="number"
          min="1"
          value={state.maxFinalExamAttempts}
          onChange={(e) => dispatch({ type: 'UPDATE_FIELD', field: 'maxFinalExamAttempts', value: e.target.value })}
          placeholder="3"
        />
      </FieldGroup>
    </div>
  );
}

function Step4Aprobacion({ state, onSubmit, isSubmitting }) {
  if (state.approvalStatus === 'locked') {
    return (
      <div className="flex flex-col items-center gap-3 py-8 text-center">
        <p className="text-[14px] text-ink-faint">
          Completá los pasos anteriores y guardá la materia para enviarla a aprobación.
        </p>
      </div>
    );
  }

  if (state.approvalStatus === 'draft') {
    return (
      <div className="flex flex-col items-center gap-4 py-8 text-center">
        <div className="flex h-14 w-14 items-center justify-center rounded-full bg-neutral-bg">
          <Send className="h-7 w-7 text-ink-faint" />
        </div>
        <div>
          <p className="text-[16px] font-semibold text-ink">Borrador guardado</p>
          <p className="mt-1 text-[13px] text-ink-faint">
            <span className="font-bold">{state.code}</span> — {state.name} fue guardada como borrador.
            Cuando esté lista, enviala a aprobación del director.
          </p>
        </div>
        <Button variant="gradient" onClick={onSubmit} disabled={isSubmitting} className="mt-2">
          {isSubmitting ? 'Enviando…' : 'Enviar a aprobación'}
        </Button>
      </div>
    );
  }

  if (state.approvalStatus === 'pending') {
    return (
      <div className="flex flex-col items-center gap-4 py-8 text-center">
        <div className="flex h-14 w-14 items-center justify-center rounded-full bg-warning/15">
          <Clock className="h-7 w-7 text-warning" />
        </div>
        <div>
          <p className="text-[16px] font-semibold text-ink">Pendiente de aprobación</p>
          <p className="mt-1 text-[13px] text-ink-faint">
            La materia fue enviada al director para su revisión. Vas a ser notificado cuando sea aprobada o rechazada.
          </p>
        </div>
      </div>
    );
  }

  if (state.approvalStatus === 'approved') {
    return (
      <div className="flex flex-col items-center gap-4 py-8 text-center">
        <CheckCircle2 className="h-14 w-14 text-success" />
        <div>
          <p className="text-[16px] font-semibold text-ink">Materia activa</p>
          <p className="mt-1 text-[13px] text-ink-faint">
            <span className="font-bold">{state.code}</span> — {state.name} fue aprobada y está activa en el banco de materias.
          </p>
        </div>
      </div>
    );
  }

  if (state.approvalStatus === 'rejected') {
    return (
      <div className="flex flex-col items-center gap-4 py-8 text-center">
        <XCircle className="h-14 w-14 text-danger" />
        <div>
          <p className="text-[16px] font-semibold text-ink">Solicitud rechazada</p>
          <p className="mt-1 text-[13px] text-ink-faint">
            El director rechazó la solicitud. Revisá los datos en los pasos anteriores y volvé a enviarla.
          </p>
        </div>
        <Button variant="gradient" onClick={onSubmit} disabled={isSubmitting} className="mt-2">
          {isSubmitting ? 'Enviando…' : 'Volver a enviar a aprobación'}
        </Button>
      </div>
    );
  }

  return null;
}

function WizardContent({ state, dispatch, onSubmit, isSubmitting }) {
  return (
    <>
      {state.step === 'identification' && <Step1Identificacion state={state} dispatch={dispatch} />}
      {state.step === 'programmaticDetail' && <Step2DetalleProgramatico state={state} dispatch={dispatch} />}
      {state.step === 'teachingEvaluation' && <Step3DictadoEvaluacion state={state} dispatch={dispatch} />}
      {state.step === 'approval' && <Step4Aprobacion state={state} onSubmit={onSubmit} isSubmitting={isSubmitting} />}
    </>
  );
}

function WizardForm({ isEdit, existingSubject, subjectId }) {
  const navigate = useNavigate();
  const createSubject = useCreateSubject();
  const updateSubject = useUpdateSubject(subjectId);
  const submitForApproval = useSubmitForApproval();

  const [state, dispatch] = useReducer(reducer, existingSubject, initialState);

  const validity = computeValidity(state);
  const allValid = validity.identification && validity.teachingEvaluation;

  const currentIndex = STEPS.findIndex((s) => s.key === state.step);
  const isLastEditableStep = currentIndex === STEPS.length - 2;
  const isApprovalStep = state.step === 'approval';

  function goTo(step) {
    dispatch({ type: 'GO_TO_STEP', step });
  }

  function goNext() {
    const next = STEPS[currentIndex + 1];
    if (next) goTo(next.key);
  }

  function goPrev() {
    const prev = STEPS[currentIndex - 1];
    if (prev) goTo(prev.key);
  }

  async function handleSave() {
    const payload = {
      code: state.code.trim(),
      name: state.name.trim(),
      description: state.description.trim() || undefined,
      weeklyHours: state.weeklyHours ? Number(state.weeklyHours) : undefined,
      weeklyHoursLab: state.weeklyHoursLab ? Number(state.weeklyHoursLab) : undefined,
      gradeMode: state.gradeMode || undefined,
      maxFinalExamAttempts: state.maxFinalExamAttempts ? Number(state.maxFinalExamAttempts) : undefined,
    };

    try {
      let result;
      if (isEdit) {
        const { code: _code, ...updatePayload } = payload;
        result = await updateSubject.mutateAsync(updatePayload);
      } else {
        result = await createSubject.mutateAsync(payload);
      }
      dispatch({ type: 'SAVE_SUCCESS', id: result.id });
      toast.success(isEdit ? 'Cambios guardados' : 'Borrador guardado');
    } catch {
      toast.error('No se pudo guardar la materia. Revisá los datos e intentá de nuevo.');
    }
  }

  async function handleSubmitForApproval() {
    const id = state.savedSubjectId;
    if (!id) return;
    try {
      await submitForApproval.mutateAsync(id);
      dispatch({ type: 'SUBMIT_SUCCESS' });
      toast.success('Solicitud enviada al director');
    } catch {
      toast.error('No se pudo enviar la solicitud. Intentá de nuevo.');
    }
  }

  const isSaving = createSubject.isPending || updateSubject.isPending;
  const isSubmitting = submitForApproval.isPending;

  return (
    <PageContainer
      titulo={isEdit ? 'Editar materia' : 'Nueva materia'}
      accion={
        <Button variant="outline" onClick={() => navigate('/administrativo/gestion-academica')}>
          <ArrowLeft className="mr-2 h-4 w-4" />
          Volver al banco
        </Button>
      }
    >
      <GlassCard className="p-6">
        <Stepper
          pasos={STEPS}
          pasoActual={state.step}
          pasosVisitados={state.visitedSteps}
          onStepClick={goTo}
          stepValidity={validity}
          approvalStepKey="approval"
          approvalStepStatus={stepperApprovalStatus(state.approvalStatus)}
        />

        <div className="mt-8 min-h-[240px]">
          <WizardContent state={state} dispatch={dispatch} onSubmit={handleSubmitForApproval} isSubmitting={isSubmitting} />
        </div>

        <div className="mt-8 flex items-center justify-between border-t border-neutral-bg pt-5">
          <Button
            variant="outline"
            onClick={goPrev}
            disabled={currentIndex === 0}
          >
            <ArrowLeft className="mr-2 h-4 w-4" />
            Anterior
          </Button>

          <div className="flex gap-3">
            {isLastEditableStep && (
              <Button
                variant="gradient"
                onClick={handleSave}
                disabled={!allValid || isSaving}
              >
                {isSaving ? 'Guardando…' : isEdit ? 'Guardar cambios' : 'Guardar borrador'}
              </Button>
            )}

            {!isLastEditableStep && !isApprovalStep && (
              <Button variant="outline" onClick={goNext}>
                Siguiente
                <ArrowRight className="ml-2 h-4 w-4" />
              </Button>
            )}

            {isApprovalStep && (state.approvalStatus === 'approved' || state.approvalStatus === 'pending') && (
              <Button variant="outline" onClick={() => navigate('/administrativo/gestion-academica')}>
                Ver banco de materias
              </Button>
            )}
          </div>
        </div>
      </GlassCard>
    </PageContainer>
  );
}

export function AdministrativoSubjectWizard() {
  const { id } = useParams();
  const isEdit = !!id;
  const { data: existingSubject, isLoading } = useSubject(id);

  if (isEdit && isLoading) {
    return (
      <PageContainer titulo="Editar materia">
        <GlassCard className="p-6">
          <Skeleton rows={6} />
        </GlassCard>
      </PageContainer>
    );
  }

  return <WizardForm isEdit={isEdit} existingSubject={existingSubject} subjectId={id} />;
}
