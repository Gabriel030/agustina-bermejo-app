import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Notice } from '@/components/Notice';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { StickyActionBar } from '@/components/StickyActionBar';
import { Button } from '@/components/ui/button';
import { TypedConfirmDialog } from '../components/TypedConfirmDialog';
import { useDataImport, useRollbackDataImport, useRollbackPreview } from '../hooks/useDataImports';
import { ROLLBACK_STATUSES, entityTypeLabel } from '../importLabels';

function detailPath(id) {
  return `/director/importacion-inicial/${id}`;
}

function CountList({ items, label }) {
  return (
    <ul className="grid gap-x-4 sm:grid-cols-2 lg:grid-cols-3" aria-label={label}>
      {items.map((item) => (
        <li key={item.entityType} className="flex justify-between gap-2 border-b border-border py-2">
          <span className="text-ink-soft">{entityTypeLabel(item.entityType)}</span>
          <b className="tabular-nums text-ink">{item.total.toLocaleString('es-AR')}</b>
        </li>
      ))}
    </ul>
  );
}

function BlockerList({ blockers }) {
  return (
    <GlassCard className="flex flex-col gap-2 p-4">
      <h2 className="font-heading text-base font-semibold text-ink">Qué lo impide</h2>
      <ul className="list-disc space-y-1 pl-5 text-sm text-ink-soft" aria-label="Motivos">
        {blockers.map((blocker, index) => (
          <li key={`${blocker.entityType}-${blocker.naturalKey}-${index}`}>
            {blocker.reason}
            {blocker.naturalKey && ` (${entityTypeLabel(blocker.entityType)}: ${blocker.naturalKey})`}
          </li>
        ))}
      </ul>
    </GlassCard>
  );
}

export function DataImportRollbackPage() {
  const { id } = useParams();
  const batchQuery = useDataImport(id);
  const eligible = ROLLBACK_STATUSES.includes(batchQuery.data?.status);
  const preview = useRollbackPreview(id, { enabled: eligible });
  const rollback = useRollbackDataImport(id);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [blockers, setBlockers] = useState([]);

  if (batchQuery.isLoading || (eligible && preview.isLoading)) {
    return (
      <PageContainer>
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-64 w-full" />
      </PageContainer>
    );
  }
  const loadError = batchQuery.isError ? batchQuery : preview.isError ? preview : null;
  if (loadError) {
    return (
      <PageContainer>
        <LoadErrorState error={loadError.error} onRetry={loadError.refetch} resource="la importación" backTo={detailPath(id)} backLabel="Volver al reporte" />
      </PageContainer>
    );
  }

  const batch = batchQuery.data;
  const data = preview.data;
  const shownBlockers = blockers.length > 0 ? blockers : (data?.blockers ?? []);
  const canRollback = eligible && data?.canRollback && blockers.length === 0;

  async function handleRollback(confirmation) {
    try {
      await rollback.mutateAsync(confirmation);
      toast.success('Importación deshecha.');
    } catch (error) {
      setConfirmOpen(false);
      if (error.code === 'IMPORT_ROLLBACK_BLOCKED') {
        setBlockers(error.details?.blockers ?? []);
        return;
      }
      toast.error(error.message || 'No pudimos deshacer la importación.');
    }
  }

  return (
    <PageContainer
      title="Deshacer la importación"
      description={`Se borra todo lo que creó «${batch.name}». Lo que ya existía antes no se toca.`}
    >
      <Link to={detailPath(id)} className="w-fit text-sm font-semibold text-primary hover:underline">
        Volver al reporte
      </Link>
      {!eligible && <Notice tone="warning">Esta importación ya no se puede deshacer.</Notice>}
      {eligible && shownBlockers.length > 0 && (
        <>
          <Notice tone="danger" title="No se puede deshacer: hay actividad posterior.">
            No se borró nada. Para poder deshacer, primero hay que resolver lo que sigue.
          </Notice>
          <BlockerList blockers={shownBlockers} />
        </>
      )}
      {eligible && data && (
        <>
          <GlassCard className="flex flex-col gap-2 p-4">
            <h2 className="font-heading text-base font-semibold text-ink">Se va a borrar</h2>
            <CountList items={data.created} label="Se va a borrar" />
          </GlassCard>
          {data.retained.length > 0 && (
            <GlassCard className="flex flex-col gap-2 p-4">
              <h2 className="font-heading text-base font-semibold text-ink">Se conserva</h2>
              <p className="text-sm text-ink-soft">La estructura académica se carga aparte y no se deshace.</p>
              <CountList items={data.retained} label="Se conserva" />
            </GlassCard>
          )}
        </>
      )}
      <StickyActionBar
        summary={canRollback ? 'Se puede deshacer' : 'No hay nada para deshacer'}
        hint={canRollback ? 'Se pide una confirmación escrita antes de borrar.' : undefined}
      >
        <Button type="button" variant="destructive" disabled={!canRollback} onClick={() => setConfirmOpen(true)}>
          Deshacer la importación
        </Button>
      </StickyActionBar>
      <TypedConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title="Deshacer la importación"
        description="Se borra lo que creó la importación, todo o nada. Las carreras, materias, plan y correlativas se conservan."
        word="DESHACER"
        confirmLabel="Deshacer la importación"
        pending={rollback.isPending}
        onConfirm={handleRollback}
      />
    </PageContainer>
  );
}
