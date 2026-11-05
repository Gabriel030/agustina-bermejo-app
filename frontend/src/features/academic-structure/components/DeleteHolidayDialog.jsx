import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { formatLongDate } from '@/features/academic-structure/lib/holidays';

export function DeleteHolidayDialog({ holiday, deleting, onConfirm, onCancel }) {
  return (
    <Dialog open={holiday !== null} onOpenChange={(open) => { if (!open) onCancel(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Eliminar feriado</DialogTitle>
          <DialogDescription>
            {holiday && `Vas a eliminar "${holiday.name}" del ${formatLongDate(holiday.date)}. Ese día vuelve a contar como día hábil: se podrán programar clases y evaluaciones.`}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onCancel}>Cancelar</Button>
          <Button type="button" variant="destructive" loading={deleting} onClick={onConfirm}>Eliminar feriado</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
