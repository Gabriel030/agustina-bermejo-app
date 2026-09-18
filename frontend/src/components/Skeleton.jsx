import { cn } from '@/lib/utils';

// Caja gris con un barrido de brillo (shimmer, ver .skeleton-shimmer en
// index.css) para reemplazar "Cargando…" en requests que demoran.
export function Skeleton({ className }) {
  return <div className={cn('skeleton-shimmer rounded-lg bg-neutral-bg', className)} />;
}
