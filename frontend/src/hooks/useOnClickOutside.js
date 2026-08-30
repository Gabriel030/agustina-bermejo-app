import { useEffect } from 'react';

export function useOnClickOutside(ref, onOutsideClick) {
  useEffect(() => {
    function handler(event) {
      if (ref.current && !ref.current.contains(event.target)) {
        onOutsideClick();
      }
    }
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [ref, onOutsideClick]);
}
