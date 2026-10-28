import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { cn } from '@/lib/utils';

export function OptionSelect({ id, value, onChange, options, placeholder, invalid, disabled, className }) {
  function handleChange(next) {
    if (next !== '') onChange(next);
  }

  return (
    <Select value={value ?? ''} onValueChange={handleChange} disabled={disabled}>
      <SelectTrigger id={id} aria-invalid={invalid || undefined} className={cn('h-10 w-full', className)}>
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value} disabled={option.disabled}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
