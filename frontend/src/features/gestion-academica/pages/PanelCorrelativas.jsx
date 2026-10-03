import { useState } from 'react';
import { Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/Skeleton';
import {
  usePrerequisites,
  useAddPrerequisite,
  useRemovePrerequisite,
  useCorequisites,
  useAddCorequisite,
  useRemoveCorequisite,
  useProgramSubjects,
} from '@/features/gestion-academica/hooks/usePrograms';

const REQUIREMENT_TYPE_OPTIONS = [
  { value: 'APPROVED', label: 'Aprobada' },
  { value: 'PASSED_COURSEWORK', label: 'Regularizada' },
];

const REQUIREMENT_TYPE_LABELS = {
  APPROVED: 'Aprobada',
  PASSED_COURSEWORK: 'Regularizada',
};

const PREREQUISITE_ERROR_MESSAGES = {
  INVALID_PREREQUISITE: 'La correlativa tiene que ser una materia anterior del mismo plan.',
  CIRCULAR_PREREQUISITE: 'Esa correlativa armaría un ciclo con otra que ya existe.',
  PREREQUISITE_ALREADY_EXISTS: 'Esa correlativa ya está cargada.',
};

const COREQUISITE_ERROR_MESSAGES = {
  INVALID_COREQUISITE: 'El co-requisito tiene que ser otra materia del mismo plan.',
  COREQUISITE_ALREADY_EXISTS: 'Ese co-requisito ya está cargado.',
};

function PlanSubjectPicker({ programId, excludeId, selected, onSelect }) {
  const { data: planSubjects, isLoading } = useProgramSubjects(programId, 'ACTIVE');
  const [search, setSearch] = useState('');

  if (selected) {
    return (
      <div className="flex items-start justify-between rounded-xl bg-neutral-bg/60 px-3 py-2">
        <div>
          <span className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">{selected.subjectCode}</span>
          <p className="text-[13.5px] font-medium text-ink">{selected.subjectName}</p>
        </div>
        <button
          type="button"
          onClick={() => onSelect(null)}
          className="text-[12px] text-ink-faint underline hover:text-ink"
        >
          Cambiar
        </button>
      </div>
    );
  }

  const q = search.toLowerCase();
  const candidates = (planSubjects ?? [])
    .filter((ps) => ps.id !== excludeId)
    .filter((ps) => !q || ps.subjectName.toLowerCase().includes(q) || ps.subjectCode.toLowerCase().includes(q));

  return (
    <div className="flex flex-col gap-2">
      <Input placeholder="Buscar materia del plan…" value={search} onChange={(e) => setSearch(e.target.value)} />
      {isLoading && <Skeleton rows={2} />}
      <div className="flex max-h-56 flex-col divide-y divide-neutral-bg overflow-y-auto">
        {candidates.map((ps) => (
          <button
            key={ps.id}
            type="button"
            onClick={() => onSelect(ps)}
            className="flex flex-col gap-0.5 rounded-xl px-3 py-2 text-left transition-colors hover:bg-neutral-bg/60"
          >
            <span className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">{ps.subjectCode}</span>
            <span className="text-[13.5px] font-medium text-ink">{ps.subjectName}</span>
          </button>
        ))}
        {!isLoading && candidates.length === 0 && (
          <p className="py-3 text-center text-[13px] text-ink-faint">No hay otras materias en el plan que coincidan.</p>
        )}
      </div>
    </div>
  );
}

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

function FormAgregarCorrelativa({ programId, programSubjectId, onCancel }) {
  const [selected, setSelected] = useState(null);
  const [requirementType, setRequirementType] = useState('APPROVED');
  const [minGrade, setMinGrade] = useState('');
  const [error, setError] = useState(null);
  const addPrerequisite = useAddPrerequisite(programSubjectId);

  async function handleAgregar() {
    if (!selected) {
      setError('Seleccioná una correlativa.');
      return;
    }
    setError(null);
    try {
      await addPrerequisite.mutateAsync({
        prerequisiteProgramSubjectId: selected.id,
        requirementType,
        ...(minGrade && { minGrade: parseFloat(minGrade) }),
      });
      toast.success('Correlativa agregada');
      onCancel();
    } catch (err) {
      setError(PREREQUISITE_ERROR_MESSAGES[err?.code] ?? 'No se pudo agregar la correlativa. Intentá de nuevo.');
    }
  }

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-surface-border bg-neutral-bg/30 p-3">
      <p className="text-[12px] font-semibold uppercase tracking-wide text-ink-faint">Nueva correlativa</p>
      <div className="flex flex-col gap-1.5">
        <span className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">Materia correlativa *</span>
        <PlanSubjectPicker
          programId={programId}
          excludeId={programSubjectId}
          selected={selected}
          onSelect={setSelected}
        />
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

function FormAgregarCorequisito({ programId, programSubjectId, onCancel }) {
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState(null);
  const addCorequisite = useAddCorequisite(programSubjectId);

  async function handleAgregar() {
    if (!selected) {
      setError('Seleccioná la materia co-requisito.');
      return;
    }
    setError(null);
    try {
      await addCorequisite.mutateAsync({ corequisiteProgramSubjectId: selected.id });
      toast.success('Co-requisito agregado');
      onCancel();
    } catch (err) {
      setError(COREQUISITE_ERROR_MESSAGES[err?.code] ?? 'No se pudo agregar el co-requisito. Intentá de nuevo.');
    }
  }

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-surface-border bg-neutral-bg/30 p-3">
      <p className="text-[12px] font-semibold uppercase tracking-wide text-ink-faint">Nuevo co-requisito</p>
      <div className="flex flex-col gap-1.5">
        <span className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">Materia co-requisito *</span>
        <PlanSubjectPicker
          programId={programId}
          excludeId={programSubjectId}
          selected={selected}
          onSelect={setSelected}
        />
        <p className="text-[11px] text-ink-faint">
          El vínculo no es simétrico: si querés bloqueo mutuo, agregalo desde la otra materia también.
        </p>
      </div>

      {error && <p className="text-[13px] text-danger">{error}</p>}

      <div className="flex justify-end gap-2">
        <Button variant="outline" size="sm" onClick={onCancel}>
          Cancelar
        </Button>
        <Button variant="gradient" size="sm" loading={addCorequisite.isPending} onClick={handleAgregar}>
          Agregar
        </Button>
      </div>
    </div>
  );
}

export function PanelCorrelativas({ open, onClose, programId, programSubjectId, subjectName }) {
  const { data: prerequisites, isLoading } = usePrerequisites(programSubjectId);
  const { data: corequisites, isLoading: loadingCorequisites } = useCorequisites(programSubjectId);
  const removePrerequisite = useRemovePrerequisite();
  const removeCorequisite = useRemoveCorequisite();
  const [showForm, setShowForm] = useState(false);
  const [showCoreqForm, setShowCoreqForm] = useState(false);

  async function handleRemove(id) {
    try {
      await removePrerequisite.mutateAsync(id);
      toast.success('Correlativa eliminada');
    } catch {
      toast.error('No se pudo eliminar la correlativa.');
    }
  }

  async function handleRemoveCorequisite(id) {
    try {
      await removeCorequisite.mutateAsync(id);
      toast.success('Co-requisito eliminado');
    } catch {
      toast.error('No se pudo eliminar el co-requisito.');
    }
  }

  function handleClose() {
    setShowForm(false);
    setShowCoreqForm(false);
    onClose();
  }

  return (
    <Sheet open={open} onOpenChange={(v) => !v && handleClose()}>
      <SheetContent className="flex flex-col gap-4 overflow-y-auto">
        <SheetHeader>
          <SheetTitle>Correlativas y co-requisitos</SheetTitle>
        </SheetHeader>

        {subjectName && (
          <p className="text-[13px] text-ink-faint">
            Para <span className="font-medium text-ink">{subjectName}</span>
          </p>
        )}

        <div className="flex flex-col gap-1">
          <p className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">Correlativas</p>
          <p className="text-[11px] text-ink-faint">Condición previa para cursar esta materia.</p>
        </div>

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
          <FormAgregarCorrelativa
            programId={programId}
            programSubjectId={programSubjectId}
            onCancel={() => setShowForm(false)}
          />
        ) : (
          <Button variant="outline" size="sm" onClick={() => setShowForm(true)} className="self-start">
            + Agregar correlativa
          </Button>
        )}

        <div className="my-1 border-t border-neutral-bg" />

        <div className="flex flex-col gap-1">
          <p className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">Co-requisitos</p>
          <p className="text-[11px] text-ink-faint">Materia que debe cursarse simultáneamente.</p>
        </div>

        {loadingCorequisites && <Skeleton rows={2} />}

        {!loadingCorequisites && (corequisites ?? []).length === 0 && !showCoreqForm && (
          <p className="text-[13px] text-ink-faint">Sin co-requisitos definidos.</p>
        )}

        {!loadingCorequisites && (corequisites ?? []).length > 0 && (
          <div className="flex flex-col divide-y divide-neutral-bg">
            {corequisites.map((c) => (
              <div key={c.id} className="flex items-center justify-between gap-3 py-2">
                <div className="min-w-0">
                  <span className="block text-[11px] font-bold uppercase tracking-widest text-ink-faint">
                    {c.corequisiteSubjectCode}
                  </span>
                  <span className="block truncate text-[13.5px] font-medium text-ink">
                    {c.corequisiteSubjectName}
                  </span>
                </div>
                <button
                  type="button"
                  onClick={() => handleRemoveCorequisite(c.id)}
                  disabled={removeCorequisite.isPending}
                  className="shrink-0 rounded-lg p-1.5 text-ink-faint transition-colors hover:bg-danger/10 hover:text-danger"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              </div>
            ))}
          </div>
        )}

        {showCoreqForm ? (
          <FormAgregarCorequisito
            programId={programId}
            programSubjectId={programSubjectId}
            onCancel={() => setShowCoreqForm(false)}
          />
        ) : (
          <Button variant="outline" size="sm" onClick={() => setShowCoreqForm(true)} className="self-start">
            + Agregar co-requisito
          </Button>
        )}
      </SheetContent>
    </Sheet>
  );
}
