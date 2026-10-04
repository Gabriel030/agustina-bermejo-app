import { describe, expect, it } from 'vitest';
import { formatMoney, moneyValue } from '@/lib/utils';

const normalize = (text) => text.replace(/\s/g, ' ');

describe('formatMoney', () => {
  it('formatea pesos con siempre dos decimales', () => {
    expect(normalize(formatMoney({ amount: '48000.00', currency: 'ARS' }))).toBe('$ 48.000,00');
    expect(normalize(formatMoney({ amount: '52800', currency: 'ARS' }))).toBe('$ 52.800,00');
    expect(normalize(formatMoney({ amount: '1500.5', currency: 'ARS' }))).toBe('$ 1.500,50');
  });

  it('usa la moneda del dato y no asume pesos', () => {
    expect(normalize(formatMoney({ amount: '120.00', currency: 'USD' }))).toContain('120,00');
    expect(formatMoney({ amount: '120.00', currency: 'USD' })).toMatch(/US\$|USD/);
  });

  it('devuelve cadena vacía cuando no hay monto', () => {
    expect(formatMoney(null)).toBe('');
    expect(formatMoney(undefined)).toBe('');
  });
});

describe('moneyValue', () => {
  it('convierte el monto a número y trata null como cero', () => {
    expect(moneyValue({ amount: '48000.00', currency: 'ARS' })).toBe(48000);
    expect(moneyValue(null)).toBe(0);
  });
});
