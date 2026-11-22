import { describe, expect, it } from 'vitest';
import {
  approvalBlockReason,
  blockingReasonMessages,
  cohortLabel,
  debtMessages,
  documentationLabel,
  filterPendingApplications,
  isApplicationAlreadyResolved,
  missingDocumentsText,
  nextPendingId,
  pendingFilterOptions,
} from '@/features/enrollment/lib/applicationReview';

describe('applicationReview', () => {
  it('turns the cohort name into a readable label', () => {
    expect(cohortLabel('FIRST 2026')).toBe('Primer ingreso 2026');
    expect(cohortLabel('SECOND 2027')).toBe('Segundo ingreso 2027');
    expect(cohortLabel('Cohorte especial')).toBe('Cohorte especial');
    expect(cohortLabel(null)).toBeNull();
  });

  it('joins the missing document names', () => {
    expect(missingDocumentsText(['DNI_DORSO'])).toBe('DNI dorso');
    expect(missingDocumentsText(['DNI_FRENTE', 'ANALITICO_SECUNDARIO'])).toBe('DNI frente y analítico');
    expect(missingDocumentsText(['DNI_FRENTE', 'DNI_DORSO', 'ANALITICO_SECUNDARIO'])).toBe('DNI frente, DNI dorso y analítico');
  });

  it('labels the documentation state', () => {
    expect(documentationLabel({ complete: true, missingTypes: [] })).toBe('Completa');
    expect(documentationLabel({ complete: false, missingTypes: ['DNI_DORSO'] })).toBe('Falta DNI dorso');
  });

  it('explains the debt only when it blocks', () => {
    expect(debtMessages({ blocksApproval: false, violationCodes: [] })).toEqual([]);
    expect(debtMessages({ blocksApproval: true, violationCodes: ['OVERDUE_DEBT'] })).toEqual(['Tiene cuotas vencidas sin pagar.']);
    expect(debtMessages({ blocksApproval: true, violationCodes: [] })).toHaveLength(1);
    expect(debtMessages({ blocksApproval: true, violationCodes: ['UNKNOWN'] })[0]).toMatch(/deuda pendiente/);
  });

  it('builds the visible reason when approving is blocked', () => {
    expect(approvalBlockReason({ canApprove: true })).toBeNull();
    expect(
      approvalBlockReason({ canApprove: false, documentation: { complete: false, missingTypes: ['DNI_DORSO'] }, debt: { blocksApproval: true } }),
    ).toBe('Aprobar está deshabilitado: falta DNI dorso y el alumno tiene deuda.');
    expect(approvalBlockReason({ canApprove: false, documentation: { complete: true }, debt: { blocksApproval: true } })).toBe(
      'Aprobar está deshabilitado: el alumno tiene deuda.',
    );
  });

  it('lists the blocking reasons without repeating the documentation one', () => {
    expect(
      blockingReasonMessages({ blockingReasons: ['STUDENT_NOT_CONFIRMED', 'APPLICATION_DOCUMENTS_INCOMPLETE', 'OTHER'] }),
    ).toEqual(['La inscripción del alumno todavía no está confirmada.', 'Hay una condición sin cumplir.']);
    expect(blockingReasonMessages({})).toEqual([]);
  });

  it('only treats the state conflict as already resolved', () => {
    expect(isApplicationAlreadyResolved({ status: 409, code: 'INVALID_PROGRAM_ENROLLMENT_APPLICATION_STATE' })).toBe(true);
    expect(isApplicationAlreadyResolved({ status: 409, code: 'APPLICATION_DECISION_IDEMPOTENCY_KEY_CONFLICT' })).toBe(false);
    expect(isApplicationAlreadyResolved({ status: 500 })).toBe(false);
  });

  it('picks the oldest pending application other than the current one', () => {
    const pending = [
      { id: 1, requestedAt: '2026-11-05T10:00:00' },
      { id: 2, requestedAt: '2026-11-03T10:00:00' },
      { id: 3, requestedAt: '2026-11-04T10:00:00' },
    ];
    expect(nextPendingId(pending, 1)).toBe(2);
    expect(nextPendingId(pending, 2)).toBe(3);
    expect(nextPendingId([pending[0]], 1)).toBeNull();
  });
});

describe('pending filters', () => {
  const complete = { complete: true, missingTypes: [] };
  const incomplete = { complete: false, missingTypes: ['DNI_DORSO'] };
  const applications = [
    { id: 1, programName: 'Software', documentation: complete },
    { id: 2, programName: 'Software', documentation: incomplete },
    { id: 3, programName: 'Ciberseguridad', documentation: complete },
  ];
  const ids = (list) => list.map((application) => application.id);

  it('returns everything when no filter is set', () => {
    expect(ids(filterPendingApplications(applications, {}))).toEqual([1, 2, 3]);
    expect(ids(filterPendingApplications(applications))).toEqual([1, 2, 3]);
    expect(ids(filterPendingApplications(undefined, {}))).toEqual([]);
  });

  it('filters by program and by documentation independently', () => {
    expect(ids(filterPendingApplications(applications, { programs: ['Software'] }))).toEqual([1, 2]);
    expect(ids(filterPendingApplications(applications, { documentations: ['Completa'] }))).toEqual([1, 3]);
  });

  it('combines program and documentation with AND', () => {
    expect(ids(filterPendingApplications(applications, { programs: ['Software'], documentations: ['Completa'] }))).toEqual([1]);
    expect(ids(filterPendingApplications(applications, { programs: ['Ciberseguridad'], documentations: ['Incompleta'] }))).toEqual([]);
  });

  it('builds sorted options with counts from the full set', () => {
    expect(pendingFilterOptions(applications)).toEqual({
      programs: [
        { value: 'Ciberseguridad', label: 'Ciberseguridad', count: 1 },
        { value: 'Software', label: 'Software', count: 2 },
      ],
      documentations: [
        { value: 'Completa', label: 'Completa', count: 2 },
        { value: 'Incompleta', label: 'Incompleta', count: 1 },
      ],
    });
  });
});
