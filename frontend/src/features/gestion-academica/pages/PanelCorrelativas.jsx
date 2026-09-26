import { useState } from 'react';
import { Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/Skeleton';
import { usePrerequisites, useAddPrerequisite, useRemovePrerequisite } from '@/features/gestion-academica/hooks/usePrograms';

const REQUIREMENT_TYPE_OPTIONS = [
  { value: 'APPROVED', label: 'Aprobada' },
  { value: 'PASSED_COURSEWORK', label: 'Regularizada' },
];

const REQUIREMENT_TYPE_LABELS = {
  APPROVED: 'Aprobada',
  PASSED_COURSEWORK: 'Regularizada',
};

function CampoSelect({ label, value, onChange, options }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">{label}</label>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded-xl border border-surface-border bg-surface/50 px-3 py-2 text-[13.5px] text-ink outline-none focus:border-accent focus:ring-1 focus:ring-accent/30"
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </div>
  );
}

function FormAgregarCorrelativa({ programSubjectId, onCancel }) {
  const [selectedId, setSelectedId] = useState(null);
  const [requirementType, setRequirementType] = useState('APPROVED');
  const [minGrade, setMinGrade] = useState('');
  const [error, setError] = useState(null);
  const addPrerequisite = useAddPrerequisite(programSubjectId);

  async function handleAgregar() {
    if (!selectedId) {
      setError('Seleccioná una correlativa.');
      return;
    }
    setError(null);
    try {
      await addPrerequisite.mutateAsync({
        prerequisiteProgramSubjectId: selectedId,
        requirementType,
        ...(minGrade && { minGrade: parseFloat(minGrade) }),
      });
      toast.success('Correlativa agregada');
      onCancel();
    } catch {
      setError('No se pudo agregar la correlativa. Intentá de nuevo.');
    }
  }

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-surface-border bg-neutral-bg/30 p-3">
      <p className="text-[12px] font-semibold uppercase tracking-wide text-ink-faint">Nueva correlativa</p>
      <div className="flex flex-col gap-1.5">
        <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
          ID de materia en plan *
        </label>
        <Input
          type="number"
          value={selectedId ?? ''}
          onChange={(e) => setSelectedId(e.target.value ? parseInt(e.target.value) : null)}
          placeholder="ID de la relación (program-subject)"
        />
        <p className="text-[11px] text-ink-faint">
          Ingresá el ID de la materia del plan (program-subject) que es correlativa.
        </p>
      </div>

      <CampoSelect
        label="Condición"
        value={requirementType}
        onChange={setRequirementType}
        options={REQUIREMENT_TYPE_OPTIONS}
      />

      <div className="flex flex-col gap-1.5">
        <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
          Nota mínima (opcional)
        </label>
        <Input
          type="number"
          min={0}
          max={10}
          step={0.5}
          value={minGrade}
          onChange={(e) => setMinGrade(e.target.value)}
          placeholder="Ej. 4"
        />
      </div>

      {error && <p className="text-[13px] text-danger">{error}</p>}

      <div className="flex justify-end gap-2">
        <Button variant="outline" size="sm" onClick={onCancel}>
          Cancelar
        </Button>
        <Button variant="gradient" size="sm" loading={addPrerequisite.isPending} onClick={handleAgregar}>
          Agregar
        </Button>
      </div>
    </div>
  );
}

export function PanelCorrelativas({ open, onClose, programSubjectId, subjectName }) {
  const { data: prerequisites, isLoading } = usePrerequisites(programSubjectId);
  const removePrerequisite = useRemovePrerequisite();
  const [showForm, setShowForm] = useState(false);

  async function handleRemove(id) {
    try {
      await removePrerequisite.mutateAsync(id);
      toast.success('Correlativa eliminada');
    } catch {
      toast.error('No se pudo eliminar la correlativa.');
    }
  }

  function handleClose() {
    setShowForm(false);
    onClose();
  }

  return (
    <Sheet open={open} onOpenChange={(v) => !v && handleClose()}>
      <SheetContent className="flex flex-col gap-4 overflow-y-auto">
        <SheetHeader>
          <SheetTitle>Correlativas</SheetTitle>
        </SheetHeader>

        {subjectName && (
          <p className="text-[13px] text-ink-faint">
            Correlativas para <span className="font-medium text-ink">{subjectName}</span>
          </p>
        )}

        {isLoading && <Skeleton rows={3} />}

        {!isLoading && (prerequisites ?? []).length === 0 && !showForm && (
          <p className="text-[13px] text-ink-faint">Sin correlativas definidas.</p>
        )}

        {!isLoading && (prerequisites ?? []).length > 0 && (
          <div className="flex flex-col divide-y divide-neutral-bg">
            {prerequisites.map((p) => (
              <div key={p.id} className="flex items-center justify-between gap-3 py-2">
                <div className="min-w-0">
                  <span className="block text-[11px] font-bold uppercase tracking-widest text-ink-faint">
                    {p.prerequisiteSubjectCode}
                  </span>
                  <span className="block truncate text-[13.5px] font-medium text-ink">
                    {p.prerequisiteSubjectName}
                  </span>
                  <span className="text-[12px] text-ink-faint">
                    {REQUIREMENT_TYPE_LABELS[p.requirementType] ?? p.requirementType}
                    {p.minGrade != null && ` · nota mín. ${p.minGrade}`}
                  </span>
                </div>
                <button
                  type="button"
                  onClick={() => handleRemove(p.id)}
                  disabled={removePrerequisite.isPending}
                  className="shrink-0 rounded-lg p-1.5 text-ink-faint transition-colors hover:bg-danger/10 hover:text-danger"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              </div>
            ))}
          </div>
        )}

        {showForm ? (
          <FormAgregarCorrelativa programSubjectId={programSubjectId} onCancel={() => setShowForm(false)} />
        ) : (
          <Button variant="outline" size="sm" onClick={() => setShowForm(true)} className="self-start">
            + Agregar correlativa
          </Button>
        )}
      </SheetContent>
    </Sheet>
  );
}
