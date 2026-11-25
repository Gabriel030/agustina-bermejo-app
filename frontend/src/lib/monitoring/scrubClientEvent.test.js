import { describe, expect, it } from 'vitest';
import { scrubClientEvent } from './scrubClientEvent';

function scrubValue(value) {
  return scrubClientEvent({ event_id: 'a', exception: { values: [{ type: 'Error', value }] } }).exception.values[0].value;
}

describe('scrubClientEvent messages', () => {
  it.each([
    "Cannot read properties of undefined (reading 'name')",
    "Cannot read properties of null (reading 'length')",
    "Cannot set properties of undefined (setting 'value')",
    'students.map is not a function',
    'Foo is not a constructor',
    'items is not iterable',
    'someGlobal is not defined',
    "undefined is not an object (evaluating 'student.name')",
    'Failed to fetch',
    'Load failed',
    'NetworkError when attempting to fetch resource.',
    'Maximum call stack size exceeded',
    'Script error.',
    'ApiError status=404 code=NOT_FOUND',
    'ApiError status=sin-estado code=sin-codigo',
  ])('keepsKnownForm %s', (message) => {
    expect(scrubValue(message)).toBe(message);
  });

  it.each([
    'No se pudo guardar a Benítez, Joaquín',
    'ana.perez@example.com no existe',
    'DNI 35123456 inválido',
    "Cannot read properties of undefined (reading 'Benítez, Joaquín')",
    'Benítez, Joaquín is not a function',
    'ApiError status=404 code=Benítez Joaquín',
    'Failed to fetch Benítez',
  ])('omitsUnknownMessage %s', (message) => {
    expect(scrubValue(message)).toBe('[mensaje omitido]');
  });

  it('omitsUnknownEventMessage', () => {
    expect(scrubClientEvent({ event_id: 'a', message: 'Benítez, Joaquín' }).message).toBe('[mensaje omitido]');
  });

  it('keepsErrorTypeAndLeavesMissingMessagesUndefined', () => {
    const scrubbed = scrubClientEvent({ event_id: 'a', exception: { values: [{ type: 'TypeError' }] } });
    expect(scrubbed.exception.values[0].type).toBe('TypeError');
    expect(scrubbed.exception.values[0].value).toBeUndefined();
    expect(scrubbed.message).toBeUndefined();
  });
});

describe('scrubClientEvent scope tag', () => {
  it.each(['root', 'screen', 'monitoring-start', 'window-error', 'unhandledrejection', 'service-worker'])('keepsKnownScope %s', (scope) => {
    expect(scrubClientEvent({ event_id: 'a', tags: { scope } }).tags).toEqual({ scope });
  });

  it('dropsUnknownScope', () => {
    expect(scrubClientEvent({ event_id: 'a', tags: { scope: 'Benítez, Joaquín' } }).tags).toBeUndefined();
  });
});
