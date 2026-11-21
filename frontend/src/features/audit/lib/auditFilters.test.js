import { describe, expect, it } from 'vitest';
import { actorSearchFor, actorSearchInput } from './auditFilters';

describe('actorSearchInput', () => {
  it('ignores the debounced text once the field is cleared', () => {
    expect(actorSearchInput('', 'Ma')).toBe('');
    expect(actorSearchInput('   ', 'Ma')).toBe('');
  });

  it('uses the debounced text while the field has content', () => {
    expect(actorSearchInput('Mar', 'Ma')).toBe('Ma');
  });
});

describe('actorSearchFor', () => {
  it('sends the person id when the text is only digits', () => {
    expect(actorSearchFor('12')).toEqual({ actorPersonId: '12', actorName: '' });
    expect(actorSearchFor(' 7 ')).toEqual({ actorPersonId: '7', actorName: '' });
  });

  it('sends the trimmed name from two characters', () => {
    expect(actorSearchFor(' Ma ')).toEqual({ actorPersonId: '', actorName: 'Ma' });
  });

  it('sends nothing for a single letter', () => {
    expect(actorSearchFor('M')).toEqual({ actorPersonId: '', actorName: '' });
  });

  it('sends nothing for an empty field', () => {
    expect(actorSearchFor('')).toEqual({ actorPersonId: '', actorName: '' });
  });

  it('keeps a numeric id with leading zeros as person id', () => {
    expect(actorSearchFor('0012')).toEqual({ actorPersonId: '0012', actorName: '' });
  });

  it('sends a digit string that overflows a safe integer as name text, not as person id', () => {
    expect(actorSearchFor('12345678901234567890')).toEqual({ actorPersonId: '', actorName: '12345678901234567890' });
  });

  it('sends a mixed letters and digits entry as name text', () => {
    expect(actorSearchFor('Ana2')).toEqual({ actorPersonId: '', actorName: 'Ana2' });
  });
});
