import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

const DEFAULT_MAX_REASON_LENGTH = 500;

export function ModalMotivo({
  open,
  onClose,
  onConfirmar,
  confirmando,
  requerido,
  titulo = 'Rechazar trámite',
  descripcion,
  placeholder = 'Motivo del rechazo',
  confirmarLabel = 'Rechazar',
  confirmarVariant = 'gradient',
  maxLength = DEFAULT_MAX_REASON_LENGTH,
  minLength = 0,
  mensajeRequerido = 'El motivo del rechazo es obligatorio.',
}) {
  const [motivo, setMotivo] = useState('');
  const [error, setError] = useState('');

  async function confirmar() {
    if (requerido && !motivo.trim()) {
      setError(mensajeRequerido);
      return;
    }
    if (minLength > 0 && motivo.trim().length < minLength) {
      setError(`El motivo debe tener al menos ${minLength} caracteres.`);
      return;
    }
    const saved = await onConfirmar(motivo.trim());
    if (saved !== false) {
      setMotivo('');
      setError('');
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{titulo}</DialogTitle>
          <DialogDescription>
            {descripcion ?? (requerido ? 'Contale al solicitante por qué se rechaza — es obligatorio.' : 'Podés agregar una nota (opcional).')}
          </DialogDescription>
        </DialogHeader>
        <Textarea
          value={motivo}
          onChange={(e) => setMotivo(e.target.value)}
          placeholder={placeholder}
          rows={3}
          maxLength={maxLength}
        />
        {error && <div className="text-[12px] font-semibold text-danger">{error}</div>}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancelar</Button>
          <Button variant={confirmarVariant} loading={confirmando} onClick={confirmar}>{confirmarLabel}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
