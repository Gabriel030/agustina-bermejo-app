import { Link } from 'react-router-dom';
import { CheckCircle2, Check } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { Button } from '@/components/ui/button';
import { TooltipProvider } from '@/components/ui/tooltip';
import { useAuth } from '@/context/AuthContext';
import { HomeTaskCard, ComingSoonCard } from '@/features/workspace/components/HomeTaskCard';
import { UpcomingEventsCard } from '@/features/workspace/components/UpcomingEventsCard';
import { splitCards } from '@/features/workspace/lib/homeCards';
import { useAdministrativoHomeCards, useDirectorHomeCards } from '@/features/workspace/hooks/useStaffHomeCards';
import { useUpcomingEvents } from '@/features/workspace/hooks/useUpcomingEvents';

const AT_RISK_TIP = 'Se calcula con la analítica de asistencia. Lo vamos a habilitar cuando esté lista.';

function StaffHome({ roleLabel, cards, upcoming }) {
  const { usuario } = useAuth();
  const { pending, unsettled, clear, allClear } = splitCards(cards);
  const firstName = usuario?.nombre ?? '';

  return (
    <TooltipProvider>
      <PageContainer>
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="font-heading text-2xl font-semibold text-ink">{firstName ? `Hola, ${firstName}` : 'Hola'}</h1>
            <p className="mt-0.5 text-size-13-5 font-medium text-ink-faint">{roleLabel}</p>
          </div>
          {pending.length > 0 && (
            <Button asChild variant="gradient">
              <Link to={pending[0].to}>Empezar por lo más urgente</Link>
            </Button>
          )}
        </div>

        {allClear ? (
          <div className="flex flex-col items-center gap-2 rounded-card border border-border bg-surface px-4 py-10 text-center">
            <span className="grid h-16 w-16 place-items-center rounded-full bg-success-bg text-success-ink">
              <Check className="h-8 w-8" />
            </span>
            <h2 className="font-heading text-xl font-bold text-ink">Todo al día</h2>
            <p className="text-ink-soft">No tenés nada pendiente hoy.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {[...pending, ...unsettled].map((card) => (
              <HomeTaskCard key={card.id} card={card} />
            ))}
          </div>
        )}

        {!allClear && clear.length > 0 && (
          <div className="flex flex-wrap items-center gap-2 text-size-13 font-semibold text-success-ink">
            <span className="mr-1 text-size-11 font-bold uppercase tracking-wide text-ink-faint">Al día</span>
            <CheckCircle2 className="h-4 w-4" />
            {clear.map((card) => card.title).join(' · ')}: sin pendientes
          </div>
        )}

        <UpcomingEventsCard card={upcoming} />

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <ComingSoonCard title="Alumnos en riesgo" tip={AT_RISK_TIP} />
        </div>
      </PageContainer>
    </TooltipProvider>
  );
}

export function DirectorInicio() {
  return <StaffHome roleLabel="Director" cards={useDirectorHomeCards()} upcoming={useUpcomingEvents()} />;
}

export function AdministrativoInicio() {
  return <StaffHome roleLabel="Administrativo" cards={useAdministrativoHomeCards()} upcoming={useUpcomingEvents()} />;
}
