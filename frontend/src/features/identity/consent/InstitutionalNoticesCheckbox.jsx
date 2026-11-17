import { Checkbox } from '@/components/ui/checkbox';

export function InstitutionalNoticesCheckbox({ id, checked, onCheckedChange, disabled }) {
  return (
    <div className="rounded-lg border border-border p-3">
      <div className="flex items-start gap-3">
        <Checkbox
          id={id}
          checked={checked}
          onCheckedChange={(value) => onCheckedChange(value === true)}
          disabled={disabled}
          aria-describedby={`${id}-provisional`}
          className="mt-0.5"
        />
        <label htmlFor={id} className="text-size-13 font-semibold text-ink">
          Acepto recibir avisos institucionales (novedades, fechas y calendario) y los{' '}
          <a
            href="/legal/terminos"
            target="_blank"
            rel="noopener noreferrer"
            className="font-semibold text-brand hover:text-accent-2"
          >
            Términos y Condiciones
          </a>
          .
        </label>
      </div>
      <p id={`${id}-provisional`} className="mt-1.5 pl-7 text-size-12 italic text-ink-faint">
        Texto provisional, pendiente de validación.
      </p>
    </div>
  );
}
