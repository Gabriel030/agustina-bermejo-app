import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';

export function BackupsLoading() {
  return (
    <div role="status" aria-busy="true" aria-label="Cargando el estado de los backups" className="flex flex-col gap-4">
      <GlassCard className="flex items-center gap-4 p-5">
        <Skeleton className="size-11" />
        <div className="flex flex-1 flex-col gap-2">
          <Skeleton className="h-5 w-3/4" />
          <Skeleton className="h-3.5 w-2/5" />
        </div>
      </GlassCard>
      <div className="grid grid-cols-1 gap-3 md:grid-cols-3 md:gap-4">
        {[1, 2, 3].map((item) => (
          <GlassCard key={item} className="flex flex-col gap-2 p-5">
            <Skeleton className="h-3 w-2/5" />
            <Skeleton className="h-6 w-3/5" />
            <Skeleton className="h-3 w-4/5" />
          </GlassCard>
        ))}
      </div>
      <GlassCard className="flex flex-col gap-3 p-5">
        <Skeleton className="h-4.5 w-1/3" />
        {[1, 2, 3, 4, 5].map((item) => (
          <Skeleton key={item} className="h-9 w-full" />
        ))}
      </GlassCard>
    </div>
  );
}
