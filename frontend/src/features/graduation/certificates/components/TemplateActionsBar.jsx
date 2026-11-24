import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { StickyActionBar } from '@/components/StickyActionBar';

export function TemplateStatusHint({ status, hint, readOnly, edit }) {
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
      compact="menu"
      aria-label="Acciones de la plantilla"
      summary={status.label}
      hint={hint}
      primary={{ label: 'Publicar', disabled: !edit.canPublish, onClick: edit.onPublish }}
      secondary={[
        { key: 'save', label: 'Guardar borrador', disabled: !edit.canSave || edit.saving, onSelect: edit.onSave },
        { key: 'discard', label: 'Descartar borrador', variant: 'ghost', className: 'text-danger', destructive: true, disabled: !edit.canDiscard, onSelect: edit.onDiscard },
      ]}
    />
  );
}
