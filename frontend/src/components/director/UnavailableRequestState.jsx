import { Link } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { PageContainer } from '@/components/PageContainer';
import { Button } from '@/components/ui/button';

export function UnavailableRequestState({ error, backTo, backLabel }) {
  const notFound = error?.status === 404 || error?.status === 400;
  return (
    <PageContainer>
      <GlassCard className="flex flex-1 flex-col p-12">
        <EmptyState
          title={notFound ? 'Este trámite no existe o ya no está disponible' : 'No pudimos cargar el trámite'}
          description={notFound ? 'Revisá el enlace o volvé a la bandeja.' : 'Probá recargar la página en un momento.'}
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
