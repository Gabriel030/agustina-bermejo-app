import { useState } from 'react';
import { toast } from 'sonner';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAddCourseSubject } from '@/features/gestion-academica/hooks/useCourses';

const RECURRENCE_OPTIONS = [
  { value: '', label: 'Sin especificar' },
  { value: 'PROMOTIONAL', label: 'Promocional' },
  { value: 'ANNUAL', label: 'Anual' },
];

export function PanelModulo({ open, onClose, courseId }) {
  const [name, setName] = useState('');
  const [weeklyWorkload, setWeeklyWorkload] = useState('');
  const [sortOrder, setSortOrder] = useState('');
  const [subjectRecurrence, setSubjectRecurrence] = useState('');
  const [qualificationType, setQualificationType] = useState('');
  const [error, setError] = useState(null);
  const addModule = useAddCourseSubject(courseId);

  function handleClose() {
    setName('');
    setWeeklyWorkload('');
    setSortOrder('');
    setSubjectRecurrence('');
    setQualificationType('');
    setError(null);
    onClose();
  }

  async function handleAgregar() {
    if (!name.trim()) {
      setError('El nombre del módulo es obligatorio.');
      return;
    }
    setError(null);
    try {
      await addModule.mutateAsync({
        name: name.trim(),
        ...(weeklyWorkload && { weeklyWorkload: parseInt(weeklyWorkload) }),
        ...(sortOrder && { sortOrder: parseInt(sortOrder) }),
        ...(subjectRecurrence && { subjectRecurrence }),
        ...(qualificationType.trim() && { qualificationType: qualificationType.trim() }),
      });
      toast.success('Módulo agregado');
      handleClose();
    } catch {
      setError('No se pudo agregar el módulo. Intentá de nuevo.');
    }
  }

  return (
    <Sheet open={open} onOpenChange={(v) => !v && handleClose()}>
      <SheetContent className="flex flex-col gap-4 overflow-y-auto">
        <SheetHeader>
          <SheetTitle>Agregar módulo</SheetTitle>
        </SheetHeader>

        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
              Nombre <span className="text-danger">*</span>
            </label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Ej. Introducción al tema"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="flex flex-col gap-1.5">
              <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
                Hs. semanales
              </label>
              <Input
                type="number"
                min={1}
                value={weeklyWorkload}
                onChange={(e) => setWeeklyWorkload(e.target.value)}
                placeholder="Ej. 2"
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
                Orden
              </label>
              <Input
                type="number"
                min={1}
                value={sortOrder}
                onChange={(e) => setSortOrder(e.target.value)}
                placeholder="Ej. 1"
              />
            </div>
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
              Recurrencia
            </label>
            <select
              value={subjectRecurrence}
              onChange={(e) => setSubjectRecurrence(e.target.value)}
              className="w-full rounded-xl border border-surface-border bg-surface/50 px-3 py-2 text-[13.5px] text-ink outline-none focus:border-accent focus:ring-1 focus:ring-accent/30"
            >
              {RECURRENCE_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
              Tipo de calificación
            </label>
            <Input
              value={qualificationType}
              onChange={(e) => setQualificationType(e.target.value)}
              placeholder="Ej. Numérica"
            />
          </div>

          {error && <p className="text-[13px] text-danger">{error}</p>}

          <div className="flex justify-end gap-2">
            <Button variant="outline" size="sm" onClick={handleClose}>
              Cancelar
            </Button>
            <Button variant="gradient" size="sm" loading={addModule.isPending} onClick={handleAgregar}>
              Agregar
            </Button>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
