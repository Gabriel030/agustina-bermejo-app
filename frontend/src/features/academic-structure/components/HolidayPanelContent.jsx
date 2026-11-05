import { useState } from 'react';
import { X } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Textarea } from '@/components/ui/textarea';
import { HolidayTypePill } from '@/features/academic-structure/components/HolidayTypePill';
import { useCreateHoliday, useUpdateHoliday } from '@/features/academic-structure/hooks/useHolidays';
import { HOLIDAY_ALREADY_EXISTS, HOLIDAY_TYPES, formatLongDate } from '@/features/academic-structure/lib/holidays';

function FieldLabel({ htmlFor, children }) {
  return (
    <Label htmlFor={htmlFor} className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">
      {children}
    </Label>
  );
}

function FieldError({ id, children }) {
  return <p id={id} className="mt-1.5 text-[12.5px] text-danger">{children}</p>;
}

export function HolidayPanelContent({ holiday, initialDate, findByDate, titleId, onClose, onSaved, onEditExisting, onDelete }) {
  const editing = holiday !== null;
  const [date, setDate] = useState(editing ? holiday.date : initialDate);
  const [name, setName] = useState(editing ? holiday.name : '');
  const [type, setType] = useState(editing ? holiday.type : 'NATIONAL');
  const [description, setDescription] = useState(editing ? (holiday.description ?? '') : '');
  const [errors, setErrors] = useState({});
  const [conflictDate, setConflictDate] = useState(null);
  const createHoliday = useCreateHoliday();
  const updateHoliday = useUpdateHoliday();
  const saving = createHoliday.isPending || updateHoliday.isPending;
  const conflictingHoliday = conflictDate === date ? findByDate(date) : null;
  const dateInvalid = Boolean(errors.date) || conflictDate === date;

  async function handleSubmit(event) {
    event.preventDefault();
    const nextErrors = {};
    if (!date) nextErrors.date = 'Elegí la fecha del feriado.';
    if (!name.trim()) nextErrors.name = 'Escribí el nombre del feriado.';
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    const body = { date, name: name.trim(), type, description: description.trim() || null };
    try {
      const saved = editing
        ? await updateHoliday.mutateAsync({ id: holiday.id, ...body })
        : await createHoliday.mutateAsync(body);
      toast.success(editing ? 'Feriado actualizado.' : 'Feriado agregado.');
      onSaved(saved ?? { ...body, id: holiday?.id });
    } catch (error) {
      if (error.code === HOLIDAY_ALREADY_EXISTS) setConflictDate(date);
      else toast.error('No pudimos guardar el feriado. Probá de nuevo.');
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <div className="flex items-start justify-between gap-3 px-5 pb-2 pt-5">
        <div className="min-w-0">
          <h2 id={titleId} className="font-heading text-[17px] font-semibold text-ink">
            {editing ? 'Editar feriado' : 'Nuevo feriado'}
          </h2>
          {date && <p className="text-[12.5px] text-ink-faint first-letter:uppercase">{formatLongDate(date)}</p>}
        </div>
        <Button type="button" variant="ghost" size="icon" aria-label="Cerrar panel" onClick={onClose}>
          <X />
        </Button>
      </div>
      <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-auto px-5 pb-3 pt-2">
        <div>
          <FieldLabel htmlFor="holiday-date">Fecha</FieldLabel>
          <Input
            id="holiday-date"
            type="date"
            value={date}
            aria-invalid={dateInvalid}
            aria-describedby={dateInvalid ? 'holiday-date-error' : undefined}
            onChange={(event) => {
              setDate(event.target.value);
              setErrors((current) => ({ ...current, date: undefined }));
            }}
            className="mt-1.5 min-h-11"
          />
          {errors.date && <FieldError id="holiday-date-error">{errors.date}</FieldError>}
          {conflictDate === date && (
            <div id="holiday-date-error" role="alert" className="mt-1.5 flex flex-wrap items-center gap-x-3 text-[12.5px] text-danger">
              <span>Ya hay un feriado cargado en esta fecha.</span>
              {conflictingHoliday && (
                <button
                  type="button"
                  className="min-h-8 font-bold underline"
                  onClick={() => onEditExisting(conflictingHoliday)}
                >
                  Editar ese feriado
                </button>
              )}
            </div>
          )}
        </div>
        <div>
          <FieldLabel htmlFor="holiday-name">Nombre</FieldLabel>
          <Input
            id="holiday-name"
            value={name}
            maxLength={255}
            aria-invalid={Boolean(errors.name)}
            aria-describedby={errors.name ? 'holiday-name-error' : undefined}
            onChange={(event) => {
              setName(event.target.value);
              setErrors((current) => ({ ...current, name: undefined }));
            }}
            className="mt-1.5 min-h-11"
          />
          {errors.name && <FieldError id="holiday-name-error">{errors.name}</FieldError>}
        </div>
        <fieldset>
          <legend className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">Tipo</legend>
          <RadioGroup value={type} onValueChange={setType} className="mt-1.5 grid grid-cols-2 gap-2">
            {HOLIDAY_TYPES.map((option) => (
              <Label
                key={option.value}
                htmlFor={`holiday-type-${option.value}`}
                className="flex min-h-11 cursor-pointer items-center gap-2 rounded-xl border border-border px-2.5 has-[[data-state=checked]]:border-brand has-[[data-state=checked]]:bg-accent-soft"
              >
                <RadioGroupItem id={`holiday-type-${option.value}`} value={option.value} />
                <HolidayTypePill type={option.value} plain />
              </Label>
            ))}
          </RadioGroup>
        </fieldset>
        <div>
          <FieldLabel htmlFor="holiday-description">Descripción (opcional)</FieldLabel>
          <Textarea
            id="holiday-description"
            value={description}
            maxLength={255}
            onChange={(event) => setDescription(event.target.value)}
            className="mt-1.5 min-h-20"
          />
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-2 border-t border-border px-5 pb-4 pt-3">
        {editing && (
          <Button type="button" variant="outline" className="border-danger text-danger" onClick={() => onDelete(holiday)}>
            Eliminar
          </Button>
        )}
        <span className="flex-1" />
        <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
        <Button type="submit" variant="gradient" loading={saving}>
          {editing ? 'Guardar cambios' : 'Agregar feriado'}
        </Button>
      </div>
    </form>
  );
}
