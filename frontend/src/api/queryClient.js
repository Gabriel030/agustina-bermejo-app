import { QueryClient } from '@tanstack/react-query';

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, refetchOnWindowFocus: false, networkMode: 'offlineFirst' },
      mutations: { retry: false, networkMode: 'always' },
    },
  });
}
