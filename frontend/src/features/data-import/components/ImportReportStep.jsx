import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Download, RotateCcw } from 'lucide-react';
import { toast } from 'sonner';
import { GlassCard } from '@/components/GlassCard';
import { Notice } from '@/components/Notice';
import { StatCard } from '@/components/StatCard';
import { StickyActionBar } from '@/components/StickyActionBar';
import { Button } from '@/components/ui/button';
import { blockLabel } from '../importBlocks';
import { downloadImportReport, useRetryDataImport, useSealDataImport } from '../hooks/useDataImports';
import { ROLLBACK_STATUSES, ROW_STATUS_LABELS, countOf, saveBlobAs } from '../importLabels';
import { TypedConfirmDialog } from './TypedConfirmDialog';

const REPORT_STATUSES = ['COMMITTED', 'UNCHANGED', 'ERROR', 'FAILED'];
const READ_ONLY_STATUSES = ['SEALED', 'ROLLED_BACK'];

function formatDate(value) {
  return value ? new Date(value).toLocaleDateString('es-AR') : '';
}

function describeStatus(batch) {
  if (batch.status === 'SEALED') return `Cerrada el ${formatDate(batch.sealedAt)}. Queda como constancia y ya no se puede deshacer.`;
  if (batch.status === 'ROLLED_BACK') return 'Deshecha: lo que había creado se borró. La estructura académica se conserva.';
  return null;
}

function buildReportActions({ actions, canSeal, downloaded, partial }) {
  const available = [actions.download, ...(partial ? [actions.retry] : []), ...(canSeal ? [actions.seal] : [])];
  const primary = canSeal && downloaded ? actions.seal : partial ? actions.retry : actions.download;
  return {
    primary: { label: primary.label, disabled: primary.disabled, loading: primary.loading, loadingLabel: primary.label, onClick: primary.run },
    secondary: available
      .filter((action) => action.key !== primary.key)
      .map(({ key, label, icon, disabled, run }) => ({ key, label, icon, disabled, onSelect: run })),
  };
}

export function ImportReportStep({ batch }) {
  const [downloaded, setDownloaded] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [sealOpen, setSealOpen] = useState(false);
  const seal = useSealDataImport(batch.id);
  const retry = useRetryDataImport(batch.id);
  const failed = countOf(batch.counters, 'FAILED');
  const readOnly = READ_ONLY_STATUSES.includes(batch.status);
  const canRollback = ROLLBACK_STATUSES.includes(batch.status);
  const canSeal = batch.status === 'COMMITTED';
  const statusText = describeStatus(batch);

  async function handleDownload() {
    setDownloading(true);
    try {
      const { blob } = await downloadImportReport(batch.id);
      saveBlobAs(blob, 'reporte-importacion.csv');
      setDownloaded(true);
    } catch (error) {
      toast.error(error.message || 'No pudimos descargar el reporte. Probá de nuevo.');
    } finally {
      setDownloading(false);
    }
  }

  async function handleSeal(confirmation) {
    try {
      await seal.mutateAsync(confirmation);
      setSealOpen(false);
      toast.success('Importación cerrada.');
    } catch (error) {
      toast.error(error.message || 'No pudimos cerrar la importación.');
    }
  }

  async function handleRetry() {
    try {
      await retry.mutateAsync();
    } catch (error) {
      toast.error(error.message || 'No pudimos reintentar. Probá de nuevo.');
    }
  }

  const reportActions = buildReportActions({
    actions: {
      download: { key: 'download', label: 'Descargar reporte', icon: Download, disabled: downloading, loading: downloading, run: handleDownload },
      retry: { key: 'retry', label: 'Reintentar lo que falta', icon: RotateCcw, disabled: retry.isPending, loading: retry.isPending, run: handleRetry },
      seal: { key: 'seal', label: 'Cerrar la importación', disabled: !downloaded, run: () => setSealOpen(true) },
    },
    canSeal,
    downloaded,
    partial: batch.status === 'PARTIAL',
  });

  return (
    <>
      {statusText && <Notice>{statusText}</Notice>}
      {batch.status === 'PARTIAL' && (
        <Notice tone="warning">Una parte de las filas no se pudo importar. Reintentá lo que falta: sigue desde donde quedó y no duplica nada.</Notice>
      )}
      {batch.status === 'FAILED' && <Notice tone="warning">La importación no se pudo completar.</Notice>}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {REPORT_STATUSES.map((status) => (
          <StatCard key={status} label={ROW_STATUS_LABELS[status]}>
            <span className="font-heading text-3xl font-bold text-ink" data-testid={`report-${status}`}>
              {countOf(batch.counters, status).toLocaleString('es-AR')}
            </span>
          </StatCard>
        ))}
      </div>
      <GlassCard className="p-0">
        <ul className="divide-y divide-border" aria-label="Resultado por bloque">
          {batch.counters.map((block) => (
            <li key={block.entityType} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
              <span className="font-semibold text-ink">{blockLabel(block.entityType)}</span>
              <span className="text-sm text-ink-soft tabular-nums">
                {(block.rowsByStatus.COMMITTED ?? 0).toLocaleString('es-AR')} importadas
                {failed > 0 && ` · ${(block.rowsByStatus.FAILED ?? 0).toLocaleString('es-AR')} fallaron`}
              </span>
            </li>
          ))}
        </ul>
      </GlassCard>
      {canRollback && (
        <GlassCard className="flex flex-col gap-2 p-4">
          <h2 className="font-heading text-base font-semibold text-ink">Mientras no la cierres</h2>
          <p className="text-sm text-ink-soft">
            Podés deshacer lo que importó. Las carreras, materias, plan y correlativas se conservan. Si alguien ya trabajó sobre los datos importados, no se puede deshacer y te mostramos qué lo impide.
          </p>
          <Link to={`/director/importacion-inicial/${batch.id}/deshacer`} className="w-fit text-sm font-semibold text-primary hover:underline">
            Deshacer esta importación…
          </Link>
        </GlassCard>
      )}
      <StickyActionBar
        compact="menu"
        aria-label="Acciones del reporte"
        summary="Reporte de la importación"
        compactSummary="Reporte"
        hint={readOnly ? 'Solo lectura.' : canSeal && !downloaded ? 'Descargá el reporte para poder cerrar.' : undefined}
        compactHint={readOnly ? 'Solo lectura' : canSeal && !downloaded ? 'Descargá para cerrar' : undefined}
        primary={reportActions.primary}
        secondary={reportActions.secondary}
      />
      <TypedConfirmDialog
        open={sealOpen}
        onOpenChange={setSealOpen}
        title="Cerrar la importación"
        description="Después de cerrarla ya no se puede deshacer ni subir archivos. Esto es lo que queda definitivo:"
        word="CERRAR"
        confirmLabel="Cerrar la importación"
        pending={seal.isPending}
        onConfirm={handleSeal}
      >
        <ul className="list-disc space-y-1 pl-5 text-sm text-ink-soft">
          <li>{countOf(batch.counters, 'COMMITTED').toLocaleString('es-AR')} registros importados.</li>
          <li>El reporte descargado queda como constancia.</li>
        </ul>
      </TypedConfirmDialog>
    </>
  );
}
