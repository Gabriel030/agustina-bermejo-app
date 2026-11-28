import { useState } from 'react';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Notice } from '@/components/Notice';
import { PreceptorMultiSelect } from '@/components/PreceptorMultiSelect';
import { useReplaceCohortResponsibles } from '@/features/academic-structure/hooks/useCohorts';

export function CohortResponsibleDialog({ open, onClose, cohortId, cohortTitle, initialIds = [] }) {
  const [selected, setSelected] = useState(initialIds);
  const [failed, setFailed] = useState(false);
  const replace = useReplaceCohortResponsibles(cohortId);
  const empty = selected.length === 0;

  async function handleSave() {
    setFailed(false);
    try {
      await replace.mutateAsync(selected);
      onClose();
    } catch {
      setFailed(true);
    }
  }

  function handleOpenChange(next) {
    if (!next && !replace.isPending) onClose();
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Responsable de la cohorte</DialogTitle>
          <p className="text-size-13 text-ink-faint">{cohortTitle}</p>
        </DialogHeader>
        <div className="flex flex-col gap-3">
          <PreceptorMultiSelect
            label="Responsable"
            required
            value={selected}
            onChange={setSelected}
            disabled={replace.isPending}
            hint="Preceptor de toda la cohorte"
          />
          {failed && (
            <Notice tone="danger" role="alert" title="No pudimos guardar.">
              Tus cambios siguen acá. Reintentá.
            </Notice>
          )}
        </div>
        <DialogFooter className="items-center">
          {empty && (
            <span role="status" className="mr-auto text-size-13 text-ink-faint">
              Elegí un responsable
            </span>
          )}
          <Button variant="ghost" onClick={onClose} disabled={replace.isPending}>
            Cancelar
          </Button>
          <Button variant="primary" loading={replace.isPending} disabled={empty} onClick={handleSave}>
            {failed ? 'Reintentar' : 'Guardar'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
