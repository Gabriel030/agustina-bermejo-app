import { Label } from '@/components/ui/label';
import { NativeSelect } from '@/components/ui/native-select';
import { Notice } from '@/components/Notice';

export function TemplateSignatoryField({ position, label, hint, value, otherValue, signatories, readOnly, error, onChange }) {
  const selectId = `template-signatory-${position}`;
  const chosen = signatories.find((signatory) => signatory.id === value) ?? null;
  const unavailable = value != null && chosen === null;

  return (
    <div className="flex flex-col gap-3 rounded-2xl border border-border p-4">
      <div className="flex items-center justify-between gap-2">
        <h3 className="font-heading text-size-14 font-semibold text-ink">{label}</h3>
        <span className="rounded-full bg-neutral-bg px-2.5 py-0.5 text-size-11 font-bold text-ink-soft">{hint}</span>
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor={selectId}>Firmante</Label>
        <NativeSelect
          id={selectId}
          value={value ?? ''}
          disabled={readOnly}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${selectId}-error` : undefined}
          onChange={(event) => onChange(event.target.value === '' ? null : Number(event.target.value))}
        >
          <option value="">Elegí un firmante…</option>
          {signatories.map((signatory) => (
            <option key={signatory.id} value={signatory.id} disabled={signatory.id === otherValue}>
              {signatory.name} · {signatory.role}
            </option>
          ))}
        </NativeSelect>
        {error && (
          <span id={`${selectId}-error`} role="alert" className="text-size-12 font-medium text-danger">
            {error}
          </span>
        )}
      </div>
      {chosen && (
        <div className="flex flex-col gap-0.5">
          <b className="text-size-14 text-ink">{chosen.name}</b>
          <span className="text-size-13 text-ink-soft">{chosen.role}</span>
          {!chosen.hasSignature && (
            <span className="text-size-12 text-ink-faint">Este firmante no tiene firma escaneada: se imprime solo la línea.</span>
          )}
        </div>
      )}
      {unavailable && <Notice tone="warning">El firmante elegido ya no está disponible. Elegí otro.</Notice>}
      {!chosen && !unavailable && <span className="text-size-12-5 text-ink-faint">Falta elegir este firmante.</span>}
    </div>
  );
}
