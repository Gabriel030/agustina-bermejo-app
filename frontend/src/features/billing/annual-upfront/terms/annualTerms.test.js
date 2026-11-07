import { describe, expect, it } from 'vitest';
import { publisherLabelOf } from './annualTerms';

describe('publisherLabelOf', () => {
  it('showsSystemWhenThereIsNoPublisherPerson', () => {
    expect(publisherLabelOf(null)).toBe('Sistema');
    expect(publisherLabelOf(undefined)).toBe('Sistema');
  });

  it('showsPersonIdWhenPublishedByAPerson', () => {
    expect(publisherLabelOf(7)).toBe('Persona #7');
  });
});
