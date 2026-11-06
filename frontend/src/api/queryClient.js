import { QueryClient } from '@tanstack/react-query';
import { OFFLINE_MAX_AGE_MS } from './offlinePersistence';

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, refetchOnWindowFocus: false, networkMode: 'offlineFirst', gcTime: OFFLINE_MAX_AGE_MS },
      mutations: { retry: false, networkMode: 'always' },
    },
  });
}
