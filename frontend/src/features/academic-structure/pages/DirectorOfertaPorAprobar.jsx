import { Link, useNavigate } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { DataTable } from '@/components/table/DataTable';
import { formatearFecha } from '@/lib/comisiones';
import { ageLabel } from '@/lib/aprobaciones';
import { usePendingOfferCycles } from '@/features/academic-structure/hooks/useOfferApproval';
import { OFFER_APPROVALS_PATH, cycleTargetKind, cycleTargetName } from '@/features/academic-structure/lib/offerApproval';

function buildColumns() {
  return [
    {
      id: 'target',
      header: 'Carrera / curso',
      accessorFn: cycleTargetName,
      meta: { filterable: true, filterTitle: 'Carrera / curso', cardTitle: true },
      cell: ({ row }) => (
        <div className="flex flex-col">
          <Link
            to={`${OFFER_APPROVALS_PATH}/${row.original.id}`}
            className="inline-flex items-center gap-1 font-heading text-[14px] font-semibold text-ink hover:text-primary"
          >
            {cycleTargetName(row.original)}
            <ChevronRight aria-hidden="true" className="size-4 text-ink-faint" />
          </Link>
          <span className="text-xs text-ink-faint">{cycleTargetKind(row.original)}</span>
        </div>
      ),
    },
    {
      id: 'term',
      header: 'Período',
      accessorFn: (cycle) => cycle.termName,
      meta: { filterable: true, filterTitle: 'Período' },
      cell: ({ row }) => <span className="text-ink">{row.original.termName}</span>,
    },
    {
      id: 'sectionCount',
      header: 'Comisiones',
      accessorFn: (cycle) => cycle.sectionCount,
      meta: { align: 'right' },
      cell: ({ row }) => <span className="tabular-nums text-ink">{row.original.sectionCount}</span>,
    },
    {
      id: 'submittedBy',
      header: 'Enviado por',
      accessorFn: (cycle) => cycle.submittedByName ?? '',
      enableSorting: false,
      cell: ({ row }) => <span className="text-ink">{row.original.submittedByName ?? '—'}</span>,
    },
    {
      id: 'submittedAt',
      header: 'Enviado',
      accessorFn: (cycle) => cycle.submittedAt ?? '',
      cell: ({ row }) => (
        <div className="flex flex-col">
          <span className="tabular-nums text-ink">{formatearFecha(row.original.submittedAt) ?? '—'}</span>
          <span className="text-xs text-ink-faint">{ageLabel(row.original.submittedAt)}</span>
        </div>
      ),
    },
  ];
}

const COLUMNS = buildColumns();

export function DirectorOfertaPorAprobar() {
  const navigate = useNavigate();
  const { data, isLoading, isError, refetch } = usePendingOfferCycles();

  return (
    <PageContainer>
      <Link
        to="/director/aprobaciones"
        className="flex w-fit items-center gap-1.5 text-[13px] font-semibold text-ink-soft hover:text-ink"
      >
        ← Volver a Aprobaciones
      </Link>
      <div>
        <h1 className="font-heading text-2xl font-bold text-ink">Oferta por aprobar</h1>
        <p className="mt-0.5 text-sm text-ink-soft">Ciclos de oferta académica enviados por Preceptoría.</p>
      </div>
      <GlassCard className="p-2">
        <DataTable
          columns={COLUMNS}
          data={data}
          getRowId={(cycle) => String(cycle.id)}
          isLoading={isLoading}
          isError={isError}
          onRetry={refetch}
          errorMessage="No pudimos cargar la oferta por aprobar."
          onRowClick={(cycle) => navigate(`${OFFER_APPROVALS_PATH}/${cycle.id}`)}
          initialSorting={[{ id: 'submittedAt', desc: false }]}
          minWidth={720}
          emptyState={
            <EmptyState
              title="No hay ciclos esperando tu decisión"
              description="Cuando Preceptoría envíe un ciclo de oferta, va a aparecer acá."
            />
          }
        />
      </GlassCard>
    </PageContainer>
  );
}
