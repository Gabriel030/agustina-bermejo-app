import { describe, expect, it } from 'vitest';
import { closureSheet, closureSheetWithUnpublishedExam, closureStudents } from '@/test/fixtures/closureSheet';
import {
  RESULT_FILTER_ID,
  activeResultFilter,
  blockedMessage,
  effectiveScore,
  kpiCards,
  paymentsFilterValue,
  toggleResultKpi,
  withdrawnCount,
} from './closureSheet';

describe('toggleResultKpi', () => {
  it('selectsTheResultWhenNothingIsFiltered', () => {
    expect(toggleResultKpi([], 'FREE')).toEqual([{ id: RESULT_FILTER_ID, value: ['FREE'] }]);
  });

  it('clearsTheFilterWhenTheSameCardIsPressedAgain', () => {
    expect(toggleResultKpi([{ id: RESULT_FILTER_ID, value: ['FREE'] }], 'FREE')).toEqual([]);
  });

  it('replacesAMultiValueSelectionWithTheCardResultAndKeepsOtherFilters', () => {
    const current = [{ id: RESULT_FILTER_ID, value: ['FREE', 'FAILED'] }, { id: 'payments', value: ['NO'] }];

    const next = toggleResultKpi(current, 'FREE');

    expect(activeResultFilter(next)).toEqual(['FREE']);
    expect(next).toContainEqual({ id: 'payments', value: ['NO'] });
  });
});

describe('closure sheet helpers', () => {
  it('usesTheRetakeAsEffectiveScoreOnlyWhenPublished', () => {
    expect(effectiveScore({ score: 6, retakeScore: 7, published: true })).toBe(7);
    expect(effectiveScore({ score: 6, retakeScore: 7, published: false })).toBeNull();
    expect(effectiveScore(undefined)).toBeNull();
  });

  it('explainsWhichExamIsMissingWhenConfirmationIsBlocked', () => {
    expect(blockedMessage(closureSheetWithUnpublishedExam())).toBe('No se puede confirmar hasta publicar: 2.° parcial.');
    expect(blockedMessage(closureSheet())).toBeNull();
  });

  it('countsWithdrawnStudentsOutsideTheSummary', () => {
    const sheet = closureSheet();

    expect(withdrawnCount(sheet)).toBe(1);
    expect(kpiCards(sheet.summary).reduce((total, card) => total + card.count, 0)).toBe(sheet.students.length - 1);
  });

  it('treatsNotVerifiedPaymentsAsTheirOwnFilterValue', () => {
    expect(paymentsFilterValue(closureStudents.free)).toBe('UNVERIFIED');
    expect(paymentsFilterValue(closureStudents.conditional)).toBe('NO');
    expect(paymentsFilterValue(closureStudents.promotes)).toBe('YES');
  });
});
