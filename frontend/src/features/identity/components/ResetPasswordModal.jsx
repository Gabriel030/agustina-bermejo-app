import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { describeError } from '@/api/describeError';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

export function ResetPasswordModal({ open, onClose, email, onConfirmar, isPending }) {
  const [enviado, setEnviado] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  function handleClose() {
    setEnviado(false);
    setErrorMessage(null);
    onClose();
  }

  async function handleConfirmar() {
    setErrorMessage(null);
    try {
      await onConfirmar();
    } catch (error) {
      setErrorMessage(describeError(error).message);
      return;
    }
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

        {errorMessage && (
          <div role="alert" className="rounded-xl bg-danger-bg px-4 py-2 text-[13px] font-semibold text-danger">
            {errorMessage}
          </div>
        )}

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
