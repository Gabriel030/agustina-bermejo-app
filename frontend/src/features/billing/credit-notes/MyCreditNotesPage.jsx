import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { EmptyState } from '@/components/EmptyState';
import { StatusPill } from '@/components/StatusPill';
import { DataTable } from '@/components/table/DataTable';
import { formatDate, formatMoney } from '@/lib/billing';
import { useMyCreditNotes } from '../hooks/useCreditNotes';
import { STUDENT_REASON_LABELS, studentEffectOf } from './creditNotes';

const COLUMNS = [
  {
    id: 'reason',
    header: 'Qué pasó',
    accessorFn: (note) => note.reason,
    enableSorting: false,
    cell: ({ row }) => (
      <div className="flex flex-col">
        <span className="font-medium text-ink">{STUDENT_REASON_LABELS[row.original.reason] ?? 'Ajuste'}</span>
        <span className="text-xs text-ink-faint">{row.original.creditNoteNumber}</span>
      </div>
    ),
  },
  {
    id: 'invoice',
    header: 'Cuota',
    accessorFn: (note) => note.invoiceNumber,
    enableSorting: false,
    cell: ({ row }) => <span className="text-ink-soft">{row.original.invoiceNumber}</span>,
  },
  {
    id: 'issueDate',
    header: 'Fecha',
    accessorFn: (note) => note.issueDate,
    cell: ({ row }) => <span className="text-ink-soft">{formatDate(row.original.issueDate)}</span>,
  },
  {
    id: 'effect',
    header: 'Efecto',
    accessorFn: (note) => studentEffectOf(note).label,
    enableSorting: false,
    cell: ({ row }) => {
      const effect = studentEffectOf(row.original);
      return <StatusPill tone={effect.tone}>{effect.label}</StatusPill>;
    },
  },
  {
    id: 'total',
    header: 'Importe',
    accessorFn: (note) => Number(note.totalAmount.amount),
    cell: ({ row }) => <span className="font-semibold tabular-nums text-ink">{formatMoney(row.original.totalAmount)}</span>,
    meta: { align: 'right' },
  },
];

export function MyCreditNotesPage() {
  const { data, isLoading, isError, refetch } = useMyCreditNotes();

  return (
    <PageContainer title="Mis notas de crédito">
      <GlassCard className="overflow-hidden md:px-3 md:py-2">
        <DataTable
          columns={COLUMNS}
          data={data}
          getRowId={(note) => String(note.id)}
          isLoading={isLoading}
          isError={isError}
          onRetry={refetch}
          errorMessage="No pudimos cargar tus notas de crédito."
          initialSorting={[{ id: 'issueDate', desc: true }]}
          minWidth={640}
          emptyState={
            <EmptyState
              title="Sin notas de crédito"
              description="Si se corrige o se anula una cuota, el ajuste aparece acá."
            />
          }
        />
      </GlassCard>
    </PageContainer>
  );
}
