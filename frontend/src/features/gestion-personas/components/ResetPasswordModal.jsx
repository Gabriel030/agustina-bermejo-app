import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

export function ResetPasswordModal({ open, onClose, email, onConfirmar, isPending }) {
  const [enviado, setEnviado] = useState(false);

  function handleClose() {
    setEnviado(false);
    onClose();
  }

  async function handleConfirmar() {
    await onConfirmar();
    setEnviado(true);
    setTimeout(handleClose, 2000);
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && handleClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Resetear contraseña</DialogTitle>
          <DialogDescription>
            {enviado
              ? `Email enviado a ${email}.`
              : `¿Enviar el link de recuperación de contraseña a ${email}?`}
          </DialogDescription>
        </DialogHeader>

        {!enviado && (
          <DialogFooter>
            <Button variant="outline" onClick={handleClose} disabled={isPending}>
              Cancelar
            </Button>
            <Button onClick={handleConfirmar} disabled={isPending}>
              {isPending ? 'Enviando...' : 'Enviar link'}
            </Button>
          </DialogFooter>
        )}
      </DialogContent>
    </Dialog>
  );
}
