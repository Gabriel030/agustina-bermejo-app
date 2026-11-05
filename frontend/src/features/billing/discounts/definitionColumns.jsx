import { scopeLabel } from '../hooks/useOfferNames';
import { DefinitionNameCell } from './DefinitionNameCell';
import { DiscountStatusPill, PromotionStatusPill } from './StatusPills';
import {
  COHORT_PROMOTION_STATUS,
  DEFINITION_STATUS,
  appliesToLabel,
  definitionStatusKey,
  formatCalculation,
  formatCalculationWithInstallments,
  formatQuota,
  formatRange,
  typeLabel,
} from './discountLabels';

export function buildCatalogColumns({ basePath }) {
  return [
    {
      id: 'name',
      header: 'Descuento',
      accessorFn: (entry) => entry.name,
      cell: ({ row }) => <DefinitionNameCell entry={row.original} basePath={basePath} subtitle={typeLabel(row.original.discountType)} />,
    },
    {
      id: 'appliesTo',
      header: 'Aplica a',
      enableSorting: false,
      accessorFn: (entry) => entry.applicableInvoiceType,
      cell: ({ row }) => appliesToLabel(row.original.applicableInvoiceType),
    },
    {
      id: 'calculation',
      header: 'Cálculo',
      enableSorting: false,
      accessorFn: (entry) => formatCalculation(entry.calculation),
      cell: ({ row }) => formatCalculation(row.original.calculation),
    },
    {
      id: 'validity',
      header: 'Vigencia',
      enableSorting: false,
      accessorFn: (entry) => entry.availableFrom,
      cell: ({ row }) => formatRange(row.original.availableFrom, row.original.availableUntil),
    },
    {
      id: 'status',
      header: 'Estado',
      enableSorting: false,
      accessorFn: (entry) => definitionStatusKey(entry),
      cell: ({ row }) => <DiscountStatusPill entry={row.original} />,
      meta: { filterable: true, filterTitle: 'estado', filterLabel: (key) => DEFINITION_STATUS[key]?.label ?? key },
    },
  ];
}

export function buildPromotionColumns({ basePath, names }) {
  return [
    {
      id: 'name',
      header: 'Promoción',
      accessorFn: (entry) => entry.name,
      cell: ({ row }) => <DefinitionNameCell entry={row.original} basePath={basePath} subtitle={scopeLabel(row.original, names)} />,
    },
    {
      id: 'appliesTo',
      header: 'Aplica a',
      enableSorting: false,
      accessorFn: (entry) => entry.applicableInvoiceType,
      cell: ({ row }) => appliesToLabel(row.original.applicableInvoiceType),
      meta: { filterable: true, filterTitle: 'aplica a', filterLabel: appliesToLabel },
    },
    {
      id: 'calculation',
      header: 'Cálculo',
      enableSorting: false,
      accessorFn: (entry) => formatCalculationWithInstallments(entry),
      cell: ({ row }) => formatCalculationWithInstallments(row.original),
    },
    {
      id: 'window',
      header: 'Inscripción',
      enableSorting: false,
      accessorFn: (entry) => entry.enrollmentWindowFrom,
      cell: ({ row }) => formatRange(row.original.enrollmentWindowFrom, row.original.enrollmentWindowTo),
    },
    {
      id: 'quota',
      header: 'Cupo',
      enableSorting: false,
      accessorFn: (entry) => formatQuota(entry) ?? '',
      cell: ({ row }) => formatQuota(row.original) ?? 'Sin cupo',
      meta: { align: 'right' },
    },
    {
      id: 'status',
      header: 'Estado',
      enableSorting: false,
      accessorFn: (entry) => entry.cohortPromotionStatus,
      cell: ({ row }) => <PromotionStatusPill status={row.original.cohortPromotionStatus} />,
      meta: { filterable: true, filterTitle: 'estado', filterLabel: (key) => COHORT_PROMOTION_STATUS[key]?.label ?? key },
    },
  ];
}
