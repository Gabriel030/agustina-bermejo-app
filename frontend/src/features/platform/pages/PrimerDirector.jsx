import { useRef, useState } from 'react';
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

const EMPTY_FORM = { institutionName: '', firstName: '', lastName: '', email: '', documentType: 'DNI', documentNumber: '', reason: '' };
const REASON_MIN = 5;
const REASON_MAX = 500;
const EMAIL_FORMAT = /^\S+@\S+\.\S+$/;

function validateForm(form) {
  const errors = {};
  if (!form.institutionName.trim()) errors.institutionName = 'Ingresá el nombre de la institución';
  if (!form.firstName.trim()) errors.firstName = 'Ingresá el nombre';
  if (!form.lastName.trim()) errors.lastName = 'Ingresá el apellido';
  if (!EMAIL_FORMAT.test(form.email.trim())) errors.email = 'Ingresá un email válido';
  const documentError = form.documentNumber.trim() ? validateDocument(form.documentType, form.documentNumber) : 'Ingresá el documento';
  if (documentError) errors.documentNumber = documentError;
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

function Field({ id, label, error, children }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-[13px] font-medium text-ink-soft">{label}</label>
      {children}
      {error && <span className="text-xs text-danger">{error}</span>}
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
  const submittedForm = useRef(null);

  const errors = validateForm(form);
  const shown = touched ? errors : {};

  function setField(field) {
    return (event) => setForm((previous) => ({ ...previous, [field]: event.target.value }));
  }

  function askConfirmation() {
    setTouched(true);
    if (Object.keys(errors).length === 0) setConfirming(true);
  }

  function submit() {
    setConfirming(false);
    submittedForm.current = form;
    provision.mutate(toRequest(form), {
      onSuccess: (response) => setCreated(response),
      onError: (error) => {
        if (error.status === 409 && error.code !== 'IDEMPOTENCY_KEY_REUSED') {
          setAlreadyExists(true);
          status.refetch();
        }
      },
    });
  }

  const header = {
    title: 'Primer Director',
    description: 'Da de alta la institución y a su primer Director. Solo se puede hacer una vez.',
  };

  if (created) {
    const submitted = submittedForm.current;
    return (
      <PageContainer {...header}>
        <GlassCard className="flex max-w-3xl flex-col gap-4 p-5">
          <Banner tone="ok" icon={CheckCircle2} title="Se envió el email para crear su contraseña">
            Le llegó a {created.directorEmail}. Cuando la cree, ya puede entrar y dar de alta al resto del personal.
          </Banner>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <div className="text-xs text-ink-faint">Institución</div>
              <b className="text-ink">{submitted.institutionName.trim()}</b>
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

  const networkFailure = provision.isError && provision.error.status === undefined;
  const requestFailure = provision.isError && !networkFailure && !alreadyExists;

  return (
    <PageContainer {...header}>
      <GlassCard className="flex max-w-3xl flex-col gap-5 p-5">
        {networkFailure && (
          <Banner tone="danger" icon={AlertTriangle} title="No pudimos confirmar si se creó">
            Podés reintentar: si el Director ya se había creado, no se duplica.
          </Banner>
        )}
        {requestFailure && (
          <Banner tone="danger" icon={AlertTriangle} title="No se pudo crear el Director">
            {provision.error.message}
          </Banner>
        )}

        <section className="flex flex-col gap-3">
          <h2 className="font-heading text-[15px] font-semibold text-ink">Institución</h2>
          <Field id="institutionName" label="Nombre de la institución" error={shown.institutionName}>
            <Input id="institutionName" value={form.institutionName} onChange={setField('institutionName')} placeholder="Ej. Instituto Superior Nuevo Horizonte" aria-invalid={!!shown.institutionName} />
          </Field>
        </section>

        <section className="flex flex-col gap-3">
          <h2 className="font-heading text-[15px] font-semibold text-ink">Director</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field id="firstName" label="Nombre" error={shown.firstName}>
              <Input id="firstName" value={form.firstName} onChange={setField('firstName')} aria-invalid={!!shown.firstName} />
            </Field>
            <Field id="lastName" label="Apellido" error={shown.lastName}>
              <Input id="lastName" value={form.lastName} onChange={setField('lastName')} aria-invalid={!!shown.lastName} />
            </Field>
          </div>
          <Field id="email" label="Email" error={shown.email}>
            <Input id="email" type="email" value={form.email} onChange={setField('email')} placeholder="nombre@institucion.edu.ar" aria-invalid={!!shown.email} />
          </Field>
          <div className="flex flex-col gap-1.5">
            <span className="text-[13px] font-medium text-ink-soft">Documento</span>
            <DocumentField
              type={form.documentType}
              number={form.documentNumber}
              onTypeChange={(value) => setForm((previous) => ({ ...previous, documentType: value }))}
              onNumberChange={(value) => setForm((previous) => ({ ...previous, documentNumber: value }))}
              error={shown.documentNumber}
            />
          </div>
        </section>

        <Field id="reason" label="Motivo" error={shown.reason}>
          <Textarea id="reason" rows={3} value={form.reason} onChange={setField('reason')} placeholder="Por qué se da de alta este Director (queda en la auditoría)" aria-invalid={!!shown.reason} />
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
              <b className="text-ink">{form.institutionName.trim()}</b>
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
