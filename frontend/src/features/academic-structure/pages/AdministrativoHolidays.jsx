import { useMemo, useRef, useState } from 'react';
import { AlertTriangle, ChevronLeft, ChevronRight, Plus } from 'lucide-react';
import { toast } from 'sonner';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Button } from '@/components/ui/button';
import { Sheet, SheetContent, SheetDescription, SheetTitle } from '@/components/ui/sheet';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { DeleteHolidayDialog } from '@/features/academic-structure/components/DeleteHolidayDialog';
import { HolidayCalendar } from '@/features/academic-structure/components/HolidayCalendar';
import { HolidayList } from '@/features/academic-structure/components/HolidayList';
import { HolidayPanelContent } from '@/features/academic-structure/components/HolidayPanelContent';
import { HolidayTypePill } from '@/features/academic-structure/components/HolidayTypePill';
import { useDeleteHoliday, useHolidays } from '@/features/academic-structure/hooks/useHolidays';
import { countByType, defaultNewHolidayDate, holidayCountLabel } from '@/features/academic-structure/lib/holidays';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { localIsoDate } from '@/lib/calendario';

export function AdministrativoHolidays() {
  const today = useMemo(() => new Date(), []);
  const todayIso = localIsoDate(today);
  const nextYear = today.getFullYear() + 1;
  const isDesktop = useMediaQuery('(min-width: 1024px)');
  const [year, setYear] = useState(today.getFullYear());
  const [month, setMonth] = useState(today.getMonth());
  const [panel, setPanel] = useState(null);
  const [scope, setScope] = useState('month');
  const [pendingDelete, setPendingDelete] = useState(null);
  const calendarRef = useRef(null);
  const holidaysQuery = useHolidays(year);
  const nextYearQuery = useHolidays(nextYear);
  const deleteHoliday = useDeleteHoliday();
  const holidays = useMemo(() => holidaysQuery.data ?? [], [holidaysQuery.data]);
  const loading = holidaysQuery.isPending;
  const missingNextYear = nextYearQuery.isSuccess && nextYearQuery.data.length === 0;

  const selectedIso = panel?.date ?? null;
  const selectedHoliday = panel?.holidayId ? holidays.find((holiday) => holiday.id === panel.holidayId) ?? null : null;
  const typeCounts = useMemo(() => countByType(holidays), [holidays]);

  function openCreate(date) {
    setPanel({ key: `new-${date}`, date, holidayId: null });
  }

  function openEdit(holiday) {
    setPanel({ key: `edit-${holiday.id}`, date: holiday.date, holidayId: holiday.id });
    const [holidayYear, holidayMonth] = holiday.date.split('-').map(Number);
    setYear(holidayYear);
    setMonth(holidayMonth - 1);
  }

  function selectDate(iso) {
    const existing = holidays.find((holiday) => holiday.date === iso);
    if (existing) openEdit(existing);
    else openCreate(iso);
  }

  function navigate(nextYearValue, nextMonth) {
    setYear(nextYearValue);
    setMonth(nextMonth);
  }

  function changeYear(delta) {
    setYear((current) => current + delta);
  }

  function closePanel() {
    setPanel(null);
  }

  function loadNextYear() {
    setYear(nextYear);
    setMonth(0);
    openCreate(`${nextYear}-01-01`);
  }

  async function confirmDelete() {
    try {
      await deleteHoliday.mutateAsync(pendingDelete.id);
      toast.success('Feriado eliminado.');
      setPendingDelete(null);
      closePanel();
      calendarRef.current?.querySelector('[tabindex="0"]')?.focus();
    } catch {
      toast.error('No pudimos eliminar el feriado. Probá de nuevo.');
    }
  }

  const panelOpen = panel !== null;
  const panelContent = panelOpen && (
    <HolidayPanelContent
      key={panel.key}
      holiday={selectedHoliday}
      initialDate={panel.date}
      findByDate={(iso) => holidays.find((holiday) => holiday.date === iso) ?? null}
      titleId="holiday-panel-title"
      onClose={closePanel}
      onSaved={(saved) => {
        if (saved.date) {
          const [savedYear, savedMonth] = saved.date.split('-').map(Number);
          navigate(savedYear, savedMonth - 1);
        }
        closePanel();
      }}
      onEditExisting={openEdit}
      onDelete={setPendingDelete}
    />
  );

  return (
    <PageContainer
      title="Feriados"
      description="Los feriados del año bloquean clases y evaluaciones, y son requisito para crear períodos y cohortes."
      action={
        <Button
          type="button"
          variant={panelOpen ? 'outline' : 'gradient'}
          onClick={() => openCreate(defaultNewHolidayDate(year, month, today))}
        >
          <Plus />
          Agregar feriado
        </Button>
      }
    >
      {missingNextYear && (
        <div
          role="status"
          className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-2xl border border-warning/45 bg-warning-bg px-4 py-3 text-warning-ink"
        >
          <AlertTriangle aria-hidden="true" className="size-5 flex-none" />
          <p className="min-w-60 flex-1 text-sm font-medium">
            <strong className="font-bold">Faltan los feriados de {nextYear}.</strong> Sin ellos no se pueden crear períodos ni cohortes de ese año.
          </p>
          <Button type="button" variant="outline" className="border-warning-ink text-warning-ink" onClick={loadNextYear}>
            Cargar feriados de {nextYear}
          </Button>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-x-5 gap-y-3">
        <div className="inline-flex items-center rounded-full bg-neutral-bg p-1">
          <Button type="button" variant="ghost" size="icon-lg" className="rounded-full" aria-label="Año anterior" onClick={() => changeYear(-1)}>
            <ChevronLeft />
          </Button>
          <span aria-live="polite" className="min-w-20 text-center font-heading text-size-17 font-bold tabular-nums text-ink">{year}</span>
          <Button type="button" variant="ghost" size="icon-lg" className="rounded-full" aria-label="Año siguiente" onClick={() => changeYear(1)}>
            <ChevronRight />
          </Button>
        </div>
        {!loading && !holidaysQuery.isError && (
          <p className="text-sm text-ink-soft">{holidayCountLabel(holidays.length, year)}</p>
        )}
        <div className="flex flex-wrap gap-2 lg:ml-auto">
          {typeCounts.map((type) => <HolidayTypePill key={type.value} type={type.value} count={type.count} />)}
        </div>
      </div>

      {holidaysQuery.isError ? (
        <GlassCard className="p-4">
          <EmptyState
            icon="⚠️"
            title={`No pudimos cargar los feriados de ${year}`}
            description="Revisá tu conexión e intentá de nuevo."
            action={<Button type="button" variant="outline" onClick={() => holidaysQuery.refetch()}>Reintentar</Button>}
          />
        </GlassCard>
      ) : (
        <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_360px]">
          <div ref={calendarRef} className="min-w-0">
            <HolidayCalendar
              year={year}
              month={month}
              holidays={holidays}
              loading={loading}
              todayIso={todayIso}
              selectedIso={selectedIso}
              onSelect={selectDate}
              onNavigate={navigate}
            />
          </div>
          {isDesktop && panelOpen ? (
            <GlassCard
              role="complementary"
              aria-labelledby="holiday-panel-title"
              className="sticky top-4 flex max-h-[calc(100vh-2rem)] flex-col p-0"
            >
              {panelContent}
            </GlassCard>
          ) : (
            <div className="flex min-w-0 flex-col gap-2">
              {!isDesktop && (
                <Tabs value={scope} onValueChange={setScope}>
                  <TabsList variant="segmented" className="w-full">
                    <TabsTrigger value="month">Este mes</TabsTrigger>
                    <TabsTrigger value="year">Todo el año</TabsTrigger>
                  </TabsList>
                </Tabs>
              )}
              <HolidayList
                holidays={holidays}
                year={year}
                loading={loading}
                scopeMonth={!isDesktop && scope === 'month' ? month : null}
                selectedId={selectedHoliday?.id ?? null}
                onSelect={openEdit}
                emptyAction={
                  <Button type="button" variant="gradient" onClick={() => openCreate(defaultNewHolidayDate(year, month, today))}>
                    Cargar el primer feriado
                  </Button>
                }
              />
            </div>
          )}
        </div>
      )}

      {!isDesktop && (
        <Sheet open={panelOpen} onOpenChange={(open) => { if (!open) closePanel(); }}>
          <SheetContent side="bottom" showCloseButton={false} className="max-h-[90dvh] gap-0 rounded-t-3xl p-0" aria-describedby="holiday-sheet-description">
            <SheetTitle className="sr-only">Feriado</SheetTitle>
            <SheetDescription id="holiday-sheet-description" className="sr-only">
              Formulario para cargar o editar un feriado.
            </SheetDescription>
            {panelContent}
          </SheetContent>
        </Sheet>
      )}

      <DeleteHolidayDialog
        holiday={pendingDelete}
        deleting={deleteHoliday.isPending}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </PageContainer>
  );
}
