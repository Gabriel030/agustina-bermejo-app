import { setClientErrorReporter } from '@/lib/reportClientError';
import { buildClientMonitoringOptions } from './clientMonitoringOptions';
import { loadSentrySdk } from './loadSentrySdk';

function errorFromPayload(payload) {
  const error = new Error(payload.message);
  if (payload.name) error.name = payload.name;
  if (payload.stack) error.stack = payload.stack;
  return error;
}

export async function startClientErrorMonitoring({ dsn, environment, release, loadSdk = loadSentrySdk }) {
  if (!dsn) return false;
  const sdk = await loadSdk();
  sdk.init(buildClientMonitoringOptions({ dsn, environment, release }));
  setClientErrorReporter((payload) => sdk.captureException(errorFromPayload(payload)));
  return true;
}
