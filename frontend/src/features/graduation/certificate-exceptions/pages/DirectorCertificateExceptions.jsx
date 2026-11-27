import { useMemo, useState } from 'react';
import { FilePlus2 } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { TabCount } from '@/components/TabCount';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { CreateDirectExceptionDialog } from '@/features/graduation/certificate-exceptions/components/CreateDirectExceptionDialog';
import { DecideExceptionDialog } from '@/features/graduation/certificate-exceptions/components/DecideExceptionDialog';
import { ExceptionsTable } from '@/features/graduation/certificate-exceptions/components/ExceptionsTable';
import { DIRECTOR_EXCEPTIONS_PATH, PendingExceptionRow } from '@/features/graduation/certificate-exceptions/components/PendingExceptionRow';
import { useCertificateExceptions, useDescribedExceptions } from '@/features/graduation/certificate-exceptions/hooks/useCertificateExceptions';

const detailPath = (id) => `${DIRECTOR_EXCEPTIONS_PATH}/${id}`;

function PendingList({ query, rows, onApprove, onReject }) {
  if (query.isLoading) return <Skeleton className="h-24 w-full" />;
  if (query.isError) {
    return (
      <EmptyState
        title="No pudimos cargar las excepciones"
        action={
          <Button type="button" variant="outline" className="min-h-11 md:min-h-0" onClick={() => query.refetch()}>
            Reintentar
          </Button>
        }
      />
    );
  }
  if (!rows?.length) return <EmptyState title="Ninguna excepción esperando tu decisión" />;
  return (
    <div className="flex flex-col gap-1">
      {rows.map((exception) => (
        <PendingExceptionRow key={exception.id} exception={exception} onApprove={onApprove} onReject={onReject} />
      ))}
    </div>
  );
}

export function DirectorCertificateExceptions() {
  const all = useCertificateExceptions();
  const { rows, requests } = useDescribedExceptions(all.data);
  const [tab, setTab] = useState('pendientes');
  const [dialog, setDialog] = useState(null);
  const pendingRows = useMemo(() => rows?.filter((exception) => exception.status === 'PENDING'), [rows]);
  const historyRows = useMemo(() => rows?.filter((exception) => exception.status !== 'PENDING'), [rows]);

  return (
    <PageContainer
      title="Excepciones por reclamo"
      description="Alumnos con deuda que reclamaron ante un organismo"
      action={
        <Button type="button" variant="outline" className="min-h-11 md:min-h-0" onClick={() => setDialog({ kind: 'create' })}>
          <FilePlus2 aria-hidden="true" />
          Crear excepción
        </Button>
      }
    >
      <Tabs className="gap-4" value={tab} onValueChange={setTab}>
        <TabsList variant="line">
          <TabsTrigger value="pendientes">
            Pendientes <TabCount count={pendingRows?.length} />
          </TabsTrigger>
          <TabsTrigger value="historial">Historial</TabsTrigger>
        </TabsList>
        <TabsContent value="pendientes" className="mt-0">
          <GlassCard className="p-3 md:p-4">
            <PendingList
              query={all}
              rows={pendingRows}
              onApprove={(exception) => setDialog({ kind: 'approve', exception })}
              onReject={(exception) => setDialog({ kind: 'reject', exception })}
            />
          </GlassCard>
        </TabsContent>
        <TabsContent value="historial" className="mt-0">
          <GlassCard className="p-3 md:p-4">
            <ExceptionsTable
              rows={historyRows}
              query={all}
              detailPath={detailPath}
              showRequester
              errorMessage="No pudimos cargar el historial"
              emptyState={<EmptyState title="Todavía no hay excepciones decididas" />}
            />
          </GlassCard>
        </TabsContent>
      </Tabs>
      {dialog?.kind === 'create' && <CreateDirectExceptionDialog requests={requests.data} onClose={() => setDialog(null)} />}
      {(dialog?.kind === 'approve' || dialog?.kind === 'reject') && (
        <DecideExceptionDialog mode={dialog.kind} exception={dialog.exception} onClose={() => setDialog(null)} />
      )}
    </PageContainer>
  );
}
