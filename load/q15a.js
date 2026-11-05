import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8090';
const sessionsFile = __ENV.LOAD_TEST_SESSIONS_FILE || '../backend/target/load-test-sessions.json';
const duration = __ENV.LOAD_DURATION || '10m';
const studentVus = Number(__ENV.LOAD_STUDENT_VUS || 100);
const staffVus = Number(__ENV.LOAD_STAFF_VUS || 20);
const teacherVus = Number(__ENV.LOAD_TEACHER_VUS || 10);
const uploadEvery = Number(__ENV.LOAD_UPLOAD_EVERY || 10);
const receiptBytes = Number(__ENV.LOAD_RECEIPT_BYTES || 200 * 1024);
const thinkTimeSeconds = Number(__ENV.LOAD_THINK_TIME_SECONDS || 2);

const maxErrorRate = __ENV.THRESHOLD_ERROR_RATE || '0.005';
const readP95 = __ENV.THRESHOLD_READ_P95_MS || '500';
const readP99 = __ENV.THRESHOLD_READ_P99_MS || '1500';
const uploadP95 = __ENV.THRESHOLD_UPLOAD_P95_MS || '2000';
const rosterP95 = __ENV.THRESHOLD_ROSTER_P95_MS || '500';
const staffListP95 = __ENV.THRESHOLD_STAFF_LIST_P95_MS || '500';

const sessions = new SharedArray('sessions', () => [JSON.parse(open(sessionsFile))]);
const receiptPdf = '%PDF-1.4\n'.padEnd(receiptBytes, ' ');

const studentReadEndpoints = ['student_invoices', 'student_payment_status', 'student_price_breakdown'];

export const options = {
  scenarios: {
    students_viewing_and_paying: {
      executor: 'constant-vus',
      exec: 'studentsViewingAndPaying',
      vus: studentVus,
      duration,
    },
    staff_reviewing_billing: {
      executor: 'constant-vus',
      exec: 'staffReviewingBilling',
      vus: staffVus,
      duration,
    },
    teachers_opening_roster: {
      executor: 'constant-vus',
      exec: 'teachersOpeningRoster',
      vus: teacherVus,
      duration,
    },
  },
  thresholds: {
    http_req_failed: [`rate<${maxErrorRate}`],
    checks: [`rate>${1 - Number(maxErrorRate)}`],
    'http_req_duration{endpoint:student_invoices}': [`p(95)<${readP95}`, `p(99)<${readP99}`],
    'http_req_duration{endpoint:student_payment_status}': [`p(95)<${readP95}`, `p(99)<${readP99}`],
    'http_req_duration{endpoint:student_price_breakdown}': [`p(95)<${readP95}`, `p(99)<${readP99}`],
    'http_req_duration{endpoint:student_receipt_upload}': [`p(95)<${uploadP95}`],
    'http_req_duration{endpoint:staff_pending_receipts}': [`p(95)<${staffListP95}`],
    'http_req_duration{endpoint:staff_enrollment_invoices}': [`p(95)<${staffListP95}`],
    'http_req_duration{endpoint:teacher_roster}': [`p(95)<${rosterP95}`],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function authorized(token, endpoint) {
  return { headers: { Authorization: `Bearer ${token}` }, tags: { endpoint } };
}

function think() {
  sleep(thinkTimeSeconds * (0.5 + Math.random()));
}

function expectOk(response, label) {
  check(response, { [`${label} responds 2xx`]: (r) => r.status >= 200 && r.status < 300 });
}

export function studentsViewingAndPaying() {
  const { students } = sessions[0];
  const iteration = exec.scenario.iterationInTest;
  const student = students[iteration % students.length];
  const invoiceId = student.invoiceIds[iteration % student.invoiceIds.length];

  expectOk(http.get(`${baseUrl}/me/program-enrollments/${student.programEnrollmentId}/invoices`, authorized(student.token, studentReadEndpoints[0])), 'student invoices');
  expectOk(http.get(`${baseUrl}/me/enrollment-payment-status`, authorized(student.token, studentReadEndpoints[1])), 'student payment status');
  expectOk(http.get(`${baseUrl}/me/invoices/${invoiceId}/price-breakdown`, authorized(student.token, studentReadEndpoints[2])), 'student price breakdown');

  if (iteration % uploadEvery === 0) {
    const uploadSequence = iteration / uploadEvery;
    const uploader = students[uploadSequence % students.length];
    const invoiceSlot = Math.floor(uploadSequence / students.length);
    if (invoiceSlot < uploader.invoiceIds.length) {
      const body = { comprobante: http.file(receiptPdf, 'comprobante.pdf', 'application/pdf') };
      expectOk(http.post(`${baseUrl}/me/invoices/${uploader.invoiceIds[invoiceSlot]}/bank-transfer-receipts`, body, authorized(uploader.token, 'student_receipt_upload')), 'receipt upload');
    }
  }
  think();
}

export function staffReviewingBilling() {
  const { students, administrative } = sessions[0];
  const student = students[exec.scenario.iterationInTest % students.length];
  expectOk(http.get(`${baseUrl}/bank-transfer-receipts`, authorized(administrative.token, 'staff_pending_receipts')), 'staff pending receipts');
  expectOk(http.get(`${baseUrl}/program-enrollments/${student.programEnrollmentId}/invoices`, authorized(administrative.token, 'staff_enrollment_invoices')), 'staff enrollment invoices');
  think();
}

export function teachersOpeningRoster() {
  const { teachers } = sessions[0];
  const teacher = teachers[exec.scenario.iterationInTest % teachers.length];
  expectOk(http.get(`${baseUrl}/program-sections/${teacher.sectionId}/roster`, authorized(teacher.token, 'teacher_roster')), 'teacher roster');
  think();
}
