import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { SEARCH_MIN_LENGTH } from '@/features/graduation/debt-exceptions/lib/debtException';

const SEARCH_PAGE_SIZE = 8;

export function useDebtExceptionStudentSearch(term) {
  const search = term.trim();
  return useQuery({
    queryKey: ['debt-exception-student-search', search],
    enabled: search.length >= SEARCH_MIN_LENGTH,
    staleTime: 30_000,
    queryFn: () => {
      const params = new URLSearchParams({ search, status: 'ACTIVE', page: '0', size: String(SEARCH_PAGE_SIZE) });
      return apiFetch(`/students?${params.toString()}`);
    },
  });
}

export function useIssueDebtException() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request) => apiFetch('/document-debt-exceptions', { method: 'POST', body: JSON.stringify(request) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['document-debt-exceptions'] }),
  });
}
