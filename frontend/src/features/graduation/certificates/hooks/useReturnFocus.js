import { useRef } from 'react';

export function useReturnFocus() {
  const openerRef = useRef(null);
  return {
    remember(event) {
      openerRef.current = event.currentTarget;
    },
    onCloseAutoFocus(event) {
      const opener = openerRef.current;
      if (opener?.isConnected && !opener.disabled) {
        event.preventDefault();
        opener.focus();
      }
    },
  };
}
