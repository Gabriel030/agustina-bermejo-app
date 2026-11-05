import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { validateDocument } from '@/lib/documentType';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Button } from '@/components/ui/button';
import { Stepper } from '@/components/Stepper';
import { useAuth } from '@/context/AuthContext';
import { DatosBasicosStep } from '@/features/identity/steps/DatosBasicosStep';
import { DocumentosStep } from '@/features/identity/steps/DocumentosStep';
import { DatosConfirmacionStep } from '@/features/identity/steps/DatosConfirmacionStep';
import { GuardianStep } from '@/features/identity/steps/GuardianStep';
import { ReviewStep } from '@/features/identity/steps/ReviewStep';
import { RegistrationDoneStep } from '@/features/identity/steps/RegistrationDoneStep';
import {
  DOCUMENT_LABELS,
  GUARDIANS_STEP,
  GUARDIAN_DOCUMENT_PATTERN,
  REGISTRATION_STEPS,
  buildProspectDraft,
  isMinor,
  normalizeGuardianDocument,
  registrationSteps,
  todayIso,
  describeRegistrationError,
} from '@/features/identity/lib/studentRegistration';
import { useCrearProspecto, useSubirDocumentoProspecto, useGuardarBorradorProspecto, useConfirmarAlta } from '@/features/identity/hooks/useProspectos';

const schema = z.object({
  nombre: z.string().min(1, 'Requerido'),
  apellido: z.string().min(1, 'Requerido'),
  email: z.string().email('Email inválido'),
  telefono: z.string().optional(),
  documentType: z.enum(['DNI', 'PASSPORT', 'OTHER']).default('DNI'),
  dni: z.string().min(1, 'Requerido'),
  street: z.string().min(1, 'Requerido'),
  number: z.string().optional(),
  floor: z.string().optional(),
  city: z.string().min(1, 'Requerido'),
  state: z.string().optional(),
  postalCode: z.string().optional(),
  country: z.string().optional(),
  birthDate: z.string().min(1, 'Requerido'),
  sex: z.string().optional(),
  nationality: z.string().max(255, 'Máximo 255 caracteres').optional(),
  guardianName: z.string().optional(),
  guardianDni: z.string().optional(),
  guardianRelationship: z.string().optional(),
  guardianPhone: z.string().optional(),
  guardianEmail: z.string().optional(),
  emergencyName: z.string().optional(),
  emergencyRelationship: z.string().optional(),
  emergencyPhone: z.string().optional(),
}).superRefine((valores, ctx) => {
  const mensaje = validateDocument(valores.documentType, valores.dni);
  if (mensaje) ctx.addIssue({ code: 'custom', path: ['dni'], message: mensaje });
  if (valores.birthDate && valores.birthDate > todayIso()) {
    ctx.addIssue({ code: 'custom', path: ['birthDate'], message: 'La fecha no puede ser futura' });
  }
  if (!isMinor(valores.birthDate)) return;
  const required = ['guardianName', 'guardianDni', 'guardianRelationship', 'guardianPhone', 'emergencyName', 'emergencyRelationship', 'emergencyPhone'];
  required.forEach((name) => {
    if (!valores[name]?.trim()) ctx.addIssue({ code: 'custom', path: [name], message: 'Requerido' });
  });
  if (valores.guardianDni?.trim() && !GUARDIAN_DOCUMENT_PATTERN.test(normalizeGuardianDocument(valores.guardianDni))) {
    ctx.addIssue({ code: 'custom', path: ['guardianDni'], message: 'El DNI del tutor debe tener 7 u 8 dígitos' });
  }
  if (valores.guardianEmail?.trim() && !z.string().email().safeParse(valores.guardianEmail.trim()).success) {
    ctx.addIssue({ code: 'custom', path: ['guardianEmail'], message: 'Email inválido' });
  }
});

const GUARDIAN_FIELDS = [
  'guardianName',
  'guardianDni',
  'guardianRelationship',
  'guardianPhone',
  'guardianEmail',
  'emergencyName',
  'emergencyRelationship',
  'emergencyPhone',
];

const CAMPOS_POR_PASO = {
  0: ['nombre', 'apellido', 'email'],
  2: ['dni', 'street', 'city', 'birthDate', 'nationality'],
  [GUARDIANS_STEP]: GUARDIAN_FIELDS,
};

const LAST_STEP = REGISTRATION_STEPS.length - 1;
const REVIEW_STEP = LAST_STEP - 1;

function RegistrationWizard({ onRegisterAnother }) {
  const navigate = useNavigate();
  const { usuario } = useAuth();
  const rolBase = usuario?.roles[0]?.toLowerCase();
  const [currentStep, setCurrentStep] = useState(0);
  const [pasosVisitados, setPasosVisitados] = useState(['0']);
  const [studentId, setStudentId] = useState(null);
  const [documentos, setDocumentos] = useState({});
  const [submitError, setSubmitError] = useState(null);

  const crearProspecto = useCrearProspecto();
  const subirDocumento = useSubirDocumentoProspecto();
  const guardarBorrador = useGuardarBorradorProspecto();
  const confirmarAlta = useConfirmarAlta();

  const {
    register,
    control,
    trigger,
    getValues,
    setError,
    formState: { errors },
  } = useForm({ resolver: zodResolver(schema), mode: 'onTouched', defaultValues: { documentType: 'DNI', dni: '', birthDate: '', sex: '', guardianRelationship: '', emergencyRelationship: '' } });
  const values = useWatch({ control });
  const birthDate = values.birthDate;
  const minor = isMinor(birthDate);

  const isMutating = crearProspecto.isPending || guardarBorrador.isPending || confirmarAlta.isPending;
  const missingDocuments = Object.keys(DOCUMENT_LABELS).filter((type) => !documentos[type]);

  function goTo(step) {
    setSubmitError(null);
    setCurrentStep(step);
    setPasosVisitados((prev) => [...new Set([...prev, String(step)])]);
  }

  function showFailure(err, fallback) {
    const failure = describeRegistrationError(err, fallback);
    if (failure.field) {
      setError(failure.field, { type: 'server', message: failure.message });
      goTo(failure.step);
      return;
    }
    setSubmitError(failure.message);
  }

  function handleFileSelected(tipo, file) {
    subirDocumento.mutate(
      { studentId, type: tipo, file },
      {
        onSuccess: (doc) => setDocumentos((prev) => ({ ...prev, [tipo]: doc })),
        onError: () => toast.error('No se pudo subir el archivo. Intentá de nuevo.'),
      }
    );
  }

  function createProspect() {
    const values = getValues();
    crearProspecto.mutate(
      { nombre: values.nombre, apellido: values.apellido, email: values.email, telefono: values.telefono || undefined },
      {
        onSuccess: (data) => {
          setStudentId(data.id);
          goTo(1);
        },
        onError: (err) => showFailure(err, 'No se pudo registrar al alumno. Intentá de nuevo.'),
      }
    );
  }

  function saveDraft(next) {
    guardarBorrador.mutate(
      { studentId, datos: buildProspectDraft(getValues()) },
      {
        onSuccess: () => goTo(next),
        onError: (err) => showFailure(err, 'No se pudo guardar el borrador. Intentá de nuevo.'),
      }
    );
  }

  function confirmRegistration() {
    confirmarAlta.mutate(
      { studentId, datos: buildProspectDraft(getValues()) },
      {
        onSuccess: () => goTo(LAST_STEP),
        onError: (err) => showFailure(err, 'No se pudo confirmar el alta. Intentá de nuevo.'),
      }
    );
  }

  async function avanzar() {
    const camposActuales = CAMPOS_POR_PASO[currentStep] ?? [];
    if (camposActuales.length > 0 && !(await trigger(camposActuales))) return;

    if (currentStep === 0) {
      if (studentId) goTo(1);
      else createProspect();
      return;
    }
    if (currentStep === 2) {
      saveDraft(minor ? GUARDIANS_STEP : REVIEW_STEP);
      return;
    }
    if (currentStep === GUARDIANS_STEP) {
      saveDraft(REVIEW_STEP);
      return;
    }
    if (currentStep === REVIEW_STEP) {
      confirmRegistration();
      return;
    }
    goTo(currentStep + 1);
  }

  function retroceder() {
    if (currentStep === 0) navigate(-1);
    else if (currentStep === REVIEW_STEP && !minor) goTo(GUARDIANS_STEP - 1);
    else goTo(currentStep - 1);
  }

  return (
    <PageContainer>
      <div>
        <h1 className="font-heading text-2xl font-bold text-ink">Alta de alumno</h1>
        <p className="mt-0.5 text-sm text-ink-soft">Registrá un nuevo alumno en el sistema</p>
      </div>

      <GlassCard className="p-6 flex flex-col gap-6">
        <Stepper
          pasos={registrationSteps(minor)}
          pasoActual={String(currentStep)}
          pasosVisitados={pasosVisitados}
          onStepClick={isMutating || currentStep === LAST_STEP ? undefined : (key) => (Number(key) === GUARDIANS_STEP && !minor ? undefined : goTo(Number(key)))}
        />

        <form onSubmit={(e) => e.preventDefault()}>
          {currentStep === 0 && (
            <DatosBasicosStep register={register} errors={errors} isLoading={isMutating} locked={studentId !== null} />
          )}
          {currentStep === 1 && (
            <DocumentosStep documentosSubidos={documentos} onFileSelected={handleFileSelected} isUploading={subirDocumento.isPending} />
          )}
          {currentStep === 2 && <DatosConfirmacionStep register={register} control={control} errors={errors} isLoading={isMutating} birthDate={birthDate} />}
          {currentStep === GUARDIANS_STEP && (
            <GuardianStep
              register={register}
              control={control}
              errors={errors}
              isLoading={isMutating}
              fullName={`${values.nombre} ${values.apellido}`}
              birthDate={birthDate}
            />
          )}
          {currentStep === REVIEW_STEP && <ReviewStep values={values} missingDocuments={missingDocuments} />}
          {currentStep === LAST_STEP && (
            <RegistrationDoneStep
              fullName={`${values.nombre} ${values.apellido}`}
              email={values.email}
              missingDocuments={missingDocuments}
              onOpenRecord={() => navigate(`/${rolBase}/alumnos/${studentId}`)}
              onRegisterAnother={onRegisterAnother}
            />
          )}
        </form>

        {submitError && (
          <p role="alert" className="rounded-lg border border-danger/40 bg-danger-bg p-3 text-[13px] text-danger">
            {submitError}
          </p>
        )}

        {currentStep < LAST_STEP && (
          <div className="flex items-center justify-between gap-3">
            <Button type="button" variant="ghost" onClick={retroceder} disabled={isMutating}>
              {currentStep === 0 ? 'Cancelar' : 'Atrás'}
            </Button>
            <Button type="button" variant="gradient" onClick={avanzar} disabled={isMutating}>
              {currentStep === REVIEW_STEP ? 'Confirmar alta' : 'Siguiente'}
            </Button>
          </div>
        )}
      </GlassCard>
    </PageContainer>
  );
}

export function NuevoAlumno() {
  const [run, setRun] = useState(0);
  return <RegistrationWizard key={run} onRegisterAnother={() => setRun((n) => n + 1)} />;
}
