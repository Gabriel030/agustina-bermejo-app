import { StatusPill } from '@/components/StatusPill';
import { PLAN_STATUS_PILL } from '@/features/workspace/components/planStatusPills';

export function PlanStatusPill({ status }) {
  const { label, tone, Icon } = PLAN_STATUS_PILL[status];
  return (
    <StatusPill tone={tone} icon={Icon}>
      {label}
    </StatusPill>
  );
}
