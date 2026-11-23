import { describe, expect, it } from 'vitest';
import {
  EQUIVALENCE_DOCUMENT_IDS,
  EQUIVALENCE_ENROLLMENT_IDS,
  EQUIVALENCE_SUBJECT_IDS,
  EQUIVALENCE_VOID_IDS,
} from './subjectEquivalenceHandlers';

const API = 'http://localhost:8080';
const json = { 'Content-Type': 'application/json' };

async function call(method, path, body) {
  const response = await fetch(`${API}${path}`, {
    method,
    headers: json,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return { status: response.status, body: await response.json() };
}

const validRecord = (overrides = {}) => ({
  subjectId: 11,
  originInstitution: 'Universidad Nacional de Cuyo',
  originSubjectName: 'Análisis Matemático I',
  originScore: 8,
  recognizedOn: '2026-09-15',
  resolutionNumber: 'RES-2026-114',
  reason: 'Equivalencia por contenidos compatibles.',
  ...overrides,
});

const record = (overrides, enrollmentId = EQUIVALENCE_ENROLLMENT_IDS.active) =>
  call('POST', `/program-enrollments/${enrollmentId}/equivalences`, validRecord(overrides));

describe('subject equivalence handlers', () => {
  it('emptyEnrollmentReturnsEmptyList', async () => {
    const { status, body } = await call('GET', `/program-enrollments/${EQUIVALENCE_ENROLLMENT_IDS.empty}/equivalences`);
    expect(status).toBe(200);
    expect(body).toEqual([]);
  });

  it('defaultListReturnsTwoActiveEquivalences', async () => {
    const { body } = await call('GET', `/program-enrollments/${EQUIVALENCE_ENROLLMENT_IDS.active}/equivalences`);
    expect(body).toHaveLength(2);
    expect(body.every((item) => item.status === 'ACTIVE')).toBe(true);
  });

  it('statusFilterSeparatesVoidedFromActive', async () => {
    const base = `/program-enrollments/${EQUIVALENCE_ENROLLMENT_IDS.mixed}/equivalences`;
    expect((await call('GET', `${base}?status=VOIDED`)).body.map((item) => item.status)).toEqual(['VOIDED']);
    expect((await call('GET', `${base}?status=ALL`)).body).toHaveLength(4);
    const outOfPlan = (await call('GET', `${base}?status=ALL`)).body.find((item) => !item.inCurrentPlan);
    expect(outOfPlan.credits).toBeNull();
  });

  it('ownListHidesReasonAndActors', async () => {
    const { body } = await call('GET', `/me/program-enrollments/${EQUIVALENCE_ENROLLMENT_IDS.mixed}/equivalences`);
    expect(body.length).toBeGreaterThan(0);
    body.forEach((item) => {
      expect(item).not.toHaveProperty('reason');
      expect(item).not.toHaveProperty('recordedBy');
      expect(item.status).toBe('ACTIVE');
    });
  });

  it('foreignEnrollmentIsNotFound', async () => {
    const own = await call('GET', `/me/program-enrollments/${EQUIVALENCE_ENROLLMENT_IDS.notFound}/equivalences`);
    expect(own.status).toBe(404);
    expect(own.body.code).toBe('PROGRAM_ENROLLMENT_NOT_FOUND');
  });

  it('recordReturnsCreatedEquivalence', async () => {
    const { status, body } = await record({});
    expect(status).toBe(201);
    expect(body.status).toBe('ACTIVE');
  });

  it('recordOutOfPlanSubjectReturnsNullCreditsAndNotInPlan', async () => {
    const { body } = await record({ subjectId: EQUIVALENCE_SUBJECT_IDS.outOfPlanRecognized, originScore: null });
    expect(body.credits).toBeNull();
    expect(body.inCurrentPlan).toBe(false);
  });

  it.each([
    ['outOfRangeScoreIsInvalidScore', { originScore: 11 }, 422, 'INVALID_SCORE'],
    ['shortReasonIsReasonInvalid', { reason: 'corto' }, 422, 'EQUIVALENCE_REASON_INVALID'],
    ['missingReasonIsReasonInvalid', { reason: undefined }, 422, 'EQUIVALENCE_REASON_INVALID'],
    ['futureDateIsFutureDate', { recognizedOn: '2999-01-01' }, 422, 'FUTURE_DATE'],
    ['foreignDocumentIsDocumentInvalid', { studentDocumentId: EQUIVALENCE_DOCUMENT_IDS.invalid }, 422, 'EQUIVALENCE_DOCUMENT_INVALID'],
    ['unknownSubjectIsNotInPlan', { subjectId: EQUIVALENCE_SUBJECT_IDS.notInPlan }, 422, 'SUBJECT_NOT_IN_PLAN'],
    ['activeSubjectIsAlreadyActive', { subjectId: EQUIVALENCE_SUBJECT_IDS.alreadyActive }, 409, 'EQUIVALENCE_ALREADY_ACTIVE'],
    ['approvedSubjectIsAlreadyApproved', { subjectId: EQUIVALENCE_SUBJECT_IDS.alreadyApproved }, 409, 'SUBJECT_ALREADY_APPROVED'],
    ['ongoingSubjectIsInProgress', { subjectId: EQUIVALENCE_SUBJECT_IDS.inProgress }, 409, 'SUBJECT_IN_PROGRESS'],
  ])('%s', async (_name, overrides, status, code) => {
    const result = await record(overrides);
    expect(result.status).toBe(status);
    expect(result.body.code).toBe(code);
  });

  it('inactiveEnrollmentRejectsRecord', async () => {
    const result = await record({}, EQUIVALENCE_ENROLLMENT_IDS.notActive);
    expect(result.status).toBe(409);
    expect(result.body.code).toBe('ENROLLMENT_NOT_ACTIVE');
  });

  it('voidReturnsVoidedEquivalence', async () => {
    const result = await call('POST', `/program-enrollments/101/equivalences/303/void`, { reason: 'Cargada por error en la materia.' });
    expect(result.status).toBe(200);
    expect(result.body.status).toBe('VOIDED');
  });

  it('voidFailuresReturnDomainCodes', async () => {
    const reason = { reason: 'Cargada por error en la materia.' };
    const notFound = await call('POST', `/program-enrollments/101/equivalences/${EQUIVALENCE_VOID_IDS.notFound}/void`, reason);
    expect(notFound.body.code).toBe('SUBJECT_EQUIVALENCE_NOT_FOUND');
    const again = await call('POST', `/program-enrollments/101/equivalences/${EQUIVALENCE_VOID_IDS.alreadyVoided}/void`, reason);
    expect(again.body.code).toBe('EQUIVALENCE_ALREADY_VOIDED');
    const short = await call('POST', `/program-enrollments/101/equivalences/303/void`, { reason: 'x' });
    expect(short.body.code).toBe('EQUIVALENCE_REASON_INVALID');
    const missing = await call('POST', `/program-enrollments/101/equivalences/303/void`, {});
    expect(missing.status).toBe(422);
    expect(missing.body.code).toBe('EQUIVALENCE_REASON_INVALID');
  });
});
