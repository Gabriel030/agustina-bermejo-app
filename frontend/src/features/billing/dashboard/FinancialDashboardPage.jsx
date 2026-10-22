import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { AlertCircle, Info, SlidersHorizontal, X } from 'lucide-react';
import { toast } from 'sonner';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group';
import { useCourses } from '@/features/academic-structure/hooks/useCourses';
import { usePrograms } from '@/features/academic-structure/hooks/usePrograms';
import { useDebtors, useFinancialDashboard } from '@/features/billing/hooks/useFinancialDashboard';
import { FEE_TYPE_LABELS } from '@/lib/billing';
import { formatMoney } from '@/lib/utils';
import { DEFAULT_RANGE, RANGE_OPTIONS, formatShortMonth, rangeFor } from './dashboardMetrics';
import { DASHBOARD_TABS, FINANCIAL_PANELS } from './financialPanels';
import { MonthKpis, TodayKpis } from './KpiBlocks';
import { MetricPanel } from './MetricPanel';

const ALL_TYPES = 'ALL';
const ALL_SCOPE = 'ALL';
const INVOICE_TYPE_OPTIONS = [
  { value: ALL_TYPES, label: 'Todos los tipos' },
  ...Object.entries(FEE_TYPE_LABELS).map(([value, label]) => ({ value, label })),
];

const SORT_OPTIONS = [
  { value: 'DEBT_AGE', label: 'Antigüedad de deuda' },
  { value: 'DEBT_AMOUNT', label: 'Monto de deuda' },
];

function FilterSelect({ id, label, value, onChange, options }) {
  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <Label htmlFor={id} className="text-[11px] font-bold uppercase tracking-wide text-ink-faint">
        {label}
      </Label>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger id={id} className="h-10 w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent position="popper" className="bg-dialog-bg">
          {options.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}

function OfferSelect({ value, onChange, programs, courses }) {
  const loading = programs.isLoading || courses.isLoading;
  const empty = !loading && !programs.data?.length && !courses.data?.length;
  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <div className="flex items-center gap-1.5">
        <Label htmlFor="offer" className="text-[11px] font-bold uppercase tracking-wide text-ink-faint">
          Oferta
        </Label>
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              <button type="button" aria-label="Qué limita la oferta" className="text-ink-faint hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
                <Info className="size-3.5" />
              </button>
            </TooltipTrigger>
            <TooltipContent>Limita los números a una carrera o a un curso.</TooltipContent>
          </Tooltip>
        </TooltipProvider>
      </div>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger id="offer" className="h-10 w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent position="popper" className="max-h-72 bg-dialog-bg">
          <SelectItem value={ALL_SCOPE}>Todas las ofertas</SelectItem>
          {loading && (
            <SelectItem value="loading" disabled>
              Cargando ofertas…
            </SelectItem>
          )}
          {empty && (
            <SelectItem value="empty" disabled>
              No hay carreras ni cursos cargados
            </SelectItem>
          )}
          {!!programs.data?.length && (
            <SelectGroup>
              <SelectLabel>Carreras</SelectLabel>
              {programs.data.map((program) => (
                <SelectItem key={`program:${program.id}`} value={`program:${program.id}`}>
                  {program.name}
                </SelectItem>
              ))}
            </SelectGroup>
          )}
          {!!courses.data?.length && (
            <SelectGroup>
              <SelectLabel>Cursos</SelectLabel>
              {courses.data.map((course) => (
                <SelectItem key={`course:${course.id}`} value={`course:${course.id}`}>
                  {course.name}
                </SelectItem>
              ))}
            </SelectGroup>
          )}
        </SelectContent>
      </Select>
    </div>
  );
}

function ScopeChip({ label, onClear }) {
  return (
    <div className="inline-flex min-w-0 max-w-full items-center self-start rounded-full border border-border bg-surface pl-3 text-sm text-ink">
      <span className="truncate">{label}</span>
      <button
        type="button"
        aria-label="Quitar filtro de oferta"
        onClick={onClear}
        className="-my-1.5 flex size-11 shrink-0 items-center justify-center text-ink-soft hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <X className="size-4" />
      </button>
    </div>
  );
}

function DebtorsTable({ data, isLoading, isError, onRetry }) {
  const navigate = useNavigate();
  const rolePrefix = useLocation().pathname.split('/')[1];
  const openStudent = (studentId) => navigate(`/${rolePrefix}/alumnos/${studentId}`);

  if (isLoading) {
    return (
      <div className="flex flex-col gap-2 p-4">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-12" />
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <EmptyState
        title="No se pudo cargar"
        className="py-10"
        action={
          <Button type="button" variant="outline" size="sm" onClick={onRetry}>
            Reintentar
          </Button>
        }
      />
    );
  }

  if (!data?.length) {
    return <EmptyState title="Sin alumnos con deuda vencida" className="py-10" />;
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="pl-4">Alumno</TableHead>
          <TableHead>DNI</TableHead>
          <TableHead>Inscripción</TableHead>
          <TableHead className="text-right">Deuda</TableHead>
          <TableHead className="pr-4 text-right">Vencida hace</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {data.map((d) => (
          <TableRow
            key={`${d.studentId}-${d.totalDebt.currency}`}
            className="cursor-pointer"
            onClick={() => openStudent(d.studentId)}
          >
            <TableCell className="pl-4 font-medium text-ink">
              <button
                type="button"
                className="text-left hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                onClick={(event) => {
                  event.stopPropagation();
                  openStudent(d.studentId);
                }}
              >
                {d.fullName}
              </button>
            </TableCell>
            <TableCell className="text-ink-soft">{d.dni}</TableCell>
            <TableCell className="text-ink-soft">{d.enrollmentName}</TableCell>
            <TableCell className="text-right font-semibold text-danger">{formatMoney(d.totalDebt) || '—'}</TableCell>
            <TableCell className="pr-4 text-right text-ink-soft">
              {d.debtAgeDays === 0 ? 'Hoy' : `${d.debtAgeDays} día${d.debtAgeDays !== 1 ? 's' : ''}`}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function DebtorsSection() {
  const [sortBy, setSortBy] = useState('DEBT_AGE');
  const { data, isLoading, isError, refetch } = useDebtors(sortBy);

  return (
    <GlassCard>
      <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-3">
        <div className="flex items-center gap-2">
          <AlertCircle className="size-4 text-danger" />
          <span className="font-heading text-[14.5px] font-semibold text-ink">Alumnos con deuda vencida</span>
          <span className="text-xs text-ink-soft">· todas las monedas</span>
        </div>
        <select
          aria-label="Ordenar por"
          value={sortBy}
          onChange={(e) => setSortBy(e.target.value)}
          className="rounded-md border border-border bg-surface px-2.5 py-1.5 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-primary/40"
        >
          {SORT_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </div>
      <DebtorsTable data={data} isLoading={isLoading} isError={isError} onRetry={() => refetch()} />
    </GlassCard>
  );
}

export function FinancialDashboardPage() {
  const dashboard = useFinancialDashboard();
  const isDirector = useLocation().pathname.startsWith('/director');
  const [range, setRange] = useState(DEFAULT_RANGE);
  const [invoiceType, setInvoiceType] = useState(ALL_TYPES);
  const [scope, setScope] = useState(ALL_SCOPE);
  const programs = usePrograms();
  const courses = useCourses();
  const offersFailed = programs.isError || courses.isError;
  useEffect(() => {
    if (offersFailed) toast.error('No se pudieron cargar las carreras y cursos');
  }, [offersFailed]);
  const [pickedCurrency, setPickedCurrency] = useState(null);
  const [pickedMonth, setPickedMonth] = useState(null);
  const [ready, setReady] = useState(false);
  if (!ready && !dashboard.isLoading) setReady(true);

  const currencies = (dashboard.data?.totalsByCurrency ?? []).map((totals) => totals.totalDebt?.currency).filter(Boolean);
  const currency = pickedCurrency ?? currencies[0];
  const { from, to } = rangeFor(range);
  const [scopeKind, scopeId] = scope.split(':');
  const offerName = (scopeKind === 'program' ? programs.data : courses.data)?.find((offer) => String(offer.id) === scopeId)?.name;
  const scopeLabel = scope === ALL_SCOPE ? null : `${scopeKind === 'program' ? 'Carrera' : 'Curso'}: ${offerName ?? '…'}`;
  const filters = {
    from,
    to,
    currency,
    invoiceType: invoiceType === ALL_TYPES ? undefined : invoiceType,
    programId: scopeKind === 'program' ? scopeId : undefined,
    courseId: scopeKind === 'course' ? scopeId : undefined,
  };
  const months = [];
  for (let month = from; month <= to; month = nextMonth(month)) months.push(month);
  const month = pickedMonth && months.includes(pickedMonth) ? pickedMonth : to;
  const activeFilters = [range !== DEFAULT_RANGE, invoiceType !== ALL_TYPES, scope !== ALL_SCOPE].filter(Boolean).length;

  const toolbar = (
    <div className="flex w-full flex-wrap items-center gap-2 sm:w-auto">
      <Select value={month} onValueChange={setPickedMonth}>
        <SelectTrigger aria-label="Mes" className="h-10 min-w-0 flex-1 font-semibold sm:w-32 sm:flex-none">
          <SelectValue />
        </SelectTrigger>
        <SelectContent position="popper" className="bg-dialog-bg">
          {[...months].reverse().map((value) => (
            <SelectItem key={value} value={value}>
              {formatShortMonth(value)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {currencies.length > 1 && (
        <ToggleGroup
          type="single"
          variant="outline"
          aria-label="Moneda"
          value={currency}
          onValueChange={(value) => value && setPickedCurrency(value)}
        >
          {currencies.map((value) => (
            <ToggleGroupItem key={value} value={value} className="h-10 px-3">
              {value}
            </ToggleGroupItem>
          ))}
        </ToggleGroup>
      )}
      <Popover>
        <PopoverTrigger asChild>
          <Button variant="outline" className="h-10" aria-label={activeFilters ? `Filtros, ${activeFilters} activos` : 'Filtros'}>
            <SlidersHorizontal />
            <span className="hidden sm:inline">Filtros</span>
            {activeFilters > 0 && (
              <span className="rounded-full bg-primary px-1.5 text-[11px] font-bold leading-[18px] text-white">{activeFilters}</span>
            )}
          </Button>
        </PopoverTrigger>
        <PopoverContent align="end" className="flex w-72 max-w-[calc(100vw-2rem)] flex-col gap-4 bg-dialog-bg p-4">
          <FilterSelect id="range" label="Período" value={range} onChange={setRange} options={RANGE_OPTIONS} />
          <FilterSelect id="invoice-type" label="Tipo de factura" value={invoiceType} onChange={setInvoiceType} options={INVOICE_TYPE_OPTIONS} />
          <OfferSelect value={scope} onChange={setScope} programs={programs} courses={courses} />
          {activeFilters > 0 && (
            <Button
              variant="ghost"
              size="sm"
              className="self-start"
              onClick={() => {
                setRange(DEFAULT_RANGE);
                setInvoiceType(ALL_TYPES);
                setScope(ALL_SCOPE);
              }}
            >
              Quitar filtros
            </Button>
          )}
        </PopoverContent>
      </Popover>
    </div>
  );

  return (
    <PageContainer title="Financiero" description={offerName ?? (scope === ALL_SCOPE ? 'Todas las ofertas' : scopeLabel)} action={toolbar} className="gap-8">
      {scopeLabel && <ScopeChip label={scopeLabel} onClear={() => setScope(ALL_SCOPE)} />}
      {ready ? (
        <>
          <MonthKpis filters={filters} month={month} />
          <TodayKpis currency={currency} />
        </>
      ) : (
        <Skeleton className="h-40 w-full" />
      )}

      {ready && (
        <Tabs defaultValue={DASHBOARD_TABS[0].value}>
          <TabsList variant="segmented">
            {DASHBOARD_TABS.map((tab) => (
              <TabsTrigger key={tab.value} value={tab.value} className="px-3">
                {tab.label}
              </TabsTrigger>
            ))}
          </TabsList>
          {DASHBOARD_TABS.map((tab) => (
            <TabsContent key={tab.value} value={tab.value} className="mt-6">
              <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
                {FINANCIAL_PANELS.filter((panel) => panel.tab === tab.value).map((panel) => (
                  <MetricPanel key={panel.id} panel={panel} filters={filters} month={month} />
                ))}
              </div>
            </TabsContent>
          ))}
        </Tabs>
      )}

      {isDirector && (
        <Link to="/director/financiero/anios-lectivos" className="text-sm font-semibold text-primary hover:underline">
          Configurar el cobro de los años lectivos por carrera
        </Link>
      )}

      {isDirector && <DebtorsSection />}
    </PageContainer>
  );
}

function nextMonth(month) {
  const [year, number] = month.split('-').map(Number);
  return number === 12 ? `${year + 1}-01` : `${year}-${String(number + 1).padStart(2, '0')}`;
}
