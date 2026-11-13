import { useEffect, useRef, useState } from 'react';
import { AlertTriangle, CheckCircle2 } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { DocumentField } from '@/components/DocumentField';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { validateDocument, documentLabel } from '@/lib/documentType';
import { useFirstDirectorStatus } from '@/features/platform/hooks/useFirstDirectorStatus';
import { useProvisionFirstDirector } from '@/features/platform/hooks/useProvisionFirstDirector';
import { FIELD_LIMITS, failureMessageOf, fieldErrorOf, isDirectorAlreadyExists, isRetryableFailure } from '@/features/platform/lib/firstDirectorErrors';

const EMPTY_FORM = { institutionName: '', firstName: '', lastName: '', email: '', documentType: 'DNI', documentNumber: '', reason: '' };
const REASON_MIN = 5;
const REASON_MAX = 500;
const EMAIL_FORMAT = /^\S+@\S+\.\S+$/;
const FIELD_ORDER = ['institutionName', 'firstName', 'lastName', 'email', 'documentNumber', 'reason'];

function limitMessage(label, limit) {
  return `${label} admite hasta ${limit} caracteres`;
}

function validateForm(form) {
  const errors = {};
  if (!form.institutionName.trim()) errors.institutionName = 'Ingresá el nombre de la institución';
  else if (form.institutionName.trim().length > FIELD_LIMITS.institutionName) errors.institutionName = limitMessage('El nombre de la institución', FIELD_LIMITS.institutionName);
  if (!form.firstName.trim()) errors.firstName = 'Ingresá el nombre';
  else if (form.firstName.trim().length > FIELD_LIMITS.firstName) errors.firstName = limitMessage('El nombre', FIELD_LIMITS.firstName);
  if (!form.lastName.trim()) errors.lastName = 'Ingresá el apellido';
  else if (form.lastName.trim().length > FIELD_LIMITS.lastName) errors.lastName = limitMessage('El apellido', FIELD_LIMITS.lastName);
  if (!EMAIL_FORMAT.test(form.email.trim())) errors.email = 'Ingresá un email válido';
  else if (form.email.trim().length > FIELD_LIMITS.email) errors.email = limitMessage('El email', FIELD_LIMITS.email);
  const documentError = form.documentNumber.trim() ? validateDocument(form.documentType, form.documentNumber) : 'Ingresá el documento';
  if (documentError) errors.documentNumber = documentError;
  else if (form.documentNumber.trim().length > FIELD_LIMITS.documentNumber) errors.documentNumber = limitMessage('El documento', FIELD_LIMITS.documentNumber);
  if (form.reason.trim().length < REASON_MIN) errors.reason = `El motivo necesita al menos ${REASON_MIN} caracteres`;
  else if (form.reason.length > REASON_MAX) errors.reason = `El motivo admite hasta ${REASON_MAX} caracteres`;
  return errors;
}

function toRequest(form) {
  return {
    institutionName: form.institutionName.trim(),
    firstName: form.firstName.trim(),
    lastName: form.lastName.trim(),
    email: form.email.trim(),
    documentType: form.documentType,
    documentNumber: form.documentNumber.trim(),
    reason: form.reason.trim(),
  };
}

function Banner({ tone, icon: Icon, title, children, action }) {
  const tones = { ok: 'bg-success-bg text-success', danger: 'bg-danger-bg text-danger' };
  return (
    <div role={tone === 'danger' ? 'alert' : 'status'} className={`flex items-start gap-3 rounded-xl p-3.5 text-sm ${tones[tone]}`}>
      <Icon className="mt-0.5 h-5 w-5 shrink-0" />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <b>{title}</b>
        {children && <div>{children}</div>}
        {action && <div className="mt-1">{action}</div>}
      </div>
    </div>
  );
}

function errorProps(id, error) {
  return { 'aria-invalid': !!error, 'aria-describedby': error ? `${id}-error` : undefined };
}

function Field({ id, label, error, children }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-size-13 font-medium text-ink-soft">{label}</label>
      {children}
      {error && <span id={`${id}-error`} className="text-xs text-danger">{error}</span>}
    </div>
  );
}

function LoadingCard() {
  return (
    <GlassCard className="flex max-w-3xl flex-col gap-3 p-5" aria-busy="true">
      <Skeleton className="h-5 w-1/3" />
      <Skeleton className="h-10" />
      <Skeleton className="h-10" />
      <Skeleton className="h-10" />
    </GlassCard>
  );
}

export function PrimerDirector() {
  const status = useFirstDirectorStatus();
  const provision = useProvisionFirstDirector();
  const [form, setForm] = useState(EMPTY_FORM);
  const [touched, setTouched] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [created, setCreated] = useState(null);
  const [alreadyExists, setAlreadyExists] = useState(false);
  const [serverErrors, setServerErrors] = useState({});
  const [submitError, setSubmitError] = useState(null);
  const [submittedForm, setSubmittedForm] = useState(null);
  const successRef = useRef(null);

  const lockedInstitution = status.data && !status.data.directorExists ? status.data.institutionName : null;
  const effectiveForm = lockedInstitution ? { ...form, institutionName: lockedInstitution } : form;
  const errors = { ...validateForm(effectiveForm), ...serverErrors };
  const shown = touched ? errors : serverErrors;

  useEffect(() => {
    if (created) successRef.current?.focus();
  }, [created]);

  function focusFirstInvalid(currentErrors) {
    const first = FIELD_ORDER.find((field) => currentErrors[field]);
    if (first) document.getElementById(first)?.focus();
  }

  function setField(field) {
    return (event) => {
      const { value } = event.target;
      setForm((previous) => ({ ...previous, [field]: value }));
      setServerErrors((previous) => (previous[field] ? { ...previous, [field]: undefined } : previous));
    };
  }

  function setDocumentField(patch) {
    setForm((previous) => ({ ...previous, ...patch }));
    setServerErrors((previous) => (previous.documentNumber ? { ...previous, documentNumber: undefined } : previous));
  }

  function askConfirmation() {
    setTouched(true);
    const pending = Object.fromEntries(Object.entries(errors).filter(([, message]) => message));
    if (Object.keys(pending).length === 0) setConfirming(true);
    else focusFirstInvalid(pending);
  }

  function submit() {
    setConfirming(false);
    setSubmittedForm(effectiveForm);
    setSubmitError(null);
    provision.mutate(toRequest(effectiveForm), {
      onSuccess: (response) => setCreated(response),
      onError: (error) => {
        const fieldError = fieldErrorOf(error);
        if (isDirectorAlreadyExists(error)) {
          setAlreadyExists(true);
          status.refetch();
        } else if (fieldError) {
          setServerErrors({ [fieldError.field]: fieldError.message });
          setTimeout(() => document.getElementById(fieldError.field)?.focus(), 0);
        } else {
          setSubmitError(error);
        }
      },
    });
  }

  const header = {
    title: 'Primer Director',
    description: 'Da de alta la institución y a su primer Director. Solo se puede hacer una vez.',
  };

  if (created) {
    const submitted = submittedForm;
    const storedInstitution = created.institutionCreated ? submitted.institutionName.trim() : (status.data?.institutionName ?? submitted.institutionName.trim());
    return (
      <PageContainer {...header}>
        <GlassCard className="flex max-w-3xl flex-col gap-4 p-5">
          <div ref={successRef} tabIndex={-1} className="outline-none">
            <Banner tone="ok" icon={CheckCircle2} title="Director creado">
              Estamos enviando un email con el código para crear su contraseña a {created.directorEmail}. El código vale 15 minutos; si no llega, puede usar Recuperar contraseña desde el login.
            </Banner>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <div className="text-xs text-ink-faint">Institución</div>
              <b className="text-ink">{storedInstitution}</b>
            </div>
            <div>
              <div className="text-xs text-ink-faint">Director</div>
              <b className="text-ink">{submitted.firstName.trim()} {submitted.lastName.trim()}</b>
            </div>
          </div>
        </GlassCard>
      </PageContainer>
    );
  }

  if (status.isPending) {
    return (
      <PageContainer {...header}>
        <LoadingCard />
      </PageContainer>
    );
  }

  if (status.isError) {
    return (
      <PageContainer {...header}>
        <GlassCard className="flex max-w-3xl flex-col gap-3 p-5">
          <Banner
            tone="danger"
            icon={AlertTriangle}
            title="No pudimos revisar si ya existe un Director"
            action={<Button variant="outline" onClick={() => status.refetch()}>Reintentar</Button>}
          >
            Revisá tu conexión y volvé a intentar.
          </Banner>
        </GlassCard>
      </PageContainer>
    );
  }

  if (status.data.directorExists || alreadyExists) {
    return (
      <PageContainer {...header}>
        <GlassCard className="flex max-w-3xl flex-col gap-3 p-5">
          <Banner tone="danger" icon={AlertTriangle} title="Ya existe un Director">
            {status.data.institutionName ? `${status.data.institutionName} ya tiene su primer Director. ` : 'Esta institución ya tiene su primer Director. '}
            Para sumar más, entrá como Director y usá Personal.
          </Banner>
        </GlassCard>
      </PageContainer>
    );
  }

  const networkFailure = !!submitError && submitError.status === undefined;
  const retryableFailure = !!submitError && isRetryableFailure(submitError);
  const requestFailure = !!submitError && !networkFailure && !retryableFailure;

  return (
    <PageContainer {...header}>
      <GlassCard className="flex max-w-3xl flex-col gap-5 p-5">
        {networkFailure && (
          <Banner tone="danger" icon={AlertTriangle} title="No pudimos confirmar si se creó">
            Podés reintentar: si el Director ya se había creado, no se duplica.
          </Banner>
        )}
        {retryableFailure && (
          <Banner tone="danger" icon={AlertTriangle} title="No se pudo confirmar la creación">
            Podés reintentar: si el Director ya se había creado, no se duplica.
            {submitError.requestId && <div className="mt-1 text-xs opacity-80">Código para soporte: {submitError.requestId}</div>}
          </Banner>
        )}
        {requestFailure && (
          <Banner tone="danger" icon={AlertTriangle} title="No se pudo crear el Director">
            {failureMessageOf(submitError)}
            {submitError.requestId && <div className="mt-1 text-xs opacity-80">Código para soporte: {submitError.requestId}</div>}
          </Banner>
        )}

        <section className="flex flex-col gap-3">
          <h2 className="font-heading text-size-15 font-semibold text-ink">Institución</h2>
          <Field id="institutionName" label="Nombre de la institución" error={shown.institutionName}>
            <Input id="institutionName" value={effectiveForm.institutionName} onChange={setField('institutionName')} placeholder="Ej. Instituto Superior Nuevo Horizonte" maxLength={FIELD_LIMITS.institutionName} disabled={!!lockedInstitution} {...errorProps('institutionName', shown.institutionName)} />
            {lockedInstitution && <span className="text-xs text-ink-faint">La institución ya existe, se le asigna el Director.</span>}
          </Field>
        </section>

        <section className="flex flex-col gap-3">
          <h2 className="font-heading text-size-15 font-semibold text-ink">Director</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field id="firstName" label="Nombre" error={shown.firstName}>
              <Input id="firstName" value={form.firstName} onChange={setField('firstName')} maxLength={FIELD_LIMITS.firstName} {...errorProps('firstName', shown.firstName)} />
            </Field>
            <Field id="lastName" label="Apellido" error={shown.lastName}>
              <Input id="lastName" value={form.lastName} onChange={setField('lastName')} maxLength={FIELD_LIMITS.lastName} {...errorProps('lastName', shown.lastName)} />
            </Field>
          </div>
          <Field id="email" label="Email" error={shown.email}>
            <Input id="email" type="email" value={form.email} onChange={setField('email')} placeholder="nombre@institucion.edu.ar" maxLength={FIELD_LIMITS.email} {...errorProps('email', shown.email)} />
          </Field>
          <div className="flex flex-col gap-1.5">
            <span className="text-size-13 font-medium text-ink-soft">Documento</span>
            <DocumentField
              type={form.documentType}
              number={form.documentNumber}
              onTypeChange={(value) => setDocumentField({ documentType: value })}
              onNumberChange={(value) => setDocumentField({ documentNumber: value })}
              error={shown.documentNumber}
              inputId="documentNumber"
              errorId="documentNumber-error"
              maxLength={FIELD_LIMITS.documentNumber}
            />
          </div>
        </section>

        <Field id="reason" label="Motivo" error={shown.reason}>
          <Textarea id="reason" rows={3} value={form.reason} onChange={setField('reason')} placeholder="Por qué se da de alta este Director (queda en la auditoría)" {...errorProps('reason', shown.reason)} />
          <span className="self-end text-xs tabular-nums text-ink-faint">{form.reason.length}/{REASON_MAX}</span>
        </Field>

        <div className="flex flex-wrap items-center justify-between gap-3">
          <span className="text-xs text-ink-faint">Se le envía un email para crear su contraseña.</span>
          <Button variant="gradient" onClick={askConfirmation} loading={provision.isPending}>
            {provision.isPending ? 'Creando Director' : 'Crear Director'}
          </Button>
        </div>
      </GlassCard>

      <Dialog open={confirming} onOpenChange={setConfirming}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>¿Crear el primer Director?</DialogTitle>
            <DialogDescription>Esta acción envía un email y no se puede deshacer.</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-3 text-sm">
            <div>
              <div className="text-xs text-ink-faint">Institución</div>
              <b className="text-ink">{effectiveForm.institutionName.trim()}</b>
            </div>
            <div>
              <div className="text-xs text-ink-faint">Director</div>
              <b className="text-ink">{form.firstName.trim()} {form.lastName.trim()}</b> · {documentLabel(form.documentType)} {form.documentNumber.trim()}
            </div>
            <div>
              <div className="text-xs text-ink-faint">Email para crear su contraseña</div>
              <b className="text-brand">{form.email.trim()}</b>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirming(false)}>Cancelar</Button>
            <Button onClick={submit}>Sí, crear Director</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
