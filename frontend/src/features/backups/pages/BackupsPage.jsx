import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { PageContainer } from '@/components/PageContainer';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { BackupAlertsList } from '@/features/backups/components/BackupAlertsList';
import { BackupHowToRestore } from '@/features/backups/components/BackupHowToRestore';
import { BackupKeyFacts } from '@/features/backups/components/BackupKeyFacts';
import { BackupRestoreAndDisk } from '@/features/backups/components/BackupRestoreAndDisk';
import { BackupSummaryCard } from '@/features/backups/components/BackupSummaryCard';
import { BackupTiersTable } from '@/features/backups/components/BackupTiersTable';
import { BackupWhatIsBackedUp } from '@/features/backups/components/BackupWhatIsBackedUp';
import { BackupsLoading } from '@/features/backups/components/BackupsLoading';
import { useBackupStatus } from '@/features/backups/hooks/useBackupStatus';
import { describeBackupState, formatBackupMoment } from '@/features/backups/lib/backupFormat';

const DESCRIPTION = 'Copias de la base y de los archivos del instituto.';

function BackupsContent({ status, isPlatformAdmin }) {
  const summary = describeBackupState(status);
  if (status.state === 'NOT_CONFIGURED') {
    return (
      <>
        <BackupSummaryCard {...summary} />
        <GlassCard className="flex flex-1 flex-col p-12">
          <EmptyState
            icon="🗄️"
            title="Todavía no hay backups"
            description="Cuando el servicio esté activo, acá vas a ver el último backup de cada nivel."
          />
        </GlassCard>
      </>
    );
  }
  const diskPath = isPlatformAdmin ? (status.disk?.path ?? null) : null;
  return (
    <>
      <BackupSummaryCard {...summary} />
      <BackupKeyFacts summary={status.summary} />
      <Tabs defaultValue="detail" className="gap-4">
        <TabsList variant="line" aria-label="Secciones de Backups">
          <TabsTrigger value="detail">Detalle</TabsTrigger>
          <TabsTrigger value="what">Qué se respalda</TabsTrigger>
          <TabsTrigger value="how">Cómo restaurar</TabsTrigger>
        </TabsList>
        <TabsContent value="detail" className="flex flex-col gap-4">
          <BackupTiersTable tiers={status.tiers} isPlatformAdmin={isPlatformAdmin} />
          <BackupRestoreAndDisk restoreTest={status.lastRestoreTest} disk={status.disk} isPlatformAdmin={isPlatformAdmin} />
          <BackupAlertsList alerts={status.alerts} restoreTest={status.lastRestoreTest} />
        </TabsContent>
        <TabsContent value="what">
          <BackupWhatIsBackedUp tiers={status.tiers} diskPath={diskPath} isPlatformAdmin={isPlatformAdmin} />
        </TabsContent>
        <TabsContent value="how">
          <BackupHowToRestore isPlatformAdmin={isPlatformAdmin} />
        </TabsContent>
      </Tabs>
    </>
  );
}

export function BackupsPage({ viewer }) {
  const query = useBackupStatus();
  const isPlatformAdmin = viewer === 'platform_admin';
  const stamp = query.data?.generatedAt ? (
    <span className="text-size-12 text-ink-soft tabular-nums">Estado al {formatBackupMoment(query.data.generatedAt)}</span>
  ) : null;

  let body;
  if (query.isLoading) body = <BackupsLoading />;
  else if (query.isError) body = <LoadErrorState error={query.error} onRetry={query.refetch} resource="el estado de los backups" />;
  else body = <BackupsContent status={query.data} isPlatformAdmin={isPlatformAdmin} />;

  return (
    <PageContainer title="Backups" description={DESCRIPTION} action={stamp}>
      {body}
    </PageContainer>
  );
}
