import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach } from 'vitest';
import { takeContractDeviations } from '@/test/contract/withContract';
import { LocationProbe } from './LocationProbe';

export const FULL_ACCESS = { read: true, manage: true, send: true };

export function acceptPendingRetryEndpoint() {
  afterEach(() => {
    const unexpected = takeContractDeviations().filter((deviation) => !deviation.key.endsWith('/retry-failed') && !deviation.violations.every((violation) => violation.startsWith('status 0 ')));
    if (unexpected.length > 0) throw new Error(`Mocks deviate from contracts/openapi.json: ${JSON.stringify(unexpected)}`);
  });
}

export function renderInvitationScreen(element, { path, route, access = FULL_ACCESS }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(['invitation-waves', 'access'], access);
  const result = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <Routes>
          <Route path={path} element={element} />
          <Route path="*" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { ...result, queryClient };
}
