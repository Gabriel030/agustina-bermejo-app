import { Label } from '@/components/ui/label';
import { equivalenceFieldId } from '@/features/workspace/lib/equivalenceForm';

export function EquivalenceFormField({ name, label, optional = false, hint, error, children }) {
  const id = equivalenceFieldId(name);
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined;
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id} className="text-size-13 font-semibold text-ink">
        {label}
        {optional && <span className="font-normal text-ink-faint"> · opcional</span>}
      </Label>
      {children({ id, 'aria-invalid': error ? true : undefined, 'aria-describedby': describedBy })}
      {hint && (
        <p id={hintId} className="text-size-12 text-ink-faint">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-size-12 font-semibold text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

export function EquivalenceErrorSummary({ errors, fieldLabels, fieldOrder }) {
  const entries = fieldOrder.filter((name) => errors[name]).map((name) => ({ name, label: fieldLabels[name], message: errors[name].message }));
  if (entries.length === 0) return null;
  return (
    <div role="alert" className="rounded-xl bg-danger-bg px-3 py-2.5 text-size-13 text-ink">
      <p className="font-bold">Revisá {entries.length === 1 ? 'este dato' : 'estos datos'} para continuar</p>
      <ul className="mt-1 flex flex-col gap-0.5">
        {entries.map(({ name, label, message }) => (
          <li key={name}>
            <a
              href={`#${equivalenceFieldId(name)}`}
              className="inline-flex min-h-11 items-center font-semibold md:min-h-0 underline underline-offset-2"
              onClick={(event) => {
                event.preventDefault();
                document.getElementById(equivalenceFieldId(name))?.focus();
              }}
            >
              {label}
            </a>
            : {message}
          </li>
        ))}
      </ul>
    </div>
  );
}
