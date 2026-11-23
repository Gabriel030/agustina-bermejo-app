import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { reportClientError, setClientErrorReporter } from '@/lib/reportClientError';
import { ApiError } from '@/api/apiError';
import { startClientErrorMonitoring } from './startClientErrorMonitoring';

const { sentryModuleLoads } = vi.hoisted(() => ({ sentryModuleLoads: [] }));

vi.mock('@sentry/react', () => {
  sentryModuleLoads.push('@sentry/react');
  return { init: vi.fn(), captureException: vi.fn(), withScope: vi.fn() };
});

const DUMMY_DSN = 'https://publickey@o0.ingest.sentry.io/0';
const REQUEST_ID = '3f2a9c1e-7b4d-4e8a-9f0c-1a2b3c4d5e6f';

function createFakeSdk() {
  const scope = { setTag: vi.fn() };
  return {
    scope,
    init: vi.fn(),
    captureException: vi.fn(),
    withScope: vi.fn((callback) => callback(scope)),
  };
}

describe('startClientErrorMonitoring with empty DSN', () => {
  beforeEach(() => {
    sentryModuleLoads.length = 0;
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    setClientErrorReporter((payload) => console.error('client_error', payload));
  });

  it('startsNoSdkModuleAndNoNetworkRequestWhenDsnIsEmpty', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const beaconSpy = vi.fn(() => true);
    Object.defineProperty(navigator, 'sendBeacon', { value: beaconSpy, configurable: true, writable: true });

    const started = await startClientErrorMonitoring({ dsn: '', environment: 'test', release: 'abc' });
    reportClientError(new Error('boom'), { scope: 'screen' });

    expect(started).toBe(false);
    expect(sentryModuleLoads).toEqual([]);
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(beaconSpy).not.toHaveBeenCalled();
  });

  it('keepsReportingToTheConsoleWhenDsnIsEmpty', async () => {
    await startClientErrorMonitoring({ dsn: '', environment: 'test', release: 'abc' });
    reportClientError(new Error('visible en consola'));

    expect(console.error).toHaveBeenCalledWith('client_error', expect.objectContaining({ message: 'visible en consola' }));
  });
});

describe('startClientErrorMonitoring with DSN', () => {
  let sdk;

  beforeEach(() => {
    sdk = createFakeSdk();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    setClientErrorReporter((payload) => console.error('client_error', payload));
  });

  async function startWithFakeSdk() {
    return startClientErrorMonitoring({ dsn: DUMMY_DSN, environment: 'production', release: 'sha-1', loadSdk: async () => sdk });
  }

  it('initializesTheSdkWithPrivacyOptions', async () => {
    expect(await startWithFakeSdk()).toBe(true);

    expect(sdk.init).toHaveBeenCalledWith(
      expect.objectContaining({
        dsn: DUMMY_DSN,
        environment: 'production',
        release: 'sha-1',
        sendDefaultPii: false,
        sendClientReports: false,
        tracesSampleRate: 0,
        defaultIntegrations: false,
        integrations: [],
      }),
    );
  });

  it('sendsReportedErrorsToTheSdkWithScopeTag', async () => {
    await startWithFakeSdk();
    reportClientError(new Error('Falla de pantalla'), { scope: 'screen' });

    expect(sdk.scope.setTag).toHaveBeenCalledWith('scope', 'screen');
    expect(sdk.captureException).toHaveBeenCalledTimes(1);
    const [sent] = sdk.captureException.mock.calls[0];
    expect(sent).toBeInstanceOf(Error);
    expect(sent.message).toBe('Falla de pantalla');
  });

  it('sendsApiErrorsWithStatusAndCodeButNeverTheFreeTextMessage', async () => {
    await startWithFakeSdk();
    reportClientError(new ApiError('Alumna Ana Pérez no encontrada', { status: 404, code: 'NOT_FOUND', requestId: REQUEST_ID }), { scope: 'screen' });

    const [sent] = sdk.captureException.mock.calls[0];
    expect(sent.message).toBe('ApiError status=404 code=NOT_FOUND');
    expect(sent.stack).not.toContain('Ana Pérez');
    expect(sdk.scope.setTag).toHaveBeenCalledWith('request_id', REQUEST_ID);
  });

  it('dropsBreadcrumbsBeforeTheyLeaveTheBrowser', async () => {
    await startWithFakeSdk();
    const [{ beforeBreadcrumb }] = sdk.init.mock.calls[0];

    expect(beforeBreadcrumb({ category: 'navigation', data: { to: '/activar?token=SECRETO' } })).toBeNull();
  });

  it('scrubsEventsSentWithPersonalDataAndTokens', async () => {
    await startWithFakeSdk();
    const [{ beforeSend }] = sdk.init.mock.calls[0];

    const scrubbed = beforeSend({
      event_id: 'abc',
      level: 'error',
      environment: 'production',
      release: 'sha-1',
      message: 'No se pudo activar ana.perez@example.com DNI 35.123.456',
      request: { url: 'https://app.test/activar?token=SECRETO', headers: { Authorization: 'Bearer SECRETO', Cookie: 'sid=SECRETO' }, data: '{"dni":"35123456"}' },
      user: { email: 'ana.perez@example.com', ip_address: '10.0.0.1' },
      extra: { componentStack: 'at Perfil' },
      contexts: { os: { name: 'Linux' } },
      tags: { scope: 'screen', secret_tag: 'SECRETO' },
      breadcrumbs: [{ category: 'xhr', data: { url: 'https://app.test/api/x?token=SECRETO' } }],
      exception: {
        values: [
          {
            type: 'Error',
            value: 'Token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl en /verificar-email?token=SECRETO',
            stacktrace: {
              frames: [{ filename: 'https://app.test/assets/index-1.js?token=SECRETO', function: 'load', lineno: 10, colno: 4, in_app: true, vars: { password: 'SECRETO' } }],
            },
          },
        ],
      },
    });

    const serialized = JSON.stringify(scrubbed);
    expect(serialized).not.toContain('SECRETO');
    expect(serialized).not.toContain('ana.perez@example.com');
    expect(serialized).not.toContain('35.123.456');
    expect(serialized).not.toContain('eyJhbGciOiJIUzI1NiJ9');
    expect(serialized).not.toContain('10.0.0.1');
    expect(serialized).not.toContain('Bearer');
    expect(scrubbed.request).toBeUndefined();
    expect(scrubbed.user).toBeUndefined();
    expect(scrubbed.extra).toBeUndefined();
    expect(scrubbed.contexts).toBeUndefined();
    expect(scrubbed.tags).toEqual({ scope: 'screen' });
    expect(scrubbed.breadcrumbs).toBeUndefined();
    expect(scrubbed.release).toBe('sha-1');
    expect(scrubbed.environment).toBe('production');
    expect(scrubbed.exception.values[0].stacktrace.frames[0]).toEqual({
      filename: 'https://app.test/assets/index-1.js',
      function: 'load',
      lineno: 10,
      colno: 4,
      in_app: true,
    });
  });

  it('keepsRequestIdTagOnlyWhenItIsAUuid', async () => {
    await startWithFakeSdk();
    const [{ beforeSend }] = sdk.init.mock.calls[0];

    const withValidId = beforeSend({ event_id: 'a', tags: { request_id: REQUEST_ID } });
    const withInjectedId = beforeSend({ event_id: 'b', tags: { request_id: 'token=SECRETO' } });

    expect(withValidId.tags).toEqual({ request_id: REQUEST_ID });
    expect(withInjectedId.tags).toBeUndefined();
  });
});
