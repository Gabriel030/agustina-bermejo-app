import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { describeError } from '@/api/describeError';
import {
  ACTION_COPY,
  excessCharactersOf,
  reasonCounterOf,
  reasonProblemOf,
  reasonLengthOf,
} from '@/features/graduation/certificates/lib/issuedGraduationCertificates';

const STEP_REASON = 'reason';
const STEP_CONFIRM = 'confirm';
const STEP_RESULT = 'result';

export function RevokeReissueDialog({ action, certificate, pending, onClose, onConfirm }) {
  const [step, setStep] = useState(STEP_REASON);
  const [reason, setReason] = useState('');
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);
  const copy = ACTION_COPY[action];

  const problem = reasonProblemOf(reason);
  const excess = excessCharactersOf(reason);
  const showProblem = reason.length > 0 && problem;

  function continueToConfirm() {
    setError(null);
    setStep(STEP_CONFIRM);
  }

  async function confirm() {
    setError(null);
    try {
      const response = await onConfirm(reason.trim());
      setResult(response);
      setStep(STEP_RESULT);
    } catch (failure) {
      setError(describeError(failure).message);
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        {step === STEP_REASON && (
          <>
            <DialogHeader>
              <DialogTitle>{copy.title}</DialogTitle>
              <DialogDescription>{copy.description}</DialogDescription>
            </DialogHeader>
            <div className="flex flex-col gap-2">
              <label htmlFor="certificate-action-reason" className="text-size-13 font-semibold text-ink">
                {copy.fieldLabel}
              </label>
              <Textarea
                id="certificate-action-reason"
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                rows={3}
                aria-invalid={Boolean(showProblem)}
              />
              <div className="flex items-center justify-between gap-3 text-size-12 tabular-nums text-ink-faint" aria-live="polite">
                {showProblem ? (
                  <span role="alert" className="font-semibold text-danger">
                    {excess > 0 ? `Sobran ${excess} caracteres. ${problem}` : problem}
                  </span>
                ) : (
                  <span>Mínimo 10 caracteres</span>
                )}
                <span>{reasonCounterOf(reason)}</span>
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={onClose}>
                Cancelar
              </Button>
              <Button variant="primary" disabled={problem !== null} onClick={continueToConfirm}>
                Continuar
              </Button>
            </DialogFooter>
          </>
        )}

        {step === STEP_CONFIRM && (
          <>
            <DialogHeader>
              <DialogTitle>{copy.confirmTitle}</DialogTitle>
              <DialogDescription>{copy.confirmDescription}</DialogDescription>
            </DialogHeader>
            <div className="flex flex-col gap-1 rounded-control border border-border p-3">
              <span className="text-size-12 font-semibold uppercase text-ink-faint">{copy.fieldLabel}</span>
              <p className="text-size-14 text-ink">{reason.trim()}</p>
              <span className="text-size-12 text-ink-faint">{reasonLengthOf(reason)} caracteres</span>
            </div>
            {error && (
              <p role="alert" className="text-size-14 font-medium text-danger">
                {error}
              </p>
            )}
            <DialogFooter>
              <Button variant="outline" disabled={pending} onClick={() => setStep(STEP_REASON)}>
                Volver
              </Button>
              <Button variant={copy.confirmVariant} loading={pending} onClick={confirm}>
                {copy.confirmLabel}
              </Button>
            </DialogFooter>
          </>
        )}

        {step === STEP_RESULT && result && (
          <>
            <DialogHeader>
              <DialogTitle>{copy.resultTitle}</DialogTitle>
              <DialogDescription>
                {action === 'revoke'
                  ? `El certificado de ${certificate.holderFullName} quedó revocado.`
                  : `Nuevo certificado de ${certificate.holderFullName}, código ${result.code}.`}
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button variant="primary" onClick={onClose}>
                Cerrar
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
