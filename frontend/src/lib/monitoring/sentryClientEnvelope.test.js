import { afterEach, describe, expect, it } from 'vitest';
import * as Sentry from '@sentry/react';
import { buildClientMonitoringOptions } from './clientMonitoringOptions';

const DUMMY_DSN = 'https://publickey@o0.ingest.sentry.io/0';

function eventsFromEnvelopes(envelopes) {
  return envelopes.flatMap(([, items]) => items.filter(([header]) => header.type === 'event').map(([, payload]) => payload));
}

describe('real Sentry client with the privacy options', () => {
  const envelopes = [];

  afterEach(async () => {
    await Sentry.getClient()?.close(0);
    envelopes.length = 0;
  });

  it('sendsOnlyAllowlistedFieldsWithoutPersonalDataOrTokens', async () => {
    Sentry.init({
      ...buildClientMonitoringOptions({ dsn: DUMMY_DSN, environment: 'production', release: 'sha-1' }),
      transport: () => ({
        send: async (envelope) => {
          envelopes.push(envelope);
          return { statusCode: 200 };
        },
        flush: async () => true,
      }),
    });

    Sentry.withScope((scope) => {
      scope.setTag('scope', 'screen');
      scope.setUser({ email: 'ana.perez@example.com', ip_address: '10.0.0.1' });
      scope.setExtra('componentStack', 'at Perfil apellido=Pérez');
      Sentry.captureException(new Error('Alumno ana.perez@example.com DNI 35.123.456 en /alumnos/123?apellido=Pérez'));
    });
    await Sentry.flush(2000);

    const [event] = eventsFromEnvelopes(envelopes);
    const serialized = JSON.stringify(event);

    expect(event).toBeDefined();
    expect(event.message ?? event.exception.values[0].value).toContain('[email]');
    expect(serialized).not.toContain('ana.perez@example.com');
    expect(serialized).not.toContain('35.123.456');
    expect(serialized).not.toContain('Pérez');
    expect(serialized).not.toContain('10.0.0.1');
    expect(serialized).not.toContain('componentStack');
    expect(event).not.toHaveProperty('contexts');
    expect(event).not.toHaveProperty('user');
    expect(event).not.toHaveProperty('request');
    expect(event).not.toHaveProperty('breadcrumbs');
    expect(event).not.toHaveProperty('extra');
    expect(event.tags).toEqual({ scope: 'screen' });
  });
});
