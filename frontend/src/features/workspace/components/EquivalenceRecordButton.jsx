import { useId } from 'react';
import { Button } from '@/components/ui/button';

export function EquivalenceRecordButton({ label = 'Cargar equivalencia', actions, subjectId = null, onBeforeOpen, getTrigger, variant = 'primary', className }) {
  const reasonId = useId();
  const denied = !actions.canRecord;
  return (
    <div className="flex flex-col items-start gap-1">
      <Button
        type="button"
        variant={variant}
        className={`min-h-11 ${className ?? ''}`}
        disabled={denied}
        aria-describedby={denied ? reasonId : undefined}
        onClick={(event) => {
          const trigger = getTrigger ? getTrigger() : event.currentTarget;
          onBeforeOpen?.();
          actions.openRecord(subjectId, trigger);
        }}
      >
        {label}
      </Button>
      {denied && (
        <span id={reasonId} className="text-size-12 text-ink-soft">
          {actions.recordDeniedReason}
        </span>
      )}
    </div>
  );
}
