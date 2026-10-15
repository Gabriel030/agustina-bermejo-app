import { afterEach, describe, expect, it, vi } from 'vitest';
import { formatAmountEsAr, newIdempotencyKey, parseAmountInput } from './cashAmount';

describe('parseAmountInput', () => {
  it.each([
    ['1.500,50', '1500.50'],
    ['1500,50', '1500.50'],
    ['1500.50', '1500.50'],
    ['1500', '1500.00'],
    ['1.500', '1500.00'],
    ['1.500.000', '1500000.00'],
    ['1.500.000,5', '1500000.50'],
    ['0,5', '0.50'],
    ['1500,', '1500.00'],
    ['$ 2.000,00', '2000.00'],
    ['9.999.999.999,99', '9999999999.99'],
    ['135600.30', '135600.30'],
    ['12345678901234.5', null],
  ])('reads %s as %s', (input, expected) => {
    const result = parseAmountInput(input);
    if (expected === null) expect(result.value).toBeUndefined();
    else expect(result.value).toBe(expected);
  });

  it('treats a dot followed by exactly three digits as thousands', () => {
    expect(parseAmountInput('1.500').value).toBe('1500.00');
    expect(parseAmountInput('1.50').value).toBe('1.50');
    expect(parseAmountInput('1.5').value).toBe('1.50');
  });

  it('stays exact beyond what a float can hold', () => {
    expect(parseAmountInput('9.999.999.999,99').value).toBe('9999999999.99');
    expect(parseAmountInput('0,07').value).toBe('0.07');
  });

  it.each([
    ['', 'EMPTY'],
    ['   ', 'EMPTY'],
    ['0', 'NOT_POSITIVE'],
    ['0,00', 'NOT_POSITIVE'],
    ['1,234', 'TOO_MANY_DECIMALS'],
    ['1500.123', 'TOO_MANY_DECIMALS'],
    ['10.000.000.000,00', 'TOO_LARGE'],
    ['9.999.999.999,991', 'TOO_MANY_DECIMALS'],
    ['abc', 'INVALID'],
    ['1,5,5', 'INVALID'],
    ['1.5000,50', 'INVALID'],
    ['1.50.500', 'INVALID'],
    ['-5', 'INVALID'],
  ])('rejects %s with %s', (input, error) => {
    expect(parseAmountInput(input).error).toBe(error);
  });
});

describe('formatAmountEsAr', () => {
  it('groups thousands with a dot and uses a decimal comma', () => {
    expect(formatAmountEsAr('1500.50')).toBe('1.500,50');
    expect(formatAmountEsAr('135600.30')).toBe('135.600,30');
    expect(formatAmountEsAr('0.07')).toBe('0,07');
    expect(formatAmountEsAr('9999999999.99')).toBe('9.999.999.999,99');
  });
});

describe('newIdempotencyKey', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('works without randomUUID, as in a page served over plain http', () => {
    vi.stubGlobal('crypto', { getRandomValues: (bytes) => bytes.fill(171) });
    expect(newIdempotencyKey()).toBe('ab'.repeat(16));
  });

  it('works without any crypto at all and gives different keys', () => {
    vi.stubGlobal('crypto', undefined);
    const first = newIdempotencyKey();
    expect(first).toMatch(/^[0-9a-f]{32}$/);
    expect(newIdempotencyKey()).not.toBe(first);
  });
});
