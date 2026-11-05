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
      <select
        id={id}
        value={value}
        disabled={disabled}
        required={required}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded-xl border border-surface-border bg-surface/50 px-3 py-2 text-[13.5px] text-ink outline-none focus:border-accent focus:ring-1 focus:ring-accent/30 aria-[invalid=true]:border-danger"
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </FormField>
  );
}
