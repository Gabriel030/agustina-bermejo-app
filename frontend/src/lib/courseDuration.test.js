import { describe, expect, it } from 'vitest';
import { formatCourseDuration } from './courseDuration';

describe('formatCourseDuration', () => {
  it('pluralizes the months of a course', () => {
    expect(formatCourseDuration(1)).toBe('1 mes');
    expect(formatCourseDuration(6)).toBe('6 meses');
  });

  it('never shows "null meses" nor a negative duration for legacy courses without a valid duration', () => {
    expect(formatCourseDuration(null)).toBe('Duración a definir');
    expect(formatCourseDuration(undefined)).toBe('Duración a definir');
    expect(formatCourseDuration(-3)).toBe('Duración a definir');
    expect(formatCourseDuration(0)).toBe('Duración a definir');
  });
});
