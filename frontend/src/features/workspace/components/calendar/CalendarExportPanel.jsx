import { Download, X } from 'lucide-react';
import { Notice } from '@/components/Notice';
import { Button } from '@/components/ui/button';
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { describeError } from '@/api/describeError';
import { CalendarFeedSection } from '@/features/workspace/components/calendar/CalendarFeedSection';
import { useDownloadCalendarIcs } from '@/features/workspace/hooks/useCalendarFeed';

function CalendarIcsSection() {
  const download = useDownloadCalendarIcs();
  return (
    <section aria-labelledby="calendar-ics-title" className="flex flex-col gap-3 border-t border-border pt-4">
      <h3 id="calendar-ics-title" className="font-heading text-size-14 font-semibold text-ink">
        Descargar archivo .ics
      </h3>
      <p className="text-size-13 text-ink-soft">
        Una copia de hoy. No se actualiza: si cambian las clases, tenés que descargarlo de nuevo.
      </p>
      <Button variant="outline" className="min-h-11 self-start" disabled={download.isPending} onClick={() => download.mutate()}>
        <Download aria-hidden="true" />
        {download.isPending ? 'Descargando…' : 'Descargar archivo .ics'}
      </Button>
      {download.isError && (
        <Notice tone="danger" role="alert">
          {describeError(download.error, { fallback: 'No pudimos descargar el archivo. Probá de nuevo.' }).message}
        </Notice>
      )}
    </section>
  );
}

export function CalendarExportPanel({ open, onOpenChange, onCloseAutoFocus, isDesktop }) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side={isDesktop ? 'right' : 'bottom'}
        showCloseButton={false}
        aria-describedby="calendar-export-description"
        onCloseAutoFocus={onCloseAutoFocus}
        className="max-h-[90dvh] gap-0 overflow-y-auto data-[side=right]:max-h-none data-[side=right]:w-full data-[side=right]:sm:max-w-[400px] max-md:rounded-t-3xl"
      >
        <SheetHeader className="flex-row items-start justify-between gap-3 p-5 pb-3">
          <div className="flex min-w-0 flex-col gap-1">
            <span className="text-size-11 font-bold uppercase tracking-wide text-ink-faint">Exportar</span>
            <SheetTitle className="font-heading text-size-17 font-bold text-ink">Agregar a mi calendario</SheetTitle>
            <SheetDescription id="calendar-export-description" className="sr-only">
              Suscribite con un enlace o descargá un archivo .ics con tus clases, feriados y mesas.
            </SheetDescription>
          </div>
          <SheetClose asChild>
            <Button variant="ghost" size="icon" aria-label="Cerrar" className="-mr-2 size-11 shrink-0">
              <X aria-hidden="true" />
            </Button>
          </SheetClose>
        </SheetHeader>
        <div className="flex flex-col gap-5 p-5 pt-2">
          <CalendarFeedSection />
          <CalendarIcsSection />
          <p className="border-t border-border pt-4 text-size-12 text-ink-faint">
            El enlace es personal y va firmado. Incluye solo tus clases, feriados y mesas. No incluye notas. No lo compartas.
          </p>
        </div>
      </SheetContent>
    </Sheet>
  );
}
