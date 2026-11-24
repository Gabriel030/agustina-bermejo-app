import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';

export function NoAccessState({ permission, action }) {
  return (
    <GlassCard className="flex flex-1 flex-col p-8 sm:p-12" role="note">
      <EmptyState
        icon="🔒"
        title="No tenés acceso a esta pantalla"
        description={`Pedile al Director que te habilite el permiso correspondiente (${permission}).`}
        action={action}
      />
    </GlassCard>
  );
}
