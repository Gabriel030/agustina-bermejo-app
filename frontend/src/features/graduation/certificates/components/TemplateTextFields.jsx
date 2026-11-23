import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { cn } from '@/lib/utils';
import { REQUIRED_MESSAGE, fieldError, markersOf, textFieldsOf } from '@/features/graduation/certificates/lib/certificateTemplate';

export function TemplateTextFields({ templateKey, form, readOnly, showRequired, onChange }) {
  const markers = markersOf(templateKey);
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-1.5">
        <span id="template-markers-label" className="text-size-12-5 text-ink-faint">
          Marcadores permitidos:
        </span>
        <ul aria-labelledby="template-markers-label" className="flex flex-wrap gap-1.5">
          {markers.map((marker) => (
            <li key={marker}>
              <code className="rounded-md bg-accent-soft px-1.5 py-0.5 text-size-12 text-primary">{`{${marker}}`}</code>
            </li>
          ))}
        </ul>
      </div>
      {textFieldsOf(templateKey).map((field) => {
        const value = form[field.name];
        const trimmed = value.trim();
        const requiredMissing = field.required && trimmed === '';
        const error = requiredMissing ? (showRequired ? REQUIRED_MESSAGE : null) : fieldError(field, trimmed, markers);
        const errorId = `${field.name}-error`;
        const Control = field.multiline ? Textarea : Input;
        return (
          <div key={field.name} className="flex flex-col gap-1.5">
            <Label htmlFor={`template-${field.name}`}>{field.label}</Label>
            <Control
              id={`template-${field.name}`}
              value={value}
              readOnly={readOnly}
              aria-required={field.required ? true : undefined}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? errorId : undefined}
              className={cn(!field.multiline && 'min-h-11 md:min-h-8', readOnly && 'cursor-default')}
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
