import { ChevronDown, Info } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { PERMISSION_MODES } from '@/features/permissions/lib/permissionGrid';

export function PermissionModeLegend() {
  return (
    <GlassCard className="p-0">
      <details className="group">
        <summary className="flex min-h-11 cursor-pointer list-none items-center gap-2 rounded-card px-3.5 py-3 text-size-13 font-semibold text-ink outline-none focus-visible:ring-2 focus-visible:ring-ring [&::-webkit-details-marker]:hidden">
          <Info className="size-4 text-brand" aria-hidden="true" />
          ¿Qué significan Legado, Sombra y Activo?
          <ChevronDown className="ml-auto size-4 transition-transform group-open:rotate-180 motion-reduce:transition-none" aria-hidden="true" />
        </summary>
        <div className="grid gap-3 px-3.5 pb-3.5 md:grid-cols-3">
          {Object.entries(PERMISSION_MODES).map(([mode, config]) => (
            <div key={mode} className="flex flex-col gap-1.5 rounded-control border border-border p-3 text-size-13 text-ink-soft">
              <StatusPill tone={config.tone} dot>
                {config.label}
              </StatusPill>
              <span>{config.description}</span>
            </div>
          ))}
        </div>
      </details>
    </GlassCard>
  );
}
