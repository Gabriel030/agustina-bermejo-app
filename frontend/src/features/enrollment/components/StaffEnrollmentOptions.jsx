import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { ProgressBar } from '@/components/ProgressBar';
import { StatusPill } from '@/components/StatusPill';
import { cn } from '@/lib/utils';

const LAST_SEATS_THRESHOLD = 0.9;

export function OptionCard({ value, disabled, children }) {
  return (
    <label
      className={cn(
        'flex cursor-pointer items-start gap-3 rounded-xl border border-border bg-card/60 p-3 transition-colors has-[[data-state=checked]]:border-brand has-[[data-state=checked]]:bg-accent-soft',
        disabled && 'cursor-not-allowed opacity-60',
      )}
    >
      <RadioGroupItem value={String(value)} disabled={disabled} className="mt-1" />
      <div className="flex min-w-0 flex-1 flex-col gap-1">{children}</div>
    </label>
  );
}

export function OfferRadioGroup({ label, value, onValueChange, children }) {
  return (
    <RadioGroup aria-label={label} value={value == null ? '' : String(value)} onValueChange={onValueChange} className="gap-2.5">
      {children}
    </RadioGroup>
  );
}

export function SectionOccupancyBar({ section }) {
  if (section.capacity == null) {
    return <span className="text-xs text-ink-faint">Sin límite de cupo</span>;
  }
  const ratio = section.capacity > 0 ? section.enrolled / section.capacity : 1;
  const tone = section.full ? 'danger' : ratio >= LAST_SEATS_THRESHOLD ? 'warning' : 'accent';
  return (
    <ProgressBar
      value={Math.min(ratio, 1) * 100}
      tone={tone}
      label={`${section.enrolled}/${section.capacity} cupos ocupados`}
      showValue={false}
    />
  );
}

function sectionTag(section) {
  if (section.full) return <StatusPill tone="danger">Lleno</StatusPill>;
  if (section.capacity != null && section.enrolled / section.capacity >= LAST_SEATS_THRESHOLD) {
    return <StatusPill tone="warning">Últimos cupos</StatusPill>;
  }
  return null;
}

export function SectionOption({ section }) {
  return (
    <OptionCard value={section.id} disabled={section.full}>
      <div className="flex items-center justify-between gap-2">
        <span className="font-medium text-ink">
          Comisión {section.code} · {section.name}
        </span>
        {sectionTag(section)}
      </div>
      {section.schedule && <span className="text-xs text-ink-faint">{section.schedule}</span>}
      <SectionOccupancyBar section={section} />
    </OptionCard>
  );
}

export function ModuleSectionPicker({ module, value, onChange }) {
  const { sections } = module;

  if (sections.length === 0) {
    return (
      <div className="flex flex-col gap-1">
        <span className="font-medium text-ink">{module.name}</span>
        <span className="text-xs text-ink-faint">Este módulo no tiene comisiones activas en el período del curso.</span>
      </div>
    );
  }

  if (sections.length === 1) {
    const [only] = sections;
    return (
      <div className="flex flex-col gap-1">
        <span className="font-medium text-ink">{module.name}</span>
        <span className="text-sm text-ink-soft">
          Comisión {only.code} · {only.name}
        </span>
        {only.full && (
          <span className="text-xs text-warning">La comisión está llena: el alumno queda en lista de espera.</span>
        )}
      </div>
    );
  }

  const allFull = sections.every((section) => section.full);
  return (
    <div className="flex flex-col gap-2">
      <span className="font-medium text-ink">{module.name}</span>
      <OfferRadioGroup label={`Comisión de ${module.name}`} value={value} onValueChange={(next) => onChange(module.id, Number(next))}>
        {sections.map((section) => (
          <SectionOption key={section.id} section={section} />
        ))}
      </OfferRadioGroup>
      {allFull && <span className="text-xs text-danger">Todas las comisiones de este módulo están llenas.</span>}
    </div>
  );
}
