import { server } from '@/test/mocks/server';

export function recordApiRequests() {
  const requests = [];
  const onStart = ({ request }) => {
    const url = new URL(request.url);
    requests.push(`${request.method} ${url.pathname}${url.search}`);
  };
  server.events.on('request:start', onStart);
  return {
    requests,
    stop: () => server.events.removeListener('request:start', onStart),
    duplicates: () => requests.filter((entry, index) => requests.indexOf(entry) !== index),
  };
}
