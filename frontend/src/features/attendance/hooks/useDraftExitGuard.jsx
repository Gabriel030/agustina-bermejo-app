import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AttendanceConfirmDialog } from '@/features/attendance/components/AttendanceConfirmDialog';

function internalTarget(event) {
  if (event.defaultPrevented || event.button !== 0) return null;
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return null;
  const anchor = event.target instanceof Element ? event.target.closest('a[href]') : null;
  if (!anchor || anchor.target === '_blank' || anchor.hasAttribute('download')) return null;
  const url = new URL(anchor.href, window.location.href);
  if (url.origin !== window.location.origin) return null;
  const target = `${url.pathname}${url.search}${url.hash}`;
  const current = `${window.location.pathname}${window.location.search}${window.location.hash}`;
  return target === current ? null : target;
}

export function useDraftExitGuard(changedCount, active) {
  const navigate = useNavigate();
  const [pendingPath, setPendingPath] = useState(null);

  useEffect(() => {
    if (!active) return undefined;
    function handleBeforeUnload(event) {
      event.preventDefault();
      event.returnValue = '';
    }
    function handleClick(event) {
      const target = internalTarget(event);
      if (!target) return;
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
  }, [active]);

  const leaveDialog = (
    <AttendanceConfirmDialog
      open={pendingPath !== null}
      onOpenChange={(open) => {
        if (!open) setPendingPath(null);
      }}
      title="¿Salir sin guardar?"
      description={`Tenés ${changedCount === 1 ? '1 cambio sin guardar' : `${changedCount || 'varios'} cambios sin guardar`}. Quedan como borrador en este dispositivo hasta 12 horas, pero los alumnos y Secretaría no los ven hasta que guardes.`}
      cancelLabel="Seguir acá"
      confirmLabel="Salir"
      confirmVariant="destructive"
      onConfirm={() => {
        const path = pendingPath;
        setPendingPath(null);
        navigate(path);
      }}
    />
  );

  return { leaveDialog };
}
