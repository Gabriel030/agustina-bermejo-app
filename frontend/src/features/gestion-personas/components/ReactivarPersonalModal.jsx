import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

export function ReactivarPersonalModal({ open, onClose, nombre, onConfirmar, isPending }) {
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Reactivar a {nombre}</DialogTitle>
          <DialogDescription>
            El miembro del personal volverá a estado activo y podrá acceder al sistema.
          </DialogDescription>
        </DialogHeader>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            Cancelar
          </Button>
          <Button variant="gradient" onClick={onConfirmar} disabled={isPending}>
            {isPending ? 'Reactivando...' : 'Confirmar reactivación'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
