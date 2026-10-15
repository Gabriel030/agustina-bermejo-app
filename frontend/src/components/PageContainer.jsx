import { cn } from '@/lib/utils';

// Patrón único de ancho para el contenido dentro de <main> (RoleShell) —
// feedback explícito del usuario: Catálogo, Detalle y el wizard de
// inscripción tenían cada uno su propio max-width (o ninguno), y pasar de
// uno a otro se sentía como si la pantalla "se achicara". Todas las páginas
// de contenido (no los placeholders, que centran su propio mensaje) usan
// este mismo ancho máximo.
const ANCHO_MAXIMO = 'max-w-[1200px]';

export function PageContainer({ children, className, title, action }) {
  return (
    <div className={cn('mx-auto flex min-w-0 w-full flex-1 flex-col gap-6', ANCHO_MAXIMO, className)}>
      {(title || action) && (
        <div className="flex flex-wrap items-center justify-between gap-3">
          {title && <h1 className="font-heading text-2xl font-bold tracking-tight text-ink">{title}</h1>}
          {action}
        </div>
      )}
      {children}
    </div>
  );
}
