import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { Buscador } from '@/components/Buscador';
import { Paginador } from '@/components/Paginador';
import { TabCount } from '@/components/TabCount';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { DataTable } from '@/components/table/DataTable';
import { useReviewBasePath } from '@/features/enrollment/hooks/useReviewBasePath';
import {
  HISTORY_PAGE_SIZE,
  useApplicationHistory,
  usePendingApplications,
} from '@/features/enrollment/hooks/useApplicationReview';
import {
  APPLICATION_REVIEW_PATH,
  APPLICATION_STATUS,
  HISTORY_STATUS_OPTIONS,
  cohortLabel,
  documentationFilterValue,
  documentationLabel,
} from '@/features/enrollment/lib/applicationReview';
import { formatearFecha } from '@/lib/comisiones';
import { ageLabel } from '@/lib/aprobaciones';

const SEARCH_DEBOUNCE_MS = 300;

function StudentCell({ application }) {
  return (
    <div className="flex min-w-0 flex-col items-start gap-0.5 whitespace-normal">
      <span className="font-heading text-[14px] font-semibold leading-tight text-ink">{application.studentFullName ?? '—'}</span>
      {application.studentEmail && <span className="text-[12px] text-ink-faint">{application.studentEmail}</span>}
    </div>
  );
}

function ProgramCell({ application }) {
  const cohort = cohortLabel(application.programCohortName);
  return (
    <div className="flex min-w-0 flex-col items-start gap-0.5 whitespace-normal">
      <span className="text-[13px] text-ink">{application.programName}</span>
      {cohort && <span className="text-[12px] text-ink-faint">{cohort}</span>}
    </div>
  );
}

function RequestedCell({ application }) {
  const age = ageLabel(application.requestedAt);
  return (
    <div className="flex flex-col items-start gap-0.5">
      <span className="text-[13px] text-ink">{formatearFecha(application.requestedAt) ?? '—'}</span>
      {age && <span className="text-[12px] text-ink-faint">{age}</span>}
    </div>
  );
}

function ReviewCell({ application, basePath }) {
  return (
    <Button asChild variant="outline" size="sm">
      <Link
        to={`${basePath}/${APPLICATION_REVIEW_PATH}/${application.id}`}
        aria-label={`Revisar la solicitud de ${application.studentFullName ?? 'la persona'}`}
      >
        Revisar
      </Link>
    </Button>
  );
}

function buildColumns({ basePath, history }) {
  const student = {
    id: 'student',
    header: 'Alumno',
    accessorFn: (application) => application.studentFullName ?? '',
    enableSorting: false,
    meta: { stickyStart: true, cardTitle: true, headerClassName: 'min-w-[180px]' },
    cell: ({ row }) => <StudentCell application={row.original} />,
  };
  const program = {
    id: 'program',
    header: 'Carrera',
    accessorFn: (application) => application.programName,
    enableSorting: false,
    enableColumnFilter: true,
    meta: { filterable: true, filterTitle: 'carrera' },
    cell: ({ row }) => <ProgramCell application={row.original} />,
  };
  const requested = {
    id: 'requestedAt',
    header: 'Solicitada',
    accessorFn: (application) => application.requestedAt,
    meta: { headerClassName: 'whitespace-nowrap' },
    cell: ({ row }) => <RequestedCell application={row.original} />,
  };
  const review = {
    id: 'review',
    header: '',
    enableSorting: false,
    cell: ({ row }) => <ReviewCell application={row.original} basePath={basePath} />,
  };
  if (history) {
    return [
      student,
      { ...program, enableColumnFilter: false, meta: {} },
      requested,
      {
        id: 'status',
        header: 'Estado',
        accessorFn: (application) => application.status,
        enableSorting: false,
        cell: ({ row }) => {
          const status = APPLICATION_STATUS[row.original.status] ?? { label: row.original.status, tone: 'neutral' };
          return <StatusPill tone={status.tone}>{status.label}</StatusPill>;
        },
      },
      {
        id: 'decidedBy',
        header: 'Resuelta por',
        accessorFn: (application) => application.decidedByName ?? '',
        enableSorting: false,
        cell: ({ row }) => (
          <div className="flex flex-col items-start gap-0.5">
            <span className="text-[13px] text-ink">{row.original.decidedByName ?? '—'}</span>
            {row.original.decidedAt && (
              <span className="text-[12px] text-ink-faint">{formatearFecha(row.original.decidedAt)}</span>
            )}
          </div>
        ),
      },
      review,
    ];
  }
  return [
    student,
    program,
    requested,
    {
      id: 'documentation',
      header: 'Documentación',
      accessorFn: (application) => documentationFilterValue(application.documentation),
      enableSorting: false,
      enableColumnFilter: true,
      meta: { filterable: true, filterTitle: 'documentación' },
      cell: ({ row }) => (
        <StatusPill tone={row.original.documentation?.complete ? 'success' : 'warning'}>
          {documentationLabel(row.original.documentation)}
        </StatusPill>
      ),
    },
    review,
  ];
}

function PendingList({ basePath }) {
  const navigate = useNavigate();
  const pending = usePendingApplications();
  const columns = useMemo(() => buildColumns({ basePath, history: false }), [basePath]);
  return (
    <DataTable
      compact
      mobileLayout="scroll"
      columns={columns}
      data={pending.data}
      getRowId={(application) => String(application.id)}
      initialSorting={[{ id: 'requestedAt', desc: false }]}
      minWidth={720}
      isLoading={pending.isLoading}
      isError={pending.isError}
      onRetry={pending.refetch}
      errorMessage="No pudimos cargar las solicitudes"
      onRowClick={(application) => navigate(`${basePath}/${APPLICATION_REVIEW_PATH}/${application.id}`)}
      emptyState={
        <EmptyState
          icon="📥"
          title="No hay solicitudes por revisar"
          description="Cuando alguien solicite inscribirse a una carrera, la vas a ver acá y en tus notificaciones."
        />
      }
    />
  );
}

function HistoryList({ basePath }) {
  const navigate = useNavigate();
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('ALL');
  const [page, setPage] = useState(1);
  const history = useApplicationHistory({ status, search, page: page - 1 });
  const columns = useMemo(() => buildColumns({ basePath, history: true }), [basePath]);

  useEffect(() => {
    const timer = setTimeout(() => {
      setSearch(searchInput.trim());
      setPage(1);
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [searchInput]);

  const items = history.data?.items;
  const totalPages = Math.max(1, Math.ceil((history.data?.totalElements ?? 0) / HISTORY_PAGE_SIZE));
  const isFiltered = Boolean(search) || status !== 'ALL';

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-3">
        <Buscador value={searchInput} onChange={setSearchInput} placeholder="Buscar por alumno o DNI" />
        <Select
          value={status}
          onValueChange={(next) => {
            setStatus(next);
            setPage(1);
          }}
        >
          <SelectTrigger aria-label="Filtrar por estado" className="w-auto min-w-44">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {HISTORY_STATUS_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <DataTable
        compact
        mobileLayout="scroll"
        columns={columns}
        data={items}
        getRowId={(application) => String(application.id)}
        initialSorting={[{ id: 'requestedAt', desc: true }]}
        minWidth={860}
        isLoading={history.isLoading}
        isError={history.isError}
        onRetry={history.refetch}
        errorMessage="No pudimos cargar el historial"
        onRowClick={(application) => navigate(`${basePath}/${APPLICATION_REVIEW_PATH}/${application.id}`)}
        emptyState={
          isFiltered ? (
            <EmptyState title="Ninguna solicitud coincide" description="Probá con otro nombre, DNI o estado." />
          ) : (
            <EmptyState
              title="Todavía no hay solicitudes resueltas"
              description="Las aprobadas, rechazadas y vencidas quedan acá."
            />
          )
        }
      />
      <Paginador page={page} totalPages={totalPages} onChange={setPage} />
    </div>
  );
}

export function SolicitudesDeIngreso() {
  const basePath = useReviewBasePath();
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') === 'historial' ? 'historial' : 'pendientes';
  const pending = usePendingApplications();

  return (
    <PageContainer
      title="Solicitudes de ingreso"
      description="Personas que pidieron inscribirse a una carrera. Revisá la documentación y aprobá o rechazá: al aprobar se genera su matrícula de ingreso y obtiene acceso como alumno."
    >
      <Tabs
        className="gap-4"
        value={tab}
        onValueChange={(value) => setParams(value === 'historial' ? { tab: 'historial' } : {}, { replace: true })}
      >
        <TabsList variant="segmented">
          <TabsTrigger value="pendientes">
            Pendientes
            <TabCount count={pending.data?.length} />
          </TabsTrigger>
          <TabsTrigger value="historial">Historial</TabsTrigger>
        </TabsList>
        <TabsContent value="pendientes" className="mt-0">
          <GlassCard className="p-3 md:p-4">
            <PendingList basePath={basePath} />
          </GlassCard>
        </TabsContent>
        <TabsContent value="historial" className="mt-0">
          <GlassCard className="p-3 md:p-4">
            <HistoryList basePath={basePath} />
          </GlassCard>
        </TabsContent>
      </Tabs>
    </PageContainer>
  );
}
