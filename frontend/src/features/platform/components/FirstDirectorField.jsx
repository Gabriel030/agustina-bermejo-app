import { AlertCircle } from 'lucide-react';
import { cn } from '@/lib/utils';

export const FIRST_DIRECTOR_CONTROL_CLASS = 'h-11 aria-invalid:border-danger aria-invalid:border-2';

export function FirstDirectorErrorText({ message }) {
  return (
    <span className="inline-flex items-start gap-1.5 font-bold">
      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
      <span>
        <span>Error:</span> <span>{message}</span>
      </span>
    </span>
  );
}

export function FirstDirectorFieldError({ id, message }) {
  return (
    <p id={id} role="alert" className="text-size-12-5 text-danger-ink">
      <FirstDirectorErrorText message={message} />
    </p>
  );
}

export function FirstDirectorField({ id, label, hint, error, badge, className, children }) {
  return (
    <div className={cn('flex min-w-0 flex-col gap-1.5', className)}>
      <div className="flex flex-wrap items-center gap-2">
        <label htmlFor={id} className="text-size-13 font-bold text-ink-soft">{label}</label>
        {badge}
      </div>
      {children}
      {error ? (
        <FirstDirectorFieldError id={`${id}-error`} message={error} />
      ) : (
        hint && <p id={`${id}-hint`} className="text-size-12-5 text-ink-faint">{hint}</p>
      )}
    </div>
  );
}
