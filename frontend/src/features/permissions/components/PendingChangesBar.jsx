import { Button } from '@/components/ui/button';
import { describeChangeCount } from '@/features/permissions/lib/permissionGrid';

export function PendingChangesBar({ changeCount, moduleCount, onDiscard, onReview }) {
  return (
    <div role="region" aria-label="Cambios sin guardar" className="fixed inset-x-0 bottom-0 z-20 border-t border-border bg-surface px-4 py-2.5 pb-[calc(10px+env(safe-area-inset-bottom,0px))] shadow-overlay">
      <div className="mx-auto flex max-w-[1200px] flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-2.5 text-size-14 font-semibold text-ink" aria-live="polite">
          <span className="size-2.5 rounded-full bg-brand" aria-hidden="true" />
          {describeChangeCount(changeCount)} sin guardar
          <span className="font-medium text-ink-soft">
            en {moduleCount} {moduleCount === 1 ? 'módulo' : 'módulos'}
          </span>
        </div>
        <div className="flex gap-2 [&>button]:min-h-11 [&>button]:flex-1 sm:[&>button]:flex-none">
          <Button type="button" variant="ghost" onClick={onDiscard}>
            Descartar
          </Button>
          <Button type="button" variant="gradient" onClick={onReview}>
            Revisar y guardar
          </Button>
        </div>
      </div>
    </div>
  );
}
