import { Link } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';

export function NotFound() {
  return (
    <PageContainer>
      <GlassCard className="flex flex-1 flex-col p-12">
        <EmptyState
          title="Página no encontrada"
          description="La dirección que abriste no existe o fue movida."
          action={
            <Button asChild>
              <Link to="/">Volver al inicio</Link>
            </Button>
          }
        />
      </GlassCard>
    </PageContainer>
  );
}
