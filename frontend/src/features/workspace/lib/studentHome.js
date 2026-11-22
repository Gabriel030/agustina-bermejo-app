const ACTIVE_SUBJECT_STATUSES = new Set(['IN_PROGRESS', 'ENROLLED']);
const LISTED_SUBJECT_STATUSES = new Set(['IN_PROGRESS', 'ENROLLED', 'REGULAR', 'WAITLISTED']);

export function currentProgram(enrollments) {
  return (enrollments ?? [])
    .filter((enrollment) => enrollment.status !== 'WITHDRAWN')
    .sort((a, b) => Number(a.status === 'COMPLETED') - Number(b.status === 'COMPLETED'))[0];
}

export function listedSubjects(sectionEnrollments) {
  return (sectionEnrollments ?? [])
    .filter((enrollment) => LISTED_SUBJECT_STATUSES.has(enrollment.status))
    .sort((a, b) => a.subjectName.localeCompare(b.subjectName));
}

export function activeSubjectsCount(sectionEnrollments) {
  return (sectionEnrollments ?? []).filter((enrollment) => ACTIVE_SUBJECT_STATUSES.has(enrollment.status)).length;
}

export function creditsPercent(progress) {
  if (!progress?.creditosRequeridos) return 0;
  return Math.round((progress.creditosAcumulados / progress.creditosRequeridos) * 100);
}

export function planStatusOf(sectionEnrollment) {
  if (sectionEnrollment.status === 'REGULAR') return 'regular';
  if (sectionEnrollment.status === 'WAITLISTED') return 'waitlisted';
  return 'en-curso';
}
