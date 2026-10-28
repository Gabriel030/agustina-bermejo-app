import { useState } from 'react';
import { toast } from 'sonner';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { todayIsoDate } from '@/lib/billing';
import { useCompleteRefund } from '../hooks/useRefunds';
import { RefundPartSummary } from './RefundPartSummary';
import { STALE_REFUND_CODES, refundErrorMessage, refundMethodLabel } from './refundRequests';

const REFERENCE_MAX_LENGTH = 120;

export function CompleteRefundDialog({ refund, onClose }) {
  const today = todayIsoDate();
  const [completedOn, setCompletedOn] = useState(today);
  const [reference, setReference] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const mutation = useCompleteRefund();

  function validate() {
    const errors = {};
    if (!completedOn) errors.completedOn = 'Indicá la fecha de la transferencia.';
    else if (completedOn > today) errors.completedOn = 'La fecha no puede ser posterior a hoy.';
    else if (refund.requestedOn && completedOn < refund.requestedOn) errors.completedOn = 'La fecha no puede ser anterior al pedido.';
    if (!reference.trim()) errors.reference = 'Indicá la referencia de la operación.';
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setFormError('');
    if (!validate()) return;
    try {
      await mutation.mutateAsync({
        refundId: refund.id,
        payload: { completedOn, reference: reference.trim(), methodType: refund.methodType },
      });
      toast.success('Reembolso marcado como hecho.');
      onClose();
    } catch (error) {
      if (STALE_REFUND_CODES.includes(error?.code)) {
        toast.error(refundErrorMessage(error));
        onClose();
        return;
      }
      setFormError(refundErrorMessage(error));
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open && !mutation.isPending) onClose(); }}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Marcar como hecho</DialogTitle>
          <DialogDescription>Registrá los datos de la transferencia que ya hiciste.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <RefundPartSummary refund={refund} />

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="refund-completed-on">Fecha</Label>
            <Input
              id="refund-completed-on"
              type="date"
              value={completedOn}
              max={today}
              min={refund.requestedOn ?? undefined}
              onChange={(event) => setCompletedOn(event.target.value)}
              aria-invalid={!!fieldErrors.completedOn}
            />
            {fieldErrors.completedOn && <p className="text-xs text-danger">{fieldErrors.completedOn}</p>}
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="refund-method">Método</Label>
            <Input id="refund-method" value={refundMethodLabel(refund.methodType)} readOnly className="cursor-default" />
            <p className="text-xs text-ink-faint">Es el mismo método del pago original.</p>
          </div>

          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="refund-reference">Referencia</Label>
              <span className="text-xs text-ink-faint">{reference.length}/{REFERENCE_MAX_LENGTH}</span>
            </div>
            <Input
              id="refund-reference"
              maxLength={REFERENCE_MAX_LENGTH}
              value={reference}
              placeholder="Ej.: Nº de operación de la transferencia"
              onChange={(event) => setReference(event.target.value)}
              aria-invalid={!!fieldErrors.reference}
            />
            {fieldErrors.reference && <p className="text-xs text-danger">{fieldErrors.reference}</p>}
          </div>

          <p className="text-xs text-ink-faint">Esto descuenta el monto del saldo a favor y no se puede deshacer.</p>
          {formError && <p role="alert" className="text-sm text-danger">{formError}</p>}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose} disabled={mutation.isPending}>
              Cancelar
            </Button>
            <Button type="submit" variant="gradient" loading={mutation.isPending}>
              Marcar como hecho
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
