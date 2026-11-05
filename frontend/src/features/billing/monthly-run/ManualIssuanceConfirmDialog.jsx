import { useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { formatMoney } from '@/lib/billing';
import { useIssueMonthlyRunManually } from '../hooks/useMonthlyRuns';
import { dueDateText, formatPeriod, manualIssuanceErrorMessage } from './monthlyRunLabels';

export function ManualIssuanceConfirmDialog({ period, simulationRunId, summary, onClose, onIssued }) {
  const issue = useIssueMonthlyRunManually();
  const [errorMessage, setErrorMessage] = useState(null);

  async function handleConfirm() {
    setErrorMessage(null);
    try {
      await issue.mutateAsync({ period, simulationRunId });
      toast.success(`Emitimos las cuotas de ${formatPeriod(period)}.`);
      onIssued();
    } catch (error) {
      const message = manualIssuanceErrorMessage(error);
      setErrorMessage(message);
      toast.error(message);
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && !issue.isPending && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-md">
        <DialogHeader>
          <DialogTitle>¿Emitir las cuotas de {formatPeriod(period)}?</DialogTitle>
          <DialogDescription>Se generan hoy con el precio vigente al inicio del período.</DialogDescription>
        </DialogHeader>
        <dl className="grid grid-cols-2 gap-3 rounded-xl bg-neutral-bg px-4 py-3 text-sm">
          <div className="flex flex-col gap-0.5">
            <dt className="text-xs text-ink-faint">Cuotas</dt>
            <dd className="text-lg font-semibold tabular-nums text-ink">{summary.installmentCount}</dd>
          </div>
          <div className="flex flex-col gap-0.5">
            <dt className="text-xs text-ink-faint">Total</dt>
            <dd className="text-lg font-semibold tabular-nums text-ink">{formatMoney(summary.total)}</dd>
          </div>
          <div className="col-span-2 flex flex-col gap-0.5">
            <dt className="text-xs text-ink-faint">Vencimiento</dt>
            <dd className="text-ink">{dueDateText(summary)}</dd>
          </div>
        </dl>
        <div className="flex items-start gap-3 rounded-2xl border border-danger/30 bg-danger-bg px-4 py-3 text-sm text-ink">
          <AlertTriangle className="mt-0.5 size-4 shrink-0 text-danger" aria-hidden="true" />
          <span>Esta acción no se puede deshacer.</span>
        </div>
        {errorMessage && (
          <div role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger">
            {errorMessage}
          </div>
        )}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose} disabled={issue.isPending}>
            Cancelar
          </Button>
          <Button type="button" variant="destructive" loading={issue.isPending} onClick={handleConfirm}>
            Emitir
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
