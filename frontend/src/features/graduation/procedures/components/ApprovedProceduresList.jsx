import { Download, RefreshCw } from 'lucide-react';
import { toast } from 'sonner';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { describeError } from '@/api/describeError';
import { formatearFechaCorta } from '@/lib/avisos';
import { saveBlob } from '../../certificates/lib/saveBlob';
import { useApprovedProcedurePdf } from '../hooks/useStudentProcedures';
import { APPROVED_STATUS_PILL, isRegeneratable, procedureOf } from '../lib/procedures';

function ApprovedRow({ item, downloading, onDownload, onRegenerate }) {
  const pill = APPROVED_STATUS_PILL[item.status];
  return (
    <li className="flex flex-wrap items-center gap-3 px-4 py-3.5">
      <div className="flex min-w-[200px] flex-1 flex-col gap-0.5">
        <span className="font-semibold text-ink">{item.title}</span>
        <span className="text-sm text-ink-soft">
          {item.detail ? `${item.detail} · ` : ''}emitida el {formatearFechaCorta(item.issuedAt)}
        </span>
      </div>
      <StatusPill tone={pill.tone}>{pill.label}</StatusPill>
      {item.status === 'VALID' && (
        <Button variant="outline" className="w-full sm:w-auto" loading={downloading} onClick={() => onDownload(item)}>
          {!downloading && <Download aria-hidden="true" />}
          Descargar PDF
        </Button>
      )}
      {isRegeneratable(item) && procedureOf(item.documentType) && (
        <Button variant="outline" className="w-full sm:w-auto" onClick={() => onRegenerate(item.documentType)}>
          <RefreshCw aria-hidden="true" />
          Generar de nuevo
        </Button>
      )}
    </li>
  );
}

export function ApprovedProceduresList({ items, onRegenerate }) {
  const downloadPdf = useApprovedProcedurePdf();

  async function download(item) {
    try {
      const { blob, filename } = await downloadPdf.mutateAsync({ documentType: item.documentType, id: item.id });
      saveBlob(blob, filename);
    } catch (error) {
      toast.error(describeError(error).message);
    }
  }

  if (items.length === 0) {
    return (
      <GlassCard className="p-4">
        <EmptyState title="Todavía no tenés trámites aprobados" description="Las constancias que generes aparecen acá." />
      </GlassCard>
    );
  }
  return (
    <GlassCard className="p-0">
      <ul className="flex flex-col divide-y divide-border">
        {items.map((item) => (
          <ApprovedRow
            key={`${item.documentType}-${item.id}`}
            item={item}
            downloading={downloadPdf.isPending && downloadPdf.variables?.id === item.id}
            onDownload={download}
            onRegenerate={onRegenerate}
          />
        ))}
      </ul>
    </GlassCard>
  );
}
