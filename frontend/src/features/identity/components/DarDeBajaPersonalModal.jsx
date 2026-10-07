import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

export function DarDeBajaPersonalModal({ open, onClose, nombre, onConfirmar, isPending }) {
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Dar de baja a {nombre}</DialogTitle>
          <DialogDescription>
            El miembro del personal pasará a estado inactivo. Podés reactivarlo más adelante.
          </DialogDescription>
        </DialogHeader>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            Cancelar
          </Button>
          <Button variant="destructive" onClick={onConfirmar} disabled={isPending}>
            {isPending ? 'Dando de baja...' : 'Confirmar baja'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
