import { waitFor } from '@testing-library/react';

export async function settledRequests(recorder, { quietMs = 300, timeout = 5000 } = {}) {
  let previous = -1;
  await waitFor(
    async () => {
      const current = recorder.requests.length;
      const stable = current === previous && current > 0;
      previous = current;
      await new Promise((resolve) => setTimeout(resolve, quietMs));
      if (!stable) throw new Error('requests still arriving');
    },
    { timeout, interval: 10 },
  );
  return recorder.requests;
}

export function countOf(requests, entry) {
  return requests.filter((request) => request === entry).length;
}
