import { CircleAlert, Eye, EyeOff } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

export function ActivationPasswordField({ id, label, value, onChange, onBlur, error, hint, disabled, visible, onToggleVisible, inputRef }) {
  const describedBy = [hint ? `${id}-hint` : null, error ? `${id}-error` : null].filter(Boolean).join(' ') || undefined;

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-size-13-5 font-semibold text-ink">
        {label}
      </label>
      <div className="relative">
        <Input
          ref={inputRef}
          id={id}
          name={id}
          type={visible ? 'text' : 'password'}
          autoComplete="new-password"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          onBlur={onBlur}
          disabled={disabled}
          aria-invalid={error ? 'true' : undefined}
          aria-describedby={describedBy}
          className="h-11 pr-12"
        />
        <Button
          type="button"
          variant="ghost"
          size="icon"
          disabled={disabled}
          aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
          aria-pressed={visible}
          onClick={onToggleVisible}
          className="absolute inset-y-0 right-0 size-11 text-ink-soft focus-visible:ring-0 focus-visible:outline-2 focus-visible:outline-solid focus-visible:-outline-offset-2 focus-visible:outline-(--focus-ring)"
        >
          {visible ? <EyeOff size={18} aria-hidden="true" /> : <Eye size={18} aria-hidden="true" />}
        </Button>
      </div>
      {hint && (
        <span id={`${id}-hint`} className="text-size-12-5 text-ink-faint">
          {hint}
        </span>
      )}
      {error && (
        <p id={`${id}-error`} role="alert" className="flex items-start gap-1.5 text-size-12-5 font-semibold text-danger">
          <CircleAlert size={14} className="mt-px shrink-0" aria-hidden="true" />
          {error}
        </p>
      )}
    </div>
  );
}
