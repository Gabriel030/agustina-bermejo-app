import { cn } from '@/lib/utils';

// Caja gris titilante para reemplazar "Cargando…" en requests que demoran —
// `animate-pulse` es utilidad nativa de Tailwind, sin librería nueva.
export function Skeleton({ className }) {
  return <div className={cn('animate-pulse rounded-lg bg-neutral-bg', className)} />;
}
