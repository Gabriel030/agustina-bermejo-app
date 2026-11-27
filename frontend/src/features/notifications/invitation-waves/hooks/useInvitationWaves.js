import { useCallback } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiFetchBlob } from '@/api/client';
import { pollWhenOnline } from '@/api/cachePolicy';
import { reportFilename } from '../lib/invitationWaveMath';

const ROOT = 'invitation-waves';
const RUNNING_REFETCH_MS = 15000;
const SETTINGS_STALE_MS = 5 * 60 * 1000;
const DEFAULT_ACCESS = { read: true, manage: true, send: true };
const ACCESS_KEY = [ROOT, 'access'];

const listKey = (page, size) => [ROOT, 'list', page, size];
const waveKey = (id) => [ROOT, 'detail', String(id)];

export function useInvitationWaveAccess() {
  const queryClient = useQueryClient();
  const { data } = useQuery({ queryKey: ACCESS_KEY, queryFn: () => DEFAULT_ACCESS, initialData: DEFAULT_ACCESS, staleTime: Infinity, gcTime: Infinity });
  const deny = useCallback(
    (capability) =>
      queryClient.setQueryData(ACCESS_KEY, (current) => {
        const base = current ?? DEFAULT_ACCESS;
        return base[capability] === false ? base : { ...base, [capability]: false };
      }),
    [queryClient],
  );
  return { ...data, deny };
}

export function useInvitationWaves({ page, size, enabled = true }) {
  return useQuery({
    queryKey: listKey(page, size),
    queryFn: () => apiFetch(`/invitation-waves?page=${page - 1}&size=${size}`),
    enabled,
    placeholderData: keepPreviousData,
  });
}

export function useInvitationWave(id, { enabled = true } = {}) {
  return useQuery({
    queryKey: waveKey(id),
    queryFn: () => apiFetch(`/invitation-waves/${id}`),
    enabled: !!id && enabled,
    refetchInterval: (query) => (query.state.data?.status === 'RUNNING' ? pollWhenOnline(RUNNING_REFETCH_MS)() : false),
    refetchIntervalInBackground: false,
  });
}

export function useInvitationWaveRecipients(id, { status, page, size, enabled = true, live = false }) {
  const params = new URLSearchParams({ page: String(page - 1), size: String(size) });
  if (status) params.set('status', status);
  return useQuery({
    queryKey: [ROOT, 'recipients', String(id), status ?? null, page, size],
    queryFn: () => apiFetch(`/invitation-waves/${id}/recipients?${params}`),
    enabled: !!id && enabled,
    placeholderData: keepPreviousData,
    refetchInterval: live ? pollWhenOnline(RUNNING_REFETCH_MS) : false,
    refetchIntervalInBackground: false,
  });
}

export function useInvitationWaveSettings({ enabled = true } = {}) {
  return useQuery({
    queryKey: [ROOT, 'settings'],
    queryFn: () => apiFetch('/invitation-waves/settings'),
    enabled,
    staleTime: SETTINGS_STALE_MS,
  });
}

export function useImportBatches({ enabled = true } = {}) {
  return useQuery({
    queryKey: [ROOT, 'import-batches'],
    queryFn: () => apiFetch('/invitation-waves/import-batches'),
    enabled,
  });
}

export function useCreateInvitationWave() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) => apiFetch('/invitation-waves', { method: 'POST', body: JSON.stringify(payload) }),
    onSuccess: (wave) => {
      queryClient.setQueryData(waveKey(wave.id), wave);
      queryClient.invalidateQueries({ queryKey: [ROOT, 'list'] });
    },
  });
}

export function useInvitationWaveAction(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (endpoint) => apiFetch(`/invitation-waves/${id}/${endpoint}`, { method: 'POST' }),
    onSuccess: (wave) => {
      if (wave && wave.id) queryClient.setQueryData(waveKey(id), wave);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: waveKey(id) });
      queryClient.invalidateQueries({ queryKey: [ROOT, 'recipients', String(id)] });
      queryClient.invalidateQueries({ queryKey: [ROOT, 'list'] });
    },
  });
}

export function useDownloadInvitationReport(id) {
  return useMutation({
    mutationFn: async () => {
      const { blob, filename } = await apiFetchBlob(`/invitation-waves/${id}/report.csv`);
      return { blob, filename: filename === 'archivo' ? reportFilename(id) : filename };
    },
  });
}
