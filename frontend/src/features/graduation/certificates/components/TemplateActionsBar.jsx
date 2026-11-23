import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { StickyActionBar } from '@/components/StickyActionBar';
import { Button } from '@/components/ui/button';

const HINT_ID = 'template-status-hint';

export function TemplateStatusHint({ status, hint, readOnly, actions }) {
  if (readOnly) {
    return (
      <GlassCard className="flex flex-wrap items-center gap-3 p-4">
        <StatusPill tone={status.tone}>{status.label}</StatusPill>
        <span role="status" className="text-size-13 text-ink-faint">
          {hint}
        </span>
      </GlassCard>
    );
  }
  return (
    <StickyActionBar
      summary={status.label}
      hint={
        <span id={HINT_ID} role="status" className="block text-size-12 text-ink-faint">
          {hint}
        </span>
      }
    >
      {actions}
    </StickyActionBar>
  );
}

export function TemplateEditActions({ canDiscard, canSave, canPublish, saving, onDiscard, onSave, onPublish }) {
  return (
    <>
      <Button
        type="button"
        variant="ghost"
        className="min-h-11 text-danger md:min-h-8"
        disabled={!canDiscard}
        onClick={onDiscard}
      >
        Descartar borrador
      </Button>
      <Button type="button" variant="outline" className="min-h-11 md:min-h-8" disabled={!canSave} loading={saving} onClick={onSave}>
        Guardar borrador
      </Button>
      <Button
        type="button"
        variant="primary"
        className="min-h-11 md:min-h-8"
        disabled={!canPublish}
        aria-describedby={canPublish ? undefined : HINT_ID}
        onClick={onPublish}
      >
        Publicar
      </Button>
    </>
  );
}
