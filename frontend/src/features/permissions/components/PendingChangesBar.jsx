import { StickyActionBar } from '@/components/StickyActionBar';
import { Button } from '@/components/ui/button';
import { describeChangeCount } from '@/features/permissions/lib/permissionGrid';

export function PendingChangesBar({ changeCount, moduleCount, onDiscard, onReview }) {
  return (
    <StickyActionBar
      role="region"
      aria-label="Cambios sin guardar"
      summary={<span aria-live="polite">{describeChangeCount(changeCount)} sin guardar</span>}
      hint={`en ${moduleCount} ${moduleCount === 1 ? 'módulo' : 'módulos'}`}
    >
      <Button type="button" variant="ghost" className="min-h-11" onClick={onDiscard}>
        Descartar
      </Button>
      <Button type="button" variant="primary" className="min-h-11" onClick={onReview}>
        Revisar y guardar
      </Button>
    </StickyActionBar>
  );
}
