import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { useMiSolicitudEgreso, useSolicitarEgreso } from '../hooks/useGraduation';
import { GRADUATION_STATE, canRequestGraduation, deriveGraduationState, graduationRequestErrorMessage } from '../lib/graduationState';

export function GraduationRequestAction({ carreraId }) {
  const [open, setOpen] = useState(false);
  const { data: own } = useMiSolicitudEgreso(carreraId);
  const requestGraduation = useSolicitarEgreso(carreraId);

  if (!own || !canRequestGraduation(own)) return null;

  const label = deriveGraduationState(own) === GRADUATION_STATE.REJECTED ? 'Volver a solicitar egreso' : 'Solicitar egreso';

  async function confirmRequest() {
    try {
      await requestGraduation.mutateAsync();
      toast.success('Solicitud de egreso enviada');
    } catch (error) {
      toast.error(graduationRequestErrorMessage(error));
    } finally {
      setOpen(false);
    }
  }

  return (
    <>
      <Button variant="gradient" className="w-full sm:w-auto" onClick={() => setOpen(true)}>
        {label}
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>¿Solicitar el egreso?</DialogTitle>
            <DialogDescription>
              Se enviará tu solicitud a Administración y Dirección. No podés tener otra solicitud activa para esta carrera.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)} disabled={requestGraduation.isPending}>
              Cancelar
            </Button>
            <Button loading={requestGraduation.isPending} onClick={confirmRequest}>
              Solicitar
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
