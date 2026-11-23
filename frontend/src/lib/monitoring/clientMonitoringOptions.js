import { scrubClientEvent } from './scrubClientEvent';

export function buildClientMonitoringOptions({ dsn, environment, release }) {
  return {
    dsn,
    environment,
    release,
    defaultIntegrations: false,
    integrations: [],
    sendDefaultPii: false,
    tracesSampleRate: 0,
    maxBreadcrumbs: 0,
    beforeBreadcrumb: () => null,
    beforeSend: scrubClientEvent,
  };
}
