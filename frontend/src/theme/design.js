export const DESIGNS = ['vl', 'b'];
export const DESIGN_STORAGE_KEY = 'design';
export const DEFAULT_DESIGN = 'vl';

export function resolveDesign({ search = '', stored = null, fallback = DEFAULT_DESIGN } = {}) {
  const fromQuery = new URLSearchParams(search).get('design');
  if (DESIGNS.includes(fromQuery)) return fromQuery;
  if (DESIGNS.includes(stored)) return stored;
  return DESIGNS.includes(fallback) ? fallback : DEFAULT_DESIGN;
}

export function applyDesign(design, root = document.documentElement) {
  root.dataset.design = design;
}
