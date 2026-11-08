import { renderHook, act, waitFor } from '@testing-library/react';
import { useQuery } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createInvalidationHarness } from '@/test/invalidationHarness';
import { apiFetch } from '@/api/client';
import { useCompleteRefund, useFailRefund, useRequestRefund } from './useRefunds';
import { useRegisterCashPayment } from '@/hooks/useBilling';
import {
  useApproveAuthorizationRequest,
  useRejectAuthorizationRequest,
  useWithdrawAuthorizationRequest,
} from './useAuthorizationRequests';

vi.mock('@/api/client', () => ({ apiFetch: vi.fn() }));

const STUDENT_KEYS = [
  ['students', 7, 'refunds'],
  ['students', 7, 'refund-eligibility'],
  ['students', 7, 'billing-overview'],
  ['students', 7, 'credit-balance'],
  ['students', 7, 'payments'],
  ['students', 7, 'credit-notes'],
  ['students', 8, 'refunds'],
  ['students', 8, 'billing-overview'],
  ['alumnos', {}],
];

beforeEach(() => {
  apiFetch.mockReset();
  apiFetch.mockResolvedValue({});
});

async function run(hook, variables, keys = STUDENT_KEYS) {
  const harness = createInvalidationHarness(keys);
  const { result } = renderHook(hook, { wrapper: harness.wrapper });
  await act(async () => {
    await result.current.mutateAsync(variables);
  });
  await waitFor(() => expect(result.current.isSuccess).toBe(true));
  return harness.invalidatedKeys();
}

const REFUND_AFFECTED = [
  ['students', 7, 'refunds'],
  ['students', 7, 'refund-eligibility'],
  ['students', 7, 'billing-overview'],
  ['students', 7, 'credit-balance'],
];

describe('refund mutations', () => {
  it('requestRefundInvalidatesOnlyRefundQueriesOfThatStudent', async () => {
    const invalidated = await run(() => useRequestRefund(7), { payload: {}, idempotencyKey: 'k' });
    expect(invalidated).toEqual(expect.arrayContaining(REFUND_AFFECTED));
    expect(invalidated).toHaveLength(REFUND_AFFECTED.length);
    expect(invalidated).not.toContainEqual(['students', 8, 'refunds']);
    expect(invalidated).not.toContainEqual(['students', 7, 'payments']);
    expect(invalidated).not.toContainEqual(['students', 7, 'credit-notes']);
    expect(invalidated).not.toContainEqual(['alumnos', {}]);
  });

  it('completeRefundInvalidatesOnlyRefundQueriesOfThatStudent', async () => {
    const invalidated = await run(() => useCompleteRefund(), { studentId: 7, refundId: 3, payload: {} });
    expect(invalidated).toEqual(expect.arrayContaining(REFUND_AFFECTED));
    expect(invalidated).toHaveLength(REFUND_AFFECTED.length);
    expect(invalidated).not.toContainEqual(['students', 8, 'billing-overview']);
  });

  it('failRefundInvalidatesOnlyRefundQueriesOfThatStudent', async () => {
    const invalidated = await run(() => useFailRefund(), { studentId: 7, refundId: 3, reason: 'x' });
    expect(invalidated).toEqual(expect.arrayContaining(REFUND_AFFECTED));
    expect(invalidated).toHaveLength(REFUND_AFFECTED.length);
  });

  it('refundInvalidationMatchesStudentIdAsStringOrNumber', async () => {
    const invalidated = await run(() => useCompleteRefund(), { studentId: '7', refundId: 3, payload: {} });
    expect(invalidated).toHaveLength(REFUND_AFFECTED.length);
  });
});

describe('cash payment mutation', () => {
  it('registerCashPaymentInvalidatesPaymentQueriesOfThatStudentOnly', async () => {
    const invalidated = await run(() => useRegisterCashPayment(7), { key: 'k', payload: {} });
    expect(invalidated).toEqual(
      expect.arrayContaining([
        ['students', 7, 'billing-overview'],
        ['students', 7, 'payments'],
        ['students', 7, 'credit-balance'],
        ['students', 7, 'refund-eligibility'],
      ]),
    );
    expect(invalidated).toHaveLength(4);
    expect(invalidated).not.toContainEqual(['students', 7, 'refunds']);
    expect(invalidated).not.toContainEqual(['students', 7, 'credit-notes']);
    expect(invalidated).not.toContainEqual(['students', 8, 'billing-overview']);
  });
});

describe('authorization decisions', () => {
  const KEYS = [
    ['authorization-requests', 'list', { page: 0 }],
    ['authorization-requests', 'detail', 5],
    ['authorization-requests', 'detail', 6],
    ['students', 7, 'refunds'],
  ];

  it.each([
    ['approve', useApproveAuthorizationRequest],
    ['reject', useRejectAuthorizationRequest],
    ['withdraw', useWithdrawAuthorizationRequest],
  ])('%sInvalidatesListAndOwnDetailOnly', async (_name, hook) => {
    const invalidated = await run(hook, { id: 5 }, KEYS);
    expect(invalidated).toContainEqual(['authorization-requests', 'list', { page: 0 }]);
    expect(invalidated).toContainEqual(['authorization-requests', 'detail', 5]);
    expect(invalidated).not.toContainEqual(['authorization-requests', 'detail', 6]);
  });

  it('decisionRefetchesActiveStudentQueriesAndLeavesOtherAuthorizationDetailsAlone', async () => {
    const harness = createInvalidationHarness(KEYS);
    const studentFetch = vi.fn().mockResolvedValue({ fresh: true });
    const { result } = renderHook(
      () => ({
        student: useQuery({ queryKey: ['students', 7, 'refunds'], queryFn: studentFetch, staleTime: Infinity }),
        decision: useApproveAuthorizationRequest(),
      }),
      { wrapper: harness.wrapper },
    );
    await act(async () => {
      await result.current.decision.mutateAsync({ id: 5 });
    });
    await waitFor(() => expect(studentFetch).toHaveBeenCalledTimes(1));
    expect(harness.invalidatedKeys()).not.toContainEqual(['authorization-requests', 'detail', 6]);
  });
});
