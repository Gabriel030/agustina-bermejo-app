import { useCallback, useEffect, useRef, useState } from 'react';

const CLOSE_DELAY_MS = 200;

export function useTopNavMenus(pathname) {
  const [state, setState] = useState({ id: null, pathname });
  const openId = state.pathname === pathname ? state.id : null;
  const setOpenId = useCallback(
    (next) => setState((current) => {
      const currentId = current.pathname === pathname ? current.id : null;
      return { id: typeof next === 'function' ? next(currentId) : next, pathname };
    }),
    [pathname],
  );
  const openedBy = useRef('click');
  const closeTimer = useRef(null);

  const cancelClose = useCallback(() => {
    clearTimeout(closeTimer.current);
    closeTimer.current = null;
  }, []);

  const close = useCallback(() => {
    cancelClose();
    setOpenId(null);
  }, [cancelClose, setOpenId]);

  const open = useCallback(
    (id, source) => {
      cancelClose();
      openedBy.current = source;
      setOpenId(id);
    },
    [cancelClose, setOpenId],
  );

  const toggle = useCallback(
    (id) => {
      cancelClose();
      if (openId === id && openedBy.current === 'hover') {
        openedBy.current = 'click';
        return;
      }
      openedBy.current = 'click';
      setOpenId((current) => (current === id ? null : id));
    },
    [cancelClose, openId, setOpenId],
  );

  const scheduleClose = useCallback(
    (id) => {
      cancelClose();
      closeTimer.current = setTimeout(() => setOpenId((current) => (current === id ? null : current)), CLOSE_DELAY_MS);
    },
    [cancelClose, setOpenId],
  );

  useEffect(() => cancelClose, [cancelClose]);

  return { openId, open, toggle, close, scheduleClose, cancelClose };
}
