import { useReducer } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, ArrowRight, CheckCircle2, XCircle, Clock, Send, PowerOff } from 'lucide-react';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Stepper } from '@/components/Stepper';
import { RejectionReasonNotice } from '@/features/academic-structure/components/RejectionReasonNotice';
import { useUnsavedChangesGuard } from '@/features/academic-structure/components/UnsavedChangesGuard';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import SubjectLifecycleAction from '@/features/academic-structure/components/SubjectLifecycleAction';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  useSubject,
  useCreateSubject,
  useUpdateSubject,
  useSubmitForApproval,
} from '@/features/academic-structure/hooks/useSubjects';

const STEPS = [
  { key: 'identification', label: 'Identificación' },
  { key: 'programmaticDetail', label: 'Detalle programático' },
  { key: 'teachingEvaluation', label: 'Dictado y evaluación' },
  { key: 'approval', label: 'Aprobación' },
];

const SUBJECT_BANK = '/administrativo/gestion-academica';

const CODE_EDITABLE_STATUSES = ['DRAFT', 'REJECTED'];

const SAVE_ERROR_MESSAGES = {
  SUBJECT_ALREADY_EXISTS: 'Ya existe una materia con ese código o ese nombre.',
  SUBJECT_INVALID_TRANSITION: 'La materia está pendiente de aprobación y no se puede editar hasta que dirección la revise.',
};

const SUBMIT_ERROR_MESSAGES = {
  INCOMPLETE_SUBJECT: 'Faltan datos obligatorios: cargá al menos un tipo de horas semanales y la modalidad de calificación.',
  SUBJECT_INVALID_TRANSITION: 'La materia ya no está en borrador: recargá la página para ver su estado actual.',
};

function mapApprovalStatus(subject) {
  if (!subject) return 'locked';
  const map = {
    DRAFT: 'draft',
    PENDING_APPROVAL: 'pending',
    ACTIVE: 'approved',
    INACTIVE: 'inactive',
    REJECTED: 'rejected',
  };
  return map[subject.status] ?? 'locked';
}

const FORM_FIELDS = ['code', 'name', 'weeklyHours', 'weeklyHoursLab', 'description', 'gradeMode', 'maxFinalExamAttempts'];

function pickFormFields(state) {
  return Object.fromEntries(FORM_FIELDS.map((key) => [key, state[key]]));
}

function withBaseline(state) {
  return { ...state, baseline: pickFormFields(state) };
}

function isDirty(state) {
  return FORM_FIELDS.some((key) => state[key] !== state.baseline[key]);
}

function initialState(subject) {
  return withBaseline({
    step: 'identification',
    visitedSteps: subject ? STEPS.map((step) => step.key) : ['identification'],
    savedSubjectId: subject?.id ?? null,
    savedStatus: subject?.status ?? null,
    approvalStatus: mapApprovalStatus(subject),
    rejectionReason: subject?.rejectionReason ?? null,
    code: subject?.code ?? '',
    name: subject?.name ?? '',
    weeklyHours: subject?.weeklyHours != null ? String(subject.weeklyHours) : '',
    weeklyHoursLab: subject?.weeklyHoursLab != null ? String(subject.weeklyHoursLab) : '',
    description: subject?.description ?? '',
    gradeMode: subject?.gradeMode ?? '',
    maxFinalExamAttempts: subject?.maxFinalExamAttempts != null ? String(subject.maxFinalExamAttempts) : '1',
  });
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
        {
          ...withBaseline(state),
          savedSubjectId: action.subject.id,
          savedStatus: action.subject.status,
          approvalStatus: mapApprovalStatus(action.subject),
          step: 'approval',
        },
        'approval',
      );
    case 'STATUS_CHANGED':
      return {
        ...state,
        savedStatus: action.status,
        approvalStatus: mapApprovalStatus({ status: action.status }),
      };
    case 'SUBMIT_SUCCESS':
      return { ...state, savedStatus: 'PENDING_APPROVAL', approvalStatus: 'pending', rejectionReason: null };
    default:
      return state;
  }
}

function updatePayloadFor(payload, savedStatus) {
  if (CODE_EDITABLE_STATUSES.includes(savedStatus)) return payload;
  const { code: _code, ...rest } = payload;
  return rest;
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

function missingFieldLabels(state) {
  const missing = [];
  if (!state.code.trim()) missing.push('el código');
  if (!state.name.trim()) missing.push('el nombre');
  if (!(Number(state.weeklyHours) > 0 || Number(state.weeklyHoursLab) > 0)) missing.push('las horas semanales (teóricas o prácticas)');
  if (!state.gradeMode) missing.push('la modalidad de calificación');
  return missing;
}

function stepperApprovalStatus(approvalStatus) {
  if (approvalStatus === 'pending') return 'pending';
  if (approvalStatus === 'approved') return 'approved';
  if (approvalStatus === 'rejected') return 'rejected';
  return null;
}

function FieldGroup({ label, required, children, hint, htmlFor }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={htmlFor} className="text-[13px] font-semibold text-ink">
        {label}
        {required && <span className="ml-1 text-danger">*</span>}
      </label>
      {children}
      {hint && <p className="text-[11px] text-ink-faint">{hint}</p>}
    </div>
  );
}

function Step1Identificacion({ state, dispatch }) {
  const codeLocked = state.savedStatus !== null && !CODE_EDITABLE_STATUSES.includes(state.savedStatus);
  return (
    <div className="flex flex-col gap-5">
      {state.approvalStatus === 'rejected' && state.rejectionReason && <RejectionReasonNotice
          reason={state.rejectionReason}
          title="Dirección rechazó esta materia"
          hint="Corregí los datos, guardá los cambios y volvé a enviarla a aprobación."
        />}
      <div className="grid grid-cols-2 gap-4">
        <FieldGroup
          label="Código"
          htmlFor="subject-code"
          required
          hint={codeLocked ? 'El código no se puede cambiar una vez aprobada la materia' : 'Máximo 4 caracteres'}
        >
          <Input
            id="subject-code"
            disabled={codeLocked}
            value={state.code}
            onChange={(e) => dispatch({ type: 'UPDATE_FIELD', field: 'code', value: e.target.value.toUpperCase().slice(0, 4) })}
            placeholder="MAT1"
            maxLength={4}
          />
        </FieldGroup>
        <div />
      </div>
      <FieldGroup label="Nombre" htmlFor="subject-name" required>
        <Input
          id="subject-name"
          value={state.name}
          onChange={(e) => dispatch({ type: 'UPDATE_FIELD', field: 'name', value: e.target.value })}
          placeholder="Matemática I"
        />
      </FieldGroup>
      <div className="grid grid-cols-2 gap-4">
        <FieldGroup label="Horas semanales teóricas" htmlFor="subject-weekly-hours" required hint="Al menos un tipo de horas es obligatorio">
          <Input
            id="subject-weekly-hours"
            type="number"
            min="0"
            value={state.weeklyHours}
            onChange={(e) => dispatch({ type: 'UPDATE_FIELD', field: 'weeklyHours', value: e.target.value })}
            placeholder="3"
          />
        </FieldGroup>
        <FieldGroup label="Horas semanales prácticas" htmlFor="subject-weekly-hours-lab">
          <Input
            id="subject-weekly-hours-lab"
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
      <FieldGroup label="Descripción" htmlFor="subject-description" hint="Breve descripción del contenido y objetivos de la materia">
        <Textarea
          id="subject-description"
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
      <FieldGroup label="Modalidad de calificación" htmlFor="subject-grade-mode" required>
        <Select
          value={state.gradeMode}
          onValueChange={(v) => dispatch({ type: 'UPDATE_FIELD', field: 'gradeMode', value: v })}
        >
          <SelectTrigger id="subject-grade-mode">
            <SelectValue placeholder="Seleccioná una modalidad" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="NUMERIC">Numérica (1–10)</SelectItem>
            <SelectItem value="PASS_FAIL">Aprobado / Desaprobado</SelectItem>
          </SelectContent>
        </Select>
      </FieldGroup>
      <FieldGroup label="Intentos máximos de examen final" htmlFor="subject-max-attempts" hint="Si lo dejás vacío, el final regular tiene un solo intento.">
        <Input
          id="subject-max-attempts"
          type="number"
          min="1"
          value={state.maxFinalExamAttempts}
          onChange={(e) => dispatch({ type: 'UPDATE_FIELD', field: 'maxFinalExamAttempts', value: e.target.value })}
          placeholder="1"
        />
      </FieldGroup>
    </div>
  );
}

function Step4Aprobacion({ state, onSubmit, isSubmitting, onStatusChanged }) {
  const lifecycleSubject = { id: state.savedSubjectId, code: state.code, name: state.name, status: state.savedStatus };
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
        <SubjectLifecycleAction subject={lifecycleSubject} size="default" onDone={() => onStatusChanged('INACTIVE')} />
      </div>
    );
  }

  if (state.approvalStatus === 'inactive') {
    return (
      <div className="flex flex-col items-center gap-4 py-8 text-center">
        <div className="flex h-14 w-14 items-center justify-center rounded-full bg-neutral-bg">
          <PowerOff className="h-7 w-7 text-ink-faint" />
        </div>
        <div>
          <p className="text-[16px] font-semibold text-ink">Materia inactiva</p>
          <p className="mt-1 text-[13px] text-ink-faint">
            <span className="font-bold">{state.code}</span> — {state.name} está dada de baja. Reactivala para volver a usarla desde el banco.
          </p>
        </div>
        <SubjectLifecycleAction subject={lifecycleSubject} size="default" onDone={() => onStatusChanged('ACTIVE')} />
      </div>
    );
  }

  if (state.approvalStatus === 'rejected') {
    return (
      <div className="flex flex-col items-center gap-4 py-8 text-center">
        <XCircle className="h-14 w-14 text-danger" />
        <div>
          <p className="text-[16px] font-semibold text-ink">Solicitud rechazada</p>
          {state.rejectionReason && <p className="mt-1 text-[13px] text-ink">Motivo: {state.rejectionReason}</p>}
          <p className="mt-1 text-[13px] text-ink-faint">
            Revisá los datos en los pasos anteriores y volvé a enviarla.
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

function ReviewLockNotice() {
  return (
    <div role="status" className="mb-5 flex items-start gap-3 rounded-xl border border-warning/30 bg-warning/10 px-4 py-3">
      <Clock className="mt-0.5 h-4 w-4 shrink-0 text-warning" />
      <p className="text-[13px] text-ink">
        Está en revisión de dirección: los datos no se pueden editar hasta que la apruebe o la rechace.
      </p>
    </div>
  );
}

function WizardContent({ state, dispatch, onSubmit, isSubmitting, onStatusChanged }) {
  const underReview = state.savedStatus === 'PENDING_APPROVAL';
  const isFormStep = state.step !== 'approval';
  return (
    <>
      {underReview && isFormStep && <ReviewLockNotice />}
      <fieldset disabled={underReview && isFormStep} className="min-w-0 border-0 p-0">
        {state.step === 'identification' && <Step1Identificacion state={state} dispatch={dispatch} />}
        {state.step === 'programmaticDetail' && <Step2DetalleProgramatico state={state} dispatch={dispatch} />}
        {state.step === 'teachingEvaluation' && <Step3DictadoEvaluacion state={state} dispatch={dispatch} />}
      </fieldset>
      {state.step === 'approval' && (
        <Step4Aprobacion state={state} onSubmit={onSubmit} isSubmitting={isSubmitting} onStatusChanged={onStatusChanged} />
      )}
    </>
  );
}

function WizardForm({ isEdit, existingSubject }) {
  const createSubject = useCreateSubject();
  const updateSubject = useUpdateSubject();
  const submitForApproval = useSubmitForApproval();

  const [state, dispatch] = useReducer(reducer, existingSubject, initialState);

  const validity = computeValidity(state);
  const { requestNavigation, guardDialog } = useUnsavedChangesGuard(isDirty(state));
  const bankPath = state.savedStatus ? `${SUBJECT_BANK}?section=materias&tab=${state.savedStatus}` : SUBJECT_BANK;
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
      const alreadySaved = state.savedSubjectId !== null;
      const result = alreadySaved
        ? await updateSubject.mutateAsync({ id: state.savedSubjectId, ...updatePayloadFor(payload, state.savedStatus) })
        : await createSubject.mutateAsync(payload);
      dispatch({ type: 'SAVE_SUCCESS', subject: result });
      toast.success(alreadySaved ? 'Cambios guardados' : 'Borrador guardado');
    } catch (err) {
      toast.error(SAVE_ERROR_MESSAGES[err?.code] ?? 'No se pudo guardar la materia. Revisá los datos e intentá de nuevo.');
    }
  }

  async function handleSubmitForApproval() {
    const id = state.savedSubjectId;
    if (!id) return;
    try {
      await submitForApproval.mutateAsync(id);
      dispatch({ type: 'SUBMIT_SUCCESS' });
      toast.success('Solicitud enviada al director');
    } catch (err) {
      toast.error(SUBMIT_ERROR_MESSAGES[err?.code] ?? 'No se pudo enviar la solicitud. Intentá de nuevo.');
    }
  }

  const missing = missingFieldLabels(state);
  const isSaving = createSubject.isPending || updateSubject.isPending;
  const isSubmitting = submitForApproval.isPending;

  return (
    <PageContainer
      title={isEdit ? 'Editar materia' : 'Nueva materia'}
      action={
        <Button variant="outline" onClick={() => requestNavigation(bankPath)}>
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
          <WizardContent
            state={state}
            dispatch={dispatch}
            onSubmit={handleSubmitForApproval}
            isSubmitting={isSubmitting}
            onStatusChanged={(status) => dispatch({ type: 'STATUS_CHANGED', status })}
          />
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

          <div className="flex flex-col items-end gap-2">
           <div className="flex gap-3">
            {isLastEditableStep && (
              <Button
                variant="gradient"
                onClick={handleSave}
                disabled={!allValid || isSaving || state.savedStatus === 'PENDING_APPROVAL'}
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
              <Button variant="outline" onClick={() => requestNavigation(bankPath)}>
                Ver banco de materias
              </Button>
            )}
           </div>
            {isLastEditableStep && missing.length > 0 && (
              <p role="status" className="max-w-sm text-right text-[12px] text-ink-faint">
                Para guardar falta completar: {missing.join(', ')}.
              </p>
            )}
          </div>
        </div>
      </GlassCard>
      {guardDialog}
    </PageContainer>
  );
}

function SubjectLoadFailure({ notFound, onRetry }) {
  return (
    <PageContainer title="Editar materia">
      <GlassCard className="flex flex-1 flex-col p-12">
        <EmptyState
          title={notFound ? 'Esta materia no existe o ya no está disponible' : 'No pudimos cargar la materia'}
          description={
            notFound
              ? 'Revisá el enlace o volvé al banco de materias.'
              : 'Revisá tu conexión e intentá de nuevo.'
          }
          action={
            <div className="flex gap-3">
              {!notFound && (
                <Button variant="gradient" onClick={onRetry}>
                  Reintentar
                </Button>
              )}
              <Button asChild variant="outline">
                <Link to={SUBJECT_BANK}>Volver al banco de materias</Link>
              </Button>
            </div>
          }
        />
      </GlassCard>
    </PageContainer>
  );
}

export function AdministrativoSubjectWizard() {
  const { id } = useParams();
  const isEdit = !!id;
  const { data: existingSubject, isLoading, isError, error, refetch } = useSubject(id);

  if (isEdit && isLoading) {
    return (
      <PageContainer title="Editar materia">
        <GlassCard className="p-6">
          <Skeleton rows={6} />
        </GlassCard>
      </PageContainer>
    );
  }

  if (isEdit && (isError || !existingSubject)) {
    return <SubjectLoadFailure notFound={error?.status === 404} onRetry={() => refetch()} />;
  }

  return <WizardForm isEdit={isEdit} existingSubject={existingSubject} />;
}
