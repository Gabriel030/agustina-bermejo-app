import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export const INFORMATIVE_SESSIONS_KEY = ['informative-sessions'];

const STALE_REGISTRATION_CODES = ['SESSION_FULL', 'ALREADY_REGISTERED_FOR_INFORMATIVE_SESSION'];

export function useInformativeSessions() {
  return useQuery({
    queryKey: INFORMATIVE_SESSIONS_KEY,
    queryFn: () => apiFetch('/informative-sessions?status=SCHEDULED'),
  });
}

export function useRegisterForInformativeSession(sessionId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch(`/informative-sessions/${sessionId}/register`, { method: 'POST' }),
    onSuccess: (updated) =>
      queryClient.setQueryData(INFORMATIVE_SESSIONS_KEY, (sessions) =>
        sessions?.map((session) => (session.id === updated.id ? updated : session)),
      ),
    onError: (error) => {
      if (STALE_REGISTRATION_CODES.includes(error?.code)) {
        queryClient.invalidateQueries({ queryKey: INFORMATIVE_SESSIONS_KEY });
      }
    },
  });
}
