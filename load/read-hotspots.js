import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8461';
const directorUser = __ENV.DIRECTOR_USER || 'director.demo';
const directorPassword = __ENV.DIRECTOR_PASSWORD || 'Test1234';
const directorVus = Number(__ENV.LOAD_DIRECTOR_VUS || 20);
const duration = __ENV.LOAD_DURATION || '1m';
const thinkTimeSeconds = Number(__ENV.LOAD_THINK_TIME_SECONDS || 0.5);

const dashboardP95 = __ENV.THRESHOLD_DASHBOARD_P95_MS || '300';
const studentsP95 = __ENV.THRESHOLD_STUDENTS_P95_MS || '300';
const debtorsP95 = __ENV.THRESHOLD_DEBTORS_P95_MS || '300';
const maxErrorRate = __ENV.THRESHOLD_ERROR_RATE || '0.005';

export const options = {
  scenarios: {
    director_reads: {
      executor: 'constant-vus',
      exec: 'directorReads',
      vus: directorVus,
      duration,
    },
  },
  thresholds: {
    http_req_failed: [`rate<${maxErrorRate}`],
    'http_req_duration{endpoint:financial_dashboard}': [`p(95)<${dashboardP95}`],
    'http_req_duration{endpoint:students}': [`p(95)<${studentsP95}`],
    'http_req_duration{endpoint:debtors}': [`p(95)<${debtorsP95}`],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const response = http.post(
    `${baseUrl}/auth/login`,
    JSON.stringify({ username: directorUser, password: directorPassword }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(response, { 'director login responds 200': (r) => r.status === 200 });
  return { token: response.json('accessToken') };
}

function authorized(token, endpoint) {
  return { headers: { Authorization: `Bearer ${token}` }, tags: { endpoint } };
}

function expectOk(response, label) {
  check(response, { [`${label} responds 2xx`]: (r) => r.status >= 200 && r.status < 300 });
}

export function directorReads(data) {
  expectOk(http.get(`${baseUrl}/financial-dashboard`, authorized(data.token, 'financial_dashboard')), 'financial dashboard');
  expectOk(http.get(`${baseUrl}/students?page=0&size=50`, authorized(data.token, 'students')), 'students');
  expectOk(http.get(`${baseUrl}/financial-dashboard/debtors?page=0&size=25`, authorized(data.token, 'debtors')), 'debtors');
  sleep(thinkTimeSeconds * (0.5 + Math.random()));
}
