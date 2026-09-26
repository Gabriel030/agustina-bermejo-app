import { useState } from 'react';
import { toast } from 'sonner';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useSubjects } from '@/features/gestion-academica/hooks/useSubjects';
import { useAddProgramSubject, useElectiveGroups } from '@/features/gestion-academica/hooks/usePrograms';

const CATEGORY_OPTIONS = [
  { value: 'MANDATORY', label: 'Obligatoria' },
  { value: 'ADDITIONAL_MANDATORY', label: 'Obligatoria adicional' },
  { value: 'ELECTIVE', label: 'Electiva' },
];

const TERM_OPTIONS = [
  { value: '', label: 'Sin especificar' },
  { value: 'FIRST', label: '1.° cuatrimestre' },
  { value: 'SECOND', label: '2.° cuatrimestre' },
  { value: 'ANNUAL', label: 'Anual' },
];

function CampoSelect({ label, value, onChange, options, required }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">{label}</label>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required={required}
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

export function PanelAgregarMateria({ open, onClose, programId, defaultYear }) {
  const { data: subjects } = useSubjects('ACTIVE');
  const { data: electiveGroups } = useElectiveGroups();
  const addProgramSubject = useAddProgramSubject(programId);

  const [search, setSearch] = useState('');
  const [selectedSubjectId, setSelectedSubjectId] = useState(null);
  const [category, setCategory] = useState('MANDATORY');
  const [curricularYear, setCurricularYear] = useState(defaultYear?.toString() ?? '');
  const [curricularTerm, setCurricularTerm] = useState('');
  const [credits, setCredits] = useState('');
  const [electiveGroupId, setElectiveGroupId] = useState('');
  const [error, setError] = useState(null);

  const filtered = (subjects ?? []).filter((s) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return s.name.toLowerCase().includes(q) || s.code.toLowerCase().includes(q);
  });

  const selectedSubject = (subjects ?? []).find((s) => s.id === selectedSubjectId);

  function handleClose() {
    setSearch('');
    setSelectedSubjectId(null);
    setCategory('MANDATORY');
    setCurricularYear(defaultYear?.toString() ?? '');
    setCurricularTerm('');
    setCredits('');
    setElectiveGroupId('');
    setError(null);
    onClose();
  }

  async function handleAgregar() {
    if (!selectedSubjectId) {
      setError('Seleccioná una materia.');
      return;
    }
    if (!credits || parseInt(credits) < 0) {
      setError('Ingresá los créditos.');
      return;
    }
    setError(null);
    try {
      await addProgramSubject.mutateAsync({
        subjectId: selectedSubjectId,
        category,
        credits: parseInt(credits),
        ...(curricularYear && { curricularYear: parseInt(curricularYear) }),
        ...(curricularTerm && { curricularTerm }),
        ...(electiveGroupId && category === 'ELECTIVE' && { electiveGroupId: parseInt(electiveGroupId) }),
      });
      toast.success('Materia agregada al plan');
      handleClose();
    } catch {
      setError('No se pudo agregar la materia. Intentá de nuevo.');
    }
  }

  return (
    <Sheet open={open} onOpenChange={(v) => !v && handleClose()}>
      <SheetContent className="flex flex-col gap-4 overflow-y-auto">
        <SheetHeader>
          <SheetTitle>Agregar materia</SheetTitle>
        </SheetHeader>

        {!selectedSubject ? (
          <div className="flex flex-col gap-3">
            <Input
              placeholder="Buscar materia activa…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <div className="flex flex-col divide-y divide-neutral-bg">
              {filtered.slice(0, 20).map((s) => (
                <button
                  key={s.id}
                  type="button"
                  onClick={() => setSelectedSubjectId(s.id)}
                  className="flex flex-col gap-0.5 rounded-xl px-3 py-2 text-left transition-colors hover:bg-neutral-bg/60"
                >
                  <span className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">{s.code}</span>
                  <span className="text-[13.5px] font-medium text-ink">{s.name}</span>
                </button>
              ))}
              {filtered.length === 0 && (
                <p className="py-4 text-center text-[13px] text-ink-faint">Sin resultados</p>
              )}
            </div>
          </div>
        ) : (
          <div className="flex flex-col gap-4">
            <div className="flex items-start justify-between rounded-xl bg-neutral-bg/60 px-3 py-2">
              <div>
                <span className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">
                  {selectedSubject.code}
                </span>
                <p className="text-[13.5px] font-medium text-ink">{selectedSubject.name}</p>
              </div>
              <button
                type="button"
                onClick={() => setSelectedSubjectId(null)}
                className="text-[12px] text-ink-faint underline hover:text-ink"
              >
                Cambiar
              </button>
            </div>

            <CampoSelect
              label="Categoría *"
              value={category}
              onChange={setCategory}
              options={CATEGORY_OPTIONS}
              required
            />

            <div className="flex gap-3">
              <div className="flex-1 flex flex-col gap-1.5">
                <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">Año curricular</label>
                <Input
                  type="number"
                  min={1}
                  max={10}
                  value={curricularYear}
                  onChange={(e) => setCurricularYear(e.target.value)}
                  placeholder="Ej. 1"
                />
              </div>
              <div className="flex-1">
                <CampoSelect
                  label="Cuatrimestre"
                  value={curricularTerm}
                  onChange={setCurricularTerm}
                  options={TERM_OPTIONS}
                />
              </div>
            </div>

            <div className="flex flex-col gap-1.5">
              <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">Créditos *</label>
              <Input
                type="number"
                min={0}
                value={credits}
                onChange={(e) => setCredits(e.target.value)}
                placeholder="Ej. 6"
              />
            </div>

            {category === 'ELECTIVE' && (electiveGroups ?? []).length > 0 && (
              <CampoSelect
                label="Grupo electivo"
                value={electiveGroupId}
                onChange={setElectiveGroupId}
                options={[
                  { value: '', label: 'Sin grupo' },
                  ...(electiveGroups ?? []).map((g) => ({ value: g.id.toString(), label: g.name })),
                ]}
              />
            )}

            {error && <p className="text-[13px] text-danger">{error}</p>}

            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={handleClose}>
                Cancelar
              </Button>
              <Button variant="gradient" loading={addProgramSubject.isPending} onClick={handleAgregar}>
                Agregar
              </Button>
            </div>
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}
