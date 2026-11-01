import { test, expect } from '@playwright/test';
import { findAttendanceRecord, RECEIPT_FILE, recordFreshAbsenceForDemoStudent } from '../../fixtures/attendance.js';
import { LoginPage } from '../../pages/LoginPage.js';
import { StudentAttendancePage } from '../../pages/StudentAttendancePage.js';
import { AttendanceRequestsPage } from '../../pages/AttendanceRequestsPage.js';

const REJECTION_REASON = 'El certificado no tiene fecha ni sello del médico';

async function signedInPage(browser, role, homePattern) {
  const context = await browser.newContext();
  const page = await context.newPage();
  const loginPage = new LoginPage(page);
  await loginPage.open();
  await loginPage.signInAs(role);
  await expect(page).toHaveURL(homePattern);
  return { context, page };
}

async function justifyAsStudent(browser, absence) {
  const { context, page } = await signedInPage(browser, 'alumno', /\/alumno\/inicio$/);
  const attendancePage = new StudentAttendancePage(page);
  await attendancePage.open(absence.sectionEnrollmentId);
  await attendancePage.justifyAbsence(absence.dayOfMonth, RECEIPT_FILE);
  await expect(attendancePage.justifyDialog).toBeHidden();
  await expect(attendancePage.absentDay(absence.dayOfMonth)).toHaveCount(0);
  await context.close();
}

async function reviewAsPreceptor(browser, absence, reviewAction) {
  const { context, page } = await signedInPage(browser, 'preceptor', /\/preceptor\/inicio$/);
  const requestsPage = new AttendanceRequestsPage(page);
  await requestsPage.open('preceptor');
  await expect(requestsPage.justificationCard(absence.attendanceId)).toContainText(absence.classDate);
  await reviewAction(page, requestsPage);
  await context.close();
}

test.describe('@flow attendance justification', () => {
  test('rejectedJustificationCanBeSubmittedAgainAndApproved', async ({ browser, request }) => {
    const absence = await recordFreshAbsenceForDemoStudent(request);

    await justifyAsStudent(browser, absence);
    await reviewAsPreceptor(browser, absence, async (page, requestsPage) => {
      await requestsPage.expectReceiptOpens(absence.attendanceId);
      await requestsPage.rejectJustification(absence.attendanceId, REJECTION_REASON);
      await expect(page.getByText('Justificación rechazada')).toBeVisible();
      await expect(requestsPage.justificationCard(absence.attendanceId)).toHaveCount(0);
    });

    const rejected = await findAttendanceRecord(request, absence);
    expect(rejected.status).toBe('ABSENT');

    const rejectedView = await signedInPage(browser, 'alumno', /\/alumno\/inicio$/);
    const rejectedAttendancePage = new StudentAttendancePage(rejectedView.page);
    await rejectedAttendancePage.open(absence.sectionEnrollmentId);
    await expect(rejectedView.page.getByText(`Motivo del rechazo: ${REJECTION_REASON}`)).toBeVisible();
    await rejectedAttendancePage.openAbsence(absence.dayOfMonth);
    await expect(rejectedAttendancePage.justifyDialog.getByText('Tu justificación fue rechazada')).toBeVisible();
    await expect(rejectedAttendancePage.justifyDialog.getByText(REJECTION_REASON)).toBeVisible();
    await expect(rejectedAttendancePage.justifyDialog.getByRole('heading', { name: 'Volver a justificar' })).toBeVisible();
    await rejectedView.context.close();

    await justifyAsStudent(browser, absence);
    await reviewAsPreceptor(browser, absence, async (page, requestsPage) => {
      await requestsPage.approveJustification(absence.attendanceId);
      await expect(page.getByText('Justificación aprobada')).toBeVisible();
      await expect(requestsPage.justificationCard(absence.attendanceId)).toHaveCount(0);
    });

    const approved = await findAttendanceRecord(request, absence);
    expect(approved.status).toBe('JUSTIFIED');

    const { context, page } = await signedInPage(browser, 'alumno', /\/alumno\/inicio$/);
    await new StudentAttendancePage(page).open(absence.sectionEnrollmentId);
    await expect(page.getByTitle('Ausencia justificada')).not.toHaveCount(0);
    await context.close();
  });
});
