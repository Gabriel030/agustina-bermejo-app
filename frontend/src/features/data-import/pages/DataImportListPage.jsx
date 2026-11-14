import { useMemo } from 'react';
import { Plus } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { EmptyState } from '@/components/EmptyState';
import { PageContainer } from '@/components/PageContainer';
import { StatusPill } from '@/components/StatusPill';
import { DataTable } from '@/components/table/DataTable';
import { Button } from '@/components/ui/button';
import { formatDate } from '@/lib/billing';
import { useDataImports } from '../hooks/useDataImports';
import { BATCH_STATUS_LABELS, BATCH_STATUS_TONES, contentSummary } from '../importLabels';

const BASE_PATH = '/director/importacion-inicial';

const COLUMNS = [
  {
    id: 'name',
    header: 'Nombre',
    accessorFn: (batch) => batch.name,
    cell: ({ row }) => <span className="font-semibold text-ink">{row.original.name}</span>,
    meta: { cardTitle: true },
  },
  {
    id: 'status',
    header: 'Estado',
    accessorFn: (batch) => batch.status,
    cell: ({ row }) => (
      <StatusPill tone={BATCH_STATUS_TONES[row.original.status] ?? 'neutral'}>{BATCH_STATUS_LABELS[row.original.status] ?? row.original.status}</StatusPill>
    ),
    meta: { filterable: true, filterTitle: 'estado', filterLabel: (status) => BATCH_STATUS_LABELS[status] ?? status },
  },
  {
    id: 'content',
    header: 'Contenido',
    accessorFn: (batch) => contentSummary(batch),
  },
  {
    id: 'createdAt',
    header: 'Creada',
    accessorFn: (batch) => batch.createdAt,
    cell: ({ row }) => formatDate(row.original.createdAt),
  },
];

export function DataImportListPage() {
  const navigate = useNavigate();
  const query = useDataImports();
  const columns = useMemo(() => COLUMNS, []);
  const batches = query.data ?? [];

  return (
    <PageContainer
      title="Importación inicial"
      description="Cargá la historia del instituto desde plantillas. Primero se revisa sin escribir nada."
      action={
        <Button asChild>
          <Link to={`${BASE_PATH}/nueva`}>
            <Plus className="size-4" aria-hidden="true" />
            Nueva importación
          </Link>
        </Button>
      }
    >
      <DataTable
        columns={columns}
        data={batches}
        getRowId={(batch) => String(batch.id)}
        isLoading={query.isLoading}
        isError={query.isError}
        onRetry={query.refetch}
        errorMessage="No pudimos cargar las importaciones."
        emptyState={<EmptyState icon="📥" title="Todavía no hay importaciones" description="Creá la primera para cargar personas desde una plantilla." />}
        onRowClick={(batch) => navigate(`${BASE_PATH}/${batch.id}`)}
        minWidth={640}
      />
    </PageContainer>
  );
}
