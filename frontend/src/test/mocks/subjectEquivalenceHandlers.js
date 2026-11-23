import { http, HttpResponse } from 'msw';
import { errorBody } from '@/test/fixtures/errors';
import {
  activeSubjectEquivalences,
  outOfPlanSubjectEquivalence,
  ownSubjectEquivalence,
  subjectEquivalence,
  voidedSubjectEquivalence,
} from '@/test/fixtures/subjectEquivalences';

const API = 'http://localhost:8080';

export const EQUIVALENCE_ENROLLMENT_IDS = {
  empty: 100,
  active: 101,
  mixed: 102,
  notFound: 404,
  notActive: 900,
};

export const EQUIVALENCE_SUBJECT_IDS = {
  outOfPlanRecognized: 99,
  notInPlan: 404,
  alreadyActive: 409,
  alreadyApproved: 410,
  inProgress: 411,
};

export const EQUIVALENCE_DOCUMENT_IDS = { invalid: 999 };

export const EQUIVALENCE_VOID_IDS = { notFound: 404, alreadyVoided: 409 };

const failure = (status, code) => HttpResponse.json(errorBody(code), { status });

const listFor = (enrollmentId) => {
  if (enrollmentId === EQUIVALENCE_ENROLLMENT_IDS.empty) return [];
  if (enrollmentId === EQUIVALENCE_ENROLLMENT_IDS.mixed) {
    return [...activeSubjectEquivalences(), voidedSubjectEquivalence(), outOfPlanSubjectEquivalence()];
  }
  return activeSubjectEquivalences();
};

const todayIso = () => new Date().toISOString().slice(0, 10);

const reasonInvalid = (reason) => typeof reason !== 'string' || reason.trim().length < 10 || reason.length > 500;

export const recordEquivalenceError = (enrollmentId, body) => {
  if (enrollmentId === EQUIVALENCE_ENROLLMENT_IDS.notFound) return failure(404, 'PROGRAM_ENROLLMENT_NOT_FOUND');
  if (enrollmentId === EQUIVALENCE_ENROLLMENT_IDS.notActive) return failure(409, 'ENROLLMENT_NOT_ACTIVE');
  if (reasonInvalid(body?.reason)) return failure(422, 'EQUIVALENCE_REASON_INVALID');
  const score = body?.originScore;
  if (score !== null && score !== undefined && (typeof score !== 'number' || score < 1 || score > 10)) {
    return failure(422, 'INVALID_SCORE');
  }
  if (typeof body?.recognizedOn === 'string' && body.recognizedOn > todayIso()) return failure(422, 'FUTURE_DATE');
  if (body?.studentDocumentId === EQUIVALENCE_DOCUMENT_IDS.invalid) return failure(422, 'EQUIVALENCE_DOCUMENT_INVALID');
  if (body?.subjectId === EQUIVALENCE_SUBJECT_IDS.notInPlan) return failure(422, 'SUBJECT_NOT_IN_PLAN');
  if (body?.subjectId === EQUIVALENCE_SUBJECT_IDS.alreadyActive) return failure(409, 'EQUIVALENCE_ALREADY_ACTIVE');
  if (body?.subjectId === EQUIVALENCE_SUBJECT_IDS.alreadyApproved) return failure(409, 'SUBJECT_ALREADY_APPROVED');
  if (body?.subjectId === EQUIVALENCE_SUBJECT_IDS.inProgress) return failure(409, 'SUBJECT_IN_PROGRESS');
  return null;
};

export const voidEquivalenceError = (enrollmentId, equivalenceId, body) => {
  if (enrollmentId === EQUIVALENCE_ENROLLMENT_IDS.notFound) return failure(404, 'PROGRAM_ENROLLMENT_NOT_FOUND');
  if (equivalenceId === EQUIVALENCE_VOID_IDS.notFound) return failure(404, 'SUBJECT_EQUIVALENCE_NOT_FOUND');
  if (reasonInvalid(body?.reason)) return failure(422, 'EQUIVALENCE_REASON_INVALID');
  if (equivalenceId === EQUIVALENCE_VOID_IDS.alreadyVoided) return failure(409, 'EQUIVALENCE_ALREADY_VOIDED');
  return null;
};

export const subjectEquivalenceHandlers = [
  http.get(`${API}/program-enrollments/:id/equivalences`, ({ params, request }) => {
    const enrollmentId = Number(params.id);
    if (enrollmentId === EQUIVALENCE_ENROLLMENT_IDS.notFound) return failure(404, 'PROGRAM_ENROLLMENT_NOT_FOUND');
    const status = new URL(request.url).searchParams.get('status') ?? 'ACTIVE';
    const all = listFor(enrollmentId);
    return HttpResponse.json(status === 'ALL' ? all : all.filter((item) => item.status === status));
  }),
  http.get(`${API}/me/program-enrollments/:id/equivalences`, ({ params }) => {
    const enrollmentId = Number(params.id);
    if (enrollmentId === EQUIVALENCE_ENROLLMENT_IDS.notFound) return failure(404, 'PROGRAM_ENROLLMENT_NOT_FOUND');
    return HttpResponse.json(listFor(enrollmentId).filter((item) => item.status === 'ACTIVE').map(ownSubjectEquivalence));
  }),
  http.post(`${API}/program-enrollments/:id/equivalences`, async ({ params, request }) => {
    const body = await request.json().catch(() => ({}));
    const error = recordEquivalenceError(Number(params.id), body);
    if (error) return error;
    const outOfPlan = body.subjectId === EQUIVALENCE_SUBJECT_IDS.outOfPlanRecognized;
    const base = outOfPlan ? outOfPlanSubjectEquivalence() : subjectEquivalence();
    return HttpResponse.json(
      {
        ...base,
        id: 310,
        subjectId: body.subjectId,
        originInstitution: body.originInstitution,
        originSubjectName: body.originSubjectName,
        originScore: body.originScore ?? null,
        resolutionNumber: body.resolutionNumber,
        recognizedOn: body.recognizedOn,
        reason: body.reason,
        document: body.studentDocumentId ? { id: body.studentDocumentId, name: 'Documento del legajo.pdf' } : null,
        status: 'ACTIVE',
      },
      { status: 201 },
    );
  }),
  http.post(`${API}/program-enrollments/:id/equivalences/:equivalenceId/void`, async ({ params, request }) => {
    const body = await request.json().catch(() => ({}));
    const equivalenceId = Number(params.equivalenceId);
    const error = voidEquivalenceError(Number(params.id), equivalenceId, body);
    if (error) return error;
    return HttpResponse.json(
      voidedSubjectEquivalence({
        id: equivalenceId,
        voidReason: body.reason,
      }),
    );
  }),
];
