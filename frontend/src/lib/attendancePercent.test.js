import { describe, expect, it } from 'vitest';
import { attendancePercentToPoints } from './attendancePercent';

describe('attendancePercentToPoints', () => {
  it('returns the points when the backend sends a plain number', () => {
    expect(attendancePercentToPoints(95.83)).toBe(95.83);
  });

  it('converts the fraction of the documented object shape into points', () => {
    expect(attendancePercentToPoints({ fraction: 0.5 })).toBe(50);
  });

  it('returns null when there is no percentage', () => {
    expect(attendancePercentToPoints(null)).toBeNull();
    expect(attendancePercentToPoints(undefined)).toBeNull();
    expect(attendancePercentToPoints({})).toBeNull();
  });
});
