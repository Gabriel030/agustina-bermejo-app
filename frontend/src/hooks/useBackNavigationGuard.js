import { useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';

function historyIndex() {
  const index = window.history.state?.idx;
  return typeof index === 'number' ? index : null;
}

export function useBackNavigationGuard(active) {
  const location = useLocation();
  const [pending, setPending] = useState(null);
  const currentIndexRef = useRef(null);
  const confirmedTargetIndexRef = useRef(null);

  useEffect(() => {
    currentIndexRef.current = historyIndex();
  }, [location.key]);

  useEffect(() => {
    if (!active) return undefined;
    function handlePopState(event) {
      const arrivedIndex = typeof event.state?.idx === 'number' ? event.state.idx : null;
      if (confirmedTargetIndexRef.current !== null && arrivedIndex === confirmedTargetIndexRef.current) {
        confirmedTargetIndexRef.current = null;
        return;
      }
      const leftIndex = currentIndexRef.current;
      if (leftIndex === null || arrivedIndex === null || leftIndex === arrivedIndex) return;
      event.stopImmediatePropagation();
      window.history.go(leftIndex - arrivedIndex);
      setPending({ steps: arrivedIndex - leftIndex, targetIndex: arrivedIndex });
    }
    window.addEventListener('popstate', handlePopState, true);
    return () => {
      window.removeEventListener('popstate', handlePopState, true);
      confirmedTargetIndexRef.current = null;
      setPending(null);
    };
  }, [active]);

  function confirmBackNavigation() {
    if (pending === null) return;
    confirmedTargetIndexRef.current = pending.targetIndex;
    window.history.go(pending.steps);
    setPending(null);
  }

  function cancelBackNavigation() {
    setPending(null);
  }

  return {
    isBackPending: pending !== null,
    confirmBackNavigation,
    cancelBackNavigation,
  };
}
