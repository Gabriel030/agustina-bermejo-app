import { useEffect, useMemo, useRef, useState } from 'react';
import { Dialog as DialogPrimitive } from 'radix-ui';
import { useLocation } from 'react-router-dom';
import { ChevronRight, LogOut, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { RoleSwitcher } from '@/components/RoleSwitcher';
import { DRAWER_ROW, DrawerBackRow, DrawerEntryList } from '@/components/MobileDrawerLevel';
import { buildDrawerEntries, findCurrentTarget, resolveLevelNodes } from '@/components/mobileDrawerLevels';
import { useAuth } from '@/context/AuthContext';
import { useActiveRole } from '@/hooks/useActiveRole';
import { roleLabel } from '@/lib/activeRole';

const ROLES_LEVEL_ID = 'role-switch';
const LEVEL_MOTION = 'animate-in fade-in-0 duration-150 motion-reduce:animate-none';

// Botón hamburguesa cuyas 3 líneas giran y forman una X, en vez de cambiar de
// ícono (Menu → X) en seco.
function HamburgerIcon({ open }) {
  return (
    <span className="flex h-4 w-5 shrink-0 flex-col items-center justify-center gap-[5px]">
      <span
        className={cn(
          'h-0.5 w-5 rounded-full bg-current transition-transform duration-300 ease-[cubic-bezier(0.22,1,0.36,1)]',
          open && 'translate-y-[6.5px] rotate-45',
        )}
      />
      <span
        className={cn('h-0.5 w-5 rounded-full bg-current transition-opacity duration-200', open && 'opacity-0')}
      />
      <span
        className={cn(
          'h-0.5 w-5 rounded-full bg-current transition-transform duration-300 ease-[cubic-bezier(0.22,1,0.36,1)]',
          open && '-translate-y-[6.5px] -rotate-45',
        )}
      />
    </span>
  );
}

export function MobileMenu({ titulo, items, groups, hiddenFrom = 'lg:hidden' }) {
  const [open, setOpen] = useState(false);
  const [path, setPath] = useState([]);
  const [direction, setDirection] = useState('forward');
  const { logout } = useAuth();
  const { pathname } = useLocation();
  const { canSwitch, activeRole } = useActiveRole();
  const contentRef = useRef(null);
  const pendingFocus = useRef(null);

  const entries = useMemo(() => buildDrawerEntries(items, groups), [items, groups]);
  const target = findCurrentTarget(entries, pathname);
  const inRolesLevel = path[0] === ROLES_LEVEL_ID;
  const levelNodes = inRolesLevel ? [] : resolveLevelNodes(entries, path);
  const currentNode = levelNodes[levelNodes.length - 1];
  const atRoot = path.length === 0;
  const levelEntries = currentNode ? currentNode.children : entries;
  const levelTitle = inRolesLevel ? 'Cambiar de rol' : currentNode ? currentNode.label : `Menú de ${titulo}`;

  useEffect(() => {
    const pending = pendingFocus.current;
    if (!open || !pending) return;
    pendingFocus.current = null;
    const scope = contentRef.current;
    if (!scope) return;
    const returnRow = pending.rowId ? [...scope.querySelectorAll('[data-row-id]')].find((row) => row.dataset.rowId === pending.rowId) : null;
    const next = returnRow ?? scope.querySelector('[data-drawer-back]') ?? scope.querySelector('[data-row-id]');
    next?.focus();
  }, [open, path]);

  function changeOpen(nextOpen) {
    setOpen(nextOpen);
    if (!nextOpen) setPath([]);
  }

  function enterLevel(id) {
    pendingFocus.current = {};
    setDirection('forward');
    setPath((current) => [...current, id]);
  }

  function goBack() {
    pendingFocus.current = { rowId: path[path.length - 1] };
    setDirection('back');
    setPath((current) => current.slice(0, -1));
  }

  function closeDrawer() {
    changeOpen(false);
  }

  return (
    <DialogPrimitive.Root open={open} onOpenChange={changeOpen}>
      <DialogPrimitive.Trigger asChild>
        <button
          type="button"
          aria-label={open ? 'Cerrar menú' : 'Abrir menú'}
          className={cn('flex h-11 w-11 shrink-0 items-center justify-center rounded-xl border border-border bg-surface text-ink', hiddenFrom)}
        >
          <HamburgerIcon open={open} />
        </button>
      </DialogPrimitive.Trigger>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-(--overlay-bg-menu) duration-150 data-open:animate-in data-open:fade-in-0 data-closed:animate-out data-closed:fade-out-0" />
        <DialogPrimitive.Content
          ref={contentRef}
          aria-describedby={undefined}
          onOpenAutoFocus={(event) => {
            event.preventDefault();
            contentRef.current?.querySelector('[data-row-id]')?.focus();
          }}
          className="fixed inset-y-0 left-0 z-50 flex h-dvh w-[min(340px,88vw)] flex-col border-r border-border bg-surface shadow-overlay duration-200 data-open:animate-in data-open:slide-in-from-left data-closed:animate-out data-closed:slide-out-to-left"
        >
          <div className="flex shrink-0 items-center justify-between gap-3 border-b border-border px-4 py-3">
            <InstitutionBrand layout="horizontal" />
            <DialogPrimitive.Close asChild>
              <button type="button" aria-label="Cerrar menú" className="flex h-11 w-11 shrink-0 items-center justify-center rounded-control p-0 text-ink-faint hover:text-ink focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring">
                <X aria-hidden="true" className="h-5 w-5" />
              </button>
            </DialogPrimitive.Close>
          </div>

          <nav aria-label="Menú" className="min-h-0 flex-1 overflow-y-auto overscroll-contain p-3">
            <div
              key={path.join('>')}
              className={cn(LEVEL_MOTION, direction === 'forward' ? 'slide-in-from-right-4' : 'slide-in-from-left-4')}
            >
              {!atRoot && <DrawerBackRow onBack={goBack} />}
              <DialogPrimitive.Title
                className={cn(
                  atRoot ? 'sr-only' : 'px-3 pb-2 pt-1 text-size-11 font-bold uppercase tracking-[0.08em] text-ink-faint',
                )}
              >
                {levelTitle}
              </DialogPrimitive.Title>
              {inRolesLevel ? (
                <RoleSwitcher variant="list" onNavigate={closeDrawer} />
              ) : (
                <DrawerEntryList entries={levelEntries} target={target} onOpen={enterLevel} onNavigate={closeDrawer} />
              )}
            </div>
          </nav>
          <p role="status" className="sr-only">
            {levelTitle}
          </p>

          {atRoot && (
            <div className="shrink-0 border-t border-border p-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
              {canSwitch && (
                <button type="button" onClick={() => enterLevel(ROLES_LEVEL_ID)} className={cn(DRAWER_ROW, 'mb-1')}>
                  <span className="min-w-0 flex-1 truncate">Cambiar de rol</span>
                  <span className="shrink-0 text-size-13 font-normal text-ink-faint">{roleLabel(activeRole)}</span>
                  <ChevronRight aria-hidden="true" className="h-4 w-4 shrink-0" />
                </button>
              )}
              <Button
                type="button"
                variant="ghost"
                onClick={() => {
                  closeDrawer();
                  logout();
                }}
                className="h-12 w-full justify-start gap-3 px-3 text-sm font-medium text-ink-soft hover:text-danger"
              >
                <LogOut aria-hidden="true" className="h-4 w-4 shrink-0" />
                Cerrar sesión
              </Button>
            </div>
          )}
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
