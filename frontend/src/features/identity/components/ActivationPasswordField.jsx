import { Eye, EyeOff } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

export function ActivationPasswordField({ id, label, value, onChange, onBlur, error, hint, disabled, visible, onToggleVisible }) {
  const describedBy = [hint ? `${id}-hint` : null, error ? `${id}-error` : null].filter(Boolean).join(' ') || undefined;

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-size-13-5 font-semibold text-ink">
        {label}
      </label>
      <div className="relative">
        <Input
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
          className="absolute inset-y-0 right-0 size-11 text-ink-soft"
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
        <p id={`${id}-error`} role="alert" className="text-size-12-5 font-semibold text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
