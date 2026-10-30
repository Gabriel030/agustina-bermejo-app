import { GlassCard } from '@/components/GlassCard';

export function ManualIssuanceSimulationPanel({ period }) {
  return (
    <GlassCard className="p-4 text-sm text-ink-soft" data-testid="simulation-placeholder">
      La simulación de {period} estará disponible próximamente.
    </GlassCard>
  );
}
