import { ArrowLeftRight } from 'lucide-react';
import { StatusPill } from '@/components/StatusPill';

export function EquivalenceChip({ className }) {
  return (
    <StatusPill tone="accent" icon={ArrowLeftRight} className={className}>
      Equivalencia
    </StatusPill>
  );
}
