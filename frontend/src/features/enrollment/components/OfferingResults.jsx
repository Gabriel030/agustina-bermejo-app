import { Link } from 'react-router-dom';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { OfferingCard } from '@/features/enrollment/components/OfferingCard';
import { CATALOG_PATH, groupOfferingByArea } from '@/features/enrollment/lib/catalogOffering';

const GRID_CLASS = 'grid grid-cols-1 gap-3 md:grid-cols-2';

function OfferingGrid({ items, badges }) {
  return (
    <ul className={GRID_CLASS}>
      {items.map((item) => (
        <OfferingCard key={item.key} item={item} badge={badges[item.key]} />
      ))}
    </ul>
  );
}

function OfferingSkeleton() {
  return (
    <div className={GRID_CLASS} aria-busy="true" aria-label="Cargando la oferta">
      {Array.from({ length: 4 }).map((_, index) => (
        <Skeleton key={index} className="h-36" />
      ))}
    </div>
  );
}

export function OfferingResults({ status, items, badges, filtered, onRetry, error }) {
  if (status === 'loading') return <OfferingSkeleton />;
  if (status === 'error') return <LoadErrorState error={error} onRetry={onRetry} resource="la oferta" />;
  if (items.length === 0 && filtered) {
    return (
      <GlassCard className="flex flex-col p-6">
        <EmptyState
          title="No encontramos resultados"
          description="Revisá la ortografía o mirá toda la oferta."
          action={
            <Button asChild variant="primary" className="min-h-11">
              <Link to={CATALOG_PATH}>Ver toda la oferta</Link>
            </Button>
          }
        />
      </GlassCard>
    );
  }
  if (items.length === 0) {
    return (
      <GlassCard className="flex flex-col p-6">
        <EmptyState
          title="Todavía no hay oferta publicada"
          description="Cuando se publique una carrera o un curso, vas a poder verlo y anotarte desde acá."
        />
      </GlassCard>
    );
  }
  if (filtered) return <OfferingGrid items={items} badges={badges} />;
  return (
    <div className="flex flex-col gap-6">
      {groupOfferingByArea(items).map((group) => (
        <section key={group.key} aria-labelledby={`offering-area-${group.key}`} className="flex flex-col gap-3">
          <h2 id={`offering-area-${group.key}`} className="font-heading text-base font-semibold text-ink">
            {group.label}
          </h2>
          <OfferingGrid items={group.items} badges={badges} />
        </section>
      ))}
    </div>
  );
}
