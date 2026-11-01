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
      await requestsPage.rejectJustification(absence.attendanceId, REJECTION_REASON);
      await expect(page.getByText('Justificación rechazada')).toBeVisible();
      await expect(requestsPage.justificationCard(absence.attendanceId)).toHaveCount(0);
    });

    const rejected = await findAttendanceRecord(request, absence);
    expect(rejected.status).toBe('ABSENT');

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

  test.fixme('studentSeesRejectionReasonAfterRejection', async ({ browser, request }) => {
    const absence = await recordFreshAbsenceForDemoStudent(request);
    await justifyAsStudent(browser, absence);
    await reviewAsPreceptor(browser, absence, async (_page, requestsPage) => {
      await requestsPage.rejectJustification(absence.attendanceId, REJECTION_REASON);
    });

    const { context, page } = await signedInPage(browser, 'alumno', /\/alumno\/inicio$/);
    await new StudentAttendancePage(page).open(absence.sectionEnrollmentId);
    await expect(page.getByText(REJECTION_REASON)).toBeVisible();
    await context.close();
  });

  test.fixme('staffCanOpenReceiptOfPendingJustification', async ({ browser, request }) => {
    const absence = await recordFreshAbsenceForDemoStudent(request);
    await justifyAsStudent(browser, absence);

    const { context, page } = await signedInPage(browser, 'preceptor', /\/preceptor\/inicio$/);
    const requestsPage = new AttendanceRequestsPage(page);
    await requestsPage.open('preceptor');
    const receiptLink = requestsPage.justificationCard(absence.attendanceId).getByRole('link', { name: 'Ver adjunto' });
    await expect(receiptLink).toBeVisible();
    const href = await receiptLink.getAttribute('href');
    const receiptResponse = await page.request.get(href);
    expect(receiptResponse.status()).toBe(200);
    await context.close();
  });
});
