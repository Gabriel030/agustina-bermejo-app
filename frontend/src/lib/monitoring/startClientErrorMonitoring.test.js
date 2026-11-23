import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { reportClientError, setClientErrorReporter } from '@/lib/reportClientError';
import { startClientErrorMonitoring } from './startClientErrorMonitoring';

const { sentryImports } = vi.hoisted(() => ({ sentryImports: [] }));

vi.mock('@sentry/react', () => {
  sentryImports.push('@sentry/react');
  return { init: vi.fn(), captureException: vi.fn() };
});

const DUMMY_DSN = 'https://publickey@o0.ingest.sentry.io/0';

function createFakeSdk() {
  return { init: vi.fn(), captureException: vi.fn() };
}

describe('startClientErrorMonitoring without DSN', () => {
  beforeEach(() => {
    sentryImports.length = 0;
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    setClientErrorReporter((payload) => console.error('client_error', payload));
  });

  it('doesNotImportTheSdkWhenDsnIsEmpty', async () => {
    const started = await startClientErrorMonitoring({ dsn: '', environment: 'test', release: 'abc' });

    expect(started).toBe(false);
    expect(sentryImports).toEqual([]);
  });

  it('doesNotFetchAnythingWhenDsnIsEmpty', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const beaconSpy = vi.fn(() => true);
    Object.defineProperty(navigator, 'sendBeacon', { value: beaconSpy, configurable: true, writable: true });

    await startClientErrorMonitoring({ dsn: undefined, environment: 'test', release: 'abc' });
    reportClientError(new Error('boom'), { scope: 'screen' });

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

  it('initializesTheSdkWithPrivacyOptions', async () => {
    const started = await startClientErrorMonitoring({
      dsn: DUMMY_DSN,
      environment: 'production',
      release: 'sha-1',
      loadSdk: async () => sdk,
    });

    expect(started).toBe(true);
    expect(sdk.init).toHaveBeenCalledWith(
      expect.objectContaining({
        dsn: DUMMY_DSN,
        environment: 'production',
        release: 'sha-1',
        sendDefaultPii: false,
        tracesSampleRate: 0,
        defaultIntegrations: false,
        integrations: [],
      }),
    );
  });

  it('sendsReportedErrorsToTheSdk', async () => {
    await startClientErrorMonitoring({ dsn: DUMMY_DSN, environment: 'production', release: 'sha-1', loadSdk: async () => sdk });
    reportClientError(new Error('Falla de pantalla'), { scope: 'screen' });

    expect(sdk.captureException).toHaveBeenCalledTimes(1);
    const [sent] = sdk.captureException.mock.calls[0];
    expect(sent).toBeInstanceOf(Error);
    expect(sent.message).toBe('Falla de pantalla');
  });

  it('dropsBreadcrumbsBeforeTheyLeaveTheBrowser', async () => {
    await startClientErrorMonitoring({ dsn: DUMMY_DSN, environment: 'production', release: 'sha-1', loadSdk: async () => sdk });
    const [{ beforeBreadcrumb }] = sdk.init.mock.calls[0];

    expect(beforeBreadcrumb({ category: 'navigation', data: { to: '/activar?token=SECRETO' } })).toBeNull();
  });

  it('scrubsEventsSentWithPersonalDataAndTokens', async () => {
    await startClientErrorMonitoring({ dsn: DUMMY_DSN, environment: 'production', release: 'sha-1', loadSdk: async () => sdk });
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
      tags: { scope: 'screen' },
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
    expect(scrubbed.tags).toBeUndefined();
    expect(scrubbed.breadcrumbs).toBeUndefined();
    expect(scrubbed.release).toBe('sha-1');
    expect(scrubbed.environment).toBe('production');
    expect(scrubbed.exception.values[0].stacktrace.frames[0]).toEqual({
      filename: 'https://app.test/assets/index-1.js?[query]',
      function: 'load',
      lineno: 10,
      colno: 4,
      in_app: true,
    });
  });
});
