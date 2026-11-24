import { StatusPill } from '@/components/StatusPill';

export function EquivalenceStatusPill({ status }) {
  return status === 'VOIDED' ? <StatusPill tone="neutral">Anulada</StatusPill> : <StatusPill tone="success">Vigente</StatusPill>;
}
