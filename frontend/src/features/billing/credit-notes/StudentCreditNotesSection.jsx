import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { StatusPill } from '@/components/StatusPill';
import { DataTable } from '@/components/table/DataTable';
import { cn } from '@/lib/utils';
import { formatDate, formatMoney, invoiceConcept } from '@/lib/billing';
import { useStudentCreditNotes } from '../hooks/useCreditNotes';
import {
  STAFF_EFFECT_LABELS,
  STAFF_REASON_LABELS,
  creditNoteAuthorshipOf,
  creditNoteEffectOf,
} from './creditNotes';

function CreditNoteDetail({ note, invoiceLines }) {
  const { requestedBy, authorizedBy } = creditNoteAuthorshipOf(note);
  return (
    <div className="grid gap-4 px-4 py-3 text-sm sm:grid-cols-2">
      <div className="flex flex-col gap-1.5">
        <span className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Renglones acreditados</span>
        {(note.lines ?? []).map((line) => {
          const source = invoiceLines.find((candidate) => candidate.lineNumber === line.invoiceLineNumber);
          const name = source ? source.description || invoiceConcept(source.feeType, source.period) : `Renglón ${line.invoiceLineNumber}`;
          return (
            <div key={line.invoiceLineNumber} className="flex items-baseline justify-between gap-3">
              <span className="text-ink-soft">{name}</span>
              <span className="tabular-nums text-ink">{formatMoney(line.amount)}</span>
            </div>
          );
        })}
      </div>
      <div className="flex flex-col gap-1.5">
        <span className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Quién intervino</span>
        <div className="flex items-baseline justify-between gap-3">
          <span className="text-ink-faint">Pedida por</span>
          <span className="text-ink">{requestedBy}</span>
        </div>
        <div className="flex items-baseline justify-between gap-3">
          <span className="text-ink-faint">Autorizada por</span>
          <span className="text-ink">{authorizedBy}</span>
        </div>
      </div>
      {note.justification && (
        <div className="flex flex-col gap-1 sm:col-span-2">
          <span className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Justificación</span>
          <p className="text-ink-soft">{note.justification}</p>
        </div>
      )}
    </div>
  );
}

function buildColumns() {
  return [
    {
      id: 'number',
      header: 'Número',
      accessorFn: (note) => note.creditNoteNumber,
      cell: ({ row }) => (
        <div className="flex flex-col">
          <span className="font-medium text-ink">{row.original.creditNoteNumber}</span>
          <span className="text-xs text-ink-faint">{formatDate(row.original.issueDate)}</span>
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
      id: 'reason',
      header: 'Motivo',
      accessorFn: (note) => note.reason,
      enableSorting: false,
      cell: ({ row }) => <span className="text-ink-soft">{STAFF_REASON_LABELS[row.original.reason] ?? row.original.reason}</span>,
      meta: { filterable: true, filterTitle: 'motivo', filterLabel: (key) => STAFF_REASON_LABELS[key] ?? key },
    },
    {
      id: 'effect',
      header: 'Efecto',
      accessorFn: (note) => creditNoteEffectOf(note),
      enableSorting: false,
      cell: ({ row }) => {
        const effect = STAFF_EFFECT_LABELS[creditNoteEffectOf(row.original)];
        return <StatusPill tone={effect.tone}>{effect.label}</StatusPill>;
      },
    },
    {
      id: 'total',
      header: 'Total',
      accessorFn: (note) => Number(note.totalAmount.amount),
      cell: ({ row }) => <span className="font-semibold tabular-nums text-ink">{formatMoney(row.original.totalAmount)}</span>,
      meta: { align: 'right' },
    },
  ];
}

export function StudentCreditNotesSection({ studentId, invoices }) {
  const [open, setOpen] = useState(false);
  const { data, isLoading, isError, refetch } = useStudentCreditNotes(studentId);
  const count = data?.length;
  const invoiceLinesOf = (note) => invoices.find((invoice) => invoice.id === note.invoiceId)?.lines ?? [];

  return (
    <section aria-labelledby="student-credit-notes-heading">
      <GlassCard className="overflow-hidden">
        <button
          type="button"
          aria-expanded={open}
          aria-controls="student-credit-notes-body"
          onClick={() => setOpen((value) => !value)}
          className="flex w-full items-center justify-between gap-3 px-5 py-4 text-left"
        >
          <h2 id="student-credit-notes-heading" className="font-heading text-base font-semibold text-ink">
            {count == null ? 'Notas de crédito del alumno' : `Notas de crédito del alumno · ${count}`}
          </h2>
          <ChevronDown className={cn('size-4 shrink-0 text-ink-faint transition-transform', open && 'rotate-180')} aria-hidden="true" />
        </button>
        {open && (
          <div id="student-credit-notes-body" className="border-t border-border md:px-3 md:pb-2">
            <DataTable
              columns={buildColumns()}
              data={data}
              getRowId={(note) => String(note.id)}
              isLoading={isLoading}
              isError={isError}
              onRetry={refetch}
              errorMessage="No pudimos cargar las notas de crédito."
              initialSorting={[{ id: 'number', desc: true }]}
              minWidth={640}
              expandLabel="Ver detalle de la nota"
              renderExpanded={(row) => <CreditNoteDetail note={row.original} invoiceLines={invoiceLinesOf(row.original)} />}
              emptyState={
                <EmptyState
                  title="Sin notas de crédito"
                  description="Si se anula una cuota o se corrige un importe, va a aparecer acá."
                />
              }
            />
          </div>
        )}
      </GlassCard>
    </section>
  );
}
