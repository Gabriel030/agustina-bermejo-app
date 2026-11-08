import { describe, expect, it } from 'vitest';
import { advanceOpeningError, billingTimeline, diffBillingForm, emissionMonthOf, toBillingForm, toBillingPayload } from './billingConfigForm';

const TERM = { enrollmentStart: '2027-02-01T00:00:00', enrollmentEnd: '2027-02-28T23:59:59' };
const TEMPLATE = { enrollmentFeeCount: 1, firstEnrollmentFeeMonth: 2, enrollmentFeeLeadMonths: 1, lateEnrollmentFeeDueDays: 10 };

describe('billing configuration form', () => {
  it('lists only the fields that changed', () => {
    const base = toBillingForm(TEMPLATE);
    const rows = diffBillingForm(base, { ...base, advanceEnrollmentFeeOpensOn: '2026-09-01' });
    expect(rows.map((row) => row.key)).toEqual(['advanceEnrollmentFeeOpensOn']);
    expect(rows[0]).toMatchObject({ today: 'Con la inscripción', next: '01/09/2026' });
  });

  it('omits the opening date and the second month from the payload when they do not apply', () => {
    const payload = toBillingPayload(toBillingForm(TEMPLATE));
    expect(payload).not.toHaveProperty('advanceEnrollmentFeeOpensOn');
    expect(payload).not.toHaveProperty('secondEnrollmentFeeMonth');
    expect(toBillingPayload({ ...toBillingForm(TEMPLATE), advanceEnrollmentFeeOpensOn: '2026-09-01' }).advanceEnrollmentFeeOpensOn).toBe('2026-09-01');
  });

  it('places the regular emission the lead months before the enrollment window, crossing the year', () => {
    expect(emissionMonthOf(TERM, 1)).toEqual({ year: 2027, month: 1 });
    expect(emissionMonthOf(TERM, 3)).toEqual({ year: 2026, month: 11 });
  });

  it('orders the timeline with the advance opening first and the automatic expiry last', () => {
    const events = billingTimeline({ term: TERM, form: { ...toBillingForm(TEMPLATE), advanceEnrollmentFeeOpensOn: '2026-09-01' } });
    expect(events.map((event) => event.key)).toEqual(['advance', 'emission', 'window', 'due', 'expiry']);
    expect(events.at(-1).date).toBe('2027-03-01');
  });

  it('rejects an opening after the close of the enrollment window', () => {
    expect(advanceOpeningError(TERM, '2027-03-01')).not.toBeNull();
    expect(advanceOpeningError(TERM, '2027-02-28')).toBeNull();
  });
});
