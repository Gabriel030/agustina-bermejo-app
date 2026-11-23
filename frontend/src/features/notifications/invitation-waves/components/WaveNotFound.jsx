import { SearchX } from 'lucide-react';
import { Link } from 'react-router-dom';
import { describeError } from '@/api/describeError';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Button } from '@/components/ui/button';

export function WaveNotFound({ error, basePath }) {
  return (
    <PageContainer>
      <GlassCard className="flex flex-1 flex-col p-8 sm:p-12" role="alert">
        <EmptyState
          icon={
            <span className="flex size-14 items-center justify-center rounded-full bg-neutral-bg text-ink-faint" data-testid="wave-not-found-icon">
              <SearchX className="size-6" aria-hidden="true" />
            </span>
          }
          title={describeError(error).message}
          description="Puede que el enlace sea viejo. Volvé a la lista y buscala de nuevo."
          action={
            <Button asChild variant="primary" className="min-h-11 md:min-h-8">
              <Link to={basePath}>Volver a Invitaciones</Link>
            </Button>
          }
        />
      </GlassCard>
    </PageContainer>
  );
}
