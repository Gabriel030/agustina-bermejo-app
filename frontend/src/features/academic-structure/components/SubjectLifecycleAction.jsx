import { useState } from 'react';
import { PowerOff, RotateCcw } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { useActivateSubject, useDeactivateSubject } from '@/features/academic-structure/hooks/useSubjects';

const LIFECYCLE_ERROR_MESSAGES = {
  SUBJECT_INVALID_TRANSITION: 'La materia cambió de estado: recargá la página para ver su estado actual.',
};

export default function SubjectLifecycleAction({ subject, size = 'sm', onDone }) {
  const deactivate = useDeactivateSubject();
  const reactivate = useActivateSubject();
  const [confirming, setConfirming] = useState(false);

  async function handleDeactivate() {
    try {
      await deactivate.mutateAsync(subject.id);
      toast.success('La materia fue dada de baja');
      setConfirming(false);
      onDone?.();
    } catch (err) {
      toast.error(LIFECYCLE_ERROR_MESSAGES[err?.code] ?? 'No se pudo dar de baja la materia. Intentá de nuevo.');
    }
  }

  async function handleReactivate() {
    try {
      await reactivate.mutateAsync(subject.id);
      toast.success('La materia fue reactivada');
      onDone?.();
    } catch (err) {
      toast.error(LIFECYCLE_ERROR_MESSAGES[err?.code] ?? 'No se pudo reactivar la materia. Intentá de nuevo.');
    }
  }

  if (subject.status === 'ACTIVE') {
    return (
      <>
        <Button
          variant="outline"
          size={size}
          onClick={() => setConfirming(true)}
          className="text-danger hover:border-danger/50 hover:text-danger"
        >
          <PowerOff className="mr-1.5 h-3.5 w-3.5" />
          Dar de baja
        </Button>
        <ConfirmActionDialog
          open={confirming}
          onOpenChange={setConfirming}
          title="Dar de baja la materia"
          description={`${subject.code} — ${subject.name} pasa a la pestaña Inactivas del banco. Podés reactivarla cuando quieras.`}
          confirmLabel="Dar de baja"
          pending={deactivate.isPending}
          onConfirm={handleDeactivate}
        />
      </>
    );
  }

  if (subject.status === 'INACTIVE') {
    return (
      <Button variant="outline" size={size} onClick={handleReactivate} loading={reactivate.isPending}>
        <RotateCcw className="mr-1.5 h-3.5 w-3.5" />
        Reactivar
      </Button>
    );
  }

  return null;
}
