import { BackendApi } from './api.js';

const FIRST_UNUSED_YEAR = 2040;
const DAYS_PER_MONTH_AVOIDING_SEEDED_ONES = 9;
const FIRST_DAY_OF_MONTH_AVOIDING_SEEDED_ONES = 20;
const DISTINCT_CLASS_DATES = 36_500;

export const RECEIPT_FILE = {
  name: 'certificado.pdf',
  mimeType: 'application/pdf',
  buffer: Buffer.from('%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n'),
};

export function nextUnusedClassDate() {
  const offset = Math.floor(Date.now() / 1000) % DISTINCT_CLASS_DATES;
  const month = Math.floor(offset / DAYS_PER_MONTH_AVOIDING_SEEDED_ONES);
  const day = FIRST_DAY_OF_MONTH_AVOIDING_SEEDED_ONES + (offset % DAYS_PER_MONTH_AVOIDING_SEEDED_ONES);
  return new Date(Date.UTC(FIRST_UNUSED_YEAR, month, day)).toISOString().slice(0, 10);
}

export async function recordFreshAbsenceForDemoStudent(request) {
  const student = await BackendApi.as(request, 'alumno');
  const [sectionEnrollment] = (await student.get('/me/section-enrollments')).body;
  const teacher = await BackendApi.as(request, 'docente');
  const classDate = nextUnusedClassDate();
  const [record] = await teacher.post(`/program-sections/${sectionEnrollment.sectionId}/attendance`, {
    classDate,
    entries: [{ enrollmentId: sectionEnrollment.id, status: 'ABSENT' }],
  });
  return {
    sectionEnrollmentId: sectionEnrollment.id,
    attendanceId: record.id,
    classDate,
    dayOfMonth: Number(classDate.slice(8)),
  };
}

export async function findAttendanceRecord(request, absence) {
  const student = await BackendApi.as(request, 'alumno');
  const records = (await student.get(`/me/section-enrollments/${absence.sectionEnrollmentId}/attendance`)).body;
  return records.find((record) => record.id === absence.attendanceId);
}
