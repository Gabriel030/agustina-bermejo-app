import { useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import { Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { NativeSelect } from '@/components/ui/native-select';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Stepper } from '@/components/Stepper';
import { Notice } from '@/components/Notice';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { describeError } from '@/api/describeError';
import { cn } from '@/lib/utils';
import { useClassrooms } from '@/features/academic-structure/hooks/useClassrooms';
import { useRequestSectionModification } from '@/features/academic-structure/hooks/useRequestSectionModification';
import { ClassroomModuleSelector } from '@/features/academic-structure/components/ClassroomModuleSelector';
import { WEEKDAYS } from '@/features/academic-structure/lib/classroomAvailability';
import {
  buildScheduleRows,
  draftErrors,
  draftsToSlots,
  emptyDraft,
  formatSlot,
  hasScheduleChanges,
  shiftForSection,
  slotsToDrafts,
} from '@/features/academic-structure/lib/sectionScheduleRequest';

const STEPS = [
  { key: 'edit', label: 'Horario nuevo' },
  { key: 'review', label: 'Revisá los cambios' },
];

function SlotDraftCard({ index, draft, shift, classrooms, canRemove, onChange, onRemove }) {
  const dayId = `schedule-day-${draft.key}`;
  const roomId = `schedule-room-${draft.key}`;
  return (
    <div className="flex flex-col gap-3 rounded-2xl border border-surface-border p-3" role="group" aria-label={`Franja ${index + 1}`}>
      <div className="flex items-center justify-between">
        <span className="font-heading text-size-13 font-semibold text-ink">Franja {index + 1}</span>
        {canRemove && (
          <Button type="button" variant="ghost" size="sm" className="min-h-11" onClick={onRemove}>
            Quitar
          </Button>
        )}
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor={dayId}>Día</Label>
          <NativeSelect
            id={dayId}
            className="min-h-11"
            value={draft.dayOfWeek}
            onChange={(event) => onChange({ ...draft, dayOfWeek: event.target.value })}
          >
            {WEEKDAYS.map((day) => (
              <option key={day.id} value={day.id}>{day.label}</option>
            ))}
          </NativeSelect>
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor={roomId}>Aula</Label>
          <NativeSelect
            id={roomId}
            className="min-h-11"
            value={draft.classroomId ?? ''}
            onChange={(event) => onChange({ ...draft, classroomId: event.target.value ? Number(event.target.value) : null })}
          >
            <option value="">Elegí un aula…</option>
            {classrooms.map((classroom) => (
              <option key={classroom.id} value={classroom.id}>{classroom.name}</option>
            ))}
          </NativeSelect>
        </div>
      </div>
      {draft.legacy && draft.selection.length === 0 && (
        <p role="note" className="text-size-12 text-warning">
          El horario actual de esta franja no coincide con los módulos del turno. Elegí los módulos de nuevo para reemplazarla.
        </p>
      )}
      <ClassroomModuleSelector
        shift={shift}
        selection={draft.selection}
        onChange={(selection) => onChange({ ...draft, selection })}
      />
    </div>
  );
}

function ScheduleDiffTable({ rows }) {
  return (
    <div className="overflow-hidden rounded-2xl border border-surface-border">
      <div className="grid grid-cols-2 gap-3 bg-surface/50 px-3 py-2 text-size-12 font-semibold uppercase tracking-wide text-ink-faint">
        <span>Hoy</span>
        <span>Nuevo</span>
      </div>
      {rows.map((row) => (
        <div key={row.key} className="grid grid-cols-2 gap-3 border-t border-surface-border px-3 py-2.5 text-size-13">
          <span className="text-ink-soft">{row.today}</span>
          <span
            className={cn(
              'rounded-lg px-2 py-1',
              row.unchanged ? 'text-ink-faint' : 'bg-accent-soft font-semibold text-ink',
            )}
          >
            {row.removed ? 'Se quita esta franja' : row.unchanged ? 'sin cambios' : row.next}
          </span>
        </div>
      ))}
    </div>
  );
}

export function SectionScheduleRequestSheet({ section, cycleId, open, onClose }) {
  const compact = useMediaQuery('(max-width: 767px)');
  const shift = shiftForSection(section);
  const { data: classrooms = [] } = useClassrooms();
  const mutation = useRequestSectionModification(cycleId);
  const [step, setStep] = useState('edit');
  const [drafts, setDrafts] = useState(() => slotsToDrafts(section.slots ?? [], shift));
  const [nextKey, setNextKey] = useState(() => (section.slots ?? []).length);
  const [serverError, setServerError] = useState(null);
  const reviewHeadingRef = useRef(null);

  useEffect(() => {
    if (step === 'review') reviewHeadingRef.current?.focus();
  }, [step]);

  const errors = draftErrors(drafts);
  const nextSlots = errors.length === 0 ? draftsToSlots(drafts, shift) : [];
  const changed = errors.length === 0 && hasScheduleChanges(section.slots ?? [], nextSlots);
  const classroomNameById = Object.fromEntries(classrooms.map((classroom) => [classroom.id, classroom.name]));

  function updateDraft(next) {
    setDrafts((current) => current.map((draft) => (draft.key === next.key ? next : draft)));
  }

  function addDraft() {
    setDrafts((current) => [...current, emptyDraft(nextKey)]);
    setNextKey((current) => current + 1);
  }

  function removeDraft(key) {
    setDrafts((current) => current.filter((draft) => draft.key !== key));
  }

  function submit() {
    setServerError(null);
    mutation.mutate(
      { sectionId: section.id, slots: nextSlots },
      {
        onSuccess: () => {
          toast.success('Pedido enviado al Director');
          onClose();
        },
        onError: (error) => setServerError(describeError(error).message),
      },
    );
  }

  return (
    <Sheet open={open} onOpenChange={(next) => !next && !mutation.isPending && onClose()}>
      <SheetContent side={compact ? 'bottom' : 'right'} className={cn('overflow-y-auto p-4 sm:max-w-xl', compact && 'max-h-[88vh] rounded-t-2xl')}>
        <SheetHeader>
          <SheetTitle>Nuevo horario de la comisión</SheetTitle>
          <SheetDescription>{`${section.subjectName} · ${section.name}`}</SheetDescription>
        </SheetHeader>
        <Stepper pasos={STEPS} pasoActual={step} />

        {step === 'edit' && (
          <div className="flex flex-col gap-4">
            <p className="text-size-13 text-ink-soft">
              Pedí un cambio de día, hora o aula. El Director lo tiene que aprobar antes de que se aplique.
            </p>
            <div className="rounded-2xl bg-surface/50 px-4 py-3">
              <p className="text-size-11 font-medium uppercase tracking-wide text-ink-faint">Horario actual</p>
              <ul className="mt-1 flex flex-col gap-0.5 text-size-13 text-ink">
                {(section.slots ?? []).map((slot, index) => (
                  <li key={index}>{formatSlot(slot)}</li>
                ))}
              </ul>
            </div>
            <h3 className="font-heading text-size-15 font-semibold text-ink">Horario nuevo</h3>
            {drafts.map((draft, index) => (
              <SlotDraftCard
                key={draft.key}
                index={index}
                draft={draft}
                shift={shift}
                classrooms={classrooms}
                canRemove={drafts.length > 1}
                onChange={updateDraft}
                onRemove={() => removeDraft(draft.key)}
              />
            ))}
            <Button type="button" variant="outline" className="min-h-11 self-start" onClick={addDraft}>
              <Plus className="mr-1.5 h-4 w-4" />
              Agregar franja
            </Button>
            {errors.length > 0 && (
              <ul role="alert" className="flex flex-col gap-0.5 text-size-12 font-semibold text-danger">
                {errors.map((message) => (
                  <li key={message}>{message}</li>
                ))}
              </ul>
            )}
            {errors.length === 0 && !changed && (
              <p role="status" className="text-size-12 text-ink-faint">
                No hay cambios respecto del horario actual. Modificá una franja para continuar.
              </p>
            )}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" className="min-h-11" onClick={onClose}>
                Cancelar
              </Button>
              <Button type="button" variant="primary" className="min-h-11" disabled={!changed} onClick={() => setStep('review')}>
                Continuar
              </Button>
            </div>
          </div>
        )}

        {step === 'review' && (
          <div className="flex flex-col gap-4">
            <h3 ref={reviewHeadingRef} tabIndex={-1} className="font-heading text-size-15 font-semibold text-ink outline-none">
              Revisá los cambios
            </h3>
            <ScheduleDiffTable rows={buildScheduleRows(section.slots ?? [], nextSlots, classroomNameById)} />
            <p className="text-size-12 text-ink-faint">No se cambian alumnos, docente ni cupo: solo día, hora y aula.</p>
            <Notice tone="neutral" title="Qué va a pasar">
              <ul className="mt-1 list-disc pl-4">
                <li>El pedido queda pendiente hasta que el Director lo resuelva.</li>
                <li>Mientras tanto sigue vigente el horario actual.</li>
                <li>Te avisamos del resultado en la comisión.</li>
              </ul>
            </Notice>
            {serverError && (
              <p role="alert" className="text-size-12 font-semibold text-danger">
                {serverError}
              </p>
            )}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" className="min-h-11" disabled={mutation.isPending} onClick={() => setStep('edit')}>
                Volver
              </Button>
              <Button type="button" variant="primary" className="min-h-11" loading={mutation.isPending} onClick={submit}>
                Enviar pedido de aprobación
              </Button>
            </div>
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}
