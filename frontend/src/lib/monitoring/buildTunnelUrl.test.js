import { describe, expect, it } from 'vitest';
import { buildTunnelUrl } from './buildTunnelUrl';

describe('buildTunnelUrl', () => {
  it('buildsASameOriginPathWhenTheApiIsRelative', () => {
    expect(buildTunnelUrl('/api')).toBe('/api/public/monitoring/tunnel');
  });

  it('buildsAnAbsoluteUrlWhenTheApiHasAnOrigin', () => {
    expect(buildTunnelUrl('http://localhost:8080')).toBe('http://localhost:8080/public/monitoring/tunnel');
  });

  it('ignoresTrailingSlashes', () => {
    expect(buildTunnelUrl('/api/')).toBe('/api/public/monitoring/tunnel');
  });
});
