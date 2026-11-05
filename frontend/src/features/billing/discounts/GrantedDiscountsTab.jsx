import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Gift } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Paginador } from '@/components/Paginador';
import { InfoTip } from '@/components/charts/InfoTip';
import { DataTable } from '@/components/table/DataTable';
import { Label } from '@/components/ui/label';
import { useStudentDiscountSearch } from '../hooks/useStudentDiscounts';
import { OptionSelect } from './OptionSelect';
import { StudentDiscountStatusPill } from './StatusPills';
import { APPLIED_TYPE_LABELS, STUDENT_DISCOUNT_STATUS } from './discountLabels';
import { discountAppliesTo, discountDefinitionSubtitle, discountKind, discountPeriod, discountValue } from './studentDiscountView';

const ALL = 'ALL';
const PAGE_SIZE = 20;
const TYPE_FILTERS = ['SCHOLARSHIP', 'COHORT_PROMOTION', 'ANNUAL_UPFRONT', 'INDIVIDUAL'];

function buildColumns(roleBase) {
  return [
    {
      id: 'student',
      header: 'Alumno',
      enableSorting: false,
      accessorFn: (discount) => discount.studentName ?? '',
      cell: ({ row }) => (
        <div className="flex min-w-0 flex-col text-left">
          <Link
            to={`/${roleBase}/alumnos/${row.original.studentId}?view=financiero`}
            className="font-semibold text-ink hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            {row.original.studentName ?? `Alumno ${row.original.studentId}`}
          </Link>
          {row.original.enrollmentName && <span className="text-xs text-ink-faint">{row.original.enrollmentName}</span>}
        </div>
      ),
    },
    {
      id: 'kind',
      header: 'Descuento',
      enableSorting: false,
      accessorFn: (discount) => discountKind(discount),
      cell: ({ row }) => (
        <div className="flex min-w-0 flex-col text-left">
          <span className="font-semibold text-ink">{discountKind(row.original)}</span>
          {discountDefinitionSubtitle(row.original) && <span className="text-xs text-ink-faint">{discountDefinitionSubtitle(row.original)}</span>}
        </div>
      ),
    },
    { id: 'appliesTo', header: 'Aplica a', enableSorting: false, accessorFn: (discount) => discountAppliesTo(discount), cell: ({ row }) => discountAppliesTo(row.original) },
    { id: 'value', header: 'Valor', enableSorting: false, accessorFn: (discount) => discountValue(discount), cell: ({ row }) => discountValue(row.original), meta: { align: 'right' } },
    { id: 'period', header: 'Período', enableSorting: false, accessorFn: (discount) => discount.fromPeriod, cell: ({ row }) => discountPeriod(row.original) },
    {
      id: 'status',
      header: 'Estado',
      enableSorting: false,
      accessorFn: (discount) => discount.status,
      cell: ({ row }) => <StudentDiscountStatusPill status={row.original.status} />,
    },
  ];
}

export function GrantedDiscountsTab({ roleBase }) {
  const [status, setStatus] = useState(ALL);
  const [type, setType] = useState(ALL);
  const [page, setPage] = useState(1);
  const query = useStudentDiscountSearch({
    status: status === ALL ? null : status,
    type: type === ALL ? null : type,
    page: page - 1,
    size: PAGE_SIZE,
  });
  const columns = useMemo(() => buildColumns(roleBase), [roleBase]);
  const filtering = status !== ALL || type !== ALL;
  const totalPages = Math.max(1, Math.ceil((query.data?.totalElements ?? 0) / PAGE_SIZE));

  const statusOptions = [{ value: ALL, label: 'Todos los estados' }, ...Object.entries(STUDENT_DISCOUNT_STATUS).map(([value, info]) => ({ value, label: info.label }))];
  const typeOptions = [{ value: ALL, label: 'Todos los tipos' }, ...TYPE_FILTERS.map((value) => ({ value, label: APPLIED_TYPE_LABELS[value] }))];

  function changeStatus(value) {
    setStatus(value);
    setPage(1);
  }

  function changeType(value) {
    setType(value);
    setPage(1);
  }

  return (
    <section className="flex flex-col gap-3" aria-label="Descuentos otorgados">
      <div className="flex items-center gap-2">
        <h2 className="font-heading text-base font-semibold text-ink">Descuentos otorgados</h2>
        <InfoTip>Todos los descuentos que tienen los alumnos, por estado. Para revocar uno, entrá al legajo del alumno.</InfoTip>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="granted-status">Estado</Label>
          <OptionSelect id="granted-status" value={status} onChange={changeStatus} options={statusOptions} />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="granted-type">Tipo</Label>
          <OptionSelect id="granted-type" value={type} onChange={changeType} options={typeOptions} />
        </div>
      </div>
      <GlassCard className="overflow-x-auto p-0">
        <DataTable
          columns={columns}
          data={query.data?.items}
          getRowId={(discount) => String(discount.id)}
          isLoading={query.isLoading}
          isError={query.isError}
          onRetry={() => query.refetch()}
          errorMessage="No pudimos cargar los descuentos."
          minWidth={760}
          emptyState={
            <EmptyState
              icon={<Gift className="mx-auto size-6 text-ink-faint" />}
              title={filtering ? 'Ningún descuento coincide' : 'Todavía no se otorgó ningún descuento'}
              description={filtering ? 'Probá con otros filtros.' : 'Cuando se otorgue una beca o un descuento, lo ves acá.'}
            />
          }
        />
      </GlassCard>
      <Paginador page={page} totalPages={totalPages} onChange={setPage} />
    </section>
  );
}
