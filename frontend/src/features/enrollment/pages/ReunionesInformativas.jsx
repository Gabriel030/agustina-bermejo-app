import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { InformativeSessionCard } from '@/features/enrollment/components/InformativeSessionCard';
import { useCatalogPrograms } from '@/features/enrollment/hooks/useCatalogo';
import { useInformativeSessions } from '@/features/enrollment/hooks/useInformativeSessions';

const GRID = 'grid grid-cols-1 gap-3 md:grid-cols-2';

export function ReunionesInformativas() {
  const { data: sessions, isLoading, isError, error, refetch } = useInformativeSessions();
  const { data: programs } = useCatalogPrograms();
  const programNames = new Map((programs ?? []).map((program) => [program.id, program.name]));

  return (
    <PageContainer title="Reuniones informativas" description="Conocé la oferta con quienes la dictan y reservá tu lugar.">
      {isLoading ? (
        <div className={GRID} aria-busy="true">
          <Skeleton className="h-40" />
          <Skeleton className="h-40" />
        </div>
      ) : isError ? (
        <LoadErrorState error={error} onRetry={refetch} resource="las reuniones" distinguishForbidden />
      ) : sessions.length === 0 ? (
        <GlassCard className="flex flex-1 flex-col p-12">
          <EmptyState title="Todavía no hay reuniones programadas" description="Cuando se programe una, la vas a ver acá." />
        </GlassCard>
      ) : (
        <div className={GRID}>
          {sessions.map((session) => (
            <InformativeSessionCard key={session.id} session={session} programName={programNames.get(session.relatedProgramId)} />
          ))}
        </div>
      )}
    </PageContainer>
  );
}
