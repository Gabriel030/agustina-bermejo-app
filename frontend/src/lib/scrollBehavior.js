export function smoothScrollBehavior() {
  const reduced = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  return reduced ? 'auto' : 'smooth';
}
