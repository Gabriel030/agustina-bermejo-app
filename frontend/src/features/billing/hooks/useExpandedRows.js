import { useState } from 'react';

export function useExpandedRows() {
  const [expanded, setExpanded] = useState(() => new Set());

  function toggle(id) {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  return { isExpanded: (id) => expanded.has(id), toggle };
}
