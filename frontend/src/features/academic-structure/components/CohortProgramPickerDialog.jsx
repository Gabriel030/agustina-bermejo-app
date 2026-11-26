import { useState } from 'react';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { NativeSelect } from '@/components/ui/native-select';

export function CohortProgramPickerDialog({ open, programs, onClose, onPick }) {
  const [programId, setProgramId] = useState('');

  function handleOpenChange(isOpen) {
    if (isOpen) return;
    setProgramId('');
    onClose();
  }

  function handleSubmit(event) {
    event.preventDefault();
    if (!programId) return;
    const selected = Number(programId);
    setProgramId('');
    onPick(selected);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Nueva cohorte de ingreso</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          <div className="flex flex-col gap-1">
            <label
              htmlFor="cohort-program-picker"
              className="text-size-11 font-bold uppercase tracking-widest text-ink-faint"
            >
              Carrera
            </label>
            <NativeSelect id="cohort-program-picker" value={programId} onChange={(e) => setProgramId(e.target.value)}>
              <option value="">Seleccioná una carrera</option>
              {programs.map((program) => (
                <option key={program.id} value={program.id}>
                  {program.name}
                </option>
              ))}
            </NativeSelect>
          </div>
          <DialogFooter>
            <Button type="submit" variant="primary" disabled={!programId}>
              Continuar
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
