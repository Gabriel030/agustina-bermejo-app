import { useCallback, useState } from 'react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Paginador } from '@/components/Paginador';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { describeError } from '@/api/describeError';
import { EligibleBatchConfirmDialog } from '@/features/graduation/certificates/components/EligibleBatchConfirmDialog';
import { EligibleBatchResult } from '@/features/graduation/certificates/components/EligibleBatchResult';
import { EligibleGraduatesTable } from '@/features/graduation/certificates/components/EligibleGraduatesTable';
import { IssuedCertificatesTable } from '@/features/graduation/certificates/components/IssuedCertificatesTable';
import { EligiblePreviewDialog } from '@/features/graduation/certificates/components/EligiblePreviewDialog';
import {
  useEligibleGraduates,
  useIssueGraduationBatch,
  usePreviewGraduationCertificate,
} from '@/features/graduation/certificates/hooks/useStaffGraduationCertificates';
import {
  BATCH_LIMIT,
  KIND_TABS,
  batchItemOf,
  eligibleKey,
  isSelectable,
} from '@/features/graduation/certificates/lib/staffGraduationCertificates';

export function StaffGraduationCertificates() {
  const [view, setView] = useState('eligible');
  const [kind, setKind] = useState('PROGRAM');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState(() => new Map());
  const [issuance, setIssuance] = useState(null);
  const [batchResult, setBatchResult] = useState(null);
  const [preview, setPreview] = useState(null);
  const [actionError, setActionError] = useState(null);

  const eligible = useEligibleGraduates({ kind, query, page });
  const issueBatch = useIssueGraduationBatch();
  const previewCertificate = usePreviewGraduationCertificate();

  const rows = eligible.data?.content ?? [];
  const totalPages = eligible.data?.totalPages ?? 1;
  const canSelectMore = selected.size < BATCH_LIMIT;

  const changeKind = useCallback((nextKind) => {
    setKind(nextKind);
    setPage(1);
    setSelected(new Map());
    setBatchResult(null);
    setActionError(null);
  }, []);

  const changeQuery = useCallback((nextQuery) => {
    setQuery(nextQuery);
    setPage(1);
    setSelected(new Map());
  }, []);

  const toggleRow = useCallback((row) => {
    if (!isSelectable(row)) return;
    const key = eligibleKey(row);
    setSelected((current) => {
      const next = new Map(current);
      if (next.has(key)) next.delete(key);
      else if (next.size < BATCH_LIMIT) next.set(key, row);
      return next;
    });
  }, []);

  const closePreview = useCallback(() => {
    if (preview) URL.revokeObjectURL(preview.url);
    setPreview(null);
  }, [preview]);

  const openPreview = useCallback(
    async (row) => {
      setActionError(null);
      try {
        const { blob } = await previewCertificate.mutateAsync(batchItemOf(row));
        if (preview) URL.revokeObjectURL(preview.url);
        setPreview({ url: URL.createObjectURL(blob), row });
      } catch (error) {
        setActionError(describeError(error).message);
      }
    },
    [previewCertificate, preview],
  );

  const confirmIssuance = useCallback(async () => {
    const rowsToIssue = issuance;
    try {
      const response = await issueBatch.mutateAsync(rowsToIssue.map(batchItemOf));
      setBatchResult({ response, rows: rowsToIssue });
      setSelected(new Map());
      setActionError(null);
    } catch (error) {
      setActionError(describeError(error).message);
    } finally {
      setIssuance(null);
    }
  }, [issuance, issueBatch]);

  const selectedRows = [...selected.values()];

  const panel = (
    <div className="flex flex-col gap-4">
      {batchResult && (
        <EligibleBatchResult response={batchResult.response} rows={batchResult.rows} onDismiss={() => setBatchResult(null)} />
      )}

      {actionError && (
        <p role="alert" className="text-size-14 font-medium text-danger">
          {actionError}
        </p>
      )}

      <GlassCard className="flex flex-col gap-3 p-3 md:p-4">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <Input
            type="search"
            aria-label="Buscar por nombre o documento"
            placeholder="Buscar por nombre o documento"
            value={query}
            onChange={(event) => changeQuery(event.target.value)}
            className="md:max-w-xs"
          />
          <div className="flex flex-wrap items-center justify-between gap-3 md:justify-end">
            <span className="text-size-13 text-ink-faint">
              {selected.size} seleccionados · máximo {BATCH_LIMIT}
            </span>
            <Button type="button" variant="primary" disabled={selected.size === 0} onClick={() => setIssuance(selectedRows)}>
              Emitir seleccionados
            </Button>
          </div>
        </div>

        <EligibleGraduatesTable
          rows={rows}
          query={query}
          isLoading={eligible.isPending}
          isError={eligible.isError}
          onRetry={eligible.refetch}
          selected={selected}
          canSelectMore={canSelectMore}
          onToggle={toggleRow}
          onPreview={openPreview}
          onIssue={(row) => setIssuance([row])}
        />
        <Paginador page={page} totalPages={totalPages} onChange={setPage} />
      </GlassCard>
    </div>
  );

  return (
    <PageContainer title="Egresos" description="Certificados de egreso">
      <Tabs value={view} onValueChange={setView} className="gap-4">
        <TabsList variant="segmented">
          <TabsTrigger value="eligible">Por emitir</TabsTrigger>
          <TabsTrigger value="issued">Emitidos</TabsTrigger>
        </TabsList>
        <TabsContent value="eligible" className="mt-0 flex flex-col gap-4">
          <Tabs value={kind} onValueChange={changeKind} className="gap-4">
            <TabsList variant="segmented">
              {KIND_TABS.map((tab) => (
                <TabsTrigger key={tab.value} value={tab.value}>
                  {tab.label}
                </TabsTrigger>
              ))}
            </TabsList>
            {KIND_TABS.map((tab) => (
              <TabsContent key={tab.value} value={tab.value} className="hidden" />
            ))}
          </Tabs>
          {panel}
        </TabsContent>
        <TabsContent value="issued" className="mt-0">
          <IssuedCertificatesTable />
        </TabsContent>
      </Tabs>

      <EligibleBatchConfirmDialog
        rows={issuance}
        pending={issueBatch.isPending}
        onCancel={() => setIssuance(null)}
        onConfirm={confirmIssuance}
      />
      <EligiblePreviewDialog preview={preview} onClose={closePreview} />
    </PageContainer>
  );
}
