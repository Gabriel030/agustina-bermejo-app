import { test, expect } from '@playwright/test';
import { BackendApi } from '../../fixtures/api.js';
import { enrollStudentInProgram, withdrawFromActiveSections } from '../../fixtures/academic.js';
import { enrollAsStudent, prepareCareerWithSection } from '../../fixtures/enrollment.js';
import { ROLES } from '../../fixtures/accounts.js';
import { signedInPage } from '../../fixtures/session.js';
import { StudentCareerPage } from '../../pages/StudentCareerPage.js';

const STUDENT_HOME = /\/alumno\/inicio$/;

test.describe('@flow section enrollment', () => {
  test.describe.configure({ mode: 'serial' });

  test.fixme('studentEnrollsInOpenSectionThroughTheUi', async ({ browser, request }) => {
    const setup = await prepareCareerWithSection(request, 'enrollmentStudent');

    const { context, page } = await signedInPage(browser, 'enrollmentStudent', STUDENT_HOME);
    const careerPage = new StudentCareerPage(page);
    await careerPage.open(setup.programEnrollmentId);
    await expect(page.getByText('No tenés materias en curso')).toBeVisible();
    await careerPage.enrollInSection(setup.subjectName, setup.sectionName);
    await expect(page.getByText(`Te inscribiste a ${setup.subjectName} — ${setup.sectionName}`)).toBeVisible();
    await careerPage.backToSubjectsButton.click();
    await expect(careerPage.enrolledSubjectRow(setup.subjectName)).toContainText('Inscripto');
    await context.close();
  });

  test('studentEnrollmentIsVisibleInMyCareersAndToStaffAndTeacher', async ({ browser, request }) => {
    const setup = await prepareCareerWithSection(request, 'enrollmentStudent');
    const enrolled = await enrollAsStudent(request, 'enrollmentStudent', setup.programEnrollmentId, setup.sectionId);
    expect(enrolled.status).toBe(201);

    const { context, page } = await signedInPage(browser, 'enrollmentStudent', STUDENT_HOME);
    const careerPage = new StudentCareerPage(page);
    await careerPage.open(setup.programEnrollmentId);
    await expect(careerPage.enrolledSubjectRow(setup.subjectName)).toContainText('Inscripto');
    await context.close();

    for (const staffRole of ['preceptor', 'administrativo']) {
      const staff = await BackendApi.as(request, staffRole);
      const { body: enrollments } = await staff.get(`/program-enrollments/${setup.programEnrollmentId}/section-enrollments`);
      expect(enrollments).toHaveLength(1);
      expect(enrollments[0]).toMatchObject({ sectionId: setup.sectionId, status: 'ENROLLED' });
    }

    const teacher = await BackendApi.as(request, 'docente');
    const { body: roster } = await teacher.get(`/program-sections/${setup.sectionId}/roster`);
    expect(roster.map((entry) => entry.studentId)).toEqual([setup.studentId]);
  });

  test('fullSectionPutsStudentOnWaitlistAndRepeatedEnrollmentInTheSubjectIsRejected', async ({ browser, request }) => {
    const setup = await prepareCareerWithSection(request, 'enrollmentStudent', { capacity: 1 });
    await withdrawFromActiveSections(request, 'seatCompetitorStudent');
    const competitor = await enrollStudentInProgram(
      request, 'seatCompetitorStudent', ROLES.seatCompetitorStudent.username, setup.programId);
    const seatTaken = await enrollAsStudent(request, 'seatCompetitorStudent', competitor.programEnrollmentId, setup.sectionId);
    expect(seatTaken.status).toBe(201);
    expect(seatTaken.body.status).toBe('ENROLLED');

    const waitlisted = await enrollAsStudent(request, 'enrollmentStudent', setup.programEnrollmentId, setup.sectionId);
    expect(waitlisted.status).toBe(201);
    expect(waitlisted.body.status).toBe('WAITLISTED');

    const { context, page } = await signedInPage(browser, 'enrollmentStudent', STUDENT_HOME);
    const careerPage = new StudentCareerPage(page);
    await careerPage.open(setup.programEnrollmentId);
    await expect(careerPage.enrolledSubjectRow(setup.subjectName)).toContainText('En lista de espera');
    await context.close();

    const duplicate = await enrollAsStudent(request, 'enrollmentStudent', setup.programEnrollmentId, setup.sectionId);
    expect(duplicate.status).toBe(409);

    await withdrawFromActiveSections(request, 'seatCompetitorStudent');
  });

  test('courseStudentSeesCourseEnrollmentInMyCourses', async ({ browser }) => {
    const { context, page } = await signedInPage(browser, 'courseStudent', STUDENT_HOME);
    await page.goto('/alumno/mis-cursos');
    await expect(page.getByText('Taller de Fotografía Digital (Demo)')).toBeVisible();
    await context.close();
  });
});
