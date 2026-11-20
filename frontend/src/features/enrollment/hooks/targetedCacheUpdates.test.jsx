import { renderHook, act, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createInvalidationHarness } from '@/test/invalidationHarness';
import { apiFetch } from '@/api/client';
import { useEnrollStudentInCourse, useEnrollStudentInProgram } from './useStaffEnrollment';

vi.mock('@/api/client', () => ({ apiFetch: vi.fn() }));

beforeEach(() => {
  apiFetch.mockReset();
});

function mount(hook, seeds) {
  const harness = createInvalidationHarness([]);
  seeds.forEach(([key, data]) => harness.queryClient.setQueryData(key, data));
  const { result } = renderHook(hook, { wrapper: harness.wrapper });
  return { ...harness, result };
}

const PROGRAMS_KEY = ['alumno', 7, 'program-enrollments'];
const COURSES_KEY = ['alumno', 7, 'course-enrollments'];

describe('staff enrollment targeted cache updates', () => {
  it('programEnrollmentAppendsToCachedListWithoutRefetch', async () => {
    apiFetch.mockResolvedValue({ id: 22, programId: 3, status: 'ACTIVE' });
    const { result, queryClient } = mount(() => useEnrollStudentInProgram(7), [[PROGRAMS_KEY, [{ id: 1, programId: 1 }]], [['alumno', 7], { id: 7 }]]);
    await act(async () => { await result.current.mutateAsync({ programId: 3, cohortId: 4 }); });
    expect(queryClient.getQueryData(PROGRAMS_KEY)).toEqual([{ id: 1, programId: 1 }, { id: 22, programId: 3, status: 'ACTIVE' }]);
    expect(queryClient.getQueryState(PROGRAMS_KEY).isInvalidated).toBe(true);
    expect(apiFetch).toHaveBeenCalledTimes(1);
  });

  it('programEnrollmentDoesNotCreateListCacheWhenNotLoaded', async () => {
    apiFetch.mockResolvedValue({ id: 22 });
    const { result, queryClient } = mount(() => useEnrollStudentInProgram(7), []);
    await act(async () => { await result.current.mutateAsync({ programId: 3, cohortId: 4 }); });
    expect(queryClient.getQueryData(PROGRAMS_KEY)).toBeUndefined();
  });

  it('programEnrollmentErrorKeepsCacheAndInvalidatesStudent', async () => {
    apiFetch.mockImplementation(() => Promise.reject(new Error('boom')));
    const { result, queryClient } = mount(() => useEnrollStudentInProgram(7), [[PROGRAMS_KEY, [{ id: 1 }]]]);
    act(() => result.current.mutate({ programId: 3, cohortId: 4 }));
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(queryClient.getQueryData(PROGRAMS_KEY)).toEqual([{ id: 1 }]);
    expect(queryClient.getQueryState(PROGRAMS_KEY).isInvalidated).toBe(true);
  });

  it('courseEnrollmentReplacesSameIdAndRefreshesOccupancyOnly', async () => {
    apiFetch.mockResolvedValue({ id: 5, courseId: 2, status: 'ACTIVE' });
    const { result, queryClient } = mount(() => useEnrollStudentInCourse(7), [
      [COURSES_KEY, [{ id: 5, status: 'WAITLISTED' }]],
      [['staff-enrollment', 'cycles', 9, 'occupancy'], { sections: [] }],
      [['staff-enrollment', 'courses'], []],
    ]);
    await act(async () => { await result.current.mutateAsync({ courseId: 2, sectionOverrides: [] }); });
    expect(queryClient.getQueryData(COURSES_KEY)).toEqual([{ id: 5, courseId: 2, status: 'ACTIVE' }]);
    expect(queryClient.getQueryState(['staff-enrollment', 'cycles', 9, 'occupancy']).isInvalidated).toBe(true);
    expect(queryClient.getQueryState(['staff-enrollment', 'courses']).isInvalidated).toBe(false);
  });

  it('courseEnrollmentErrorKeepsCacheAndInvalidatesStudent', async () => {
    apiFetch.mockImplementation(() => Promise.reject(new Error('boom')));
    const { result, queryClient } = mount(() => useEnrollStudentInCourse(7), [[COURSES_KEY, [{ id: 5 }]]]);
    act(() => result.current.mutate({ courseId: 2 }));
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(queryClient.getQueryData(COURSES_KEY)).toEqual([{ id: 5 }]);
    expect(queryClient.getQueryState(COURSES_KEY).isInvalidated).toBe(true);
  });
});
