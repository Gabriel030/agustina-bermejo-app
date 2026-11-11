import { http, HttpResponse } from 'msw';

const API = 'http://localhost:8080';

const SCHEME = {
  minRegularGrade: 6,
  minApprovalGrade: 7,
  promotionMinGrade: 7,
  examCountExpected: 2,
  tpCountExpected: 2,
  retakeQuota: 1,
  maxAbsenceRate: 25,
};

const COLUMNS_PUBLISHED_ALL = [
  { type: 'EXAM', sequenceNumber: 1, published: true },
  { type: 'EXAM', sequenceNumber: 2, published: true },
];

const COLUMNS_SECOND_UNPUBLISHED = [
  { type: 'EXAM', sequenceNumber: 1, published: true },
  { type: 'EXAM', sequenceNumber: 2, published: false },
];

const grade = (sequenceNumber, score, retakeScore = null, published = true) => ({ type: 'EXAM', sequenceNumber, score, retakeScore, published });

const ALL_MET = { coursework: true, correlatives: true, attendance: true, grade: true };

function student(enrollmentId, lastname, name, overrides = {}) {
  return {
    enrollmentId,
    lastname,
    name,
    dni: `40000${enrollmentId}`,
    enrollmentStatus: 'ACTIVE',
    grades: [grade(1, 8), grade(2, 9)],
    retakesUsed: 0,
    average: 8.5,
    attendancePercent: 94,
    suggestedResult: 'PROMOTES',
    requirements: { ...ALL_MET },
    administrative: { payments: 'OK', documentsComplete: true },
    alerts: [],
    exempted: false,
    teacherObservation: null,
    ...overrides,
  };
}

export const closureStudents = {
  withdrawn: student(1, 'Aguilar', 'Melina', { enrollmentStatus: 'WITHDRAWN', grades: [], suggestedResult: 'PENDING', requirements: {}, administrative: { payments: 'NOT_VERIFIED', documentsComplete: false }, average: null, attendancePercent: null }),
  promotes: student(2, 'Acosta', 'Lucía'),
  conditional: student(3, 'Benítez', 'Tomás', {
    suggestedResult: 'PROMOTION_CONDITIONAL',
    administrative: { payments: 'NOT_OK', documentsComplete: false, missingDocuments: ['Analítico'] },
  }),
  retake: student(4, 'Cabrera', 'Julieta', {
    grades: [grade(1, 6, 7), grade(2, 8)],
    retakesUsed: 1,
    average: 7.5,
    suggestedResult: 'PROMOTES',
  }),
  goesToFinal: student(5, 'Domínguez', 'Martín', {
    suggestedResult: 'GOES_TO_FINAL',
    requirements: { ...ALL_MET, correlatives: false, grade: false },
    alerts: ['UNMET_PREREQUISITE'],
  }),
  free: student(6, 'Herrera', 'Joaquín', {
    suggestedResult: 'FREE',
    attendancePercent: 58,
    requirements: { ...ALL_MET, attendance: false },
    administrative: { payments: 'NOT_VERIFIED', documentsComplete: true },
  }),
  pending: student(7, 'Escobar', 'Valentina', {
    grades: [grade(1, 8), grade(2, null, null, false)],
    average: null,
    suggestedResult: 'PENDING',
    requirements: { ...ALL_MET, grade: null },
    alerts: ['EXAM_WITHOUT_COURSEWORK'],
  }),
};

function summaryOf(students) {
  const active = students.filter((row) => row.enrollmentStatus !== 'WITHDRAWN');
  const count = (result) => active.filter((row) => row.suggestedResult === result).length;
  return {
    promote: count('PROMOTES'),
    goToFinal: count('GOES_TO_FINAL'),
    failed: count('FAILED'),
    free: count('FREE'),
    pending: count('PENDING'),
    conditional: count('PROMOTION_CONDITIONAL'),
  };
}

export function closureSheet(overrides = {}) {
  const students = overrides.students ?? Object.values(closureStudents).filter((row) => row !== closureStudents.pending);
  return {
    closureId: 31,
    sectionId: 7,
    sectionName: 'Comisión A',
    subjectName: 'Teorías Socioculturales',
    programName: 'Enfermería',
    termLabel: 'Cuatrimestral 2026',
    termEndDate: '2026-07-12',
    instructors: [{ name: 'E. Staib', role: 'LEAD' }],
    status: 'DRAFT_AVAILABLE',
    availability: { canConfirm: true, canApprove: false, blockedReason: null },
    scheme: SCHEME,
    columns: COLUMNS_PUBLISHED_ALL,
    students,
    summary: summaryOf(students),
    reportNumber: null,
    closedAt: null,
    ...overrides,
  };
}

export function closureSheetWithUnpublishedExam() {
  const students = [closureStudents.promotes, closureStudents.pending];
  return closureSheet({
    students,
    columns: COLUMNS_SECOND_UNPUBLISHED,
    availability: { canConfirm: false, canApprove: false, blockedReason: 'PUBLISHED_GRADES_INCOMPLETE' },
  });
}

export const closurePrerequisites = [
  { subjectCode: 'ANAT', subjectName: 'Anatomía y Fisiología I', requirementType: 'APPROVED', studentStatus: 'REGULAR', finalScore: null, met: false, academicRecordLink: null },
  { subjectCode: 'SALU', subjectName: 'Introducción a la Salud Pública', requirementType: 'PASSED_COURSEWORK', studentStatus: 'WAIVED', finalScore: null, met: true, academicRecordLink: null },
];

const plannedEndpoint = (path) => new RegExp(`^${API.replace(/[.]/g, '\\.')}${path}$`);

export function closureSheetPath(sectionId = 7, kind = 'program') {
  return plannedEndpoint(`/${kind}-sections/${sectionId}/closure-sheet`);
}

export function closureConfirmPath(sectionId = 7, kind = 'program') {
  return plannedEndpoint(`/${kind}-sections/${sectionId}/closure/confirm`);
}

export function mockClosureSheetEndpoints(server, sheet, { sectionId = 7, kind = 'program' } = {}) {
  server.use(
    http.get(closureSheetPath(sectionId, kind), () => HttpResponse.json(sheet)),
    http.get(plannedEndpoint(`/commission-closures/${sheet.closureId}/students/\\d+/prerequisites`), () => HttpResponse.json(closurePrerequisites)),
  );
}
