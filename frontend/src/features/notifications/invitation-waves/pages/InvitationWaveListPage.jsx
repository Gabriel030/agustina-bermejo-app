import { useEffect, useMemo } from 'react';
import { Plus } from 'lucide-react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Paginador } from '@/components/Paginador';
import { DataTable } from '@/components/table/DataTable';
import { Button } from '@/components/ui/button';
import { formatInstantDate } from '../lib/invitationWaveMath';
import { NoAccessState } from '../components/NoAccessState';
import { WaveProgress } from '../components/WaveProgress';
import { WaveStatusPill } from '../components/WaveStatusPill';
import { useInvitationsBasePath } from '../hooks/useInvitationsBasePath';
import { useInvitationWaveAccess, useInvitationWaves } from '../hooks/useInvitationWaves';

const PAGE_SIZE = 20;
const READ_PERMISSION = 'notification.read_invitation_waves';

function buildColumns(basePath) {
  return [
    {
      id: 'name',
      header: 'Nombre',
      cell: ({ row }) => (
        <Link to={`${basePath}/${row.original.id}`} className="inline-flex min-h-11 items-center font-semibold text-ink hover:text-primary md:min-h-8">
          {row.original.name}
        </Link>
      ),
      meta: { cardTitle: true },
    },
    { id: 'batch', header: 'Lote de importación', cell: ({ row }) => row.original.importBatchName ?? '—' },
    { id: 'status', header: 'Estado', cell: ({ row }) => <WaveStatusPill status={row.original.status} /> },
    { id: 'createdAt', header: 'Creada', cell: ({ row }) => formatInstantDate(row.original.createdAt) },
    {
      id: 'progress',
      header: 'Progreso',
      cell: ({ row }) => <WaveProgress wave={row.original} label={`Avance de ${row.original.name}`} className="min-w-36" />,
      meta: { cardBlock: true },
    },
  ];
}

export function InvitationWaveListPage() {
  const navigate = useNavigate();
  const basePath = useInvitationsBasePath();
  const access = useInvitationWaveAccess();
  const [params, setParams] = useSearchParams();
  const page = Math.max(1, Number(params.get('page')) || 1);
  const query = useInvitationWaves({ page, size: PAGE_SIZE, enabled: access.read });
  const columns = useMemo(() => buildColumns(basePath), [basePath]);
  const forbidden = !access.read || query.error?.status === 403;
  const { deny, read } = access;

  useEffect(() => {
    if (read && query.error?.status === 403) deny('read');
  }, [read, query.error, deny]);

  const createAction = access.manage ? (
    <Button asChild variant="primary" className="min-h-11 md:min-h-8">
      <Link to={`${basePath}/nueva`}>
        <Plus aria-hidden="true" />
        Nueva oleada
      </Link>
    </Button>
  ) : null;

  const description = 'Armá oleadas para invitar por mail de activación a los alumnos importados que todavía no entraron al sistema.';

  if (forbidden) {
    return (
      <PageContainer title="Invitaciones" description={description}>
        <NoAccessState permission={READ_PERMISSION} />
      </PageContainer>
    );
  }

  function changePage(next) {
    setParams(next > 1 ? { page: String(next) } : {});
  }

  return (
    <PageContainer title="Invitaciones" description={description} action={createAction}>
      <GlassCard className="p-2 sm:p-4">
        <DataTable
          columns={columns}
          data={query.data?.content ?? []}
          getRowId={(wave) => String(wave.id)}
          isLoading={query.isLoading}
          isError={query.isError}
          onRetry={query.refetch}
          errorMessage="No pudimos cargar las oleadas."
          onRowClick={(wave) => navigate(`${basePath}/${wave.id}`)}
          minWidth={720}
          emptyState={
            <EmptyState
              icon="✉️"
              title="Todavía no hay oleadas"
              description="Una oleada invita por mail, de a tandas, a las personas de un lote importado. Creá la primera para empezar."
              action={
                access.manage ? (
                  <Button asChild variant="primary" className="min-h-11 md:min-h-8">
                    <Link to={`${basePath}/nueva`}>
                      <Plus aria-hidden="true" />
                      Nueva oleada
                    </Link>
                  </Button>
                ) : (
                  <p className="text-sm text-ink-faint">Un Director o Administrativo con permiso puede crearla.</p>
                )
              }
            />
          }
        />
        <Paginador page={page} totalPages={query.data?.totalPages ?? 1} onChange={changePage} />
      </GlassCard>
    </PageContainer>
  );
}
