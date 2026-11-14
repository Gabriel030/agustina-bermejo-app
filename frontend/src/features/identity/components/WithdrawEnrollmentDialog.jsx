import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import {
  REASON_MAX_LENGTH,
  REASON_MIN_LENGTH,
  matchesConfirmation,
  reasonError,
  withdrawalDateError,
  withdrawalDateRange,
} from '@/features/identity/lib/enrollmentChanges';

const COPY = {
  'withdraw-subject': {
    title: 'Dar de baja de la materia',
    effects: ['Se libera su lugar en la comisión.', 'Conserva el historial de asistencia y notas.', 'Se avisa al alumno y al docente.'],
    waitlistEffects: ['Sale de la lista de espera: todavía no tenía lugar asignado.', 'Se avisa al alumno y al docente.'],
    confirmLabel: 'Dar de baja de la materia',
  },
  'withdraw-module': {
    title: 'Dar de baja del módulo',
    effects: ['Se libera su lugar en el módulo.', 'Conserva el historial de asistencia y notas.'],
    waitlistEffects: ['Sale de la lista de espera del módulo.'],
    confirmLabel: 'Dar de baja del módulo',
  },
  'withdraw-course': {
    title: 'Dar de baja del curso',
    effects: ['Se da de baja el curso completo, con todos sus módulos.', 'Conserva el historial académico.'],
    confirmLabel: 'Dar de baja del curso',
    confirmWord: 'BAJA',
    dated: true,
  },
  'withdraw-program': {
    title: 'Dar de baja de la carrera',
    effects: ['Se da de baja la carrera y todas sus cursadas en curso.', 'Conserva el historial académico.'],
    confirmLabel: 'Dar de baja de la carrera',
    confirmWord: 'BAJA',
    dated: true,
  },
};

export function WithdrawEnrollmentDialog({ target, onClose, onConfirm, pending, serverError }) {
  const [reason, setReason] = useState('');
  const [reasonShown, setReasonShown] = useState(false);
  const [confirmation, setConfirmation] = useState('');
  const [effectiveDate, setEffectiveDate] = useState('');

  if (!target) return null;
  const copy = COPY[target.kind];
  const effects = target.waitlisted && copy.waitlistEffects ? copy.waitlistEffects : copy.effects;
  const range = withdrawalDateRange(target.enrollmentDate);
  const dateError = copy.dated ? withdrawalDateError(effectiveDate, range) : null;
  const localReasonError = reasonError(reason);
  const confirmationOk = !copy.confirmWord || matchesConfirmation(confirmation, copy.confirmWord);
  const canSubmit = !localReasonError && !dateError && confirmationOk;

  function submit() {
    setReasonShown(true);
    if (!canSubmit) return;
    onConfirm({ reason: reason.trim(), effectiveDate: copy.dated && effectiveDate ? effectiveDate : null });
  }

  return (
    <Dialog open onOpenChange={(open) => !open && !pending && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{copy.title}</DialogTitle>
          <DialogDescription>{target.name}</DialogDescription>
        </DialogHeader>
        <ul className="flex list-disc flex-col gap-1 pl-5 text-size-13 text-ink-soft">
          {effects.map((effect) => (
            <li key={effect}>{effect}</li>
          ))}
        </ul>
        {copy.dated && (
          <div className="flex flex-col gap-1">
            <Label htmlFor="withdraw-date">Fecha de la baja (opcional, por defecto hoy)</Label>
            <Input
              id="withdraw-date"
              type="date"
              min={range.min}
              max={range.max}
              value={effectiveDate}
              onChange={(event) => setEffectiveDate(event.target.value)}
              aria-invalid={!!dateError}
            />
            {dateError && (
              <span role="alert" className="text-size-12 font-semibold text-danger">
                {dateError}
              </span>
            )}
          </div>
        )}
        <div className="flex flex-col gap-1">
          <Label htmlFor="withdraw-reason">Motivo</Label>
          <Textarea
            id="withdraw-reason"
            rows={3}
            maxLength={REASON_MAX_LENGTH}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder="Motivo de la baja"
            aria-invalid={reasonShown && !!localReasonError}
          />
          <div className="text-right text-xs tabular-nums text-ink-faint" aria-live="polite">
            <span className="mr-2">{`mínimo ${REASON_MIN_LENGTH}`}</span>
            <span>
              {reason.length}/{REASON_MAX_LENGTH}
            </span>
          </div>
          {reasonShown && localReasonError && (
            <span role="alert" className="text-size-12 font-semibold text-danger">
              {localReasonError}
            </span>
          )}
        </div>
        {copy.confirmWord && (
          <div className="flex flex-col gap-1">
            <Label htmlFor="withdraw-confirmation">{`Escribí ${copy.confirmWord} para confirmar`}</Label>
            <Input
              id="withdraw-confirmation"
              value={confirmation}
              autoComplete="off"
              onChange={(event) => setConfirmation(event.target.value)}
            />
          </div>
        )}
        {serverError && (
          <div role="alert" className="text-size-12 font-semibold text-danger">
            {serverError}
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" disabled={pending} onClick={onClose}>
            Cancelar
          </Button>
          <Button variant="destructive" loading={pending} disabled={!confirmationOk} onClick={submit}>
            {copy.confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
