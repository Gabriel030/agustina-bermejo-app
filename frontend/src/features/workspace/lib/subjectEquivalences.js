export const EQUIVALENCE_FILTER_OPTIONS = [
  { id: 'vigentes', label: 'Vigentes', status: 'ACTIVE' },
  { id: 'anuladas', label: 'Anuladas', status: 'VOIDED' },
  { id: 'todas', label: 'Todas', status: 'ALL' },
];

export const EQUIVALENCES_SECTION_ID = 'equivalences-section';

export function filterEquivalences(equivalences, filterId) {
  const { status } = EQUIVALENCE_FILTER_OPTIONS.find((option) => option.id === filterId) ?? EQUIVALENCE_FILTER_OPTIONS[0];
  return status === 'ALL' ? equivalences : equivalences.filter((equivalence) => equivalence.status === status);
}

export function equivalenceTitle(equivalence) {
  return equivalence.subjectName ?? equivalence.originSubjectName;
}
