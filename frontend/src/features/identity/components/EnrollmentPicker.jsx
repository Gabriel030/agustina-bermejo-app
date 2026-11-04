import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

export function EnrollmentPicker({ id, label, options, value, onChange }) {
  return (
    <div className="flex max-w-md flex-col gap-1">
      <label htmlFor={id} className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">
        {label}
      </label>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger id={id} className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {options.map((option) => (
            <SelectItem key={option.key} value={option.key}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}
