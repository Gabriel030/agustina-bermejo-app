import { useState } from 'react';
import { toast } from 'sonner';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { useFailRefund } from '../hooks/useRefunds';
import { RefundPartSummary } from './RefundPartSummary';
import { STALE_REFUND_CODES, refundErrorMessage } from './refundRequests';

const REASON_MAX_LENGTH = 500;

export function FailRefundDialog({ refund, onClose }) {
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState('');
  const [formError, setFormError] = useState('');
  const mutation = useFailRefund();

  async function handleSubmit(event) {
    event.preventDefault();
    setFormError('');
    if (!reason.trim()) {
      setReasonError('Indicá por qué no se pudo transferir.');
      return;
    }
    setReasonError('');
    try {
      await mutation.mutateAsync({ refundId: refund.id, reason: reason.trim() });
      toast.success('Quedó registrado que no se pudo transferir.');
      onClose();
    } catch (error) {
      if (STALE_REFUND_CODES.includes(error?.code)) {
        toast.error(refundErrorMessage(error));
        onClose();
        return;
      }
      if (error?.code === 'REFUND_FAILURE_REASON_INVALID') {
        setReasonError(refundErrorMessage(error));
        return;
      }
      setFormError(refundErrorMessage(error));
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open && !mutation.isPending) onClose(); }}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-md">
        <DialogHeader>
          <DialogTitle>No se pudo transferir</DialogTitle>
          <DialogDescription>No se descuenta nada del saldo a favor. Queda registrado el motivo.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <RefundPartSummary refund={refund} />

          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="refund-failure-reason">Motivo</Label>
              <span className="text-xs text-ink-faint">{reason.length}/{REASON_MAX_LENGTH}</span>
            </div>
            <Textarea
              id="refund-failure-reason"
              rows={3}
              maxLength={REASON_MAX_LENGTH}
              value={reason}
              placeholder="Ej.: El banco rechazó la transferencia."
              onChange={(event) => setReason(event.target.value)}
              aria-invalid={!!reasonError}
            />
            {reasonError && <p className="text-xs text-danger">{reasonError}</p>}
          </div>

          {formError && <p role="alert" className="text-sm text-danger">{formError}</p>}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose} disabled={mutation.isPending}>
              Cancelar
            </Button>
            <Button type="submit" variant="destructive" loading={mutation.isPending}>
              Registrar fallo
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
