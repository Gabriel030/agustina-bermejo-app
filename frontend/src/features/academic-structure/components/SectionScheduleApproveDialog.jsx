import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Skeleton } from '@/components/Skeleton';
import { buildScheduleRows } from '@/features/academic-structure/lib/sectionScheduleRequest';
import { scheduleConflictText, sectionScheduleTitle } from '@/features/academic-structure/lib/sectionScheduleApprovals';

function ConflictsNotice({ preview }) {
  if (preview.isPending) {
    return <Skeleton className="h-10" />;
  }
  if (preview.isError) {
    return (
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p role="alert" className="text-size-12 font-semibold text-danger">No pudimos revisar los conflictos del nuevo horario.</p>
        <Button variant="outline" onClick={() => preview.refetch()}>Reintentar</Button>
      </div>
    );
  }
  const conflicts = preview.data?.conflicts ?? [];
  if (conflicts.length === 0) {
    return <p className="text-size-12 text-ink-faint">Sin conflictos con el nuevo horario.</p>;
  }
  return (
    <div role="alert" className="flex flex-col gap-1 rounded-xl border border-danger/40 p-3">
      <span className="text-size-12 font-semibold text-danger">Conflictos con el nuevo horario</span>
      <ul className="flex flex-col gap-0.5 text-size-13 text-ink">
        {conflicts.map((conflict, index) => (
          <li key={`${conflict.type}-${conflict.slotIndex}-${index}`}>{scheduleConflictText(conflict)}</li>
        ))}
      </ul>
    </div>
  );
}

export function SectionScheduleApproveDialog({ request, open, onClose, onConfirm, confirming, errorMessage, preview }) {
  if (!request) {
    return null;
  }
  const rows = buildScheduleRows(request.currentSlots, request.proposedSlots);
  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Aprobar el nuevo horario</DialogTitle>
          <DialogDescription>{sectionScheduleTitle(request)}</DialogDescription>
        </DialogHeader>
        <div className="grid grid-cols-[auto_minmax(0,1fr)_minmax(0,1fr)] gap-x-3 gap-y-2 text-size-13 text-ink">
          <span className="text-size-12 font-semibold text-ink-faint">Comparación</span>
          <span className="text-size-12 font-semibold text-ink-faint">Hoy</span>
          <span className="text-size-12 font-semibold text-ink-faint">Nuevo</span>
          {rows.map((row, index) => (
            <div key={index} className="contents">
              <span className="min-w-0 break-words text-ink-faint">{row.unchanged ? 'Sin cambio' : `Franja ${index + 1}`}</span>
              <span className="min-w-0 break-words tabular-nums">{row.today}</span>
              <span className={`min-w-0 break-words tabular-nums ${row.unchanged ? 'text-ink-faint' : 'font-semibold text-ink'}`}>
                {row.removed ? 'Se quita' : row.next ?? row.today}
              </span>
            </div>
          ))}
        </div>
        <ConflictsNotice preview={preview} />
        {errorMessage && <p role="alert" className="text-size-12 font-semibold text-danger">{errorMessage}</p>}
        <DialogFooter>
          <Button variant="outline" disabled={confirming} onClick={onClose}>Cancelar</Button>
          <Button variant="primary" loading={confirming} disabled={preview.isPending || preview.isError} onClick={onConfirm}>Confirmar aprobación</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
