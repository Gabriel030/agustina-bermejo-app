import { useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';

function historyIndex() {
  const index = window.history.state?.idx;
  return typeof index === 'number' ? index : null;
}

export function useBackNavigationGuard(active) {
  const location = useLocation();
  const [pendingSteps, setPendingSteps] = useState(null);
  const currentIndexRef = useRef(null);
  const ignoredPopStatesRef = useRef(0);

  useEffect(() => {
    currentIndexRef.current = historyIndex();
  }, [location.key]);

  useEffect(() => {
    if (!active) return undefined;
    function handlePopState(event) {
      if (ignoredPopStatesRef.current > 0) {
        ignoredPopStatesRef.current -= 1;
        return;
      }
      const leftIndex = currentIndexRef.current;
      const arrivedIndex = typeof event.state?.idx === 'number' ? event.state.idx : null;
      if (leftIndex === null || arrivedIndex === null || leftIndex === arrivedIndex) return;
      event.stopImmediatePropagation();
      ignoredPopStatesRef.current += 1;
      window.history.go(leftIndex - arrivedIndex);
      setPendingSteps(arrivedIndex - leftIndex);
    }
    window.addEventListener('popstate', handlePopState, true);
    return () => {
      window.removeEventListener('popstate', handlePopState, true);
    };
  }, [active]);

  function confirmBackNavigation() {
    if (pendingSteps === null) return;
    ignoredPopStatesRef.current += 1;
    window.history.go(pendingSteps);
    setPendingSteps(null);
  }

  function cancelBackNavigation() {
    setPendingSteps(null);
  }

  return {
    isBackPending: pendingSteps !== null,
    confirmBackNavigation,
    cancelBackNavigation,
  };
}
