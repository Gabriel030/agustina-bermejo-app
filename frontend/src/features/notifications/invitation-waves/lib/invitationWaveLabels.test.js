import { describe, expect, it } from 'vitest';
import { lacksPersonalData, recipientDetail } from './invitationWaveLabels';
import { buildRecipient, withoutPersonalData } from '../test/fixtures';

const hidden = (personId, overrides) => withoutPersonalData(buildRecipient(personId, overrides));
const excluded = (personId) => hidden(personId, { status: 'EXCLUDED', exclusionReason: 'NO_EMAIL' });

describe('lacksPersonalData', () => {
  it('isFalseWithoutRows', () => {
    expect(lacksPersonalData([])).toBe(false);
  });

  it('isFalseWithASingleRowWithoutName', () => {
    expect(lacksPersonalData([hidden(1)])).toBe(false);
  });

  it('isTrueWhenTwoOrMoreNonExcludedRowsHaveNoName', () => {
    expect(lacksPersonalData([hidden(1), hidden(2)])).toBe(true);
  });

  it('isFalseWhenOnlyExcludedRowsHaveNoName', () => {
    expect(lacksPersonalData([excluded(1), excluded(2), excluded(3)])).toBe(false);
  });

  it('ignoresExcludedRowsWhenCounting', () => {
    expect(lacksPersonalData([hidden(1), excluded(2), excluded(3)])).toBe(false);
    expect(lacksPersonalData([hidden(1), hidden(2), excluded(3)])).toBe(true);
  });

  it('isFalseWhenAnyNonExcludedRowHasAName', () => {
    expect(lacksPersonalData([hidden(1), hidden(2), buildRecipient(3)])).toBe(false);
  });
});

describe('recipientDetail', () => {
  it('saysTheSentRecipientIsWaitingForAccountActivation', () => {
    expect(recipientDetail(buildRecipient(1, { status: 'SENT' }))).toBe('Esperando que active su cuenta');
  });
});
