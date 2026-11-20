import { describe, expect, it } from 'vitest';
import css from '@/index.css?raw';
import { FONT_SIZE_TOKENS, cn } from './utils';

describe('font size tokens', () => {
  it('everyTokenDeclaredInTheThemeIsKnownToTheClassMerger', () => {
    const declared = [...css.matchAll(/--text-(size-[\d-]+):/g)].map((match) => match[1]);
    expect([...declared].sort()).toEqual([...FONT_SIZE_TOKENS].sort());
  });

  it('keepsTheTextColorWhenAFontSizeTokenIsAdded', () => {
    expect(cn('text-ink-soft', 'text-size-13')).toBe('text-ink-soft text-size-13');
  });

  it('replacesAnEarlierFontSizeTokenWithALaterOne', () => {
    expect(cn('text-size-12', 'text-size-13')).toBe('text-size-13');
  });

  it('replacesATextColorOnlyWhenAnotherColorFollows', () => {
    expect(cn('text-size-13 text-ink', 'text-brand')).toBe('text-size-13 text-brand');
  });
});
