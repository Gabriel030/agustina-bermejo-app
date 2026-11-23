import { describe, expect, it } from 'vitest';
import { buildClientMonitoringOptions } from './clientMonitoringOptions';

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
