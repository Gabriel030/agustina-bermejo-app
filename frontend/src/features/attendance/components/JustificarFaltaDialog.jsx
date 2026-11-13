import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { ChangesTable } from '@/components/ChangesTable';
import { StatusPill } from '@/components/StatusPill';
import { describeError } from '@/api/describeError';
import { useJustifyAbsence } from '@/features/attendance/hooks/useStaffAbsences';
import {
  MAX_JUSTIFICATION_CHARS,
  buildJustificationImpact,
  canSubmitJustification,
  countRemainingChars,
  formatIsoDate,
} from '@/features/attendance/lib/absenceRows';

export function JustificarFaltaDialog({ sectionEnrollmentId, absence, studentName, subjectLabel, computedAbsences, authorName, onClose }) {
  const [reason, setReason] = useState('');
  const [exceptionReason, setExceptionReason] = useState('');
  const [submitError, setSubmitError] = useState(null);
  const justify = useJustifyAbsence(sectionEnrollmentId);
  const expired = absence.justifiable !== true;
  const sentException = expired ? exceptionReason : '';
  const remaining = countRemainingChars(reason, sentException);
  const canSubmit = canSubmitJustification({ reason, exceptionReason, expired }) && !justify.isPending;

  async function submit() {
    setSubmitError(null);
    try {
      await justify.mutateAsync({
        attendanceId: absence.id,
        reason: reason.trim(),
        exceptionReason: expired ? exceptionReason.trim() : undefined,
      });
      toast.success(`Inasistencia del ${formatIsoDate(absence.classDate)} justificada`);
      onClose();
    } catch (error) {
      if (error?.status === 409) {
        justify.refresh();
        toast.warning('Esta inasistencia ya cambió de estado. Actualizamos la lista.');
        onClose();
        return;
      }
      setSubmitError(describeError(error, { fallback: 'No pudimos justificar la inasistencia. Revisá tu conexión y probá de nuevo.' }).message);
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Justificar inasistencia</DialogTitle>
          <DialogDescription>
            {studentName} · {subjectLabel} · clase del {formatIsoDate(absence.classDate)}
          </DialogDescription>
        </DialogHeader>
        {expired ? (
          <div className="rounded-xl bg-warning-bg px-4 py-2 text-size-13 text-warning-ink">
            <b>Plazo vencido el {formatIsoDate(absence.justificationDeadline)}.</b> Podés justificarla como excepción: explicá por qué.
          </div>
        ) : (
          <div className="flex items-center gap-2 text-size-13 text-ink-soft">
            <StatusPill tone="success">Dentro de plazo</StatusPill>
            <span>hasta el {formatIsoDate(absence.justificationDeadline)}</span>
          </div>
        )}
        {absence.justificationRejectionReason && (
          <div className="rounded-xl bg-danger-bg px-4 py-2 text-size-13 text-danger">
            Justificación rechazada antes: {absence.justificationRejectionReason}
          </div>
        )}
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="justify-reason">Motivo</Label>
          <Textarea id="justify-reason" value={reason} onChange={(event) => setReason(event.target.value)} />
        </div>
        {expired && (
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="justify-exception">Motivo de la excepción</Label>
            <Textarea id="justify-exception" value={exceptionReason} onChange={(event) => setExceptionReason(event.target.value)} />
          </div>
        )}
        <div className={`flex justify-between text-xs ${remaining < 0 ? 'text-danger' : 'text-ink-faint'}`}>
          <span aria-live="polite">
            {reason.length + sentException.length} de {MAX_JUSTIFICATION_CHARS} caracteres
          </span>
          <span>{remaining < 0 ? `Te pasaste ${-remaining}` : `Quedan ${remaining}`}</span>
        </div>
        <p className="text-xs text-ink-faint">Esta justificación queda sin adjunto: el comprobante lo carga el alumno o el docente.</p>
        <ChangesTable rows={buildJustificationImpact(computedAbsences)} />
        <div className="rounded-xl bg-warning-bg px-4 py-2 text-size-13 text-warning-ink">No se puede deshacer desde esta pantalla.</div>
        {authorName && <p className="text-xs text-ink-faint">Queda registrado a nombre de {authorName}.</p>}
        {submitError && (
          <div role="alert" className="rounded-xl bg-danger-bg px-4 py-2 text-size-13 font-semibold text-danger">
            {submitError}
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button variant="gradient" onClick={submit} disabled={!canSubmit}>
            {justify.isPending ? 'Justificando…' : 'Justificar inasistencia'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
