import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { ALLOWED_MARKERS, TEXT_FIELDS, fieldError } from '@/features/graduation/certificates/lib/certificateTemplate';

export function TemplateTextFields({ form, readOnly, onChange }) {
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-1.5" aria-label="Marcadores permitidos">
        <span className="text-size-12-5 text-ink-faint">Marcadores permitidos:</span>
        {ALLOWED_MARKERS.map((marker) => (
          <code key={marker} className="rounded-md bg-accent-soft px-1.5 py-0.5 text-size-12 text-primary">{`{${marker}}`}</code>
        ))}
      </div>
      {TEXT_FIELDS.map((field) => {
        const value = form[field.name];
        const error = fieldError(field, value);
        const errorId = `${field.name}-error`;
        const Control = field.multiline ? Textarea : Input;
        return (
          <div key={field.name} className="flex flex-col gap-1.5">
            <Label htmlFor={`template-${field.name}`}>{field.label}</Label>
            <Control
              id={`template-${field.name}`}
              value={value}
              readOnly={readOnly}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? errorId : undefined}
              className={readOnly ? 'cursor-default' : undefined}
              onChange={(event) => onChange({ ...form, [field.name]: event.target.value })}
            />
            <div className="flex items-start justify-between gap-3 text-size-12">
              {error ? (
                <span id={errorId} role="alert" className="font-medium text-danger">
                  {error}
                </span>
              ) : (
                <span />
              )}
              <span className="shrink-0 text-ink-faint">
                {value.length}/{field.max}
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}
