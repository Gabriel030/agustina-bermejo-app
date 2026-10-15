import { useEffect, useState } from 'react';

export const SERIES_COLORS = [
  'var(--accent)',
  'var(--accent-2)',
  'var(--success)',
  'var(--warning)',
  'var(--danger)',
];

export const GRID_COLOR = 'var(--border)';
export const AXIS_COLOR = 'var(--ink-faint)';
export const GOAL_COLOR = 'var(--ink-soft)';

export function seriesColor(series, index) {
  return series.color ?? SERIES_COLORS[index % SERIES_COLORS.length];
}

const REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)';

function readReducedMotion() {
  return typeof window !== 'undefined' && window.matchMedia(REDUCED_MOTION_QUERY).matches;
}

export function useChartAnimation() {
  const [reduced, setReduced] = useState(readReducedMotion);

  useEffect(() => {
    const query = window.matchMedia(REDUCED_MOTION_QUERY);
    const update = () => setReduced(query.matches);
    query.addEventListener?.('change', update);
    return () => query.removeEventListener?.('change', update);
  }, []);

  return { isAnimationActive: !reduced, animationDuration: 700 };
}
