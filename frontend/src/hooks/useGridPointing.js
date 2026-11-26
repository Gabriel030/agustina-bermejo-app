import { useEffect, useRef, useState } from 'react';

const POINT_DELAY_MS = 140;

export function useGridPointing() {
  const [pointedId, setPointedId] = useState(null);
  const [pinnedId, setPinnedId] = useState(null);
  const pointTimer = useRef(null);

  useEffect(() => () => clearTimeout(pointTimer.current), []);

  function pointNow(id) {
    clearTimeout(pointTimer.current);
    if (pinnedId) return;
    setPointedId(id);
  }

  function pointAfterDelay(id, event) {
    if (event.pointerType !== 'mouse' || pinnedId) return;
    clearTimeout(pointTimer.current);
    pointTimer.current = setTimeout(() => setPointedId(id), POINT_DELAY_MS);
  }

  function togglePin(id) {
    clearTimeout(pointTimer.current);
    setPinnedId((current) => (current === id ? null : id));
    setPointedId(id);
  }

  return { pointedId, pinnedId, shownId: pinnedId ?? pointedId, pointNow, pointAfterDelay, togglePin };
}
