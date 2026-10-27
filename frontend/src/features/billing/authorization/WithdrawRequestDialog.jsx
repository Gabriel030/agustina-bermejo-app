import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { useWithdrawAuthorizationRequest } from '../hooks/useAuthorizationRequests';
import { requestActionErrorMessage } from './authorizationRequests';

export function WithdrawRequestDialog({ request, onClose, onWithdrawn }) {
  const mutation = useWithdrawAuthorizationRequest();

  async function handleConfirm() {
    try {
      await mutation.mutateAsync({ id: request.id });
      toast.success('Pedido retirado.');
      onWithdrawn?.();
      onClose();
    } catch (error) {
      toast.error(requestActionErrorMessage(error, { action: 'withdraw' }));
      onClose();
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Retirar el pedido</DialogTitle>
          <DialogDescription>
            {request.operationLabel}
            {request.studentName ? ` · ${request.studentName}` : ''}. La dirección ya no lo va a ver para decidir y no se ejecuta.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose}>Volver</Button>
          <Button type="button" variant="destructive" loading={mutation.isPending} onClick={handleConfirm}>
            Retirar pedido
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
