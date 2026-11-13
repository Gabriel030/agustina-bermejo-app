import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

const MOTIVOS = {
  ABANDONO: 'Abandono',
  CAMBIO_CARRERA: 'Cambio de carrera',
  OTRO: 'Otro',
};

export function DarDeBajaModal({ open, onClose, onConfirmar, isPending, error }) {
  const [reason, setReason] = useState('');
  const [detail, setDetail] = useState('');

  function handleClose() {
    setReason('');
    setDetail('');
    onClose();
  }

  function handleConfirmar() {
    if (!reason) return;
    onConfirmar({ reason, detail: detail.trim() || undefined });
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && handleClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Dar de baja al alumno</DialogTitle>
          <DialogDescription>
            El alumno será dado de baja del sistema. Podés reactivarlo después desde su legajo.
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-3">
          <div className="flex flex-col gap-1.5">
            <label className="text-size-13 font-medium text-ink">Motivo *</label>
            <Select value={reason} onValueChange={setReason}>
              <SelectTrigger>
                <SelectValue placeholder="Seleccioná un motivo" />
              </SelectTrigger>
              <SelectContent>
                {Object.entries(MOTIVOS).map(([value, label]) => (
                  <SelectItem key={value} value={value}>{label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-size-13 font-medium text-ink">Detalle (opcional)</label>
            <Textarea
              value={detail}
              onChange={(e) => setDetail(e.target.value)}
              placeholder="Información adicional sobre la baja..."
              rows={3}
            />
          </div>
        </div>

        {error && (
          <div role="alert" className="rounded-xl bg-danger-bg px-4 py-2 text-size-13 font-semibold text-danger">
            {error}
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={isPending}>
            Cancelar
          </Button>
          <Button
            variant="destructive"
            onClick={handleConfirmar}
            disabled={!reason || isPending}
          >
            {isPending ? 'Dando de baja...' : 'Confirmar baja'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
