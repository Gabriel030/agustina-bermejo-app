import { describe, expect, it } from 'vitest';
import { localIsoDate } from '@/lib/calendario';

describe('localIsoDate', () => {
  it('usa el día del reloj local aunque en UTC ya sea el día siguiente', () => {
    expect(localIsoDate(new Date(2026, 9, 3, 23, 30))).toBe('2026-10-03');
  });

  it('completa mes y día con cero a la izquierda', () => {
    expect(localIsoDate(new Date(2026, 0, 5, 9, 0))).toBe('2026-01-05');
  });
});
