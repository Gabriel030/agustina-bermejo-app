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

const ACTIVE_COURSE_STATUSES = new Set(['ENROLLED', 'IN_PROGRESS', 'REGULAR']);

export function coursesSummary(courseEnrollments) {
  const active = (courseEnrollments ?? []).filter((course) => ACTIVE_COURSE_STATUSES.has(course.status)).length;
  const waitlisted = (courseEnrollments ?? []).filter((course) => course.status === 'WAITLISTED').length;
  const parts = [];
  if (active > 0) parts.push(`Tenés ${active} curso${active === 1 ? '' : 's'} en curso`);
  if (waitlisted > 0) {
    const waiting = `lista de espera de ${waitlisted} curso${waitlisted === 1 ? '' : 's'}`;
    parts.push(active > 0 ? `y estás en ${waiting}` : `Estás en ${waiting}`);
  }
  return parts.join(' ');
}

export function activePrograms(enrollments) {
  return (enrollments ?? []).filter((enrollment) => enrollment.status !== 'WITHDRAWN');
}
