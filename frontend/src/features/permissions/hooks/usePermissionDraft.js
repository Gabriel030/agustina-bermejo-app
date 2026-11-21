import { useCallback, useMemo, useState } from 'react';
import { changeKey, grantedByRole } from '@/features/permissions/lib/permissionGrid';

export function usePermissionDraft(grid) {
  const [pending, setPending] = useState(() => new Map());
  const base = useMemo(() => grantedByRole(grid), [grid]);

  const isGranted = useCallback(
    (role, code) => {
      const key = changeKey(role, code);
      return pending.has(key) ? pending.get(key) : Boolean(base.get(role)?.has(code));
    },
    [pending, base],
  );

  const hasChanged = useCallback((role, code) => pending.has(changeKey(role, code)), [pending]);

  const toggle = useCallback(
    (role, code) => {
      const key = changeKey(role, code);
      const original = Boolean(base.get(role)?.has(code));
      setPending((current) => {
        const next = new Map(current);
        const granted = current.has(key) ? current.get(key) : original;
        if (!granted === original) next.delete(key);
        else next.set(key, !granted);
        return next;
      });
    },
    [base],
  );

  const discard = useCallback(() => setPending(new Map()), []);

  const settle = useCallback((applied) => {
    setPending((current) => {
      const next = new Map(current);
      applied.forEach(({ role, permissionCode }) => next.delete(changeKey(role, permissionCode)));
      return next;
    });
  }, []);

  const changes = useMemo(
    () =>
      [...pending.entries()].map(([key, granted]) => {
        const [role, permissionCode] = key.split('|');
        return { role, permissionCode, granted };
      }),
    [pending],
  );

  return { isGranted, hasChanged, toggle, discard, settle, changes };
}
