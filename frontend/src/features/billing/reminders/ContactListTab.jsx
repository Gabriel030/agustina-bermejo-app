import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { Buscador } from '@/components/Buscador';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { Paginador } from '@/components/Paginador';
import { StatusPill } from '@/components/StatusPill';
import { DataTable } from '@/components/table/DataTable';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { formatDate, formatMoney } from '@/lib/billing';
import { useContactList, useReminderSettingsVersions } from '@/features/billing/hooks/useCollectionReminders';
import {
  ENTRY_REASON_LABELS,
  criterionText,
  daysBucket,
  daysBucketLabel,
  daysTone,
  installmentsBucket,
  installmentsBucketLabel,
  lastReminderBucket,
  lastReminderBucketLabel,
  matchesSearch,
} from './contactList';

const PAGE_SIZE = 100;
const ALL_CURRENCIES = 'ALL';

function buildContactColumns(accountBase) {
  return [
    {
      id: 'student',
      header: 'Alumno',
      accessorFn: (item) => item.studentName,
      cell: ({ row }) => (
        <div className="flex flex-col gap-0.5">
          <span className="flex flex-wrap items-center gap-2 font-semibold text-ink">
            {row.original.studentName}
            {row.original.newlyListed && <StatusPill tone="accent">Nuevo</StatusPill>}
          </span>
          <span className="text-xs text-ink-faint">DNI {row.original.dni}</span>
        </div>
      ),
      meta: { cardTitle: true },
    },
    {
      id: 'daysOverdue',
      header: 'Vencida hace',
      accessorFn: (item) => daysBucket(item.oldestDaysOverdue),
      sortingFn: (a, b) => a.original.oldestDaysOverdue - b.original.oldestDaysOverdue,
      cell: ({ row }) => (
        <StatusPill tone={daysTone(row.original.oldestDaysOverdue)}>{row.original.oldestDaysOverdue} días</StatusPill>
      ),
      meta: { filterable: true, filterTitle: 'días de atraso', filterLabel: daysBucketLabel },
    },
    {
      id: 'installments',
      header: 'Cuotas vencidas',
      accessorFn: (item) => installmentsBucket(item.overdueInstallments),
      sortingFn: (a, b) => a.original.overdueInstallments - b.original.overdueInstallments,
      cell: ({ row }) => <span className="tabular-nums">{row.original.overdueInstallments}</span>,
      meta: { filterable: true, filterTitle: 'cuotas vencidas', filterLabel: installmentsBucketLabel, align: 'right' },
    },
    {
      id: 'debt',
      header: 'Deuda vencida',
      enableSorting: false,
      accessorFn: (item) => item.overdueAmounts.map(formatMoney).join(' · '),
      cell: ({ row }) => (
        <div className="flex flex-col gap-0.5 tabular-nums">
          {row.original.overdueAmounts.map((money) => (
            <span key={money.currency} className="font-semibold text-ink">
              {formatMoney(money)}
            </span>
          ))}
        </div>
      ),
      meta: { align: 'right' },
    },
    {
      id: 'entryReason',
      header: 'Motivo',
      accessorFn: (item) => item.entryReason,
      cell: ({ row }) => <span className="text-ink-soft">{ENTRY_REASON_LABELS[row.original.entryReason]}</span>,
      meta: { filterable: true, filterTitle: 'motivo', filterLabel: (value) => ENTRY_REASON_LABELS[value] },
    },
    {
      id: 'lastReminder',
      header: 'Último aviso',
      accessorFn: lastReminderBucket,
      sortingFn: (a, b) => (a.original.lastReminderDate ?? '').localeCompare(b.original.lastReminderDate ?? ''),
      cell: ({ row }) =>
        row.original.lastReminderDate ? formatDate(row.original.lastReminderDate) : <span className="text-ink-faint">Nunca</span>,
      meta: { filterable: true, filterTitle: 'último aviso', filterLabel: lastReminderBucketLabel },
    },
    {
      id: 'account',
      header: '',
      enableSorting: false,
      cell: ({ row }) => (
        <Link
          to={`${accountBase}/facturacion?alumno=${row.original.studentId}`}
          className="text-sm font-semibold text-primary hover:underline"
        >
          Cuenta corriente
        </Link>
      ),
    },
  ];
}

function CriterionBar() {
  const { data } = useReminderSettingsVersions();
  const { usuario } = useAuth();
  const effective = data?.find((version) => version.status === 'EFFECTIVE');
  const text = criterionText(effective?.values);
  if (!text) return null;
  const base = usuario?.roles?.includes('DIRECTOR') ? '/director' : '/administrativo';
  return (
    <p className="text-sm text-ink-soft">
      Se listan los alumnos con {text}.{' '}
      <Link to={`${base}/recordatorios-de-pago?tab=GENERAL`} className="font-semibold text-primary hover:underline">
        Cambiar criterio
      </Link>
    </p>
  );
}

export function ContactListTab({ accountBase }) {
  const [page, setPage] = useState(1);
  const [currency, setCurrency] = useState(ALL_CURRENCIES);
  const [onlyNew, setOnlyNew] = useState(false);
  const [search, setSearch] = useState('');
  const list = useContactList({
    page: page - 1,
    size: PAGE_SIZE,
    currency: currency === ALL_CURRENCIES ? undefined : currency,
    onlyNewlyListed: onlyNew,
  });
  const columns = useMemo(() => buildContactColumns(accountBase), [accountBase]);
  const items = useMemo(() => list.data?.items ?? [], [list.data]);
  const visible = useMemo(() => items.filter((item) => matchesSearch(item, search)), [items, search]);
  const total = list.data?.totalElements ?? 0;
  const hasServerFilters = currency !== ALL_CURRENCIES || onlyNew;

  function resetFilters() {
    setCurrency(ALL_CURRENCIES);
    setOnlyNew(false);
    setSearch('');
    setPage(1);
  }

  const emptyState =
    items.length === 0 && !hasServerFilters ? (
      <EmptyState icon="✅" title="Nadie para contactar hoy" description="Ningún alumno cumple el criterio de contacto." />
    ) : (
      <EmptyState
        title="Ningún resultado con estos filtros"
        action={
          <Button variant="outline" size="sm" onClick={resetFilters}>
            Limpiar filtros
          </Button>
        }
      />
    );

  return (
    <div className="flex flex-col gap-4">
      <CriterionBar />
      <div className="flex flex-wrap items-center gap-3">
        <Buscador value={search} onChange={setSearch} placeholder="Buscar por nombre o DNI" />
        <Select
          value={currency}
          onValueChange={(value) => {
            setCurrency(value);
            setPage(1);
          }}
        >
          <SelectTrigger aria-label="Moneda" className="w-40">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_CURRENCIES}>Todas las monedas</SelectItem>
            <SelectItem value="ARS">Pesos (ARS)</SelectItem>
            <SelectItem value="USD">Dólares (USD)</SelectItem>
          </SelectContent>
        </Select>
        <div className="flex items-center gap-2">
          <Switch
            id="only-newly-listed"
            checked={onlyNew}
            onCheckedChange={(checked) => {
              setOnlyNew(checked);
              setPage(1);
            }}
          />
          <Label htmlFor="only-newly-listed">Solo los nuevos</Label>
        </div>
      </div>
      <GlassCard className="p-0">
        <DataTable
          columns={columns}
          data={visible}
          getRowId={(item) => String(item.studentId)}
          isLoading={list.isLoading}
          isError={list.isError}
          onRetry={list.refetch}
          errorMessage="No pudimos cargar la lista de alumnos para contactar."
          emptyState={emptyState}
          initialSorting={[{ id: 'daysOverdue', desc: true }]}
          minWidth={880}
        />
      </GlassCard>
      {total > items.length && (
        <p className="text-xs text-ink-faint">
          Mostrando {items.length} de {total} alumnos. Los filtros de las columnas se aplican sobre esta página.
        </p>
      )}
      <Paginador page={page} totalPages={Math.ceil(total / PAGE_SIZE)} onChange={setPage} />
    </div>
  );
}
