import { CLIENT_EVENT_DEDUPE_MS, CLIENT_EVENT_MAX_PER_SESSION, createClientEventThrottle } from './clientEventThrottle';
import { scrubClientEvent } from './scrubClientEvent';

function throttleThenScrub(throttleClientEvent) {
  return (event) => {
    const accepted = throttleClientEvent(event);
    return accepted ? scrubClientEvent(accepted) : null;
  };
}

export function buildClientMonitoringOptions({ dsn, environment, release, tunnel }) {
  const throttleClientEvent = createClientEventThrottle({
    maxPerSession: CLIENT_EVENT_MAX_PER_SESSION,
    dedupeMs: CLIENT_EVENT_DEDUPE_MS,
  });
  return {
    dsn,
    environment,
    release,
    defaultIntegrations: false,
    integrations: [],
    sendDefaultPii: false,
    sendClientReports: false,
    tracesSampleRate: 0,
    maxBreadcrumbs: 0,
    beforeBreadcrumb: () => null,
    beforeSend: throttleThenScrub(throttleClientEvent),
    ...(tunnel ? { tunnel } : {}),
  };
}
