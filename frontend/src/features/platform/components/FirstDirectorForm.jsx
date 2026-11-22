import { AlertTriangle, Building2, CheckCircle2, Clock, FileText, Loader2, Lock, Mail, UserRound } from 'lucide-react';
import { StatusPill } from '@/components/StatusPill';
import { DocumentField } from '@/components/DocumentField';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { cn } from '@/lib/utils';
import {
  FIRST_DIRECTOR_CONTROL_CLASS,
  FirstDirectorField,
  FirstDirectorErrorText,
} from '@/features/platform/components/FirstDirectorField';
import { FirstDirectorSection } from '@/features/platform/components/FirstDirectorSection';
import { firstDirectorFieldAria } from '@/features/platform/lib/firstDirectorFieldAria';
import { FIELD_LIMITS } from '@/features/platform/lib/firstDirectorErrors';
import { REASON_MAX } from '@/features/platform/lib/firstDirectorForm';

const WHY_ID = 'first-director-why';

function SubmitReason({ missing, pending }) {
  if (pending) {
    return (
      <p id={WHY_ID} aria-live="polite" className="flex items-start gap-1.5 text-size-12-5 font-bold text-ink-soft">
        <Clock className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
        Creando, no cierres esta pantalla
      </p>
    );
  }
  if (missing.length > 0) {
    return (
      <p id={WHY_ID} aria-live="polite" className="flex items-start gap-1.5 text-size-12-5 font-bold text-warning-ink">
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
        Para crear falta: {missing.join(', ')}
      </p>
    );
  }
  return (
    <p id={WHY_ID} aria-live="polite" className="flex items-start gap-1.5 text-size-12-5 font-bold text-success-ink">
      <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
      Listo para crear
    </p>
  );
}

export function FirstDirectorForm({ formState, institutionLocked, pending, banner, onSubmit }) {
  const { form, effectiveForm, shownErrors, missing, changeField, changeDocumentType, blurField } = formState;
  const control = (field) => ({
    ...firstDirectorFieldAria(field, shownErrors[field], field === 'institutionName' || field === 'email'),
    className: FIRST_DIRECTOR_CONTROL_CLASS,
    onChange: (event) => changeField(field, event.target.value),
    onBlur: () => blurField(field),
  });

  return (
    <form
      noValidate
      aria-busy={pending}
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit();
      }}
      className="flex max-w-3xl flex-col gap-5 rounded-card border border-border bg-surface p-4 shadow-card sm:p-5"
    >
      {banner}

      <FirstDirectorSection icon={Building2} title="Institución">
        <FirstDirectorField
          id="institutionName"
          label="Nombre de la institución"
          hint={institutionLocked ? 'La institución ya existe, se le asigna el Director.' : 'Se crea junto con su primer Director.'}
          error={shownErrors.institutionName}
          badge={institutionLocked && <StatusPill tone="neutral"><Lock className="h-3.5 w-3.5" aria-hidden="true" />Ya existe</StatusPill>}
        >
          <Input
            {...control('institutionName')}
            className={cn(FIRST_DIRECTOR_CONTROL_CLASS, 'disabled:bg-neutral-bg disabled:text-ink-soft disabled:opacity-100')}
            value={effectiveForm.institutionName}
            placeholder="Ej. Instituto Superior Nuevo Horizonte"
            maxLength={FIELD_LIMITS.institutionName}
            disabled={institutionLocked}
            autoComplete="off"
          />
        </FirstDirectorField>
      </FirstDirectorSection>

      <FirstDirectorSection icon={UserRound} title="Director">
        <div className="grid gap-4 sm:grid-cols-2">
          <FirstDirectorField id="firstName" label="Nombre" error={shownErrors.firstName}>
            <Input {...control('firstName')} value={form.firstName} maxLength={FIELD_LIMITS.firstName} autoComplete="given-name" />
          </FirstDirectorField>
          <FirstDirectorField id="lastName" label="Apellido" error={shownErrors.lastName}>
            <Input {...control('lastName')} value={form.lastName} maxLength={FIELD_LIMITS.lastName} autoComplete="family-name" />
          </FirstDirectorField>
        </div>
        <FirstDirectorField id="email" label="Email" hint="Recibe el código para crear su contraseña." error={shownErrors.email}>
          <Input
            {...control('email')}
            type="email"
            inputMode="email"
            value={form.email}
            placeholder="nombre@institucion.edu.ar"
            maxLength={FIELD_LIMITS.email}
            autoComplete="off"
          />
        </FirstDirectorField>
        <div className="flex min-w-0 flex-col gap-1.5">
          <span className="text-size-13 font-bold text-ink-soft">Documento</span>
          <DocumentField
            type={form.documentType}
            number={form.documentNumber}
            onTypeChange={changeDocumentType}
            onNumberChange={(value) => changeField('documentNumber', value)}
            onBlur={() => blurField('documentNumber')}
            error={shownErrors.documentNumber && <FirstDirectorErrorText message={shownErrors.documentNumber} />}
            inputId="documentNumber"
            errorId="documentNumber-error"
            maxLength={FIELD_LIMITS.documentNumber}
            controlClassName={FIRST_DIRECTOR_CONTROL_CLASS}
            errorClassName="text-danger-ink"
            errorRole="alert"
          />
        </div>
      </FirstDirectorSection>

      <FirstDirectorSection icon={FileText} title="Motivo">
        <FirstDirectorField id="reason" label="Motivo" error={shownErrors.reason}>
          <Textarea
            {...control('reason')}
            rows={3}
            value={form.reason}
            placeholder="Por qué se da de alta este Director"
            className={cn(FIRST_DIRECTOR_CONTROL_CLASS, 'h-auto min-h-24')}
          />
          <div className="flex justify-between gap-2 text-size-12-5 text-ink-faint">
            <span>Queda en la Auditoría.</span>
            <span className="tabular-nums">{form.reason.length}/{REASON_MAX}</span>
          </div>
        </FirstDirectorField>
      </FirstDirectorSection>

      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3 border-t border-border pt-4">
        <div className="flex min-w-0 flex-[1_1_220px] flex-col gap-1">
          <SubmitReason missing={missing} pending={pending} />
          <span className="flex items-center gap-1.5 text-size-12-5 text-ink-faint">
            <Mail className="h-4 w-4 shrink-0" aria-hidden="true" />
            Se le envía un email para crear su contraseña.
          </span>
        </div>
        <Button
          type="submit"
          variant="primary"
          className="h-11 w-full px-4 aria-disabled:cursor-not-allowed aria-disabled:opacity-60 sm:w-auto"
          aria-disabled={missing.length > 0 || pending}
          aria-describedby={WHY_ID}
        >
          {pending && <Loader2 className="animate-spin" aria-hidden="true" />}
          {pending ? 'Creando Director…' : 'Crear Director'}
        </Button>
      </div>
    </form>
  );
}
