import { describe, expect, it } from 'vitest';
import { examOptionsOf } from './procedures';

const registration = (id, overrides = {}, boardOverrides = {}) => ({
  id,
  enrollmentId: 5,
  status: 'GRADED',
  examBoard: { status: 'OPEN', examDate: '2026-11-10T09:00:00', ...boardOverrides },
  ...overrides,
});

describe('examOptionsOf', () => {
  const now = new Date('2026-11-20T12:00:00');

  it('keeps only held exams of the career, newest first', () => {
    const result = examOptionsOf(
      [
        registration(1, {}, { examDate: '2026-11-01T09:00:00' }),
        registration(2, {}, { examDate: '2026-11-15T09:00:00' }),
        registration(3, {}, { examDate: '2026-12-01T09:00:00' }),
        registration(4, { enrollmentId: 9 }),
      ],
      5,
      now,
    );

    expect(result.map((item) => item.id)).toEqual([2, 1]);
  });

  it('keeps only registrations with recorded attendance', () => {
    const result = examOptionsOf(
      [registration(1, { status: 'REGISTERED' }), registration(2, { status: 'ABSENT' }), registration(3, { status: 'GRADED' })],
      5,
      now,
    );

    expect(result.map((item) => item.id)).toEqual([3]);
  });

  it('drops withdrawn registrations and canceled boards', () => {
    const result = examOptionsOf([registration(1, { status: 'WITHDRAWN' }), registration(2, {}, { status: 'CANCELED' })], 5, now);

    expect(result).toEqual([]);
  });

  it('returns an empty list without registrations', () => {
    expect(examOptionsOf(undefined, 5, now)).toEqual([]);
  });
});
