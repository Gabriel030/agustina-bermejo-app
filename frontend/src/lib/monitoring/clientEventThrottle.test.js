import { describe, expect, it } from 'vitest';
import { CLIENT_EVENT_DEDUPE_MS, CLIENT_EVENT_MAX_PER_SESSION, createClientEventThrottle } from './clientEventThrottle';

function errorEvent(type, { filename = 'app.js', fn = 'render', lineno = 10, colno = 4 } = {}) {
  return {
    event_id: `${type}-${filename}-${lineno}`,
    exception: {
      values: [{ type, value: 'boom', stacktrace: { frames: [{ filename, function: fn, lineno, colno }] } }],
    },
  };
}

function clockAt(initial) {
  const clock = { time: initial };
  return { clock, now: () => clock.time };
}

describe('createClientEventThrottle', () => {
  it('sendsOnlyTheFirstOfOneHundredIdenticalErrors', () => {
    const { now } = clockAt(0);
    const throttle = createClientEventThrottle({ now });

    const sent = Array.from({ length: 100 }, () => throttle(errorEvent('TypeError'))).filter(Boolean);

    expect(sent).toHaveLength(1);
  });

  it('sendsIdenticalErrorAgainOnceDedupeWindowHasPassed', () => {
    const { clock, now } = clockAt(0);
    const throttle = createClientEventThrottle({ now, dedupeMs: 60_000 });
    throttle(errorEvent('TypeError'));

    clock.time = 59_999;
    expect(throttle(errorEvent('TypeError'))).toBeNull();

    clock.time = 60_000;
    expect(throttle(errorEvent('TypeError'))).not.toBeNull();
  });

  it('dropsTheExcessOfDistinctErrorsOverTheSessionLimit', () => {
    const { now } = clockAt(0);
    const throttle = createClientEventThrottle({ now, maxPerSession: 3 });

    const sent = ['A', 'B', 'C', 'D', 'E'].map((type) => throttle(errorEvent(type))).filter(Boolean);

    expect(sent.map((event) => event.exception.values[0].type)).toEqual(['A', 'B', 'C']);
  });

  it('usesTheSessionLimitOfTwentyEventsByDefault', () => {
    const { now } = clockAt(0);
    const throttle = createClientEventThrottle({ now });

    const sent = Array.from({ length: CLIENT_EVENT_MAX_PER_SESSION + 5 }, (_, index) =>
      throttle(errorEvent(`Error${index}`)),
    ).filter(Boolean);

    expect(sent).toHaveLength(CLIENT_EVENT_MAX_PER_SESSION);
  });

  it('treatsErrorsWithDifferentFirstFrameAsDistinct', () => {
    const { now } = clockAt(0);
    const throttle = createClientEventThrottle({ now });

    expect(throttle(errorEvent('TypeError', { lineno: 10 }))).not.toBeNull();
    expect(throttle(errorEvent('TypeError', { lineno: 11 }))).not.toBeNull();
  });

  it('dedupesMessageOnlyEventsByMessage', () => {
    const { now } = clockAt(0);
    const throttle = createClientEventThrottle({ now });

    expect(throttle({ event_id: 'a', message: 'Falló la carga' })).not.toBeNull();
    expect(throttle({ event_id: 'b', message: 'Falló la carga' })).toBeNull();
    expect(throttle({ event_id: 'c', message: 'Otro mensaje' })).not.toBeNull();
  });

  it('defaultsToTheExportedLimits', () => {
    expect(CLIENT_EVENT_MAX_PER_SESSION).toBe(20);
    expect(CLIENT_EVENT_DEDUPE_MS).toBe(60_000);
  });
});
