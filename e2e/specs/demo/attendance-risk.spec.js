import { test, expect } from '@playwright/test';
import { BackendApi } from '../../fixtures/api.js';
import { nextUnusedClassDate } from '../../fixtures/attendance.js';
import { signedInPage } from '../../fixtures/session.js';
import { StudentSubjectPage } from '../../pages/StudentSubjectPage.js';

const LIMITED_SUBJECT_CODE = 'DM02';

test.describe('@flow attendance risk', () => {
  test('studentSeesAttendanceAsTheComplementOfTheRealAbsencePercent', async ({ browser, request }) => {
    const student = await BackendApi.as(request, 'alumno');
    const { body: enrollments } = await student.get('/me/section-enrollments');
    const limited = enrollments.find((enrollment) => enrollment.subjectCode === LIMITED_SUBJECT_CODE);
    const teacher = await BackendApi.as(request, 'docente');
    await teacher.post(`/program-sections/${limited.sectionId}/attendance`, {
      classDate: nextUnusedClassDate(),
      entries: [{ enrollmentId: limited.id, status: 'PRESENT' }],
    });

    const { body: risk } = await student.get(`/me/section-enrollments/${limited.id}/attendance/risk`);
    expect(risk.calculable).toBe(true);
    expect(typeof risk.absencePercent).toBe('number');
    expect(risk.maxAbsencePercent).toBe(25);
    expect(risk.absencePercent).toBeGreaterThan(25);
    expect(risk.libre).toBe(true);

    const { context, page } = await signedInPage(browser, 'alumno', /\/alumno\/inicio$/);
    await new StudentSubjectPage(page).open(limited.id);
    const attendanceValue = page.getByText('% Asistencia', { exact: true }).locator('xpath=following-sibling::div');
    await expect(attendanceValue).toHaveText(`${Math.round(100 - risk.absencePercent)}%`);
    await expect(page.getByText('Estás por debajo del 75% de asistencia requerido por la materia')).toBeVisible();
    await context.close();
  });
});
