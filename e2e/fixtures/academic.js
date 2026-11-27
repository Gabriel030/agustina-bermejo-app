import { BackendApi } from './api.js';

const DEMO_TERM_DESCRIPTION = 'Ciclo lectivo 2026 (Demo)';
const DEMO_SCHEME_NAME = 'Esquema demo: 2 parciales y final';
const DEMO_TEACHER_NAME = 'Docente Demo';
const OTHER_DEMO_TEACHER_NAME = 'Docente Demo 2';

export function uniqueSuffix() {
  return `${Date.now().toString(36)}${Math.floor(Math.random() * 1296).toString(36)}`.toUpperCase();
}

function uniqueSubjectCode() {
  return Math.floor(Math.random() * 36 ** 4).toString(36).toUpperCase().padStart(4, '0');
}

async function findByName(api, path, name) {
  const { body } = await api.get(path);
  const match = body.find((item) => item.name === name || item.description === name);
  if (!match) {
    throw new Error(`Demo seed item not found: ${name} in ${path}`);
  }
  return match;
}

export async function createProgramWithSubject(request, suffix) {
  const administrative = await BackendApi.as(request, 'administrativo');
  const director = await BackendApi.as(request, 'director');
  const program = await administrative.post('/programs', {
    name: `Carrera E2E ${suffix}`,
    totalYears: 3,
    totalCreditsRequired: 5,
  });
  const subject = await administrative.post('/subjects', {
    name: `Materia E2E ${suffix}`,
    code: uniqueSubjectCode(),
    weeklyHours: 4,
    gradeMode: 'NUMERIC',
  });
  await administrative.post(`/subjects/${subject.id}/submit-for-approval`);
  await director.post(`/subjects/${subject.id}/approve`);
  await administrative.post(`/programs/${program.id}/subjects`, {
    subjectId: subject.id,
    category: 'MANDATORY',
    curricularYear: 1,
    curricularTerm: 'FIRST',
    credits: 5,
  });
  return { programId: program.id, subjectId: subject.id, subjectName: subject.name };
}

export async function createSection(request, { programId, subjectId, name, capacity, withDemoTeacher }) {
  const administrative = await BackendApi.as(request, 'administrativo');
  const director = await BackendApi.as(request, 'director');
  const term = await findByName(administrative, '/terms', DEMO_TERM_DESCRIPTION);
  const scheme = await findByName(director, '/evaluation-schemes', DEMO_SCHEME_NAME);
  const teacher = await findByName(administrative, '/instructors', withDemoTeacher ? DEMO_TEACHER_NAME : OTHER_DEMO_TEACHER_NAME);
  const cycle = await administrative.post('/academic-offering-cycles', { programId, termId: term.id });
  const sectionId = await administrative.post(`/academic-offering-cycles/${cycle.id}/sections`, {
    name,
    subjectId,
    capacity,
    shift: 'MORNING',
  });
  await administrative.put(`/program-sections/${sectionId}`, { evaluationSchemeId: scheme.id });
  await administrative.postWithoutBody(`/program-sections/${sectionId}/instructors`, { instructorId: teacher.id, role: 'LEAD' });
  await administrative.postWithoutBody(`/academic-offering-cycles/${cycle.id}/submit`);
  await director.postWithoutBody(`/academic-offering-cycles/${cycle.id}/approve`);
  return { sectionId, sectionName: name };
}

export async function enrollStudentInProgram(request, studentRole, username, programId) {
  const administrative = await BackendApi.as(request, 'administrativo');
  const { body: students } = await administrative.get(`/students?search=${username}`);
  const student = students.find((candidate) => candidate.username === username);
  const programEnrollment = await administrative.post(`/students/${student.id}/program-enrollments`, { programId });
  return { studentId: student.id, programEnrollmentId: programEnrollment.id };
}

export async function withdrawFromActiveSections(request, studentRole) {
  const student = await BackendApi.as(request, studentRole);
  const { body: enrollments } = await student.get('/me/section-enrollments');
  for (const enrollment of enrollments.filter((candidate) => candidate.status === 'ENROLLED')) {
    await student.attempt('POST', `/me/section-enrollments/${enrollment.id}/withdraw`);
  }
}
