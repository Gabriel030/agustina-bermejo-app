import { cohortTitle } from './cohorts';

export const UPCOMING_WINDOW_DAYS = 56;
export const MAX_VISIBLE_EVENTS = 8;
export const MAX_RECENT_PROGRAMS = 3;

const MONTH_ABBREVIATIONS = ['ENE', 'FEB', 'MAR', 'ABR', 'MAY', 'JUN', 'JUL', 'AGO', 'SEP', 'OCT', 'NOV', 'DIC'];

function pad(value) {
  return String(value).padStart(2, '0');
}

export function toIsoDate(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function addDaysToIso(isoDate, days) {
  const [year, month, day] = isoDate.split('-').map(Number);
  return toIsoDate(new Date(year, month - 1, day + days));
}

export function describeEventDate(isoDate) {
  const [, month, day] = isoDate.split('-');
  return { day, month: MONTH_ABBREVIATIONS[Number(month) - 1] };
}

function isOpenForAdmission(cohort, todayIso) {
  return Boolean(cohort.admissionEnd) && cohort.admissionEnd >= todayIso;
}

function isInsideWindow(isoDate, todayIso, limitIso) {
  return Boolean(isoDate) && isoDate >= todayIso && isoDate <= limitIso;
}

function buildEvents(activePrograms, cohortsByProgramId, todayIso) {
  const limitIso = addDaysToIso(todayIso, UPCOMING_WINDOW_DAYS);
  const events = [];
  activePrograms.forEach((program) => {
    (cohortsByProgramId[program.id] ?? []).forEach((cohort) => {
      const base = { programId: program.id, programName: program.name, cohortLabel: cohortTitle(cohort) };
      if (isInsideWindow(cohort.admissionEnd, todayIso, limitIso)) {
        events.push({ ...base, id: `${cohort.id}-admission-close`, kind: 'ADMISSION_CLOSE', date: cohort.admissionEnd });
      }
      if (isInsideWindow(cohort.cursadaStart, todayIso, limitIso)) {
        events.push({ ...base, id: `${cohort.id}-classes-start`, kind: 'CLASSES_START', date: cohort.cursadaStart });
      }
    });
  });
  return events.sort(
    (a, b) => a.date.localeCompare(b.date) || a.programName.localeCompare(b.programName) || a.kind.localeCompare(b.kind),
  );
}

export function buildAcademicManagementSummary({ programs, courses, subjects, cohortsByProgramId, today }) {
  const todayIso = toIsoDate(today);
  const activePrograms = programs.filter((program) => program.status === 'ACTIVE');
  const openCohortsCount = activePrograms.reduce(
    (total, program) =>
      total + (cohortsByProgramId[program.id] ?? []).filter((cohort) => isOpenForAdmission(cohort, todayIso)).length,
    0,
  );
  const publishedWithoutOpenCohorts = activePrograms.filter(
    (program) =>
      program.visibleInCatalog &&
      !(cohortsByProgramId[program.id] ?? []).some((cohort) => isOpenForAdmission(cohort, todayIso)),
  );
  const recentPublishedPrograms = programs
    .filter((program) => program.visibleInCatalog)
    .sort((a, b) => b.id - a.id)
    .slice(0, MAX_RECENT_PROGRAMS);

  return {
    activeProgramsCount: activePrograms.length,
    publishedProgramsCount: activePrograms.filter((program) => program.visibleInCatalog).length,
    activeCoursesCount: courses.filter((course) => course.status === 'ACTIVE').length,
    subjectsCount: subjects.length,
    openCohortsCount,
    activePrograms,
    publishedWithoutOpenCohorts,
    recentPublishedPrograms,
    upcomingEvents: buildEvents(activePrograms, cohortsByProgramId, todayIso),
  };
}
