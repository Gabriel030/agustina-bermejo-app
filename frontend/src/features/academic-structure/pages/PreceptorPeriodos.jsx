import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { StatusPill } from '@/components/StatusPill';
import { DataTable } from '@/components/table/DataTable';
import { Button } from '@/components/ui/button';
import { TermActionDialog } from '@/features/academic-structure/components/TermActionDialog';
import { TermRowMenu } from '@/features/academic-structure/components/TermRowMenu';
import { useAllTerms } from '@/features/academic-structure/hooks/useTerms';
import {
  TERM_STATUS_LABELS,
  TERM_TYPE_LABELS,
  formatTermDate,
  termTypeLabel,
} from '@/features/academic-structure/lib/terms';

function CreateTermButton({ onClick, variant = 'gradient' }) {
  return (
    <Button type="button" variant={variant} onClick={onClick}>
      <Plus />
      Nuevo período
    </Button>
  );
}

function buildColumns(onEdit, onAction) {
  return [
    {
      id: 'description',
      header: 'Período',
      accessorFn: (term) => `${term.description ?? ''} ${term.year}`,
      meta: { cardTitle: true },
      cell: ({ row }) => (
        <div className="flex flex-col">
          <span className="font-semibold text-ink">{row.original.description}</span>
          <span className="text-xs text-ink-faint">{row.original.year}</span>
        </div>
      ),
    },
    {
      id: 'type',
      header: 'Tipo',
      accessorKey: 'type',
      enableColumnFilter: true,
      meta: { filterable: true, filterTitle: 'Tipo', filterLabel: termTypeLabel },
      cell: ({ row }) => termTypeLabel(row.original.type),
    },
    {
      id: 'dates',
      header: 'Fechas',
      enableSorting: false,
      cell: ({ row }) => `${formatTermDate(row.original.startDate)} → ${formatTermDate(row.original.endDate)}`,
    },
    {
      id: 'status',
      header: 'Estado',
      accessorKey: 'status',
      enableColumnFilter: true,
      meta: { filterable: true, filterTitle: 'Estado', filterLabel: (value) => TERM_STATUS_LABELS[value] ?? value },
      cell: ({ row }) => (
        <StatusPill dot tone={row.original.status === 'ACTIVE' ? 'success' : 'neutral'}>
          {TERM_STATUS_LABELS[row.original.status] ?? row.original.status}
        </StatusPill>
      ),
    },
    {
      id: 'enrollment',
      header: 'Inscripción',
      enableSorting: false,
      cell: ({ row }) => (
        <StatusPill dot tone={row.original.enrollmentOpen ? 'success' : 'neutral'}>
          {row.original.enrollmentOpen ? 'Abierta' : 'Cerrada'}
        </StatusPill>
      ),
    },
    {
      id: 'actions',
      header: '',
      enableSorting: false,
      meta: { align: 'right' },
      cell: ({ row }) => <TermRowMenu term={row.original} onEdit={onEdit} onAction={onAction} />,
    },
  ];
}

export function PreceptorPeriodos() {
  const navigate = useNavigate();
  const { data: terms, isLoading, isError, refetch } = useAllTerms();
  const [pending, setPending] = useState(null);

  const sortedTerms = useMemo(
    () => terms && [...terms].sort((a, b) => b.year - a.year || b.startDate.localeCompare(a.startDate)),
    [terms],
  );

  const columns = useMemo(
    () =>
      buildColumns(
        (term) => navigate(`/preceptor/periodos/${term.id}/editar`),
        (term, action) => setPending({ term, action }),
      ),
    [navigate],
  );

  return (
    <PageContainer
      title="Períodos lectivos"
      description="Calendario de cursada e inscripción"
      action={<CreateTermButton onClick={() => navigate('/preceptor/periodos/nuevo')} />}
    >
      <GlassCard className="overflow-x-auto p-0">
        <DataTable
          columns={columns}
          data={sortedTerms}
          getRowId={(term) => String(term.id)}
          isLoading={isLoading}
          isError={isError}
          onRetry={refetch}
          errorMessage="No pudimos cargar los períodos."
          minWidth={820}
          emptyState={
            <EmptyState
              title="Todavía no hay períodos lectivos"
              description="Creá el primero para poder abrir la inscripción y armar las comisiones."
              action={<CreateTermButton variant="outline" onClick={() => navigate('/preceptor/periodos/nuevo')} />}
            />
          }
        />
      </GlassCard>
      {pending && <TermActionDialog term={pending.term} action={pending.action} onClose={() => setPending(null)} />}
    </PageContainer>
  );
}
