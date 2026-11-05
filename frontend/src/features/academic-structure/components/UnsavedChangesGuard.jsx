import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ConfirmActionDialog from '@/features/academic-structure/components/ConfirmActionDialog';

function findInternalAnchor(event) {
  if (event.defaultPrevented || event.button !== 0) return null;
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return null;
  const anchor = event.target instanceof Element ? event.target.closest('a[href]') : null;
  if (!anchor || anchor.target === '_blank' || anchor.hasAttribute('download')) return null;
  const url = new URL(anchor.href, window.location.href);
  if (url.origin !== window.location.origin) return null;
  return url;
}

export function useUnsavedChangesGuard(dirty) {
  const navigate = useNavigate();
  const [pendingPath, setPendingPath] = useState(null);
  const dirtyRef = useRef(dirty);
  useEffect(() => {
    dirtyRef.current = dirty;
  }, [dirty]);

  useEffect(() => {
    if (!dirty) return undefined;
    function handleBeforeUnload(event) {
      event.preventDefault();
      event.returnValue = '';
    }
    function handleClick(event) {
      const url = findInternalAnchor(event);
      if (!url) return;
      const target = `${url.pathname}${url.search}${url.hash}`;
      const current = `${window.location.pathname}${window.location.search}${window.location.hash}`;
      if (target === current) return;
      event.preventDefault();
      event.stopPropagation();
      setPendingPath(target);
    }
    window.addEventListener('beforeunload', handleBeforeUnload);
    document.addEventListener('click', handleClick, true);
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
      document.removeEventListener('click', handleClick, true);
    };
  }, [dirty]);

  const requestNavigation = useCallback(
    (path) => {
      if (dirtyRef.current) setPendingPath(path);
      else navigate(path);
    },
    [navigate],
  );

  const guardDialog = (
    <ConfirmActionDialog
      open={pendingPath !== null}
      onOpenChange={(open) => {
        if (!open) setPendingPath(null);
      }}
      title="¿Salir sin guardar?"
      description="Tenés cambios sin guardar. Si salís ahora, se pierden."
      confirmLabel="Salir sin guardar"
      onConfirm={() => {
        const path = pendingPath;
        setPendingPath(null);
        navigate(path);
      }}
    />
  );

  return { requestNavigation, guardDialog };
}
