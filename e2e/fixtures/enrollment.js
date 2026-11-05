import { BackendApi } from './api.js';
import {
  createProgramWithSubject,
  createSection,
  enrollStudentInProgram,
  uniqueSuffix,
  withdrawFromActiveSections,
} from './academic.js';
import { ROLES } from './accounts.js';

export async function prepareCareerWithSection(request, studentRole, { capacity = 30, withDemoTeacher = true } = {}) {
  const suffix = uniqueSuffix();
  const career = await createProgramWithSubject(request, suffix);
  const section = await createSection(request, career.subjectId, {
    name: `Comisión ${suffix}`,
    capacity,
    withDemoTeacher,
  });
  await withdrawFromActiveSections(request, studentRole);
  const enrollment = await enrollStudentInProgram(request, studentRole, ROLES[studentRole].username, career.programId);
  return { ...career, ...section, ...enrollment, studentRole };
}

export async function enrollAsStudent(request, studentRole, programEnrollmentId, sectionId) {
  const student = await BackendApi.as(request, studentRole);
  return student.attempt('POST', '/me/section-enrollments', { programEnrollmentId, sectionId });
}
