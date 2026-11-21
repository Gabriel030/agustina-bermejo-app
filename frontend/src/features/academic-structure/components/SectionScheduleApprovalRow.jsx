import { Button } from '@/components/ui/button';
import { StatusPill } from '@/components/StatusPill';
import { ageLabel } from '@/lib/aprobaciones';
import { formatSlot } from '@/features/academic-structure/lib/sectionScheduleRequest';
import { sectionScheduleTitle } from '@/features/academic-structure/lib/sectionScheduleApprovals';

function SlotsLine({ label, slots }) {
  return (
    <div className="flex flex-wrap items-baseline gap-x-2 text-size-13 text-ink">
      <span className="w-32 shrink-0 text-size-12 font-semibold text-ink-faint">{label}</span>
      <span className="tabular-nums">{slots.map((slot) => formatSlot(slot)).join(' · ')}</span>
    </div>
  );
}

export function SectionScheduleApprovalRow({ request, onApprove, onReject, disabled }) {
  const title = sectionScheduleTitle(request);
  return (
    <li className="flex flex-col gap-3 rounded-2xl border border-surface-border p-4 sm:flex-row sm:items-start sm:justify-between">
      <div className="flex min-w-0 flex-col gap-1.5">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-heading text-size-14 font-semibold text-ink">{title}</span>
          {request.replacesSchedule && <StatusPill tone="neutral">Reemplaza el horario</StatusPill>}
        </div>
        <div className="min-w-0 break-words text-size-12 text-ink-faint">
          {[request.offeringName, request.requestedByName && `pedido por ${request.requestedByName}`, ageLabel(request.requestedAt)]
            .filter(Boolean)
            .join(' · ')}
        </div>
        <div className="mt-1 flex flex-col gap-1">
          <SlotsLine label="Horario actual" slots={request.currentSlots} />
          <SlotsLine label="Horario propuesto" slots={request.proposedSlots} />
        </div>
      </div>
      <div className="flex shrink-0 gap-2">
        <Button variant="outline" disabled={disabled} aria-label={`Rechazar el horario de ${title}`} onClick={() => onReject(request)}>
          Rechazar
        </Button>
        <Button variant="primary" disabled={disabled} aria-label={`Aprobar el horario de ${title}`} onClick={() => onApprove(request)}>
          Aprobar
        </Button>
      </div>
    </li>
  );
}
