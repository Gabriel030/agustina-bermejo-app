import { renderHook, act, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createInvalidationHarness } from '@/test/invalidationHarness';
import { apiFetch } from '@/api/client';
import { useActualizarPerfil, useSubirAvatar } from './useMiPerfil';
import { useSaveGradeCells } from '../../evaluation/hooks/useTeacherGradeGrid';
import { useMarcarNotificacionLeida } from '../../notifications/hooks/useNotifications';

vi.mock('@/api/client', () => ({ apiFetch: vi.fn(), apiFetchBlob: vi.fn() }));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

beforeEach(() => {
  apiFetch.mockReset();
});

function mount(hook, seeds) {
  const harness = createInvalidationHarness([]);
  seeds.forEach(([key, data]) => harness.queryClient.setQueryData(key, data));
  const { result } = renderHook(hook, { wrapper: harness.wrapper });
  return { ...harness, result };
}

const GRID_KEY = ['teacher-grade-grid', 'PROGRAM', '5'];
const grid = () => ({
  students: [{ enrollmentId: 1, grades: [{ type: 'PARTIAL', sequenceNumber: 1, score: 4, published: true, corrected: false, gradeItemId: 9, date: null, comments: null }] }],
  columns: [],
});

describe('targeted cache updates', () => {
  it('profileUpdateWritesResponseToCacheWithoutRefetch', async () => {
    apiFetch.mockResolvedValue({ id: 1, telefono: '999' });
    const { result, queryClient, invalidatedKeys } = mount(() => useActualizarPerfil(), [[['me'], { id: 1, telefono: '111', nombre: 'Ana' }]]);
    await act(async () => { await result.current.mutateAsync({ telefono: '999' }); });
    expect(queryClient.getQueryData(['me'])).toEqual({ id: 1, telefono: '999', nombre: 'Ana' });
    expect(apiFetch).toHaveBeenCalledTimes(1);
    expect(invalidatedKeys()).toEqual([['me']]);
  });

  it('profileUpdateErrorLeavesCacheUntouched', async () => {
    apiFetch.mockImplementation(() => Promise.reject(new Error('boom')));
    const { result, queryClient } = mount(() => useActualizarPerfil(), [[['me'], { id: 1, telefono: '111' }]]);
    act(() => result.current.mutate({}));
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(queryClient.getQueryData(['me'])).toEqual({ id: 1, telefono: '111' });
  });

  it('avatarUploadMarksProfileWithAvatarWithoutRefetchingProfile', async () => {
    apiFetch.mockResolvedValue(undefined);
    const { result, queryClient } = mount(() => useSubirAvatar(), [[['me'], { id: 1, hasAvatar: false }], [['me', 'avatar'], null]]);
    await act(async () => { await result.current.mutateAsync(new Blob(['x'])); });
    expect(queryClient.getQueryData(['me']).hasAvatar).toBe(true);
    expect(queryClient.getQueryState(['me']).isInvalidated).toBe(true);
    expect(apiFetch).toHaveBeenCalledTimes(1);
  });

  it('notificationReadUpdatesOnlyThatItemWithoutRefetch', async () => {
    apiFetch.mockResolvedValue(undefined);
    const lista = [{ id: 1, read: false }, { id: 2, read: false }];
    const { result, queryClient } = mount(() => useMarcarNotificacionLeida(), [[['me', 'notifications'], lista]]);
    await act(async () => { await result.current.mutateAsync(1); });
    expect(queryClient.getQueryData(['me', 'notifications'])).toEqual([{ id: 1, read: true }, { id: 2, read: false }]);
    expect(apiFetch).toHaveBeenCalledTimes(1);
  });

  it('notificationReadErrorInvalidatesList', async () => {
    apiFetch.mockImplementation(() => Promise.reject(new Error('boom')));
    const { result, queryClient } = mount(() => useMarcarNotificacionLeida(), [[['me', 'notifications'], [{ id: 1, read: false }]]]);
    act(() => result.current.mutate(1));
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(queryClient.getQueryData(['me', 'notifications'])).toEqual([{ id: 1, read: false }]);
    expect(queryClient.getQueryState(['me', 'notifications']).isInvalidated).toBe(true);
  });

  it('gradeSaveAppliesCellsLocallyWithoutRefetching', async () => {
    apiFetch.mockResolvedValue({ id: 9, type: 'PARTIAL', sequenceNumber: 1, score: 8, date: '2026-11-01T12:00:00' });
    const { result, queryClient } = mount(() => useSaveGradeCells(5, 'PROGRAM'), [[GRID_KEY, grid()]]);
    const job = { key: 'k', enrollmentId: 1, payload: { type: 'PARTIAL', sequenceNumber: 1, score: 8, comments: 'ok' } };
    const outcome = await result.current([job], () => {});
    const cell = queryClient.getQueryData(GRID_KEY).students[0].grades[0];
    expect(outcome).toEqual({ saved: 1, failed: 0, failedKeys: [] });
    expect(cell).toMatchObject({ score: 8, comments: 'ok', published: true, gradeItemId: 9 });
    expect(apiFetch).toHaveBeenCalledTimes(1);
  });

  it('gradeSaveWithFailureKeepsOldCellAndRefetchesGrid', async () => {
    apiFetch.mockImplementationOnce(() => Promise.reject(new Error('boom'))).mockResolvedValueOnce(grid());
    const { result } = mount(() => useSaveGradeCells(5, 'PROGRAM'), [[GRID_KEY, grid()]]);
    const job = { key: 'k', enrollmentId: 1, payload: { type: 'PARTIAL', sequenceNumber: 1, score: 8, comments: null } };
    const outcome = await result.current([job], () => {});
    expect(outcome.failed).toBe(1);
  });
});
