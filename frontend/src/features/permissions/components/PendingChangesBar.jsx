import { StickyActionBar } from '@/components/StickyActionBar';
import { describeChangeCount } from '@/features/permissions/lib/permissionGrid';

export function PendingChangesBar({ changeCount, moduleCount, onDiscard, onReview }) {
  const moduleHint = `en ${moduleCount} ${moduleCount === 1 ? 'módulo' : 'módulos'}`;
  return (
    <StickyActionBar
      compact="menu"
      aria-label="Cambios sin guardar"
      summary={<span aria-live="polite">{describeChangeCount(changeCount)} sin guardar</span>}
      hint={moduleHint}
      compactSummary={describeChangeCount(changeCount)}
      primary={{ label: 'Revisar y guardar', onClick: onReview }}
      secondary={[{ key: 'discard', label: 'Descartar', variant: 'ghost', destructive: true, onSelect: onDiscard }]}
    />
  );
}
