import { Button } from '@/components/ui/button';
import { DiscountProgressCell } from './DiscountProgressCell';
import { StudentDiscountStatusPill } from './StatusPills';
import { STUDENT_DISCOUNT_STATUS } from './discountLabels';
import {
  discountAppliesTo,
  discountDefinitionSubtitle,
  discountKind,
  discountPeriod,
  discountProgress,
  discountValue,
} from './studentDiscountView';

export function buildStudentDiscountColumns({ isDirector, onRevoke }) {
  return [
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
    {
      id: 'value',
      header: 'Valor',
      enableSorting: false,
      accessorFn: (discount) => discountValue(discount),
      cell: ({ row }) => discountValue(row.original),
      meta: { align: 'right' },
    },
    { id: 'period', header: 'Período', enableSorting: false, accessorFn: (discount) => discount.fromPeriod, cell: ({ row }) => discountPeriod(row.original) },
    { id: 'progress', header: 'Avance', enableSorting: false, accessorFn: (discount) => discountProgress(discount).applied, cell: ({ row }) => <DiscountProgressCell discount={row.original} /> },
    {
      id: 'status',
      header: 'Estado',
      enableSorting: false,
      accessorFn: (discount) => discount.status,
      cell: ({ row }) => <StudentDiscountStatusPill status={row.original.status} />,
      meta: { filterable: true, filterTitle: 'estado', filterLabel: (key) => STUDENT_DISCOUNT_STATUS[key]?.label ?? key },
    },
    {
      id: 'actions',
      header: '',
      enableSorting: false,
      cell: ({ row }) =>
        row.original.status === 'ACTIVE' ? (
          <Button type="button" variant="outline" size="xs" aria-label={`${isDirector ? 'Revocar' : 'Proponer revocar'} ${discountKind(row.original).toLowerCase()}`} onClick={() => onRevoke(row.original)}>
            {isDirector ? 'Revocar' : 'Proponer revocar'}
          </Button>
        ) : null,
      meta: { align: 'right', headerClassName: 'w-36' },
    },
  ];
}
