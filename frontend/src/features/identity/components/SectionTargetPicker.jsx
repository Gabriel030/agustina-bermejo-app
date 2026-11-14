import { useState } from 'react';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Button } from '@/components/ui/button';
import { StatusPill } from '@/components/StatusPill';
import { cn } from '@/lib/utils';
import { describeBlockReason, describeSeats, describeSlot, splitCandidates } from '@/features/identity/lib/enrollmentChanges';

function CandidateBody({ candidate }) {
  return (
    <span className="flex min-w-0 flex-1 flex-col gap-0.5">
      <span className="flex flex-wrap items-center gap-2">
        <b className="text-size-14 text-ink">{`Com. ${candidate.name}`}</b>
        <StatusPill tone={candidate.selectable ? (candidate.availableSeats === 0 ? 'danger' : 'success') : 'neutral'}>
          {candidate.selectable ? describeSeats(candidate) : describeBlockReason(candidate)}
        </StatusPill>
      </span>
      {(candidate.slots ?? []).map((slot) => (
        <span key={`${slot.dayOfWeek}-${slot.startTime}`} className="text-size-12 text-ink-faint">
          {describeSlot(slot)}
        </span>
      ))}
    </span>
  );
}

export function SectionTargetPicker({ candidates, value, onChange }) {
  const [showUnavailable, setShowUnavailable] = useState(false);
  const { available, unavailable } = splitCandidates(candidates);

  return (
    <div className="flex flex-col gap-3">
      <RadioGroup value={value == null ? '' : String(value)} onValueChange={(next) => onChange(Number(next))} aria-label="Comisión destino">
        {available.map((candidate) => {
          const id = `section-target-${candidate.sectionId}`;
          return (
            <label
              key={candidate.sectionId}
              htmlFor={id}
              className={cn(
                'flex cursor-pointer items-start gap-3 rounded-2xl border border-border p-3 transition-colors hover:bg-neutral-bg/60',
                value === candidate.sectionId && 'border-primary bg-accent-soft',
              )}
            >
              <RadioGroupItem id={id} value={String(candidate.sectionId)} className="mt-1" />
              <CandidateBody candidate={candidate} />
            </label>
          );
        })}
      </RadioGroup>
      {unavailable.length > 0 && (
        <div className="flex flex-col gap-2">
          <Button
            type="button"
            variant="ghost"
            className="w-fit"
            aria-expanded={showUnavailable}
            onClick={() => setShowUnavailable((current) => !current)}
          >
            {showUnavailable
              ? 'Ocultar comisiones no disponibles'
              : `Ver ${unavailable.length} ${unavailable.length === 1 ? 'comisión no disponible' : 'comisiones no disponibles'}`}
          </Button>
          {showUnavailable && (
            <ul className="flex flex-col gap-2">
              {unavailable.map((candidate) => (
                <li key={candidate.sectionId} className="flex items-start gap-3 rounded-2xl border border-border p-3 opacity-80">
                  <CandidateBody candidate={candidate} />
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
