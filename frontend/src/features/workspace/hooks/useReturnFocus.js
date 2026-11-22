import { useRef } from 'react';

export function useReturnFocus() {
  const opener = useRef(null);

  return {
    remember: () => {
      opener.current = document.activeElement;
    },
    restore: (event) => {
      event.preventDefault();
      if (opener.current?.isConnected) opener.current.focus();
    },
  };
}
