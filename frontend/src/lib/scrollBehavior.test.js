import { afterEach, describe, expect, it } from 'vitest';
import { smoothScrollBehavior } from './scrollBehavior';

const originalMatchMedia = window.matchMedia;

function stubReducedMotion(matches) {
  window.matchMedia = () => ({ matches, addEventListener() {}, removeEventListener() {}, addListener() {}, removeListener() {} });
}

describe('smoothScrollBehavior', () => {
  afterEach(() => {
    window.matchMedia = originalMatchMedia;
  });

  it('returnsSmoothWhenMotionIsAllowed', () => {
    stubReducedMotion(false);
    expect(smoothScrollBehavior()).toBe('smooth');
  });

  it('returnsAutoWhenUserPrefersReducedMotion', () => {
    stubReducedMotion(true);
    expect(smoothScrollBehavior()).toBe('auto');
  });
});
