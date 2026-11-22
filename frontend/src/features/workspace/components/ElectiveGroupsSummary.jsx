import { CheckCircle2 } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';

export function ElectiveGroupsSummary({ groups }) {
  if (groups.length === 0) return null;
  return (
    <GlassCard className="flex flex-col gap-3 p-4">
      <h2 className="font-heading text-base font-bold text-ink">Electivas</h2>
      <ul className="flex flex-wrap gap-2">
        {groups.map((group) => (
          <li key={group.electiveGroupId}>
            <StatusPill tone={group.fulfilled ? 'success' : 'neutral'}>
              {group.fulfilled && <CheckCircle2 className="h-3 w-3" aria-hidden="true" />}
              <span>{group.name}</span>
              <span className="tabular-nums">
                {group.approvedCount} / {group.minRequired}
              </span>
              <span className="sr-only">{group.fulfilled ? 'cumplido' : 'pendiente'}</span>
            </StatusPill>
          </li>
        ))}
      </ul>
    </GlassCard>
  );
}
