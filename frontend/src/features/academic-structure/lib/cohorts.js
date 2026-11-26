const INTAKE_LABELS = {
  FIRST: '1er semestre',
  SECOND: '2do semestre',
};

export function cohortTitle(cohort) {
  const intakeLabel = INTAKE_LABELS[cohort.intake] ?? cohort.intake;
  const year = cohort.admissionStart ? new Date(cohort.admissionStart).getFullYear() : '';
  return `Ingreso ${intakeLabel} ${year}`;
}
