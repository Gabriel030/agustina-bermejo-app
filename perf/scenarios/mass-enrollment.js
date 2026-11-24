import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8461';
const students = Number(__ENV.LOAD_STUDENTS || 2000);
const enrollSections = Number(__ENV.LOAD_ENROLL_SECTIONS || 40);
const vus = Number(__ENV.LOAD_VUS || 50);
const maxDuration = __ENV.LOAD_MAX_DURATION || '10m';
const password = __ENV.LOAD_PASSWORD || 'Test1234';
const firstProgramEnrollmentId = 400000;
const firstSectionId = 1400000;

const enrollP95 = __ENV.THRESHOLD_ENROLL_P95_MS || '800';
const enrollP99 = __ENV.THRESHOLD_ENROLL_P99_MS || '2000';
const loginP95 = __ENV.THRESHOLD_LOGIN_P95_MS || '1000';
const maxErrorRate = __ENV.THRESHOLD_ERROR_RATE || '0.01';
const minThroughput = __ENV.THRESHOLD_MIN_ENROLLMENTS_PER_SECOND || '20';

const enrollments = new Counter('enrollments_created');

export const options = {
  scenarios: {
    mass_enrollment: {
      executor: 'shared-iterations',
      vus,
      iterations: students,
      maxDuration,
    },
  },
  thresholds: {
    'http_req_failed{endpoint:enroll}': [`rate<${maxErrorRate}`],
    'http_req_duration{endpoint:enroll}': [`p(95)<${enrollP95}`, `p(99)<${enrollP99}`],
    'http_req_duration{endpoint:login}': [`p(95)<${loginP95}`],
    checks: [`rate>${1 - Number(maxErrorRate)}`],
    enrollments_created: [`rate>=${minThroughput}`],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  if (!Number.isInteger(students) || students < 1) {
    throw new Error('LOAD_STUDENTS must be a positive integer');
  }
  const probe = http.get(`${baseUrl}/actuator/health`, { tags: { endpoint: 'health' } });
  if (probe.status !== 200) {
    throw new Error(`backend not reachable at ${baseUrl} (status ${probe.status})`);
  }
}

export default function () {
  const index = exec.scenario.iterationInTest;
  const studentNumber = index + 1;
  const programEnrollmentId = firstProgramEnrollmentId + studentNumber;
  const sectionId = firstSectionId + 1 + (index % enrollSections);

  const login = http.post(
    `${baseUrl}/auth/login`,
    JSON.stringify({ username: `loads${studentNumber}`, password }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'login' } },
  );
  const loggedIn = check(login, { 'login responds 200': (r) => r.status === 200 });
  if (!loggedIn) {
    return;
  }

  const enroll = http.post(
    `${baseUrl}/me/section-enrollments`,
    JSON.stringify({ programEnrollmentId, sectionId }),
    {
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${login.json('accessToken')}` },
      tags: { endpoint: 'enroll' },
      responseCallback: http.expectedStatuses(201),
    },
  );
  const created = check(enroll, { 'enroll responds 201': (r) => r.status === 201 });
  if (created) {
    enrollments.add(1);
  }
}
