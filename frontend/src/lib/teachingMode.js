export const TEACHING_MODE_LABELS = {
  IN_PERSON: 'Presencial',
  ONLINE: 'Virtual',
  HYBRID: 'Híbrido',
};

export const TEACHING_MODE_OPTIONS = [
  { value: '', label: 'Sin especificar' },
  ...Object.entries(TEACHING_MODE_LABELS).map(([value, label]) => ({ value, label })),
];
