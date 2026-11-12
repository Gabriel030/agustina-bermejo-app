import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { offlineQuery } from '@/api/offlineQuery';
import { holidaysOfYear } from '@/features/academic-structure/lib/holidays';

const HOLIDAYS_KEY = ['holidays'];

export function useHolidays(year) {
  return useQuery(
    offlineQuery('N0c', {
      queryKey: [...HOLIDAYS_KEY, year],
      queryFn: async () => holidaysOfYear(await apiFetch(`/holidays?year=${year}`), year),
    }),
  );
}

function useInvalidateHolidays() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: HOLIDAYS_KEY });
}

export function useCreateHoliday() {
  const invalidate = useInvalidateHolidays();
  return useMutation({
    mutationFn: (body) => apiFetch('/holidays', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: invalidate,
  });
}

export function useUpdateHoliday() {
  const invalidate = useInvalidateHolidays();
  return useMutation({
    mutationFn: ({ id, ...body }) => apiFetch(`/holidays/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    onSuccess: invalidate,
  });
}

export function useDeleteHoliday() {
  const invalidate = useInvalidateHolidays();
  return useMutation({
    mutationFn: (id) => apiFetch(`/holidays/${id}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  });
}
