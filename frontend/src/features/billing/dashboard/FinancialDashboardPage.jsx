import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { AlertCircle, SlidersHorizontal } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group';
import { useDebtors, useFinancialDashboard } from '@/features/billing/hooks/useFinancialDashboard';
import { FEE_TYPE_LABELS } from '@/lib/billing';
import { formatMoney } from '@/lib/utils';
import { DEFAULT_RANGE, RANGE_OPTIONS, formatShortMonth, rangeFor } from './dashboardMetrics';
import { DASHBOARD_TABS, FINANCIAL_PANELS } from './financialPanels';
import { MonthKpis, TodayKpis } from './KpiBlocks';
import { MetricPanel } from './MetricPanel';

const ALL_TYPES = 'ALL';
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

function DebtorsTable({ data, isLoading }) {
  const navigate = useNavigate();
  const rolePrefix = useLocation().pathname.split('/')[1];

  if (isLoading) {
    return (
      <div className="flex flex-col gap-2 p-4">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-12" />
        ))}
      </div>
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
            onClick={() => navigate(`/${rolePrefix}/alumnos/${d.studentId}`)}
          >
            <TableCell className="pl-4 font-medium text-ink">{d.fullName}</TableCell>
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
  const { data, isLoading } = useDebtors(sortBy);

  return (
    <GlassCard>
      <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-3">
        <div className="flex items-center gap-2">
          <AlertCircle className="size-4 text-danger" />
          <span className="font-heading text-[14.5px] font-semibold text-ink">Alumnos con deuda vencida</span>
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
      <DebtorsTable data={data} isLoading={isLoading} />
    </GlassCard>
  );
}

export function FinancialDashboardPage() {
  const dashboard = useFinancialDashboard();
  const isDirector = useLocation().pathname.startsWith('/director');
  const [range, setRange] = useState(DEFAULT_RANGE);
  const [invoiceType, setInvoiceType] = useState(ALL_TYPES);
  const [pickedCurrency, setPickedCurrency] = useState(null);
  const [pickedMonth, setPickedMonth] = useState(null);

  const currencies = (dashboard.data?.totalsByCurrency ?? []).map((totals) => totals.totalDebt?.currency).filter(Boolean);
  const currency = pickedCurrency ?? currencies[0];
  const { from, to } = rangeFor(range);
  const filters = { from, to, currency, invoiceType: invoiceType === ALL_TYPES ? undefined : invoiceType };
  const months = [];
  for (let month = from; month <= to; month = nextMonth(month)) months.push(month);
  const month = pickedMonth && months.includes(pickedMonth) ? pickedMonth : to;
  const activeFilters = [range !== DEFAULT_RANGE, invoiceType !== ALL_TYPES].filter(Boolean).length;
  const ready = !dashboard.isLoading;

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
        <PopoverContent align="end" className="flex w-72 flex-col gap-4 bg-dialog-bg p-4">
          <FilterSelect id="range" label="Período" value={range} onChange={setRange} options={RANGE_OPTIONS} />
          <FilterSelect id="invoice-type" label="Tipo de factura" value={invoiceType} onChange={setInvoiceType} options={INVOICE_TYPE_OPTIONS} />
          {activeFilters > 0 && (
            <Button
              variant="ghost"
              size="sm"
              className="self-start"
              onClick={() => {
                setRange(DEFAULT_RANGE);
                setInvoiceType(ALL_TYPES);
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
    <PageContainer title="Financiero" action={toolbar} className="gap-8">
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

      <DebtorsSection />
    </PageContainer>
  );
}

function nextMonth(month) {
  const [year, number] = month.split('-').map(Number);
  return number === 12 ? `${year + 1}-01` : `${year}-${String(number + 1).padStart(2, '0')}`;
}
