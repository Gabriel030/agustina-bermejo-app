import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select';
import { formatMoney } from '@/lib/utils';
import {
  ORGANISM_OPTIONS,
  REASON_MAX_LENGTH,
  REASON_MIN_LENGTH,
  overdueLabel,
} from '@/features/graduation/certificate-exceptions/lib/certificateExceptions';

function FieldError({ id, message }) {
  if (!message) return null;
  return (
    <p id={id} className="text-size-12 font-semibold text-danger">
      {message}
    </p>
  );
}

export function StudentDebtContext({ studentName, programName, totalDue, overdueInstallments }) {
  return (
    <div className="flex flex-col gap-1 rounded-xl bg-neutral-bg px-3 py-2.5" data-testid="student-debt-context">
      <span className="font-heading text-size-14 font-semibold text-ink">{studentName}</span>
      <span className="text-size-12-5 text-ink-soft">{programName}</span>
      {totalDue && (
        <span className="text-size-13 tabular-nums text-ink">
          Debe <b className="font-heading font-bold">{formatMoney(totalDue)}</b>
          {overdueInstallments != null && <> · {overdueLabel(overdueInstallments)}</>}
        </span>
      )}
    </div>
  );
}

export function OrganismFields({ values, errors, onChange, onBlur }) {
  return (
    <>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="exception-organism-kind">Tipo de organismo</Label>
        <NativeSelect
          id="exception-organism-kind"
          className="[&_select]:h-11 md:[&_select]:h-8"
          value={values.organismKind}
          aria-invalid={Boolean(errors.organismKind)}
          aria-describedby={errors.organismKind ? 'exception-organism-kind-error' : undefined}
          onChange={(event) => onChange('organismKind', event.target.value)}
          onBlur={() => onBlur('organismKind')}
        >
          <NativeSelectOption value="">Elegí un organismo</NativeSelectOption>
          {ORGANISM_OPTIONS.map((option) => (
            <NativeSelectOption key={option.value} value={option.value}>
              {option.label}
            </NativeSelectOption>
          ))}
        </NativeSelect>
        <FieldError id="exception-organism-kind-error" message={errors.organismKind} />
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="exception-organism-reference">Expediente o referencia</Label>
        <Input
          id="exception-organism-reference"
          className="h-11 md:h-8"
          value={values.organismReference}
          placeholder="Por ejemplo: Exp. DC-2026-48213"
          maxLength={120}
          aria-invalid={Boolean(errors.organismReference)}
          aria-describedby={errors.organismReference ? 'exception-organism-reference-error' : undefined}
          onChange={(event) => onChange('organismReference', event.target.value)}
          onBlur={() => onBlur('organismReference')}
        />
        <FieldError id="exception-organism-reference-error" message={errors.organismReference} />
      </div>
      <ReasonField
        id="exception-reason"
        label="Motivo"
        value={values.reason}
        error={errors.reason}
        placeholder="Contá qué reclamo se presentó y por qué corresponde la excepción"
        onChange={(value) => onChange('reason', value)}
        onBlur={() => onBlur('reason')}
      />
    </>
  );
}

export function ReasonField({ id, label, value, error, placeholder, onChange, onBlur }) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Textarea
        id={id}
        rows={3}
        value={value}
        placeholder={placeholder}
        maxLength={REASON_MAX_LENGTH}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : undefined}
        onChange={(event) => onChange(event.target.value)}
        onBlur={onBlur}
      />
      <div className="flex items-start justify-between gap-2">
        <FieldError id={`${id}-error`} message={error} />
        <span className="ml-auto text-size-12 tabular-nums text-ink-faint" aria-live="polite">
          mínimo {REASON_MIN_LENGTH} · {value.length}/{REASON_MAX_LENGTH}
        </span>
      </div>
    </div>
  );
}
