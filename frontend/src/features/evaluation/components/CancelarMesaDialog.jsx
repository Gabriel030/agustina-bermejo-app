import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

export function CancelarMesaDialog({ open, onClose, onConfirmar, enviando, inscriptos }) {
  const [motivo, setMotivo] = useState('');
  const motivoLimpio = motivo.trim();

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>¿Cancelar esta mesa?</DialogTitle>
          <DialogDescription>
            {inscriptos > 0
              ? `La mesa pasa a Canceladas y los ${inscriptos} alumnos anotados la van a ver como cancelada. Esta acción no se puede deshacer.`
              : 'La mesa pasa a Canceladas. Esta acción no se puede deshacer.'}
          </DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="cancelar-motivo">Motivo de la cancelación</Label>
          <Textarea id="cancelar-motivo" value={motivo} maxLength={255} onChange={(e) => setMotivo(e.target.value)} />
        </div>
        <DialogFooter>
          <Button variant="outline" disabled={enviando} onClick={onClose}>Volver</Button>
          <Button variant="destructive" disabled={!motivoLimpio || enviando} onClick={() => onConfirmar(motivoLimpio)}>
            Cancelar mesa
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
