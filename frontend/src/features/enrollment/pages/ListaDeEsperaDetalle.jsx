import { useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Eye, Info } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Paginador } from '@/components/Paginador';
import { StatCard } from '@/components/StatCard';
import { StatusPill } from '@/components/StatusPill';
import { Breadcrumb } from '@/components/Breadcrumb';
import { Skeleton } from '@/components/Skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { DataTable } from '@/components/table/DataTable';
import { useActiveRole } from '@/hooks/useActiveRole';
import { WaitlistEntryMenu } from '@/features/enrollment/components/WaitlistEntryMenu';
import { WaitlistVerdictPill } from '@/features/enrollment/components/WaitlistVerdictPill';
import {
  WAITLIST_HISTORY_PAGE_SIZE,
  useWaitlistHistory,
  useWaitlistQueue,
} from '@/features/enrollment/hooks/useWaitlists';
import {
  WAITLIST_BASE_SLUG,
  WAITLIST_KIND_LABEL,
  firstEligiblePosition,
  historyActionPresentation,
} from '@/features/enrollment/lib/waitlist';
import { formatearFecha } from '@/lib/comisiones';

const KINDS = Object.keys(WAITLIST_KIND_LABEL);

function formatDateTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('es-AR', { day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false });
}

function buildColumns(nextPosition, queue, canAct) {
  const columns = [
    {
      id: 'position',
      header: 'Pos.',
      accessorFn: (entry) => entry.position,
      enableSorting: false,
      cell: ({ row }) => (
        <span className="inline-flex items-center gap-2">
          <span className="inline-grid h-7 min-w-9 place-items-center rounded-md bg-neutral-bg px-1.5 font-semibold tabular-nums text-ink">
            {row.original.position}
          </span>
          {row.original.position === nextPosition && <StatusPill tone="accent">Próximo</StatusPill>}
        </span>
      ),
    },
    {
      id: 'student',
      header: 'Alumno',
      accessorFn: (entry) => entry.student.fullName,
      enableSorting: false,
      meta: { cardTitle: true, headerClassName: 'min-w-[180px]' },
      cell: ({ row }) => (
        <div className="flex min-w-0 flex-col items-start gap-0.5 whitespace-normal">
          <span className="font-heading text-size-14 font-semibold leading-tight text-ink">{row.original.student.fullName}</span>
          {row.original.student.documentNumber && (
            <span className="text-size-12 tabular-nums text-ink-faint">DNI {row.original.student.documentNumber}</span>
          )}
        </div>
      ),
    },
    {
      id: 'joinedAt',
      header: 'Se anotó',
      accessorFn: (entry) => entry.joinedAt,
      enableSorting: false,
      cell: ({ row }) => <span className="whitespace-nowrap text-size-13 tabular-nums text-ink-soft">{formatDateTime(row.original.joinedAt)}</span>,
    },
    {
      id: 'verdict',
      header: 'Requisitos',
      accessorFn: (entry) => entry.verdict.reason ?? 'OK',
      enableSorting: false,
      cell: ({ row }) => (
        <span className="inline-flex flex-wrap items-center gap-1.5">
          <WaitlistVerdictPill verdict={row.original.verdict} />
          {row.original.prioritizationOrder != null && (
            <StatusPill tone="accent">{`Prioridad manual ${row.original.prioritizationOrder}`}</StatusPill>
          )}
        </span>
      ),
    },
    {
      id: 'expiresOn',
      header: 'Vence',
      accessorFn: (entry) => entry.expiresOn ?? '',
      enableSorting: false,
      cell: ({ row }) =>
        row.original.expiresOn ? (
          <span className="text-size-13 tabular-nums text-ink">{formatearFecha(row.original.expiresOn)}</span>
        ) : (
          <span className="text-ink-faint">—</span>
        ),
    },
  ];
  if (!canAct) return columns;
  return [
    ...columns,
    {
      id: 'actions',
      header: '',
      enableSorting: false,
      cell: ({ row }) => <WaitlistEntryMenu queue={queue} entry={row.original} />,
    },
  ];
}

function QueueTab({ queue, canAct }) {
  const nextPosition = firstEligiblePosition(queue.entries);
  const columns = useMemo(() => buildColumns(nextPosition, queue, canAct), [nextPosition, queue, canAct]);
  return (
    <GlassCard className="p-3 md:p-4">
      <DataTable
        compact
        mobileLayout="cards"
        columns={columns}
        data={queue.entries}
        getRowId={(entry) => String(entry.enrollmentId)}
        minWidth={760}
        emptyState={<EmptyState icon="👥" title="La cola está vacía" description="No hay personas esperando en esta lista." />}
      />
    </GlassCard>
  );
}

const HISTORY_TONE_DOT = {
  danger: 'bg-danger',
  accent: 'bg-brand',
  success: 'bg-success',
  warning: 'bg-warning',
  neutral: 'bg-neutral-ink',
};

function HistoryTab({ kind, id, active }) {
  const [page, setPage] = useState(1);
  const history = useWaitlistHistory(kind, id, page - 1, active);
  const totalPages = Math.max(1, Math.ceil((history.data?.totalElements ?? 0) / WAITLIST_HISTORY_PAGE_SIZE));
  if (history.isLoading) return <GlassCard className="p-4"><Skeleton className="h-24 w-full" /></GlassCard>;
  if (history.isError) return <LoadErrorState error={history.error} onRetry={history.refetch} resource="el historial" />;
  const items = history.data?.items ?? [];
  if (items.length === 0) {
    return (
      <GlassCard className="p-4">
        <EmptyState icon="🕘" title="Todavía no hay movimientos" description="Las bajas y los cambios de esta lista quedan registrados acá." />
      </GlassCard>
    );
  }
  return (
    <GlassCard className="flex flex-col gap-2 p-3 md:p-4">
      <ul className="flex flex-col divide-y divide-border">
        {items.map((entry, index) => {
          const action = historyActionPresentation(entry.action);
          return (
            <li key={`${entry.at}-${index}`} className="flex flex-wrap items-start gap-3 py-3">
              <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${HISTORY_TONE_DOT[action.tone]}`} aria-hidden="true" />
              <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                <span className="font-semibold text-ink">
                  {action.label}
                  {entry.studentName ? `: ${entry.studentName}` : ''}
                </span>
                <span className="text-size-13 text-ink-soft">{entry.actorName ?? 'Sistema'}</span>
                {entry.reason && <span className="text-size-13 text-ink-soft">Motivo: {entry.reason}</span>}
              </div>
              <span className="whitespace-nowrap text-size-12 tabular-nums text-ink-faint">{formatDateTime(entry.at)}</span>
            </li>
          );
        })}
      </ul>
      <Paginador page={page} totalPages={totalPages} onChange={setPage} />
    </GlassCard>
  );
}

function Notice({ icon: Icon, tone, children }) {
  const toneClass = tone === 'info' ? 'bg-accent-soft text-primary' : 'bg-success-bg text-pill-success';
  return (
    <div role="status" className={`flex items-start gap-2 rounded-md px-3 py-2.5 text-size-13 ${toneClass}`}>
      <Icon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
      <span>{children}</span>
    </div>
  );
}

export function ListaDeEsperaDetalle() {
  const { kind, id } = useParams();
  const { activeRole, roleBase } = useActiveRole();
  const [tab, setTab] = useState('queue');
  const validKind = KINDS.includes(kind);
  const queue = useWaitlistQueue(validKind ? kind : null, id);
  const listPath = `/${roleBase}/${WAITLIST_BASE_SLUG}`;

  if (!validKind) {
    return <LoadErrorState error={{ status: 404 }} backTo={listPath} backLabel="las listas de espera" />;
  }
  if (queue.isLoading) {
    return (
      <PageContainer title="Lista de espera">
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-48 w-full" />
      </PageContainer>
    );
  }
  if (queue.isError) {
    return <LoadErrorState error={queue.error} onRetry={queue.refetch} resource="la lista de espera" backTo={listPath} backLabel="las listas de espera" />;
  }

  const data = queue.data;
  const isReadOnly = activeRole === 'PRECEPTOR';
  const hasFreeSeat = data.free != null && data.free > 0 && data.waiting > 0;

  return (
    <PageContainer
      title={data.name}
      description={`${WAITLIST_KIND_LABEL[data.kind]} · ${data.termName}${data.nextExpiry ? ` · la espera vence el ${formatearFecha(data.nextExpiry)}` : ''}`}
    >
      <Breadcrumb
        items={[
          { label: 'Inscripciones' },
          { label: 'Listas de espera', to: listPath },
          { label: data.name },
        ]}
      />
      {isReadOnly && (
        <Notice icon={Eye} tone="info">
          Modo solo lectura. Promover, mover y quitar lo hacen Administrativo y Dirección.
        </Notice>
      )}
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <StatCard label="Cupo">
          <span className="font-heading text-2xl font-bold text-ink">{data.capacity ?? 'Sin límite'}</span>
        </StatCard>
        <StatCard label="Ocupados">
          <span className="font-heading text-2xl font-bold text-ink">{data.occupied}</span>
        </StatCard>
        <StatCard label="Lugares libres">
          <span className="font-heading text-2xl font-bold text-ink">{data.free ?? '—'}</span>
        </StatCard>
        <StatCard label="Esperando">
          <span className="font-heading text-2xl font-bold text-ink">{data.waiting}</span>
        </StatCard>
      </div>
      {hasFreeSeat && (
        <Notice icon={Info} tone="ok">
          {data.free === 1 ? 'Hay 1 lugar libre' : `Hay ${data.free} lugares libres`} y todavía no se promovió a nadie de la cola.
        </Notice>
      )}
      <Tabs className="gap-4" value={tab} onValueChange={setTab}>
        <TabsList variant="segmented" className="group-data-horizontal/tabs:h-12 p-0 md:group-data-horizontal/tabs:h-8 md:p-[3px]">
          <TabsTrigger value="queue" className="h-full md:h-[calc(100%-1px)]">Cola</TabsTrigger>
          <TabsTrigger value="history" className="h-full md:h-[calc(100%-1px)]">Historial</TabsTrigger>
        </TabsList>
        <TabsContent value="queue" className="mt-0">
          <QueueTab queue={data} canAct={!isReadOnly} />
        </TabsContent>
        <TabsContent value="history" className="mt-0">
          <HistoryTab kind={kind} id={id} active={tab === 'history'} />
        </TabsContent>
      </Tabs>
    </PageContainer>
  );
}
