import { useState } from 'react';
import { toast } from 'sonner';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useSubjects } from '@/features/academic-structure/hooks/useSubjects';
import {
  useAddProgramSubject,
  useElectiveGroups,
  useProgramElectiveGroups,
} from '@/features/academic-structure/hooks/usePrograms';

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

const ADD_ERROR_MESSAGES = {
  CURRICULAR_YEAR_OUT_OF_RANGE: 'El año tiene que estar entre 1 y la duración de la carrera.',
  INVALID_PROGRAM_SUBJECT_CATEGORY: 'Faltan datos que exige la categoría elegida.',
  ELECTIVE_GROUP_NOT_ASSIGNED: 'El grupo electivo no está asignado a esta carrera.',
  PROGRAM_SUBJECT_ALREADY_EXISTS: 'Esa materia ya forma parte del plan de esta carrera.',
  SUBJECT_NOT_ASSIGNABLE_TO_PLAN: 'La materia todavía no está aprobada y no se puede agregar al plan.',
};

function CampoSelect({ id, label, value, onChange, options, required }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">{label}</label>
      <select
        id={id}
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

function validationError({ selectedSubjectId, category, curricularYear, curricularTerm, credits, electiveGroupId, totalYears }) {
  if (!selectedSubjectId) return 'Seleccioná una materia.';
  if (category !== 'ADDITIONAL_MANDATORY' && curricularYear) {
    const year = parseInt(curricularYear);
    if (year < 1 || (totalYears && year > totalYears)) {
      return `El año tiene que estar entre 1 y ${totalYears ?? 'la duración de la carrera'}.`;
    }
  }
  if (category === 'MANDATORY' && !curricularYear) return 'Indicá el año de la materia obligatoria.';
  if (category === 'MANDATORY' && !curricularTerm) return 'Elegí el cuatrimestre de la materia obligatoria.';
  if (category === 'ELECTIVE' && !electiveGroupId) return 'Elegí el grupo electivo.';
  if (!credits || parseInt(credits) < 0) return 'Ingresá los créditos.';
  return null;
}

function buildBody({ selectedSubjectId, category, curricularYear, curricularTerm, credits, electiveGroupId }) {
  const body = { subjectId: selectedSubjectId, category, credits: parseInt(credits) };
  if (category === 'ADDITIONAL_MANDATORY') return body;
  if (curricularYear) body.curricularYear = parseInt(curricularYear);
  if (curricularTerm) body.curricularTerm = curricularTerm;
  if (category === 'ELECTIVE') body.electiveGroupId = parseInt(electiveGroupId);
  return body;
}

export function PanelAgregarMateria({ open, onClose, programId, defaultYear, totalYears }) {
  const { data: subjects } = useSubjects('ACTIVE');
  const { data: electiveGroups } = useElectiveGroups();
  const { data: programElectiveGroups } = useProgramElectiveGroups(programId);
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
  const groupNames = new Map((electiveGroups ?? []).map((g) => [g.id, g.name]));
  const assignedGroups = (programElectiveGroups ?? []).map((g) => ({
    value: String(g.electiveGroupId),
    label: groupNames.get(g.electiveGroupId) ?? `Grupo ${g.electiveGroupId}`,
  }));
  const electiveWithoutGroups = category === 'ELECTIVE' && assignedGroups.length === 0;

  function handleClose() {
    setError(null);
    onClose();
  }

  async function handleAgregar() {
    const form = { selectedSubjectId, category, curricularYear, curricularTerm, credits, electiveGroupId, totalYears };
    const invalid = validationError(form);
    if (invalid) {
      setError(invalid);
      return;
    }
    setError(null);
    try {
      await addProgramSubject.mutateAsync(buildBody(form));
      toast.success('Materia agregada al plan');
      handleClose();
    } catch (err) {
      setError(ADD_ERROR_MESSAGES[err?.code] ?? 'No se pudo agregar la materia. Revisá los datos e intentá de nuevo.');
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
              id="program-subject-category"
              label="Categoría *"
              value={category}
              onChange={setCategory}
              options={CATEGORY_OPTIONS}
              required
            />

            {category === 'ADDITIONAL_MANDATORY' ? (
              <p className="text-[12px] text-ink-faint">
                Las obligatorias adicionales no tienen año ni cuatrimestre: se cursan en cualquier momento de la carrera.
              </p>
            ) : (
              <div className="flex gap-3">
                <div className="flex flex-1 flex-col gap-1.5">
                  <label htmlFor="program-subject-year" className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
                    Año curricular
                  </label>
                  <Input
                    id="program-subject-year"
                    type="number"
                    min={1}
                    max={totalYears ?? 10}
                    value={curricularYear}
                    onChange={(e) => setCurricularYear(e.target.value)}
                    placeholder="Ej. 1"
                  />
                </div>
                <div className="flex-1">
                  <CampoSelect
                    id="program-subject-term"
                    label="Cuatrimestre"
                    value={curricularTerm}
                    onChange={setCurricularTerm}
                    options={TERM_OPTIONS}
                  />
                </div>
              </div>
            )}

            <div className="flex flex-col gap-1.5">
              <label htmlFor="program-subject-credits" className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
                Créditos *
              </label>
              <Input
                id="program-subject-credits"
                type="number"
                min={0}
                value={credits}
                onChange={(e) => setCredits(e.target.value)}
                placeholder="Ej. 6"
              />
            </div>

            {category === 'ELECTIVE' &&
              (electiveWithoutGroups ? (
                <p className="text-[13px] text-danger">
                  Esta carrera no tiene grupos electivos asignados, así que todavía no se le pueden agregar electivas.
                </p>
              ) : (
                <CampoSelect
                  id="program-subject-elective-group"
                  label="Grupo electivo *"
                  value={electiveGroupId}
                  onChange={setElectiveGroupId}
                  options={[{ value: '', label: 'Elegí un grupo' }, ...assignedGroups]}
                />
              ))}

            {error && <p className="text-[13px] text-danger">{error}</p>}

            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={handleClose}>
                Cancelar
              </Button>
              <Button
                variant="gradient"
                loading={addProgramSubject.isPending}
                disabled={electiveWithoutGroups}
                onClick={handleAgregar}
              >
                Agregar
              </Button>
            </div>
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}
