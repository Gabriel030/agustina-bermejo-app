import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Clock } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Paginador } from '@/components/Paginador';
import { StatusPill } from '@/components/StatusPill';
import { Breadcrumb } from '@/components/Breadcrumb';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { DataTable } from '@/components/table/DataTable';
import { useReviewBasePath } from '@/features/enrollment/hooks/useReviewBasePath';
import { WAITLIST_PAGE_SIZE, useWaitlists } from '@/features/enrollment/hooks/useWaitlists';
import {
  EXPIRING_WITHIN_DAYS,
  WAITLIST_KIND_LABEL,
  WAITLIST_KIND_OPTIONS,
  waitlistPath,
} from '@/features/enrollment/lib/waitlist';
import { formatearFecha } from '@/lib/comisiones';

function CapacityCell({ queue }) {
  if (queue.capacity == null) return <span className="tabular-nums text-ink">{queue.occupied} · sin límite</span>;
  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="tabular-nums text-ink">
        {queue.occupied} / {queue.capacity}
      </span>
      {queue.free > 0 && (
        <StatusPill tone="success" dot>
          {queue.free === 1 ? '1 lugar libre' : `${queue.free} lugares libres`}
        </StatusPill>
      )}
    </div>
  );
}

function ExpiryCell({ queue }) {
  if (!queue.nextExpiry) return <span className="text-ink-faint">—</span>;
  return <span className="tabular-nums text-size-13 text-ink">{formatearFecha(queue.nextExpiry)}</span>;
}

function buildColumns() {
  return [
    {
      id: 'name',
      header: 'Comisión o curso',
      accessorFn: (queue) => queue.name,
      enableSorting: false,
      meta: { stickyStart: true, cardTitle: true, headerClassName: 'min-w-[200px]' },
      cell: ({ row }) => (
        <span className="whitespace-normal font-heading text-size-14 font-semibold leading-tight text-ink">{row.original.name}</span>
      ),
    },
    {
      id: 'kind',
      header: 'Tipo',
      accessorFn: (queue) => WAITLIST_KIND_LABEL[queue.kind] ?? queue.kind,
      enableSorting: false,
      cell: ({ row }) => <span className="text-size-13 text-ink">{WAITLIST_KIND_LABEL[row.original.kind] ?? row.original.kind}</span>,
    },
    {
      id: 'term',
      header: 'Período',
      accessorFn: (queue) => queue.termName,
      enableSorting: false,
      cell: ({ row }) => <span className="text-size-13 text-ink-soft">{row.original.termName}</span>,
    },
    {
      id: 'capacity',
      header: 'Cupo',
      accessorFn: (queue) => queue.occupied,
      enableSorting: false,
      cell: ({ row }) => <CapacityCell queue={row.original} />,
    },
    {
      id: 'expiry',
      header: 'Vence',
      accessorFn: (queue) => queue.nextExpiry ?? '',
      enableSorting: false,
      cell: ({ row }) => <ExpiryCell queue={row.original} />,
    },
    {
      id: 'waiting',
      header: 'Esperando',
      accessorFn: (queue) => queue.waiting,
      enableSorting: false,
      meta: { headerClassName: 'text-right' },
      cell: ({ row }) => <span className="font-heading text-xl font-bold tabular-nums text-ink">{row.original.waiting}</span>,
    },
  ];
}

export function ListasDeEspera() {
  const navigate = useNavigate();
  const basePath = useReviewBasePath();
  const [kind, setKind] = useState('ALL');
  const [expiringSoon, setExpiringSoon] = useState(false);
  const [page, setPage] = useState(1);
  const waitlists = useWaitlists({
    kind,
    expiringWithinDays: expiringSoon ? EXPIRING_WITHIN_DAYS : null,
    page: page - 1,
  });
  const columns = useMemo(() => buildColumns(), []);
  const totalPages = Math.max(1, Math.ceil((waitlists.data?.totalElements ?? 0) / WAITLIST_PAGE_SIZE));
  const isFiltered = kind !== 'ALL' || expiringSoon;

  function clearFilters() {
    setKind('ALL');
    setExpiringSoon(false);
    setPage(1);
  }

  return (
    <PageContainer
      title="Listas de espera"
      description="Quién espera lugar, en qué orden y hasta cuándo."
    >
      <Breadcrumb items={[{ label: 'Inscripciones' }, { label: 'Listas de espera' }]} />
      <GlassCard className="flex flex-col gap-3 p-3 md:p-4">
        <div className="flex flex-wrap items-center gap-3">
          <Select
            value={kind}
            onValueChange={(next) => {
              setKind(next);
              setPage(1);
            }}
          >
            <SelectTrigger aria-label="Filtrar por tipo" className="h-11 w-auto min-w-44 data-[size=default]:h-11 md:h-8 md:data-[size=default]:h-8">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {WAITLIST_KIND_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button
            type="button"
            variant={expiringSoon ? 'default' : 'outline'}
            className="h-11 md:h-8"
            aria-pressed={expiringSoon}
            onClick={() => {
              setExpiringSoon((current) => !current);
              setPage(1);
            }}
          >
            <Clock className="h-4 w-4" aria-hidden="true" />
            Vence en {EXPIRING_WITHIN_DAYS} días
          </Button>
        </div>
        <DataTable
          compact
          mobileLayout="scroll"
          columns={columns}
          data={waitlists.data?.items}
          getRowId={(queue) => `${queue.kind}-${queue.id}`}
          minWidth={760}
          isLoading={waitlists.isLoading}
          isError={waitlists.isError}
          onRetry={waitlists.refetch}
          errorMessage="No pudimos cargar las listas de espera"
          onRowClick={(queue) => navigate(`${basePath}/${waitlistPath(queue.kind, queue.id)}`)}
          emptyState={
            isFiltered ? (
              <EmptyState
                title="Ninguna lista coincide"
                description="Probá quitar algún filtro."
                action={<Button variant="outline" onClick={clearFilters}>Quitar filtros</Button>}
              />
            ) : (
              <EmptyState
                icon="📋"
                title="Nadie está esperando"
                description="Cuando una comisión se llene y alguien se anote, aparece acá."
              />
            )
          }
        />
        <Paginador page={page} totalPages={totalPages} onChange={setPage} />
      </GlassCard>
    </PageContainer>
  );
}
