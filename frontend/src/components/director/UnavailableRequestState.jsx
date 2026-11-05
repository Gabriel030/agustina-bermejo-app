import { Link } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { PageContainer } from '@/components/PageContainer';
import { Button } from '@/components/ui/button';
import { LoadErrorState } from '@/components/LoadErrorState';

export function UnavailableRequestState({ error, backTo, backLabel, onRetry }) {
  const notFound = error?.status === 404 || error?.status === 400;
  if (!notFound) {
    return (
      <PageContainer>
        <LoadErrorState error={error} onRetry={onRetry} resource="el trámite" backTo={backTo} backLabel={backLabel} />
      </PageContainer>
    );
  }
  return (
    <PageContainer>
      <GlassCard className="flex flex-1 flex-col p-12">
        <EmptyState
          title="Este trámite no existe o ya no está disponible"
          description="Revisá el enlace o volvé a la bandeja."
          action={
            <Button asChild variant="outline">
              <Link to={backTo}>Volver a {backLabel}</Link>
            </Button>
          }
        />
      </GlassCard>
    </PageContainer>
  );
}
