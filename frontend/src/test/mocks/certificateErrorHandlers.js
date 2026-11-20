import { http, HttpResponse } from 'msw';
import { errorBody } from '@/test/fixtures/errors';

const API = 'http://localhost:8080';

export function certificateErrorResponse(status, code) {
  return HttpResponse.json(errorBody(code), { status });
}

export const revokedOwnCertificatePdfHandler = http.get(`${API}/me/certificates/:id/pdf`, () =>
  certificateErrorResponse(409, 'GRADUATION_CERTIFICATE_REVOKED'),
);

export const unavailableOwnCertificatePdfHandler = http.get(`${API}/me/certificates/:id/pdf`, () =>
  certificateErrorResponse(409, 'GRADUATION_CERTIFICATE_NOT_AVAILABLE'),
);

export const certificateNotFoundHandler = http.get(`${API}/graduation-certificates/:id`, () =>
  certificateErrorResponse(404, 'GRADUATION_CERTIFICATE_NOT_FOUND'),
);

export const revokeNotRevocableHandler = http.post(`${API}/graduation-certificates/:id/revoke`, () =>
  certificateErrorResponse(409, 'GRADUATION_CERTIFICATE_NOT_REVOCABLE'),
);

export const reissueNotRevocableHandler = http.post(`${API}/graduation-certificates/:id/reissue`, () =>
  certificateErrorResponse(409, 'GRADUATION_CERTIFICATE_NOT_REVOCABLE'),
);

export const discardNothingHandler = http.delete(`${API}/certificate-templates/:kind/draft`, () =>
  certificateErrorResponse(409, 'TEMPLATE_NOTHING_TO_DISCARD'),
);

export const publishNothingHandler = http.post(`${API}/certificate-templates/:kind/publish`, () =>
  certificateErrorResponse(409, 'TEMPLATE_NOTHING_TO_PUBLISH'),
);

export const publishIncompleteHandler = http.post(`${API}/certificate-templates/:kind/publish`, () =>
  certificateErrorResponse(422, 'TEMPLATE_INCOMPLETE'),
);
