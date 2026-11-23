import { setClientErrorReporter } from '@/lib/reportClientError';
import { buildClientMonitoringOptions } from './clientMonitoringOptions';
import { loadSentrySdk } from './loadSentrySdk';

function errorFromPayload(payload) {
  if (payload.name === 'ApiError') {
    const error = new Error(`ApiError status=${payload.status ?? 'sin-estado'} code=${payload.code ?? 'sin-codigo'}`);
    error.name = 'ApiError';
    error.stack = (payload.stack ?? '').split('\n').slice(1).join('\n');
    return error;
  }
  const error = new Error(payload.message);
  if (payload.name) error.name = payload.name;
  if (payload.stack) error.stack = payload.stack;
  return error;
}

function captureWithContext(sdk, payload) {
  sdk.withScope((scope) => {
    if (payload.scope) scope.setTag('scope', payload.scope);
    if (payload.requestId) scope.setTag('request_id', payload.requestId);
    sdk.captureException(errorFromPayload(payload));
  });
}

export async function startClientErrorMonitoring({ dsn, environment, release, tunnel, loadSdk = loadSentrySdk }) {
  if (!dsn) return false;
  const sdk = await loadSdk();
  sdk.init(buildClientMonitoringOptions({ dsn, environment, release, tunnel }));
  setClientErrorReporter((payload) => captureWithContext(sdk, payload));
  return true;
}
