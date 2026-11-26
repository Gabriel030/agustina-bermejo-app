import { useEffect, useId, useRef } from 'react';
import { NavLink } from 'react-router-dom';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';

const FOCUSABLE = 'a[href], button:not([disabled])';

export const TOP_NAV_BUTTON =
  'relative inline-flex min-h-11 items-center gap-1.5 whitespace-nowrap rounded-control px-2.5 text-size-13 font-semibold text-ink-soft transition-colors motion-reduce:transition-none hover:bg-neutral-bg hover:text-ink focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring';
export const TOP_NAV_BUTTON_ACTIVE =
  'rounded-b-none text-(--accent) border-b-2 border-b-(--accent) hover:text-(--accent)';

export const MENU_ROW =
  'flex min-h-11 w-full items-center justify-start gap-2 rounded-control px-3 text-left text-size-13 font-semibold text-ink transition-colors motion-reduce:transition-none hover:bg-neutral-bg focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring';
export const MENU_ROW_ACTIVE = 'bg-accent-soft text-(--accent)';

export function MenuLink({ item, menus }) {
  const Icon = item.icon;
  return (
    <NavLink to={item.to} onClick={menus.close} className={({ isActive }) => cn(MENU_ROW, isActive && MENU_ROW_ACTIVE)}>
      {Icon && <Icon aria-hidden="true" className="h-4 w-4 shrink-0" />}
      <span className="truncate">{item.label}</span>
    </NavLink>
  );
}

export function TopNavMenu({ id, active, open, menus, wide = false, align = 'start', trigger, children }) {
  const containerRef = useRef(null);
  const triggerRef = useRef(null);
  const panelRef = useRef(null);
  const focusFirstOnOpen = useRef(false);
  const panelId = useId();

  useEffect(() => {
    if (open && focusFirstOnOpen.current) {
      focusFirstOnOpen.current = false;
      panelRef.current?.querySelector(FOCUSABLE)?.focus();
    }
  }, [open]);

  function focusables() {
    return Array.from(panelRef.current?.querySelectorAll(FOCUSABLE) ?? []);
  }

  function openFromKeyboard() {
    if (open) {
      focusables()[0]?.focus();
      return;
    }
    focusFirstOnOpen.current = true;
    menus.open(id, 'key');
  }

  function handleTriggerClick(event) {
    if (event.detail === 0) {
      if (open) menus.close();
      else openFromKeyboard();
      return;
    }
    menus.toggle(id);
  }

  function handleTriggerKeyDown(event) {
    if (event.key !== 'ArrowDown') return;
    event.preventDefault();
    openFromKeyboard();
  }

  function handlePanelKeyDown(event) {
    const items = focusables();
    const index = items.indexOf(document.activeElement);
    const moves = {
      ArrowDown: items[(index + 1) % items.length],
      ArrowUp: items[(index - 1 + items.length) % items.length],
      Home: items[0],
      End: items[items.length - 1],
    };
    const target = moves[event.key];
    if (!target) return;
    event.preventDefault();
    target.focus();
  }

  function handleKeyDown(event) {
    if (event.key !== 'Escape' || !open) return;
    event.stopPropagation();
    menus.close();
    triggerRef.current?.focus();
  }

  function handleBlur(event) {
    const next = event.relatedTarget;
    if (open && next && !containerRef.current?.contains(next)) menus.close();
  }

  function handlePointerEnter(event) {
    if (event.pointerType !== 'mouse') return;
    menus.open(id, 'hover');
  }

  function handlePointerLeave(event) {
    if (event.pointerType !== 'mouse') return;
    menus.scheduleClose(id);
  }

  return (
    <div
      ref={containerRef}
      className="relative"
      onKeyDown={handleKeyDown}
      onBlur={handleBlur}
      onPointerEnter={handlePointerEnter}
      onPointerLeave={handlePointerLeave}
    >
      <Button
        ref={triggerRef}
        type="button"
        variant="ghost"
        size="inline"
        aria-expanded={open}
        aria-haspopup="true"
        aria-controls={panelId}
        onClick={handleTriggerClick}
        onKeyDown={handleTriggerKeyDown}
        className={cn(TOP_NAV_BUTTON, active && TOP_NAV_BUTTON_ACTIVE, open && 'bg-neutral-bg text-ink')}
      >
        {trigger}
        <ChevronDown
          aria-hidden="true"
          className={cn('h-3.5 w-3.5 shrink-0 transition-transform motion-reduce:transition-none', open && 'rotate-180')}
        />
      </Button>
      <div
        ref={panelRef}
        id={panelId}
        hidden={!open}
        onKeyDown={handlePanelKeyDown}
        className={cn(
          'absolute top-[calc(100%+6px)] z-40 rounded-card border border-border bg-surface p-2 shadow-overlay',
          align === 'end' ? 'right-0' : 'left-0',
          wide ? 'w-[45rem] max-w-[calc(100vw-2rem)]' : 'min-w-60',
        )}
      >
        {children}
      </div>
    </div>
  );
}
