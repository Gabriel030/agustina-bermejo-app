import { renderHook, act, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createInvalidationHarness } from '@/test/invalidationHarness';
import { apiFetch } from '@/api/client';
import { useJustificarInasistencia, useMiAsistencia, useMyAttendanceSummary } from './useStudentAttendance';

vi.mock('@/api/client', () => ({ apiFetch: vi.fn() }));

const ENTRIES = [{ id: 1, classDate: '2026-04-01', status: 'ABSENT' }];
const RISK = { calculable: true, libre: false, absencePercent: 10, maxAbsencePercent: 25 };

beforeEach(() => {
  apiFetch.mockReset();
  apiFetch.mockImplementation((path) => Promise.resolve(path.endsWith('/risk') ? RISK : ENTRIES));
});

describe('attendance requests', () => {
  it('summaryAndDetailShareOneAttendanceAndOneRiskRequest', async () => {
    const harness = createInvalidationHarness([]);
    const { result } = renderHook(
      () => ({ summary: useMyAttendanceSummary(9), detail: useMiAsistencia(9) }),
      { wrapper: harness.wrapper },
    );
    await waitFor(() => expect(result.current.summary.isSuccess && result.current.detail.isSuccess).toBe(true));
    const paths = apiFetch.mock.calls.map(([path]) => path);
    expect(paths.filter((path) => path === '/me/section-enrollments/9/attendance')).toHaveLength(1);
    expect(paths.filter((path) => path === '/me/section-enrollments/9/attendance/risk')).toHaveLength(1);
    expect(result.current.detail.data).toEqual(ENTRIES);
    expect(result.current.summary.data.days).toEqual(ENTRIES);
  });

  it('justifyInvalidatesOnlyAttendanceQueriesOfThatEnrollment', async () => {
    const keys = [
      ['me', 'section-enrollments', 9, 'attendance'],
      ['me', 'section-enrollments', 9, 'attendance', 'risk'],
      ['me', 'attendance-summary', 9],
      ['me', 'attendance-summary', 10],
      ['me', 'section-enrollments', 10, 'attendance'],
      ['me', 'sessions'],
      ['me'],
    ];
    const harness = createInvalidationHarness(keys);
    const { result } = renderHook(() => useJustificarInasistencia(), { wrapper: harness.wrapper });
    await act(async () => {
      await result.current.mutateAsync({ attendanceId: 1, comprobante: new Blob(['x']), sectionEnrollmentId: 9 });
    });
    const invalidated = harness.invalidatedKeys();
    expect(invalidated).toEqual(
      expect.arrayContaining([
        ['me', 'section-enrollments', 9, 'attendance'],
        ['me', 'section-enrollments', 9, 'attendance', 'risk'],
        ['me', 'attendance-summary', 9],
      ]),
    );
    expect(invalidated).toHaveLength(3);
  });
});
