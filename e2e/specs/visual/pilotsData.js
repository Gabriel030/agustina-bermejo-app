import { reviewApplication, approvalCheck } from '../../../frontend/src/test/fixtures/programApplications.js';
import { closureSheet } from '../../../frontend/src/test/fixtures/closureSheet.js';
import { rosterEntryFixture } from '../../../frontend/src/test/fixtures/rosterEntries.js';

const ARS = (amount) => ({ amount, currency: 'ARS', negative: false, zero: false });

const SUBJECT_STATES = [
  ['APPROVED', 'Anatomía y Fisiología I', 'ANAT', 1, 'FIRST', 8],
  ['APPROVED', 'Introducción a la Salud Pública', 'SALU', 1, 'FIRST', 9],
  ['IN_PROGRESS', 'Enfermería Básica', 'ENFB', 1, 'SECOND', null],
  ['REGULAR_PENDING_FINAL', 'Psicología General', 'PSIG', 1, 'SECOND', null],
  ['AVAILABLE', 'Farmacología', 'FARM', 2, 'FIRST', null],
  ['BLOCKED', 'Enfermería del Adulto', 'ENFA', 2, 'SECOND', null],
  ['FAILED', 'Bioquímica', 'BIOQ', 2, 'FIRST', 3],
];

function planSubject([state, subjectName, subjectCode, curricularYear, curricularTerm, finalScore], index) {
  return {
    approvalSource: state === 'APPROVED' ? 'COURSEWORK' : null,
    attempts: state === 'FAILED' ? 2 : 1,
    blockedBy: [],
    category: 'MANDATORY',
    corequisites: [],
    credits: 6,
    currentEnrollmentId: state === 'IN_PROGRESS' ? 301 : null,
    curricularTerm,
    curricularYear,
    electiveGroupId: null,
    equivalence: null,
    finalScore,
    prerequisites: [],
    prerequisitesWaived: false,
    programSubjectId: 100 + index,
    state,
    subjectCode,
    subjectId: 200 + index,
    subjectName,
  };
}

export const CAREER_ENROLLMENTS = [
  { id: 41, programId: 3, programName: 'Tecnicatura en Enfermería', status: 'ACTIVE', studentId: 1, enrollmentDate: '2025-03-10T00:00:00Z', completionDate: null },
  { id: 42, programId: 4, programName: 'Tecnicatura en Administración', status: 'ACTIVE', studentId: 1, enrollmentDate: '2025-03-10T00:00:00Z', completionDate: null },
];

export const CAREER_PROGRESS = {
  programEnrollmentId: 41,
  creditosAcumulados: 12,
  creditosRequeridos: 42,
  elegibleParaCompletar: false,
  esRegular: true,
  gruposElectivos: [],
  hasActiveCoursework: true,
  obligatoriasPendientes: [],
  promedioGeneral: 8.5,
};

export const CURRICULUM_PROGRESS = {
  programEnrollmentId: 41,
  programName: 'Tecnicatura en Enfermería',
  subjects: SUBJECT_STATES.map(planSubject),
  summary: {
    accumulatedCredits: 12,
    accumulatedCreditsByCoursework: 12,
    approvedByEquivalence: 0,
    averageScore: 8.5,
    completionPercent: 29,
    counts: { approved: 2, available: 1, blocked: 1, failed: 1, inProgress: 1, regularPendingFinal: 1 },
    creditsByEquivalence: 0,
    electiveGroups: [],
    mandatoryApproved: 2,
    mandatoryTotal: 7,
    remainingCredits: 30,
    requiredCredits: 42,
  },
  warnings: [],
};

export const GRADUATION_PROJECTION = {
  programEnrollmentId: 41,
  assumptions: [],
  basis: {
    closedPeriods: 2,
    creditsPerPeriod: 6,
    creditsPerPeriodSource: 'STUDENT_HISTORY',
    criticalPathPeriods: 4,
    remainingCredits: 30,
    remainingElectiveSubjects: 0,
    remainingMandatorySubjects: 5,
  },
  confidence: 'HIGH',
  estimatedGraduationYear: 2029,
  estimatedPeriods: 5,
  method: 'HISTORICAL_PACE_WITH_PREREQUISITES',
  periodUnit: 'SEMESTER',
  range: { maxPeriods: 6, minPeriods: 4 },
  reasons: [],
  status: 'PROJECTED',
  warnings: [],
};

export const SECTION_ENROLLMENTS = [
  {
    id: 301,
    attemptNumber: 1,
    curricularTerm: 'SECOND',
    curricularYear: 1,
    droppedAt: null,
    enrolledAt: '2026-03-15T10:00:00Z',
    finalExamEligibility: { eligible: false, reason: 'COURSEWORK_NOT_REGULAR' },
    finalGradeType: null,
    finalScore: null,
    instructorAdjunto: 'Laura Giménez',
    instructorTitular: 'Esteban Staib',
    programEnrollmentId: 41,
    schedule: 'Sábados 09:00 a 11:00',
    sectionId: 9,
    sectionName: 'Comisión A',
    status: 'IN_PROGRESS',
    subjectCode: 'ENFB',
    subjectId: 202,
    subjectName: 'Enfermería Básica',
  },
];

export const GRADE_ITEMS = [
  { id: 1, enrollmentId: 301, type: 'EXAM', sequenceNumber: 1, score: 8, date: '2026-05-10T12:00:00Z', examBoardId: null, correction: null },
  { id: 2, enrollmentId: 301, type: 'TP', sequenceNumber: 1, score: 9, date: '2026-05-20T12:00:00Z', examBoardId: null, correction: null },
];

export const PENDING_APPLICATIONS = [
  reviewApplication({ id: 51, studentFullName: 'Valentina Suárez', studentEmail: 'valentina@correo.com', requestedAt: '2026-11-20T10:00:00' }),
  reviewApplication({
    id: 52,
    studentFullName: 'Mateo Fernández',
    studentDni: '39876543',
    studentEmail: 'mateo@correo.com',
    programName: 'Tecnicatura en Enfermería',
    requestedAt: '2026-11-18T15:30:00',
    documentation: { complete: false, missingTypes: ['ANALITICO_SECUNDARIO'], documents: [{ id: 21, type: 'DNI_FRENTE', uploadedAt: '2026-11-18T15:00:00' }] },
  }),
  reviewApplication({ id: 53, studentFullName: 'Camila Rojas', studentDni: '42111222', studentEmail: 'camila@correo.com', requestedAt: '2026-11-10T09:00:00' }),
];

export const APPLICATION_CHECK = approvalCheck();

export const SETTINGS = { announcementEmailEnabled: true, evaluationConfigDueDays: 7, justificationDeadlineBusinessDays: 3 };

export const TEACHING_SECTIONS = [
  { id: 5, type: 'PROGRAM', sectionName: 'Comisión A', subjectName: 'Enfermería Básica', termStartDate: '2026-03-01T00:00:00Z', termEndDate: '2026-12-15T00:00:00Z' },
];

export const ROSTER = [
  rosterEntryFixture({ enrollmentId: 11, studentId: 1, studentName: 'Ana Gómez', dni: '30111222' }),
  rosterEntryFixture({ enrollmentId: 12, studentId: 2, studentName: 'Bruno Díaz', dni: '30111333' }),
  rosterEntryFixture({ enrollmentId: 13, studentId: 3, studentName: 'Carla Ruiz', dni: '30111444' }),
  rosterEntryFixture({ enrollmentId: 14, studentId: 4, studentName: 'Diego Paz', dni: '30111555' }),
  rosterEntryFixture({ enrollmentId: 15, studentId: 5, studentName: 'Elena Sosa', dni: '30111666' }),
];

export const CLOSURE_SHEET = closureSheet({ status: 'CLOSED', reportNumber: 'A-2026-014', reportVersion: 1, book: '3', folio: '112' });

export const FIRST_DIRECTOR_STATUS = { directorExists: false, institutionName: 'Instituto Agustina Bermejo' };

export { ARS };
