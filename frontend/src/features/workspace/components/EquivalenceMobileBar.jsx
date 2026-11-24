import { StickyActionBar } from '@/components/StickyActionBar';
import { useSubjectEquivalences } from '@/features/enrollment';
import { useEquivalenceActions } from '@/features/workspace/hooks/EquivalenceActionsContext';
import { useMediaQuery } from '@/hooks/useMediaQuery';

export function EquivalenceMobileBar({ programEnrollmentId }) {
  const compact = useMediaQuery('(max-width: 767px)');
  const actions = useEquivalenceActions();
  const { data } = useSubjectEquivalences(programEnrollmentId, 'ALL');
  if (!compact || !actions?.visible) return null;
  const activeCount = (data ?? []).filter((equivalence) => equivalence.status === 'ACTIVE').length;
  const summary = `${activeCount} ${activeCount === 1 ? 'equivalencia vigente' : 'equivalencias vigentes'}`;
  const denied = !actions.canRecord;

  return (
    <StickyActionBar
      compact="menu"
      aria-label="Acciones de equivalencias"
      summary={summary}
      compactSummary={`${activeCount} ${activeCount === 1 ? 'vigente' : 'vigentes'}`}
      compactHint="Equivalencias"
      hint={denied ? actions.recordDeniedReason : undefined}
      primary={{
        label: 'Cargar equivalencia',
        disabled: denied,
        onClick: (event) => actions.openRecord(null, event.currentTarget),
      }}
    />
  );
}
