import { Label } from '@/components/ui/label';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';

export function RadioChoice({ name, ariaLabel, value, onChange, options }) {
  return (
    <RadioGroup value={value} onValueChange={onChange} aria-label={ariaLabel} className="flex flex-wrap gap-2">
      {options.map((option) => {
        const id = `${name}-${option.value}`;
        return (
          <Label
            key={option.value}
            htmlFor={id}
            className="w-auto cursor-pointer gap-2 rounded-xl border border-border bg-surface px-3 py-2 font-semibold has-[[data-state=checked]]:border-brand has-[[data-state=checked]]:bg-accent-soft"
          >
            <RadioGroupItem id={id} value={option.value} disabled={option.disabled} />
            {option.label}
          </Label>
        );
      })}
    </RadioGroup>
  );
}
