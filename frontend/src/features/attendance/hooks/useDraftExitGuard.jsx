import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AttendanceConfirmDialog } from '@/features/attendance/components/AttendanceConfirmDialog';
import { useBackNavigationGuard } from '@/hooks/useBackNavigationGuard';

function internalLink(event) {
  if (event.defaultPrevented || event.button !== 0) return null;
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return null;
  const anchor = event.target instanceof Element ? event.target.closest('a[href]') : null;
  if (!anchor || anchor.target === '_blank' || anchor.hasAttribute('download')) return null;
  const url = new URL(anchor.href, window.location.href);
  if (url.origin !== window.location.origin) return null;
  const path = `${url.pathname}${url.search}${url.hash}`;
  const current = `${window.location.pathname}${window.location.search}${window.location.hash}`;
  return path === current ? null : { anchor, path };
}

export function useDraftExitGuard(changedCount, active) {
  const navigate = useNavigate();
  const [pendingLink, setPendingLink] = useState(null);
  const releasedAnchorRef = useRef(null);
  const back = useBackNavigationGuard(active);

  useEffect(() => {
    if (!active) return undefined;
    function handleBeforeUnload(event) {
      event.preventDefault();
      event.returnValue = '';
    }
    function handleClick(event) {
      const link = internalLink(event);
      if (!link) return;
      if (link.anchor === releasedAnchorRef.current) {
        releasedAnchorRef.current = null;
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      setPendingLink(link);
    }
    window.addEventListener('beforeunload', handleBeforeUnload);
    document.addEventListener('click', handleClick, true);
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
      document.removeEventListener('click', handleClick, true);
    };
  }, [active]);

  const leaveDialog = (
    <AttendanceConfirmDialog
      open={pendingLink !== null || back.isBackPending}
      onOpenChange={(open) => {
        if (!open) {
          setPendingLink(null);
          back.cancelBackNavigation();
        }
      }}
      title="¿Salir sin guardar?"
      description={`${changedCount > 1 ? `Tenés ${changedCount} cambios sin guardar` : 'Tenés cambios sin guardar'}. Quedan como borrador en este dispositivo hasta 12 horas, pero los alumnos y Secretaría no los ven hasta que guardes.`}
      cancelLabel="Seguir acá"
      confirmLabel="Salir"
      confirmVariant="destructive"
      onConfirm={() => {
        if (back.isBackPending) {
          back.confirmBackNavigation();
          return;
        }
        const link = pendingLink;
        setPendingLink(null);
        if (link.anchor.isConnected) {
          releasedAnchorRef.current = link.anchor;
          link.anchor.click();
          releasedAnchorRef.current = null;
          return;
        }
        navigate(link.path);
      }}
    />
  );

  return { leaveDialog };
}
