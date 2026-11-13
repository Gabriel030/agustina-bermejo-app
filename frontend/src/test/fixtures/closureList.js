import { http, HttpResponse } from 'msw';

const API = 'http://localhost:8080';

export const closureListPath = `${API}/commission-closures`;

export function closureListItem(overrides = {}) {
  return {
    sectionKind: 'PROGRAM',
    sectionId: 101,
    closureId: 31,
    subjectName: 'Teorías Socioculturales',
    sectionName: 'Comisión A · Mañana',
    termId: 5,
    termLabel: '1.er cuatrimestre 2026',
    instructorNames: ['E. Staib'],
    studentCount: 32,
    promoteCount: 24,
    toReviewCount: 3,
    status: 'TEACHER_CONFIRMED',
    statusDate: '2026-07-13T10:00:00',
    statusFact: 'CONFIRMED',
    reportNumber: null,
    reportVersion: null,
    book: null,
    folio: null,
    ...overrides,
  };
}

export function closedClosureListItem(overrides = {}) {
  return closureListItem({
    sectionId: 109,
    closureId: 39,
    subjectName: 'Ética Profesional',
    status: 'CLOSED',
    statusFact: 'CLOSED',
    reportNumber: 'ACTA-2026-0042',
    reportVersion: 1,
    book: 'L28',
    folio: '93',
    ...overrides,
  });
}

export function pendingClosureListItem(overrides = {}) {
  return closureListItem({
    sectionId: 106,
    closureId: null,
    subjectName: 'Psicología Social',
    status: 'DRAFT_AVAILABLE',
    statusFact: 'NOT_DELIVERED',
    statusDate: null,
    promoteCount: null,
    toReviewCount: null,
    ...overrides,
  });
}

export function closureListPage(items, overrides = {}) {
  return {
    items,
    page: 0,
    size: 10,
    totalElements: items.length,
    counts: { all: items.length, teacherConfirmed: 1, draftAvailable: 1, closed: 1, reopened: 0 },
    ...overrides,
  };
}

export function mockClosureList(server, pageFor) {
  const requests = [];
  server.use(
    http.get(closureListPath, ({ request }) => {
      const params = new URL(request.url).searchParams;
      requests.push(Object.fromEntries(params));
      return HttpResponse.json(pageFor(params));
    }),
  );
  return requests;
}

export function mockClosureExport(server, closureId, filename) {
  server.use(
    http.get(`${API}/commission-closures/${closureId}/export`, () =>
      new HttpResponse(new Uint8Array([80, 75]), {
        headers: {
          'Content-Type': 'application/octet-stream',
          'Content-Disposition': `attachment; filename="${filename}"; filename*=UTF-8''${filename}`,
        },
      }),
    ),
  );
}
