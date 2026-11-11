import { useEffect } from 'react';

export const UNSAVED_MESSAGE = 'Tenés notas sin guardar. Si salís las perdés.';

export function useUnsavedChangesGuard(active, message = UNSAVED_MESSAGE) {
  useEffect(() => {
    if (!active) return undefined;
    const beforeUnload = (event) => {
      event.preventDefault();
      event.returnValue = '';
    };
    const interceptLink = (event) => {
      const anchor = event.target.closest?.('a[href]');
      if (!anchor || anchor.target === '_blank' || !anchor.getAttribute('href').startsWith('/')) return;
      if (!window.confirm(message)) {
        event.preventDefault();
        event.stopPropagation();
      }
    };
    window.addEventListener('beforeunload', beforeUnload);
    document.addEventListener('click', interceptLink, true);
    return () => {
      window.removeEventListener('beforeunload', beforeUnload);
      document.removeEventListener('click', interceptLink, true);
    };
  }, [active, message]);
}
