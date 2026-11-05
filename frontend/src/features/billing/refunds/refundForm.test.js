import { describe, expect, it } from 'vitest';
import { parseAmountInput } from './refundForm';
import { parseLineAmount } from '../credit-notes/creditNotes';

const CASES = [
  ['1.5', '1.50'],
  ['1,5', '1.50'],
  ['1.500', '1500.00'],
  ['1.500,50', '1500.50'],
  ['80.5', '80.50'],
  ['80,50', '80.50'],
  ['80.000', '80000.00'],
  ['$ 80.000,5', '80000.50'],
  ['0,50', '0.50'],
  ['', null],
  ['   ', null],
  [null, null],
  ['-5', null],
  ['-1,5', null],
  ['0', null],
  ['0,00', null],
  ['abc', null],
  ['12abc', null],
  ['1,234', null],
  ['1,5,5', null],
  ['1.5.5', null],
  ['0.500', null],
  ['99999999999999', null],
];

describe('parseAmountInput', () => {
  it.each(CASES)('reads %j as %j', (text, expected) => {
    expect(parseAmountInput(text)).toBe(expected);
  });
});

describe('parseLineAmount', () => {
  it('reads a dot decimal as the amount it says', () => {
    expect(parseLineAmount('80.5')).toEqual({ cents: 8050, error: null });
  });

  it('reads thousands and decimals together', () => {
    expect(parseLineAmount('1.500,50')).toEqual({ cents: 150050, error: null });
  });

  it('treats an empty line as zero without an error', () => {
    expect(parseLineAmount('')).toEqual({ cents: 0, error: null });
  });

  it('rejects a negative amount', () => {
    expect(parseLineAmount('-5').error).not.toBeNull();
  });
});
