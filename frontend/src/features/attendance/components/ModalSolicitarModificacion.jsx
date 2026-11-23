import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { useSolicitarModificacion } from '@/features/attendance/hooks/useDocenteAsistencia';
import { MARKABLE_STATUSES, STATUS_BY_VALUE } from '@/features/attendance/lib/attendanceStatuses';

function requestErrorMessage(err) {
  if (err.status === 403) return 'No tenés permiso para pedir la modificación en esta comisión.';
  if (err.status === 404) return 'No encontramos ese registro de asistencia. Recargá la página y probá de nuevo.';
  if (err.status === 400) return 'La solicitud no es válida. Revisá el estado elegido y el motivo.';
  return 'No se pudo enviar la solicitud. Probá de nuevo.';
}

export function ModalSolicitarModificacion({ attendanceId, estadoActual, onClose }) {
  const [nuevoEstado, setNuevoEstado] = useState('PRESENT');
  const [motivo, setMotivo] = useState('');
  const [error, setError] = useState(null);
  const solicitar = useSolicitarModificacion(attendanceId);

  async function enviar() {
    setError(null);
    try {
      await solicitar.mutateAsync({ requestedStatus: nuevoEstado, reason: motivo });
      onClose();
    } catch (err) {
      setError(requestErrorMessage(err));
    }
  }

  return (
    <Dialog open={!!attendanceId} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Solicitar modificación de asistencia</DialogTitle>
          <DialogDescription>Estado actual: {STATUS_BY_VALUE[estadoActual]?.label ?? estadoActual}. La modificación queda pendiente de aprobación.</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-3">
          <Select value={nuevoEstado} onValueChange={setNuevoEstado}>
            <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
            <SelectContent>
              {MARKABLE_STATUSES.map((e) => <SelectItem key={e.value} value={e.value}>{e.label}</SelectItem>)}
            </SelectContent>
          </Select>
          <Textarea placeholder="Motivo de la modificación..." value={motivo} onChange={(e) => setMotivo(e.target.value)} />
          {error && <div className="rounded-xl bg-danger-bg px-4 py-2 text-size-13 font-semibold text-danger">{error}</div>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancelar</Button>
          <Button variant="primary" onClick={enviar} disabled={!motivo || solicitar.isPending}>Enviar solicitud</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
