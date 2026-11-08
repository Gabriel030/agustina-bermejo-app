import { renderHook, act } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createInvalidationHarness } from '@/test/invalidationHarness';
import { apiFetch } from '@/api/client';
import { useWithdrawStudent } from './useAccionesLegajo';
import { useConfirmarAlta } from './useProspectos';
import { useActualizarPerfil, useSubirAvatar } from './useMiPerfil';

vi.mock('@/api/client', () => ({ apiFetch: vi.fn(), apiFetchBlob: vi.fn() }));

beforeEach(() => {
  apiFetch.mockReset();
  apiFetch.mockResolvedValue({});
});

async function run(hook, variables, keys) {
  const harness = createInvalidationHarness(keys);
  const { result } = renderHook(hook, { wrapper: harness.wrapper });
  await act(async () => {
    await result.current.mutateAsync(variables);
  });
  return harness.invalidatedKeys();
}

const ME_KEYS = [
  ['me'],
  ['me', 'sessions'],
  ['me', 'avatar'],
  ['me', 'notification-preferences'],
  ['me', 'attendance-summary', 3],
  ['me', 'course-enrollments'],
  ['me', 'program-enrollments'],
  ['me', 'schedule'],
];

describe('legajo and prospect mutations', () => {
  it('withdrawStudentInvalidatesOwnRecordAndMarksListStale', async () => {
    const keys = [['alumno', 7], ['alumno', 7, 'program-enrollments'], ['alumno', 8], ['alumnos', {}], ['prospects']];
    const invalidated = await run(() => useWithdrawStudent(7), { reason: 'x' }, keys);
    expect(invalidated).toEqual(expect.arrayContaining([['alumno', 7], ['alumnos', {}]]));
    expect(invalidated).toHaveLength(2);
  });

  it('confirmEnrollmentInvalidatesOnlyThatProspectAndLists', async () => {
    const keys = [
      ['students', 7, 'billing-overview'],
      ['students', 8, 'billing-overview'],
      ['alumno', 7],
      ['alumno', 8],
      ['alumnos', {}],
      ['prospects'],
      ['prospects', 7, 'documents'],
    ];
    const invalidated = await run(() => useConfirmarAlta(), { studentId: 7, datos: {} }, keys);
    expect(invalidated).toEqual(
      expect.arrayContaining([['students', 7, 'billing-overview'], ['alumno', 7], ['alumnos', {}], ['prospects']]),
    );
    expect(invalidated).toHaveLength(4);
    expect(invalidated).not.toContainEqual(['students', 8, 'billing-overview']);
    expect(invalidated).not.toContainEqual(['prospects', 7, 'documents']);
  });
});

describe('own profile mutations', () => {
  it('updateProfileInvalidatesOnlyTheProfileQuery', async () => {
    const invalidated = await run(() => useActualizarPerfil(), {}, ME_KEYS);
    expect(invalidated).toEqual([['me']]);
  });

  it('uploadAvatarInvalidatesOnlyProfileAndAvatar', async () => {
    const invalidated = await run(() => useSubirAvatar(), new Blob(['x']), ME_KEYS);
    expect(invalidated).toEqual(expect.arrayContaining([['me'], ['me', 'avatar']]));
    expect(invalidated).toHaveLength(2);
  });
});
