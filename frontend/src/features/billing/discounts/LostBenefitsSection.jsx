import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Gift } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Paginador } from '@/components/Paginador';
import { EmptyState } from '@/components/EmptyState';
import { StatusPill } from '@/components/StatusPill';
import { InfoTip } from '@/components/charts/InfoTip';
import { DataTable } from '@/components/table/DataTable';
import { Label } from '@/components/ui/label';
import { LOST_BENEFITS_PAGE_SIZE, useLostBenefits } from '../hooks/useLostBenefits';
import { OptionSelect } from './OptionSelect';
import { PROMOTION_APPLIES_TO, appliesToLabel, formatMonth } from './discountLabels';

const ALL = 'ALL';

function buildColumns(roleBase) {
  return [
    {
      id: 'student',
      header: 'Alumno',
      enableSorting: false,
      accessorFn: (item) => item.studentName,
      cell: ({ row }) => (
        <Link
          to={`/${roleBase}/alumnos/${row.original.studentId}?view=financiero`}
          className="font-semibold text-ink hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          {row.original.studentName}
        </Link>
      ),
    },
    { id: 'promotion', header: 'Promoción', enableSorting: false, accessorFn: (item) => item.promotionDescription, cell: ({ row }) => row.original.promotionDescription },
    { id: 'feeType', header: 'Cargo', enableSorting: false, accessorFn: (item) => item.feeType, cell: ({ row }) => appliesToLabel(row.original.feeType) },
    {
      id: 'used',
      header: 'Usadas',
      enableSorting: false,
      accessorFn: (item) => item.used,
      cell: ({ row }) => (row.original.scopeCount == null ? row.original.used : `${row.original.used} de ${row.original.scopeCount}`),
      meta: { align: 'right' },
    },
    {
      id: 'remaining',
      header: 'Restantes',
      enableSorting: false,
      accessorFn: (item) => item.remaining,
      cell: ({ row }) => <StatusPill tone="warning">{row.original.remaining}</StatusPill>,
      meta: { align: 'right' },
    },
    { id: 'cutOff', header: 'Corte', enableSorting: false, accessorFn: (item) => item.cutOffAt, cell: ({ row }) => formatMonth(row.original.cutOffAt) },
  ];
}

export function LostBenefitsSection({ roleBase, promotions }) {
  const [promotionId, setPromotionId] = useState(ALL);
  const [feeType, setFeeType] = useState(ALL);
  const [page, setPage] = useState(1);
  const query = useLostBenefits({
    discountDefinitionId: promotionId === ALL ? null : promotionId,
    feeType: feeType === ALL ? null : feeType,
    page: page - 1,
    size: LOST_BENEFITS_PAGE_SIZE,
  });
  const columns = useMemo(() => buildColumns(roleBase), [roleBase]);
  const total = query.data?.total ?? 0;
  const filtering = promotionId !== ALL || feeType !== ALL;

  const promotionOptions = [{ value: ALL, label: 'Todas las promociones' }, ...promotions.map((promotion) => ({ value: String(promotion.id), label: promotion.name }))];
  const feeOptions = [{ value: ALL, label: 'Todos los cargos' }, ...PROMOTION_APPLIES_TO.map((type) => ({ value: type, label: appliesToLabel(type) }))];

  function changePromotion(value) {
    setPromotionId(value);
    setPage(1);
  }

  function changeFeeType(value) {
    setFeeType(value);
    setPage(1);
  }

  return (
    <section className="flex flex-col gap-3" aria-labelledby="lost-benefits-title">
      <div className="flex items-center gap-2">
        <h2 id="lost-benefits-title" className="font-heading text-base font-semibold text-ink">Beneficios perdidos</h2>
        <InfoTip>Cuotas de una promoción que el alumno no llegó a usar porque la promoción terminó o venció. Sirve para decidir si conviene extenderla.</InfoTip>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="lost-promotion">Promoción</Label>
          <OptionSelect id="lost-promotion" value={promotionId} onChange={changePromotion} options={promotionOptions} />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="lost-fee-type">Cargo</Label>
          <OptionSelect id="lost-fee-type" value={feeType} onChange={changeFeeType} options={feeOptions} />
        </div>
      </div>
      <GlassCard className="overflow-x-auto p-0">
        <DataTable
          columns={columns}
          data={query.data?.items}
          getRowId={(item) => String(item.promotionRedemptionId)}
          isLoading={query.isLoading}
          isError={query.isError}
          onRetry={() => query.refetch()}
          errorMessage="No pudimos cargar los beneficios perdidos."
          minWidth={720}
          emptyState={
            <EmptyState
              icon={<Gift className="mx-auto size-6 text-ink-faint" />}
              title={filtering ? 'Ningún beneficio coincide' : 'No hay beneficios perdidos'}
              description={filtering ? 'Probá con otros filtros.' : 'Cuando un alumno deje cuotas de promoción sin usar, aparece acá.'}
            />
          }
        />
      </GlassCard>
      <Paginador page={page} totalPages={Math.max(1, Math.ceil(total / LOST_BENEFITS_PAGE_SIZE))} onChange={setPage} />
    </section>
  );
}
