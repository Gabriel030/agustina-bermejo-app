import { GlassCard } from '@/components/GlassCard';
import { ChoiceChip } from '@/components/ChoiceChip';
import { StatusPill } from '@/components/StatusPill';
import { usePrograms, useSubjects } from '@/features/academic-structure';
import { OptionCombobox } from './OptionCombobox';
import { AudienceCounter } from './AudienceCounter';
import { ENROLLMENT_STATUS_LABELS, EXCLUSION_REASON_LABELS, STAFF_ROLE_LABELS } from '../lib/broadcastWizard';
import { useState } from 'react';

function toggled(values, key) {
  return values.includes(key) ? values.filter((value) => value !== key) : [...values, key];
}

export function AudienceStep({ state, onCriteria, catalogKeys, preview, previewState }) {
  const { criteria } = state;
  const [reasonFilter, setReasonFilter] = useState(null);
  const { data: programs = [] } = usePrograms('ACTIVE');
  const { data: subjects = [] } = useSubjects('ACTIVE');
  const sample = (preview?.sample ?? []).filter((row) => !reasonFilter || row.exclusionReason === reasonFilter);

  return (
    <div className="grid gap-4 md:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
      <GlassCard className="flex flex-col gap-5 p-5">
        <div className="flex flex-wrap gap-3">
          <OptionCombobox
            label="Carrera"
            options={programs.map((program) => ({ value: program.id, label: program.name }))}
            value={criteria.programIds[0] ?? null}
            onChange={(value) => onCriteria({ programIds: value == null ? [] : [value] })}
          />
          <OptionCombobox
            label="Materia"
            options={subjects.map((subject) => ({ value: subject.id, label: subject.name }))}
            value={criteria.subjectId}
            onChange={(value) => onCriteria({ subjectId: value })}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <span className="text-size-13 font-semibold text-ink">Estado de cursada</span>
          <div role="group" aria-label="Estado de cursada" className="flex flex-wrap gap-2">
            {Object.entries(ENROLLMENT_STATUS_LABELS).map(([key, label]) => (
              <ChoiceChip key={key} selected={criteria.statuses.includes(key)} onClick={() => onCriteria({ statuses: toggled(criteria.statuses, key) })}>
                {label}
              </ChoiceChip>
            ))}
          </div>
        </div>
        {catalogKeys.includes('STAFF_ROLE') && (
          <div className="flex flex-col gap-1.5">
            <span className="text-size-13 font-semibold text-ink">Personal</span>
            <div role="group" aria-label="Personal" className="flex flex-wrap gap-2">
              {Object.entries(STAFF_ROLE_LABELS).map(([key, label]) => (
                <ChoiceChip key={key} selected={criteria.staffRoles.includes(key)} onClick={() => onCriteria({ staffRoles: toggled(criteria.staffRoles, key) })}>
                  {label}
                </ChoiceChip>
              ))}
            </div>
          </div>
        )}
        {catalogKeys.includes('OVERDUE_DEBT') && (
          <label className="flex items-center gap-2 text-size-13 font-semibold text-ink">
            <input type="checkbox" checked={criteria.overdueDebt} onChange={(event) => onCriteria({ overdueDebt: event.target.checked })} />
            Con deuda vencida
          </label>
        )}
        <p className="text-size-12 text-ink-faint">
          Podés marcar varias en un mismo grupo (alcanza con una). Si marcás en los dos grupos, llega a quienes cumplan ambos.
        </p>
      </GlassCard>
      <div className="flex flex-col gap-3">
        <AudienceCounter {...previewState} idle={previewState.idle} preview={preview} reasonFilter={reasonFilter} onReasonFilter={setReasonFilter} />
        {sample.length > 0 && (
          <GlassCard className="flex flex-col gap-2 p-4">
            {sample.map((row) => (
              <div key={row.personId} className="flex flex-wrap items-center justify-between gap-x-2 gap-y-0.5 text-size-13">
                <span className="min-w-0 font-semibold break-words text-ink">{row.fullName}</span>
                <span className="min-w-0 break-all text-ink-faint">{row.addressMasked}</span>
                {row.exclusionReason && <StatusPill tone="warning">{EXCLUSION_REASON_LABELS[row.exclusionReason]}</StatusPill>}
              </div>
            ))}
          </GlassCard>
        )}
      </div>
    </div>
  );
}
