import { StatusPill } from '@/components/StatusPill';
import { RejectionReasonNotice } from '@/features/academic-structure/components/RejectionReasonNotice';
import { buildScheduleRows, formatRequestedDate } from '@/features/academic-structure/lib/sectionScheduleRequest';

export function SectionPendingScheduleNotice({ section }) {
  const pending = section.pendingModification;

  if (pending) {
    const rows = buildScheduleRows(section.slots ?? [], pending.slots);
    return (
      <div className="mt-3 flex flex-col gap-2 rounded-2xl bg-warning-bg px-4 py-3">
        <div className="flex flex-wrap items-center gap-2">
          <StatusPill tone="warning">Cambio de horario pendiente de aprobación</StatusPill>
          <span className="text-size-12 text-ink-soft">
            Pedido el {formatRequestedDate(pending.requestedAt)} · espera la decisión del Director
          </span>
        </div>
        <div className="grid grid-cols-2 gap-3 text-size-12 font-semibold uppercase tracking-wide text-ink-faint">
          <span>Hoy</span>
          <span>Nuevo (pedido)</span>
        </div>
        {rows.map((row) => (
          <div key={row.key} className="grid grid-cols-2 gap-3 text-size-13 text-ink">
            <span>{row.today}</span>
            <span className={row.unchanged ? 'text-ink-faint' : 'font-semibold'}>
              {row.removed ? 'Se quita esta franja' : row.unchanged ? 'sin cambios' : row.next}
            </span>
          </div>
        ))}
        <p className="text-size-12 text-ink-faint">
          Mientras tanto sigue vigente el horario actual. Solo puede haber un pedido abierto por comisión.
        </p>
      </div>
    );
  }

  if (section.lastRejectionReason) {
    return (
      <div className="mt-3">
        <RejectionReasonNotice
          title="El Director rechazó el nuevo horario."
          reason={`Motivo: “${section.lastRejectionReason}”`}
          hint="Se mantiene el horario actual. Podés pedir otra modificación."
        />
      </div>
    );
  }

  return null;
}
