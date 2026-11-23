import { useMemo, useState } from 'react';
import { Download } from 'lucide-react';
import { toast } from 'sonner';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { StatCard } from '@/components/StatCard';
import { StatusPill } from '@/components/StatusPill';
import { StickyActionBar } from '@/components/StickyActionBar';
import { DataTable } from '@/components/table/DataTable';
import { Button } from '@/components/ui/button';
import { blockLabel } from '../importBlocks';
import { ROW_STATUS_LABELS, countOf, downloadTextFile, toCsv, totalRows } from '../importLabels';
import { useCommitDataImport, useImportRowErrors } from '../hooks/useDataImports';

const PAGE_SIZE = 50;

const ERROR_COLUMNS = [
  {
    id: 'entityType',
    header: 'Archivo',
    accessorFn: (error) => error.entityType,
    cell: ({ row }) => blockLabel(row.original.entityType),
    meta: { filterable: true, filterTitle: 'archivo', filterLabel: blockLabel },
  },
  {
    id: 'rowNumber',
    header: 'Fila',
    accessorFn: (error) => error.rowNumber,
    cell: ({ row }) => <span className="tabular-nums">{row.original.rowNumber}</span>,
    meta: { align: 'right' },
  },
  {
    id: 'column',
    header: 'Columna',
    accessorFn: (error) => error.column,
    cell: ({ row }) => row.original.column || '—',
  },
  {
    id: 'code',
    header: 'Tipo',
    accessorFn: (error) => error.code,
    cell: ({ row }) => <StatusPill tone={row.original.rowStatus === 'CONFLICT' ? 'warning' : 'danger'}>{row.original.code}</StatusPill>,
    meta: { filterable: true, filterTitle: 'tipo', filterLabel: (code) => code },
  },
  {
    id: 'message',
    header: 'Qué está mal',
    accessorFn: (error) => error.message,
    meta: { cardTitle: true },
  },
];

function ReviewSummary({ counters }) {
  const cards = [
    { status: 'VALID', tone: 'text-success' },
    { status: 'ERROR', tone: 'text-danger' },
    { status: 'UNCHANGED', tone: 'text-ink' },
    { status: 'CONFLICT', tone: 'text-warning' },
  ];
  return (
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      {cards.map(({ status, tone }) => (
        <StatCard key={status} label={ROW_STATUS_LABELS[status]}>
          <span className={`font-heading text-3xl font-bold ${tone}`} data-testid={`count-${status}`}>
            {countOf(counters, status).toLocaleString('es-AR')}
          </span>
        </StatCard>
      ))}
    </div>
  );
}

function BlockResults({ counters }) {
  if (counters.length === 0) return null;
  return (
    <GlassCard className="p-0">
      <ul className="divide-y divide-border" aria-label="Resultado por bloque">
        {counters.map((block) => (
          <li key={block.entityType} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
            <span className="font-semibold text-ink">{blockLabel(block.entityType)}</span>
            <span className="text-sm text-ink-soft tabular-nums">
              {totalRows(block).toLocaleString('es-AR')} filas · {(block.rowsByStatus.VALID ?? 0).toLocaleString('es-AR')} correctas ·{' '}
              {(block.rowsByStatus.ERROR ?? 0).toLocaleString('es-AR')} con error
            </span>
          </li>
        ))}
      </ul>
    </GlassCard>
  );
}

function summaryText(batch) {
  const created = batch.counters.map((block) => `${(block.rowsByStatus.VALID ?? 0).toLocaleString('es-AR')} ${blockLabel(block.entityType).toLowerCase()}`);
  return `Se van a crear ${created.join(', ')}.`;
}

export function ImportReviewStep({ batch, onBackToFiles, onCommitting }) {
  const [size, setSize] = useState(PAGE_SIZE);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const commit = useCommitDataImport(batch.id);
  const errorCount = countOf(batch.counters, 'ERROR') + countOf(batch.counters, 'CONFLICT');
  const blocked = batch.status === 'VALIDATION_FAILED';
  const errors = useImportRowErrors(batch.id, { size, enabled: errorCount > 0 });
  const columns = useMemo(() => ERROR_COLUMNS, []);
  const items = errors.data?.items ?? [];
  const total = errors.data?.totalElements ?? 0;

  async function handleCommit() {
    setConfirmOpen(false);
    onCommitting(true);
    try {
      await commit.mutateAsync({ onlyValidRows: false });
    } catch (error) {
      toast.error(error.message || 'No pudimos importar. Probá de nuevo.');
    } finally {
      onCommitting(false);
    }
  }

  function handleDownloadErrors() {
    const header = ['Archivo', 'Fila', 'Columna', 'Tipo', 'Qué está mal'];
    const rows = items.map((error) => [blockLabel(error.entityType), error.rowNumber, error.column, error.code, error.message]);
    downloadTextFile('errores-importacion.csv', toCsv([header, ...rows]));
  }

  return (
    <>
      <ReviewSummary counters={batch.counters} />
      <BlockResults counters={batch.counters} />
      {errorCount > 0 && (
        <GlassCard className="flex flex-col gap-3 p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="font-heading text-base font-semibold text-ink">Qué corregir</h2>
            <Button className="min-h-11 md:min-h-8" type="button" variant="outline" size="sm" onClick={handleDownloadErrors} disabled={items.length === 0}>
              <Download className="size-4" aria-hidden="true" />
              Descargar errores
            </Button>
          </div>
          <DataTable
            columns={columns}
            data={items}
            getRowId={(error) => `${error.entityType}-${error.rowNumber}-${error.column}-${error.code}`}
            isLoading={errors.isLoading}
            isError={errors.isError}
            onRetry={errors.refetch}
            errorMessage="No pudimos cargar los errores."
            emptyState={<EmptyState icon="✅" title="No hay errores para mostrar" />}
            pageSize={PAGE_SIZE}
            minWidth={720}
          />
          <div className="flex flex-wrap items-center justify-between gap-2 px-1 text-sm text-ink-soft">
            <span>
              Mostrando {items.length} de {total}
            </span>
            {items.length < total && (
              <Button className="min-h-11 md:min-h-8" type="button" variant="ghost" size="sm" onClick={() => setSize((current) => current + PAGE_SIZE)}>
                Ver más
              </Button>
            )}
          </div>
        </GlassCard>
      )}
      <StickyActionBar
        summary={blocked ? 'Importar está bloqueado' : 'Todo listo para importar'}
        hint={blocked ? `Hay ${errorCount.toLocaleString('es-AR')} filas para corregir en los archivos.` : summaryText(batch)}
      >
        <Button className="min-h-11 md:min-h-8" type="button" variant="outline" onClick={onBackToFiles}>
          Volver a los archivos
        </Button>
        <Button className="min-h-11 md:min-h-8" type="button" disabled={blocked} loading={commit.isPending} onClick={() => setConfirmOpen(true)}>
          Importar
        </Button>
      </StickyActionBar>
      <ConfirmActionDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title="Importar ahora"
        description={summaryText(batch)}
        confirmLabel="Importar"
        confirmVariant="default"
        pending={commit.isPending}
        onConfirm={handleCommit}
      >
        <ul className="list-disc space-y-1 pl-5 text-sm text-ink-soft">
          <li>La facturación no se toca: tiene que estar apagada.</li>
          <li>No se envía ningún aviso ni mail.</li>
          <li>Se importa por partes y no se puede pausar.</li>
        </ul>
      </ConfirmActionDialog>
    </>
  );
}
