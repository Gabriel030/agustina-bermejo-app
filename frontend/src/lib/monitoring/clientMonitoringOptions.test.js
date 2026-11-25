import { beforeEach, describe, expect, it, vi } from 'vitest';
import { buildClientMonitoringOptions } from './clientMonitoringOptions';
import { scrubClientEvent } from './scrubClientEvent';

vi.mock('./scrubClientEvent', async (importOriginal) => {
  const actual = await importOriginal();
  return { scrubClientEvent: vi.fn(actual.scrubClientEvent) };
});

const base = { dsn: 'https://key@errors.example.org/1', environment: 'production', release: 'sha-1' };

describe('buildClientMonitoringOptions tunnel', () => {
  it('routes events through the tunnel when a url is given', () => {
    const options = buildClientMonitoringOptions({ ...base, tunnel: 'https://api.example.org/public/monitoring/tunnel' });

    expect(options.tunnel).toBe('https://api.example.org/public/monitoring/tunnel');
  });

  it('sends directly when no tunnel url is given', () => {
    expect(buildClientMonitoringOptions(base)).not.toHaveProperty('tunnel');
    expect(buildClientMonitoringOptions({ ...base, tunnel: '' })).not.toHaveProperty('tunnel');
  });
});

describe('buildClientMonitoringOptions event throttle', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('keepsThePrivacyOptionsOff', () => {
    const options = buildClientMonitoringOptions(base);

    expect(options).toMatchObject({
      sendDefaultPii: false,
      tracesSampleRate: 0,
      maxBreadcrumbs: 0,
      defaultIntegrations: false,
    });
  });

  it('dropsRepeatedEventsBeforeTheyReachTheScrubber', () => {
    const options = buildClientMonitoringOptions(base);

    const first = options.beforeSend({ event_id: 'a', message: 'Falló la carga' });
    const repeated = options.beforeSend({ event_id: 'b', message: 'Falló la carga' });

    expect(first).not.toBeNull();
    expect(repeated).toBeNull();
    expect(scrubClientEvent).toHaveBeenCalledTimes(1);
  });
});
