import { QueryClient } from '@tanstack/react-query';
import { OFFLINE_MAX_AGE_MS } from './offlinePersistence';
import { STALE_TIME, applyCachePolicy } from './cachePolicy';
import { createMutationCache, reportUnhandledMutationRejections } from './mutationErrorPolicy';

export function createQueryClient() {
  reportUnhandledMutationRejections();
  const client = new QueryClient({
    mutationCache: createMutationCache(),
    defaultOptions: {
      queries: {
        retry: false,
        staleTime: STALE_TIME.OPERATIONAL,
        refetchOnWindowFocus: false,
        networkMode: 'offlineFirst',
        gcTime: OFFLINE_MAX_AGE_MS,
      },
      mutations: { retry: false, networkMode: 'always' },
    },
  });
  applyCachePolicy(client);
  return client;
}
