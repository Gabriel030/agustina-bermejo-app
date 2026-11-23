import { scrubClientEvent } from './scrubClientEvent';

export function buildClientMonitoringOptions({ dsn, environment, release, tunnel }) {
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
    beforeSend: scrubClientEvent,
    ...(tunnel ? { tunnel } : {}),
  };
}
