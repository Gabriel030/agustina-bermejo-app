import { useCallback, useState } from 'react';
import { toast } from 'sonner';
import { describeError } from '@/api/describeError';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { GlassCard } from '@/components/GlassCard';
import { ModalMotivo } from '@/components/ModalMotivo';
import { Paginador } from '@/components/Paginador';
import { StickyActionBar } from '@/components/StickyActionBar';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { ImportedReviewBatchResult } from '@/features/graduation/certificates/components/ImportedReviewBatchResult';
import { ImportedReviewTable } from '@/features/graduation/certificates/components/ImportedReviewTable';
import {
  useDecideImportedReviewBatch,
  useDisableImportedReview,
  useEnableImportedReview,
  useImportedReviews,
} from '@/features/graduation/certificates/hooks/useImportedReview';
import {
  LOCKING_ERROR_CODE,
  REASON_MAX_LENGTH,
  REASON_MIN_LENGTH,
  REVIEW_BATCH_LIMIT,
  REVIEW_STATUS_TABS,
  batchDecisionDescription,
  batchDecisionTitle,
} from '@/features/graduation/certificates/lib/importedReview';

function addLocked(current, ids) {
  const next = new Set(current);
  ids.forEach((id) => next.add(id));
  return next;
}

export function ImportedReviewPanel() {
  const [status, setStatus] = useState('PENDING_REVIEW');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState(() => new Map());
  const [lockedIds, setLockedIds] = useState(() => new Set());
  const [disableTarget, setDisableTarget] = useState(null);
  const [enableRows, setEnableRows] = useState(null);
  const [batchResult, setBatchResult] = useState(null);
  const [dialogError, setDialogError] = useState(null);

  const reviews = useImportedReviews({ status, query, page, enabled: true });
  const enableReview = useEnableImportedReview();
  const disableReview = useDisableImportedReview();
  const decideBatch = useDecideImportedReviewBatch();

  const rows = reviews.data?.content ?? [];
  const totalPages = reviews.data?.totalPages ?? 1;
  const canSelectMore = selected.size < REVIEW_BATCH_LIMIT;
  const selectedRows = [...selected.values()];

  const changeStatus = useCallback((nextStatus) => {
    setStatus(nextStatus);
    setPage(1);
    setSelected(new Map());
    setBatchResult(null);
  }, []);

  const changeQuery = useCallback((nextQuery) => {
    setQuery(nextQuery);
    setPage(1);
    setSelected(new Map());
  }, []);

  const toggleRow = useCallback((row) => {
    setSelected((current) => {
      const next = new Map(current);
      if (next.has(row.graduationRequestId)) next.delete(row.graduationRequestId);
      else if (next.size < REVIEW_BATCH_LIMIT) next.set(row.graduationRequestId, row);
      return next;
    });
  }, []);

  const reportFailure = useCallback((error, rowsInvolved) => {
    if (error?.code === LOCKING_ERROR_CODE) {
      const lockedNow = rowsInvolved.map((row) => row.graduationRequestId);
      setLockedIds((current) => addLocked(current, lockedNow));
      setSelected((current) => {
        const next = new Map(current);
        lockedNow.forEach((id) => next.delete(id));
        return next;
      });
    }
    return describeError(error).message;
  }, []);

  const enableOne = useCallback(
    async (row) => {
      try {
        await enableReview.mutateAsync(row.graduationRequestId);
        toast.success(`Certificado de ${row.studentFullName} habilitado.`);
        setSelected(new Map());
      } catch (error) {
        toast.error(reportFailure(error, [row]));
      }
    },
    [enableReview, reportFailure],
  );

  const askDisableOne = useCallback((row) => {
    setDialogError(null);
    setDisableTarget({ rows: [row], isBatch: false });
  }, []);

  const askDisableBatch = useCallback(() => {
    setDialogError(null);
    setDisableTarget({ rows: [...selected.values()], isBatch: true });
  }, [selected]);

  const closeDisable = useCallback(() => {
    setDisableTarget(null);
    setDialogError(null);
  }, []);

  const finishBatch = useCallback((response, decision, involved) => {
    setBatchResult({ response, decision, rows: involved });
    setSelected(new Map());
    const locked = response.results.filter((item) => item.errorCode === LOCKING_ERROR_CODE).map((item) => item.graduationRequestId);
    if (locked.length > 0) setLockedIds((current) => addLocked(current, locked));
  }, []);

  const confirmDisable = useCallback(
    async (reason) => {
      const { rows: involved, isBatch } = disableTarget;
      try {
        if (!isBatch) {
          await disableReview.mutateAsync({ graduationRequestId: involved[0].graduationRequestId, reason });
          toast.success(`Certificado de ${involved[0].studentFullName} deshabilitado.`);
          setSelected(new Map());
        } else {
          const response = await decideBatch.mutateAsync({
            graduationRequestIds: involved.map((row) => row.graduationRequestId),
            decision: 'DISABLE',
            reason,
          });
          finishBatch(response, 'DISABLE', involved);
        }
        closeDisable();
        return true;
      } catch (error) {
        setDialogError(reportFailure(error, involved));
        return false;
      }
    },
    [disableTarget, disableReview, decideBatch, finishBatch, closeDisable, reportFailure],
  );

  const confirmEnableBatch = useCallback(async () => {
    const involved = enableRows;
    try {
      const response = await decideBatch.mutateAsync({
        graduationRequestIds: involved.map((row) => row.graduationRequestId),
        decision: 'ENABLE',
      });
      finishBatch(response, 'ENABLE', involved);
    } catch (error) {
      toast.error(reportFailure(error, involved));
    } finally {
      setEnableRows(null);
    }
  }, [enableRows, decideBatch, finishBatch, reportFailure]);

  const canBatchEnable = status !== 'ENABLED';
  const canBatchDisable = status !== 'DISABLED';
  const disableCount = disableTarget?.rows.length ?? 0;
  const enableCount = enableRows?.length ?? 0;

  return (
    <div className="flex flex-col gap-4">
      {batchResult && (
        <ImportedReviewBatchResult
          response={batchResult.response}
          decision={batchResult.decision}
          rows={batchResult.rows}
          onDismiss={() => setBatchResult(null)}
        />
      )}

      <Tabs value={status} onValueChange={changeStatus}>
        <TabsList variant="line">
          {REVIEW_STATUS_TABS.map((tab) => (
            <TabsTrigger key={tab.value} value={tab.value}>
              {tab.label}
            </TabsTrigger>
          ))}
        </TabsList>
        {REVIEW_STATUS_TABS.map((tab) => (
          <TabsContent key={tab.value} value={tab.value} className="hidden" />
        ))}
      </Tabs>

      <GlassCard className="flex flex-col gap-3 p-3 md:p-4">
        <Input
          type="search"
          aria-label="Buscar por nombre o documento"
          placeholder="Buscar por nombre o documento"
          value={query}
          onChange={(event) => changeQuery(event.target.value)}
          className="md:max-w-xs"
        />
        <ImportedReviewTable
          rows={rows}
          query={query}
          isLoading={reviews.isPending}
          isError={reviews.isError}
          onRetry={reviews.refetch}
          selected={selected}
          lockedIds={lockedIds}
          canSelectMore={canSelectMore}
          onToggle={toggleRow}
          onEnable={enableOne}
          onDisable={askDisableOne}
        />
        <Paginador page={page} totalPages={totalPages} onChange={setPage} />
      </GlassCard>

      {selected.size > 0 && (
        <StickyActionBar
          summary={`${selected.size} seleccionados`}
          hint={`Máximo ${REVIEW_BATCH_LIMIT} por vez`}
          aria-label="Acciones sobre la selección"
        >
          {canBatchDisable && (
            <Button type="button" variant="outline" className="min-h-11" onClick={askDisableBatch}>
              Deshabilitar seleccionados
            </Button>
          )}
          {canBatchEnable && (
            <Button type="button" variant="primary" className="min-h-11" onClick={() => setEnableRows(selectedRows)}>
              Habilitar seleccionados
            </Button>
          )}
        </StickyActionBar>
      )}

      {disableTarget && (
        <ModalMotivo
          open
          onClose={closeDisable}
          onConfirmar={confirmDisable}
          confirmando={disableReview.isPending || decideBatch.isPending}
          requerido
          titulo={disableCount > 1 ? `Deshabilitar ${disableCount} certificados` : 'Deshabilitar certificado'}
          descripcion={
            disableCount > 1
              ? 'El motivo se aplica a todos los certificados seleccionados.'
              : `Indicá por qué se deshabilita el certificado de ${disableTarget.rows[0].studentFullName}.`
          }
          placeholder="Motivo de la deshabilitación"
          confirmarLabel={disableCount > 1 ? `Deshabilitar ${disableCount}` : 'Deshabilitar'}
          confirmarVariant="destructive"
          minLength={REASON_MIN_LENGTH}
          maxLength={REASON_MAX_LENGTH}
          showCounter
          mensajeRequerido="El motivo es obligatorio para deshabilitar."
          errorMessage={dialogError}
        />
      )}

      <ConfirmActionDialog
        open={enableCount > 0}
        onOpenChange={(open) => !open && setEnableRows(null)}
        title={batchDecisionTitle('ENABLE', enableCount)}
        description={batchDecisionDescription('ENABLE', enableCount)}
        confirmLabel={enableCount === 1 ? 'Confirmar y habilitar' : `Confirmar y habilitar ${enableCount}`}
        confirmVariant="primary"
        pending={decideBatch.isPending}
        onConfirm={confirmEnableBatch}
      />
    </div>
  );
}
