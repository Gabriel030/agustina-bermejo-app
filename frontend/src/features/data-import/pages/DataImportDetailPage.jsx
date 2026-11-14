import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { LoadErrorState } from '@/components/LoadErrorState';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Stepper } from '@/components/Stepper';
import { ImportFilesStep } from '../components/ImportFilesStep';
import { ImportProgressPanel } from '../components/ImportProgressPanel';
import { ImportReportStep } from '../components/ImportReportStep';
import { ImportReviewStep } from '../components/ImportReviewStep';
import { useDataImport } from '../hooks/useDataImports';
import { BATCH_STATUS_LABELS, BATCH_STATUS_TONES, phaseOf } from '../importLabels';

const LIST_PATH = '/director/importacion-inicial';
const STEPS = [
  { key: 'files', label: 'Archivos' },
  { key: 'review', label: 'Revisión' },
];

function resolveView(batch, committing, editingFiles) {
  if (committing) return 'progress';
  const phase = phaseOf(batch.status);
  return phase === 'review' && editingFiles ? 'files' : phase;
}

export function DataImportDetailPage() {
  const { id } = useParams();
  const query = useDataImport(id);
  const [committing, setCommitting] = useState(false);
  const [editingFiles, setEditingFiles] = useState(false);

  if (query.isLoading) {
    return (
      <PageContainer>
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-64 w-full" />
      </PageContainer>
    );
  }
  if (query.isError) {
    return (
      <PageContainer>
        <LoadErrorState error={query.error} onRetry={query.refetch} resource="la importación" backTo={LIST_PATH} backLabel="Importación inicial" />
      </PageContainer>
    );
  }

  const batch = query.data;
  const view = resolveView(batch, committing, editingFiles);
  const showSteps = view === 'files' || view === 'review';

  return (
    <PageContainer
      title={batch.name}
      description="Revisá los archivos antes de importar. Nada se escribe hasta que confirmes."
      action={<StatusPill tone={BATCH_STATUS_TONES[batch.status]}>{BATCH_STATUS_LABELS[batch.status]}</StatusPill>}
    >
      <Link to={LIST_PATH} className="w-fit text-sm font-semibold text-primary hover:underline">
        Volver a Importación inicial
      </Link>
      {showSteps && <Stepper pasos={STEPS} pasoActual={view} />}
      {view === 'files' && <ImportFilesStep batch={batch} onValidated={() => setEditingFiles(false)} />}
      {view === 'review' && (
        <ImportReviewStep batch={batch} onBackToFiles={() => setEditingFiles(true)} onCommitting={setCommitting} />
      )}
      {view === 'progress' && <ImportProgressPanel batch={batch} />}
      {view === 'report' && <ImportReportStep batch={batch} />}
    </PageContainer>
  );
}
