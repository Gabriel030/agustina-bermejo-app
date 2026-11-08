import { renderHook, act } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createInvalidationHarness } from '@/test/invalidationHarness';
import { apiFetch } from '@/api/client';
import { useInscribirmeCurso } from './useCatalogo';

vi.mock('@/api/client', () => ({ apiFetch: vi.fn() }));

beforeEach(() => {
  apiFetch.mockReset();
  apiFetch.mockResolvedValue({});
});

describe('course enrollment mutation', () => {
  it('enrollInCourseInvalidatesCourseEnrollmentsAndApplicationsOnly', async () => {
    const keys = [
      ['me'],
      ['me', 'sessions'],
      ['me', 'course-enrollments'],
      ['me', 'program-enrollments'],
      ['me', 'schedule'],
      ['mis-solicitudes-curso'],
      ['mis-solicitudes-carrera'],
    ];
    const harness = createInvalidationHarness(keys);
    const { result } = renderHook(() => useInscribirmeCurso(), { wrapper: harness.wrapper });
    await act(async () => {
      await result.current.mutateAsync({ body: new FormData(), idempotencyKey: 'k' });
    });
    const invalidated = harness.invalidatedKeys();
    expect(invalidated).toEqual(expect.arrayContaining([['me', 'course-enrollments'], ['mis-solicitudes-curso']]));
    expect(invalidated).toHaveLength(2);
  });
});
