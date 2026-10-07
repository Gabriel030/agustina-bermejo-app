import { useState } from 'react';
import { toast } from 'sonner';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { useProgramBillingTemplates, useCreateCohort } from '@/features/gestion-academica/hooks/useCohorts';
import { useTerminos } from '@/features/academic-structure/hooks/useTerminos';

const INTAKE_LABELS = {
  FIRST: '1er semestre',
  SECOND: '2do semestre',
};

const INTAKE_OPTIONS = [
  { value: 'FIRST', label: '1er semestre' },
  { value: 'SECOND', label: '2do semestre' },
];

export function NuevaCohortDialog({ open, onClose, programId }) {
  const { data: templates, isSuccess: templatesLoaded } = useProgramBillingTemplates(programId);
  const missingTemplates = templatesLoaded && templates.length === 0;
  const { data: terminos } = useTerminos();
  const createCohort = useCreateCohort();

  const [selectedTemplateId, setSelectedTemplateId] = useState('');
  const [intake, setIntake] = useState('');
  const [admissionStart, setAdmissionStart] = useState('');
  const [admissionEnd, setAdmissionEnd] = useState('');
  const [cursadaStart, setCursadaStart] = useState('');
  const [entryFeeDiscount, setEntryFeeDiscount] = useState('');

  function getTemplateLabel(template) {
    const term = (terminos ?? []).find((t) => String(t.id) === String(template.termId));
    const yearLabel = term ? String(term.year) : `Término ${template.termId}`;
    return `${yearLabel} — ${template.recurringFeeInvoicesCount} cuotas`;
  }

  function reset() {
    setSelectedTemplateId('');
    setIntake('');
    setAdmissionStart('');
    setAdmissionEnd('');
    setCursadaStart('');
    setEntryFeeDiscount('');
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (!selectedTemplateId || !intake || !admissionStart || !admissionEnd || !cursadaStart) {
      toast.error('Completá todos los campos.');
      return;
    }
    try {
      await createCohort.mutateAsync({
        templateId: Number(selectedTemplateId),
        intake,
        admissionStart,
        admissionEnd,
        cursadaStart,
        ...(entryFeeDiscount !== '' ? { entryFeeDiscountPoints: Number(entryFeeDiscount) } : {}),
      });
      toast.success('Cohorte creada.');
      reset();
      onClose();
    } catch {
      toast.error('No se pudo crear la cohorte.');
    }
  }

  function handleOpenChange(isOpen) {
    if (!isOpen) {
      reset();
      onClose();
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Nueva cohorte de ingreso</DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          {missingTemplates && (
            <p className="rounded-xl bg-warning-bg px-3 py-2 text-[13px] text-ink">
              Esta carrera todavía no tiene un año lectivo configurado, que define cuántas cuotas se facturan en el año.
              Dirección tiene que cargarla antes de abrir una cohorte.
            </p>
          )}

          <div className="flex flex-col gap-1">
            <label className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">
              Año lectivo
            </label>
            <select
              value={selectedTemplateId}
              onChange={(e) => setSelectedTemplateId(e.target.value)}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-[13px] text-ink focus:outline-none focus:ring-1 focus:ring-accent"
              required
            >
              <option value="">Seleccioná un año lectivo</option>
              {(templates ?? []).map((t) => (
                <option key={t.id} value={t.id}>
                  {getTemplateLabel(t)}
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">
              Semestre de ingreso
            </label>
            <select
              value={intake}
              onChange={(e) => setIntake(e.target.value)}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-[13px] text-ink focus:outline-none focus:ring-1 focus:ring-accent"
              required
            >
              <option value="">Seleccioná</option>
              {INTAKE_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="flex flex-col gap-1">
              <label className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">
                Inicio admisión
              </label>
              <input
                type="date"
                value={admissionStart}
                onChange={(e) => setAdmissionStart(e.target.value)}
                className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-[13px] text-ink focus:outline-none focus:ring-1 focus:ring-accent"
                required
              />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">
                Fin admisión
              </label>
              <input
                type="date"
                value={admissionEnd}
                onChange={(e) => setAdmissionEnd(e.target.value)}
                className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-[13px] text-ink focus:outline-none focus:ring-1 focus:ring-accent"
                required
              />
            </div>
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">
              Inicio de cursada
            </label>
            <input
              type="date"
              value={cursadaStart}
              onChange={(e) => setCursadaStart(e.target.value)}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-[13px] text-ink focus:outline-none focus:ring-1 focus:ring-accent"
              required
            />
          </div>

          <div className="flex flex-col gap-1">
            <label
              htmlFor="cohort-entry-fee-discount"
              className="text-[11px] font-bold uppercase tracking-widest text-ink-faint"
            >
              Descuento de la matrícula de ingreso (%)
            </label>
            <input
              id="cohort-entry-fee-discount"
              type="number"
              min="0"
              max="100"
              step="0.01"
              value={entryFeeDiscount}
              onChange={(e) => setEntryFeeDiscount(e.target.value)}
              placeholder={intake === 'SECOND' ? '50 (por defecto)' : '0 (sin descuento)'}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-[13px] text-ink focus:outline-none focus:ring-1 focus:ring-accent"
            />
            <p className="text-[12px] text-ink-faint">
              Si lo dejás vacío se usa el valor por defecto: 50% para el 2do semestre y sin descuento para el 1er semestre.
            </p>
          </div>

          <DialogFooter>
            <Button type="submit" variant="gradient" loading={createCohort.isPending} disabled={missingTemplates}>
              Crear cohorte
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
