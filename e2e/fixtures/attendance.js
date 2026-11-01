import { BackendApi } from './api.js';

const FIRST_UNUSED_CLASS_DATE = Date.UTC(2040, 0, 1);
const DAY_IN_MILLISECONDS = 24 * 60 * 60 * 1000;
const DISTINCT_CLASS_DATES = 36_500;

export const RECEIPT_FILE = {
  name: 'certificado.pdf',
  mimeType: 'application/pdf',
  buffer: Buffer.from('%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n'),
};

function nextUnusedClassDate() {
  const offset = Math.floor(Date.now() / 1000) % DISTINCT_CLASS_DATES;
  return new Date(FIRST_UNUSED_CLASS_DATE + offset * DAY_IN_MILLISECONDS).toISOString().slice(0, 10);
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
