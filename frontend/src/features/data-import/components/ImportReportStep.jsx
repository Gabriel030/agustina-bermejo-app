import { Download } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Notice } from '@/components/Notice';
import { StatCard } from '@/components/StatCard';
import { StickyActionBar } from '@/components/StickyActionBar';
import { Button } from '@/components/ui/button';
import { blockLabel } from '../importBlocks';
import { ROW_STATUS_LABELS, countOf, downloadTextFile, toCsv } from '../importLabels';

const REPORT_STATUSES = ['COMMITTED', 'UNCHANGED', 'ERROR', 'FAILED'];

function buildReportCsv(batch) {
  const header = ['Bloque', ...REPORT_STATUSES.map((status) => ROW_STATUS_LABELS[status])];
  const rows = batch.counters.map((block) => [blockLabel(block.entityType), ...REPORT_STATUSES.map((status) => block.rowsByStatus[status] ?? 0)]);
  return toCsv([header, ...rows]);
}

export function ImportReportStep({ batch }) {
  const failed = countOf(batch.counters, 'FAILED');
  return (
    <>
      {batch.status === 'PARTIAL' && (
        <Notice tone="warning">Una parte de las filas no se pudo importar. Revisá el reporte antes de seguir.</Notice>
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
      <StickyActionBar summary="Reporte de la importación" hint="Solo lectura.">
        <Button type="button" variant="outline" onClick={() => downloadTextFile('reporte-importacion.csv', buildReportCsv(batch))}>
          <Download className="size-4" aria-hidden="true" />
          Descargar reporte
        </Button>
      </StickyActionBar>
    </>
  );
}
