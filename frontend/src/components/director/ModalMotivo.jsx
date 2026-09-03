import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

export function ModalMotivo({ open, onClose, onConfirmar, confirmando, requerido }) {
  const [motivo, setMotivo] = useState('');
  const [error, setError] = useState('');

  function confirmar() {
    if (requerido && !motivo.trim()) {
      setError('El motivo del rechazo es obligatorio.');
      return;
    }
    onConfirmar(motivo.trim());
    setMotivo('');
    setError('');
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Rechazar trámite</DialogTitle>
          <DialogDescription>
            {requerido ? 'Contale al solicitante por qué se rechaza — es obligatorio.' : 'Podés agregar una nota (opcional).'}
          </DialogDescription>
        </DialogHeader>
        <Textarea value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Motivo del rechazo" rows={3} />
        {error && <div className="text-[12px] font-semibold text-danger">{error}</div>}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancelar</Button>
          <Button variant="gradient" disabled={confirmando} onClick={confirmar}>Rechazar</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
