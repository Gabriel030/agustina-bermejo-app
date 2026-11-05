import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

const MAX_NOTE_LENGTH = 500;

export function ModalAprobacion({ open, onClose, onConfirmar, confirmando, titulo, resumen, withNote = false }) {
  const [nota, setNota] = useState('');

  async function confirmar() {
    const saved = await onConfirmar(withNote ? nota.trim() : undefined);
    if (saved !== false) {
      setNota('');
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{titulo}</DialogTitle>
          <DialogDescription>Revisá el resumen antes de confirmar. La decisión no se puede deshacer.</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-1 rounded-xl bg-neutral-bg/50 p-3 text-[13.5px] text-ink">{resumen}</div>
        {withNote && (
          <Textarea
            value={nota}
            onChange={(e) => setNota(e.target.value)}
            placeholder="Nota para el solicitante (opcional)"
            rows={3}
            maxLength={MAX_NOTE_LENGTH}
          />
        )}
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={confirmando}>Cancelar</Button>
          <Button variant="gradient" loading={confirmando} onClick={confirmar}>Aprobar</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
