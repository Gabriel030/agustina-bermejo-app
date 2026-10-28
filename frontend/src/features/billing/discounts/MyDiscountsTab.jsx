import { Gift, TriangleAlert } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { InfoTip } from '@/components/charts/InfoTip';
import { Button } from '@/components/ui/button';
import { useMyStudentDiscounts } from '../hooks/useStudentDiscounts';
import { MyDiscountCard } from './MyDiscountCard';

function CardsGrid({ discounts }) {
  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
      {discounts.map((discount) => (
        <MyDiscountCard key={discount.id} discount={discount} />
      ))}
    </div>
  );
}

export function MyDiscountsTab() {
  const query = useMyStudentDiscounts();

  if (query.isLoading) {
    return (
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3" aria-busy="true" aria-label="Cargando">
        {[1, 2, 3].map((item) => (
          <GlassCard key={item} className="flex flex-col gap-3 p-5">
            <Skeleton className="h-4 w-3/5" />
            <Skeleton className="h-7 w-2/5" />
            <Skeleton className="h-3 w-full" />
          </GlassCard>
        ))}
      </div>
    );
  }

  if (query.isError) {
    return (
      <GlassCard>
        <EmptyState
          icon={<TriangleAlert className="mx-auto size-6 text-ink-faint" />}
          title="No pudimos cargar tus descuentos"
          description="Revisá tu conexión e intentá de nuevo."
          action={<Button type="button" variant="outline" onClick={() => query.refetch()}>Reintentar</Button>}
        />
      </GlassCard>
    );
  }

  const discounts = query.data ?? [];
  if (!discounts.length) {
    return (
      <GlassCard>
        <EmptyState
          icon={<Gift className="mx-auto size-6 text-ink-faint" />}
          title="No tenés descuentos"
          description="Cuando te otorguen una beca o te inscribas con una promoción, la vas a ver acá."
        />
      </GlassCard>
    );
  }

  const live = discounts.filter((discount) => discount.status === 'ACTIVE');
  const past = discounts.filter((discount) => discount.status !== 'ACTIVE');

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center gap-2 text-sm text-ink-faint">
        <span>Si dos descuentos coinciden, se aplica el que más te ahorra.</span>
        <InfoTip>
          Los descuentos no se suman. En cada cuota se aplica el que deja el menor precio. Si en un mes gana otro, ese mes se pierde y no se corre al siguiente.
        </InfoTip>
      </div>
      {live.length > 0 && (
        <section className="flex flex-col gap-3" aria-labelledby="my-discounts-live">
          <h2 id="my-discounts-live" className="font-heading text-base font-semibold text-ink">Vigentes</h2>
          <CardsGrid discounts={live} />
        </section>
      )}
      {past.length > 0 && (
        <section className="flex flex-col gap-3" aria-labelledby="my-discounts-history">
          <h2 id="my-discounts-history" className="font-heading text-base font-semibold text-ink">Historial</h2>
          <CardsGrid discounts={past} />
        </section>
      )}
    </div>
  );
}
