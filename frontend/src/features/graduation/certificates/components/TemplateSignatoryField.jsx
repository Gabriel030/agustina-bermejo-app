import { Label } from '@/components/ui/label';
import { NativeSelect } from '@/components/ui/native-select';
import { Notice } from '@/components/Notice';
import { StatusPill } from '@/components/StatusPill';

export function TemplateSignatoryField({ id, label, emptyLabel, value, otherValue, signatories, readOnly, error, onChange }) {
  const chosen = signatories.find((signatory) => signatory.id === value) ?? null;
  const unavailable = value != null && chosen === null;

  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <NativeSelect
        id={id}
        className="[&_select]:min-h-11 md:[&_select]:min-h-8"
        value={value ?? ''}
        disabled={readOnly}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        onChange={(event) => onChange(event.target.value === '' ? null : Number(event.target.value))}
      >
        <option value="">{emptyLabel}</option>
        {signatories.map((signatory) => (
          <option key={signatory.id} value={signatory.id} disabled={signatory.id === otherValue}>
            {signatory.name} · {signatory.role}
          </option>
        ))}
      </NativeSelect>
      {chosen && (
        <span>
          <StatusPill tone={chosen.hasSignature ? 'success' : 'warning'}>
            {chosen.hasSignature ? 'Firma cargada' : 'Sin firma cargada'}
          </StatusPill>
        </span>
      )}
      {unavailable && <Notice tone="warning">El firmante elegido ya no está disponible. Elegí otro.</Notice>}
      {error && (
        <span id={`${id}-error`} role="alert" className="text-size-12 font-medium text-danger">
          {error}
        </span>
      )}
    </div>
  );
}
