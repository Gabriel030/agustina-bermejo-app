import { Check, Minus, X } from 'lucide-react';

const STATES = {
  yes: { Icon: Check, text: 'cumple', className: 'text-ink-soft' },
  no: { Icon: X, text: 'no cumple', className: 'text-danger' },
  unknown: { Icon: Minus, text: 'no verificado', className: 'text-ink-faint' },
};

export function ClosureRequirementMark({ label, value }) {
  const state = value === true ? STATES.yes : value === false ? STATES.no : STATES.unknown;
  const { Icon } = state;
  return (
    <span role="img" aria-label={`${label}: ${state.text}`} className={`inline-flex size-9 items-center justify-center ${state.className}`}>
      <Icon className="size-4" strokeWidth={3} aria-hidden="true" />
    </span>
  );
}
