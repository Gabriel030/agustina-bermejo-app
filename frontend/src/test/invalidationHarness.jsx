import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

export function createInvalidationHarness(keys) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } });
  keys.forEach((key) => queryClient.setQueryData(key, { seeded: true }));
  const wrapper = ({ children }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  const invalidatedKeys = () =>
    queryClient
      .getQueryCache()
      .getAll()
      .filter((query) => query.state.isInvalidated)
      .map((query) => query.queryKey);
  return { queryClient, wrapper, invalidatedKeys };
}
