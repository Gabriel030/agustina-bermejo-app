import { NativeSelect } from '@/components/ui/native-select'

export function FormField({ id, label, required, error, hint, children }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
        {label}
        {required && (
          <span aria-hidden="true" className="ml-1 text-danger">
            *
          </span>
        )}
      </label>
      {children}
      {hint && !error && <p className="text-[11px] text-ink-faint">{hint}</p>}
      {error && (
        <p id={`${id}-error`} role="alert" className="text-[12.5px] text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

export function SelectField({ id, label, required, error, value, onChange, options, disabled }) {
  return (
    <FormField id={id} label={label} required={required} error={error}>
      <NativeSelect
        id={id}
        value={value}
        disabled={disabled}
        required={required}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        onChange={(e) => onChange(e.target.value)}
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </NativeSelect>
    </FormField>
  );
}
