import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { DOCUMENT_TYPES, documentTypeOf } from '@/lib/documentType';

export function DocumentField({ type, number, onTypeChange, onNumberChange, error, disabled, ariaLabel = 'Tipo de documento', onBlur }) {
  const actual = documentTypeOf(type);
  return (
    <div>
      <div className="flex gap-2">
        <Select value={actual.value} onValueChange={onTypeChange} disabled={disabled}>
          <SelectTrigger aria-label={ariaLabel} className="w-[130px] shrink-0">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {DOCUMENT_TYPES.map((tipo) => (
              <SelectItem key={tipo.value} value={tipo.value}>{tipo.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Input
          value={number ?? ''}
          onChange={(e) => onNumberChange(e.target.value)}
          onBlur={onBlur}
          placeholder={actual.placeholder}
          inputMode={actual.value === 'DNI' ? 'numeric' : 'text'}
          aria-invalid={!!error}
          aria-label="Número de documento"
          disabled={disabled}
        />
      </div>
      <p className={error ? 'mt-1 text-xs text-danger' : 'mt-1 text-xs text-ink-soft'}>{error ?? actual.hint}</p>
    </div>
  );
}
