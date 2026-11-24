import { AlertTriangle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { EXPORT_SCOPES } from '@/features/attendance/lib/exportScopes';
import { useAttendanceExport } from '@/features/attendance/hooks/useAttendanceExport';

const FORMATS = [
  { value: 'pdf', label: 'PDF' },
  { value: 'xlsx', label: 'XLS' },
];

export function ExportAttendanceDialog({ scope, type, sectionId, classDates, onClose, onCloseAutoFocus }) {
  const exportForm = useAttendanceExport({ type, sectionId, classDates, scope, onDone: onClose });
  const { pending } = exportForm;
  const blockWhilePending = (event) => {
    if (pending) event.preventDefault();
  };

  return (
    <Dialog open={scope !== null} onOpenChange={(open) => !open && !pending && onClose()}>
      <DialogContent
        showCloseButton={!pending}
        onCloseAutoFocus={onCloseAutoFocus}
        onEscapeKeyDown={blockWhilePending}
        onPointerDownOutside={blockWhilePending}
        onInteractOutside={blockWhilePending}
      >
        <DialogHeader>
          <DialogTitle>{scope ? EXPORT_SCOPES[scope].title : 'Exportar'}</DialogTitle>
          <DialogDescription>Elegí el formato del archivo.</DialogDescription>
        </DialogHeader>

        {exportForm.needsClassDate && (
          <div className="flex flex-col gap-2">
            <Label htmlFor="export-year" className="text-size-12-5 font-bold text-ink-soft">Año y clase</Label>
            <div className="flex flex-wrap gap-2">
              <Select value={exportForm.year} onValueChange={exportForm.setYear} disabled={pending}>
                <SelectTrigger id="export-year" aria-label="Año" className="min-h-11 w-full sm:w-[110px]"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {exportForm.years.map((year) => <SelectItem key={year} value={year}>{year}</SelectItem>)}
                </SelectContent>
              </Select>
              <Select value={exportForm.classDate} onValueChange={exportForm.setClassDate} disabled={pending}>
                <SelectTrigger aria-label="Clase" className="min-h-11 w-full sm:flex-1"><SelectValue placeholder="Elegí una clase" /></SelectTrigger>
                <SelectContent>
                  {exportForm.monthGroups.map((group) => (
                    <SelectGroup key={group.month}>
                      <SelectLabel>{group.label}</SelectLabel>
                      {group.dates.map((date) => <SelectItem key={date} value={date}>{date}</SelectItem>)}
                    </SelectGroup>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        )}

        <div className="flex flex-col gap-2">
          <span id="export-format-label" className="text-size-12-5 font-bold text-ink-soft">Formato</span>
          <RadioGroup
            aria-labelledby="export-format-label"
            value={exportForm.format}
            onValueChange={exportForm.setFormat}
            disabled={pending}
            className="flex w-full gap-2"
          >
            {FORMATS.map((option) => (
              <Label key={option.value} htmlFor={`export-format-${option.value}`} className="flex min-h-11 flex-1 cursor-pointer items-center gap-2 rounded-card border border-border px-3 text-size-13 font-semibold text-ink has-data-[state=checked]:border-brand">
                <RadioGroupItem id={`export-format-${option.value}`} value={option.value} />
                {option.label}
              </Label>
            ))}
          </RadioGroup>
        </div>

        {exportForm.errorMessage && (
          <div role="alert" className="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-card border border-danger bg-danger-bg px-4 py-3 text-size-13 font-semibold text-danger-ink">
            <AlertTriangle className="size-4 shrink-0" aria-hidden="true" />
            <span className="min-w-0 flex-1 basis-40">{exportForm.errorMessage}</span>
            <Button type="button" variant="outline" className="min-h-11" onClick={exportForm.submit} disabled={pending}>Reintentar</Button>
          </div>
        )}

        <DialogFooter>
          <Button type="button" variant="outline" className="min-h-11" onClick={onClose} disabled={pending}>Cancelar</Button>
          <Button type="button" variant="primary" className="min-h-11" onClick={exportForm.submit} loading={pending} disabled={pending || !exportForm.canSubmit}>
            {pending ? 'Generando…' : 'Exportar'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
