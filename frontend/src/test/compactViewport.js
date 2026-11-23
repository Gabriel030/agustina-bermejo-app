const originalMatchMedia = window.matchMedia;

export function useCompactViewport() {
  window.matchMedia = () => ({ matches: true, addEventListener() {}, removeEventListener() {} });
}

export function restoreViewport() {
  window.matchMedia = originalMatchMedia;
}
