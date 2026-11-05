import { test, expect } from '@playwright/test';
import { BackendApi } from '../../fixtures/api.js';
import { enrollAsStudent, prepareCareerWithSection } from '../../fixtures/enrollment.js';
import { signedInPage } from '../../fixtures/session.js';
import { StudentSubjectPage } from '../../pages/StudentSubjectPage.js';

const STUDENT_HOME = /\/alumno\/inicio$/;

function firstExam(score) {
  return { type: 'EXAM', sequenceNumber: 1, date: '2026-06-10T00:00:00', score };
}

async function enrolledSetup(request, { withDemoTeacher }) {
  const setup = await prepareCareerWithSection(request, 'gradesStudent', { withDemoTeacher });
  const enrolled = await enrollAsStudent(request, 'gradesStudent', setup.programEnrollmentId, setup.sectionId);
  expect(enrolled.status).toBe(201);
  return { ...setup, sectionEnrollmentId: enrolled.body.id };
}

async function visibleFirstExamGrade(browser, sectionEnrollmentId) {
  const { context, page } = await signedInPage(browser, 'gradesStudent', STUDENT_HOME);
  const subjectPage = new StudentSubjectPage(page);
  await subjectPage.open(sectionEnrollmentId);
  const text = await subjectPage.firstExamGrade.innerText();
  await context.close();
  return text;
}

test.describe('@flow grades', () => {
  test.describe.configure({ mode: 'serial' });

  test('teacherLoadsGradeStudentSeesItAndCorrectionReplacesIt', async ({ browser, request }) => {
    const setup = await enrolledSetup(request, { withDemoTeacher: true });
    const teacher = await BackendApi.as(request, 'docente');

    expect(await visibleFirstExamGrade(browser, setup.sectionEnrollmentId)).toBe('—');

    const loaded = await teacher.attempt('POST', `/section-enrollments/${setup.sectionEnrollmentId}/grade-items`, firstExam(7));
    expect(loaded.status).toBe(201);
    expect(await visibleFirstExamGrade(browser, setup.sectionEnrollmentId)).toBe('7');

    const corrected = await teacher.attempt('POST', `/section-enrollments/${setup.sectionEnrollmentId}/grade-items`, firstExam(9));
    expect(corrected.status).toBe(201);
    expect(corrected.body.id).toBe(loaded.body.id);
    expect(await visibleFirstExamGrade(browser, setup.sectionEnrollmentId)).toBe('9');

    const preceptor = await BackendApi.as(request, 'preceptor');
    const { body: gradeItems } = await preceptor.get(`/section-enrollments/${setup.sectionEnrollmentId}/grade-items`);
    expect(gradeItems).toHaveLength(1);
  });

  test('teacherOutsideTheSectionCannotLoadGradesAndOtherRolesCannotEither', async ({ browser, request }) => {
    const setup = await enrolledSetup(request, { withDemoTeacher: false });
    const gradesPath = `/section-enrollments/${setup.sectionEnrollmentId}/grade-items`;

    const teacher = await BackendApi.as(request, 'docente');
    const outsideTeacher = await teacher.attempt('POST', gradesPath, firstExam(10));
    expect(outsideTeacher.status).toBeGreaterThanOrEqual(400);
    expect(outsideTeacher.status).toBeLessThan(500);

    for (const role of ['alumno', 'preceptor', 'administrativo', 'director']) {
      const api = await BackendApi.as(request, role);
      const rejected = await api.attempt('POST', gradesPath, firstExam(10));
      expect(rejected.status, role).toBe(403);
    }

    expect(await visibleFirstExamGrade(browser, setup.sectionEnrollmentId)).toBe('—');
  });
});
