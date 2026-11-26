import { useEffect, useRef, useState } from 'react';
import { Pin } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { MenuLink } from '@/components/TopNavMenu';

const POINT_DELAY_MS = 140;
const ROW_BASE =
  'flex min-h-11 w-full items-center justify-between gap-2 rounded-control px-3 text-left text-size-13 font-semibold text-ink transition-colors motion-reduce:transition-none hover:bg-neutral-bg focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring';
const ROW_PINNED = "relative bg-accent-soft text-(--accent) hover:bg-accent-soft before:absolute before:inset-y-2 before:left-0 before:w-[3px] before:bg-(color:--accent) before:content-['']";
const ZONE_TITLE = 'text-size-11 font-bold uppercase tracking-[0.08em] text-ink-faint';

export function TopNavGridPanel({ title, sections, menus }) {
  const [pointedId, setPointedId] = useState(null);
  const [pinnedId, setPinnedId] = useState(null);
  const rootRef = useRef(null);
  const pointTimer = useRef(null);

  useEffect(() => () => clearTimeout(pointTimer.current), []);

  const shownId = pinnedId ?? pointedId;
  const shown = sections.find((section) => section.id === shownId);
  const pinned = sections.find((section) => section.id === pinnedId);

  function pointNow(id) {
    clearTimeout(pointTimer.current);
    if (pinnedId) return;
    setPointedId(id);
  }

  function pointAfterDelay(id, event) {
    if (event.pointerType !== 'mouse' || pinnedId) return;
    clearTimeout(pointTimer.current);
    pointTimer.current = setTimeout(() => setPointedId(id), POINT_DELAY_MS);
  }

  function togglePin(id) {
    clearTimeout(pointTimer.current);
    setPinnedId((current) => (current === id ? null : id));
    setPointedId(id);
  }

  function rows() {
    return Array.from(rootRef.current?.querySelectorAll('[data-grid-row]') ?? []);
  }

  function items() {
    return Array.from(rootRef.current?.querySelectorAll('[data-grid-items] a[href]') ?? []);
  }

  function handleKeyDown(event) {
    const inRow = event.target.hasAttribute('data-grid-row');
    const zone = inRow ? rows() : items();
    const index = zone.indexOf(event.target);
    if (index < 0) return;
    const moves = {
      ArrowDown: zone[(index + 1) % zone.length],
      ArrowUp: zone[(index - 1 + zone.length) % zone.length],
      Home: zone[0],
      End: zone[zone.length - 1],
      ArrowRight: inRow ? items()[0] : undefined,
      ArrowLeft: inRow ? undefined : (rows().find((row) => row.dataset.gridRow === shownId) ?? rows()[0]),
    };
    const target = moves[event.key];
    if (!target) return;
    event.preventDefault();
    event.stopPropagation();
    target.focus();
  }

  return (
    <div ref={rootRef} onKeyDown={handleKeyDown} className="grid grid-cols-1 sm:grid-cols-[16.25rem_1fr]">
      <div className="border-b border-border p-2 sm:border-b-0 sm:border-r">
        <p className={cn(ZONE_TITLE, 'px-3 pb-2 pt-1')}>{title}</p>
        <div className="flex flex-col gap-0.5">
          {sections.map((section) => (
            <Button
              key={section.id}
              type="button"
              variant="ghost"
              size="inline"
              data-grid-row={section.id}
              aria-pressed={pinnedId === section.id}
              onPointerEnter={(event) => pointAfterDelay(section.id, event)}
              onFocus={() => pointNow(section.id)}
              onClick={() => togglePin(section.id)}
              className={cn(ROW_BASE, pinnedId === section.id ? ROW_PINNED : shownId === section.id && 'bg-neutral-bg')}
            >
              <span className="truncate">{section.label}</span>
              <span className="text-ink-faint">({section.items.length})</span>
            </Button>
          ))}
        </div>
      </div>
      <div className="p-2">
        <div className="flex items-center gap-2 px-3 pb-2 pt-1">
          <p className={ZONE_TITLE}>{shown ? shown.label : 'Pantallas'}</p>
          {pinned && (
            <span className="inline-flex items-center gap-1 rounded-full bg-accent-soft px-2 py-0.5 text-size-11 font-bold text-(--accent)">
              <Pin aria-hidden="true" className="h-3 w-3" />
              Fijado
            </span>
          )}
        </div>
        {shown ? (
          <ul data-grid-items aria-label={shown.label} className="flex flex-col gap-0.5">
            {shown.items.map((item) => (
              <li key={item.to}>
                <MenuLink item={item} menus={menus} />
              </li>
            ))}
          </ul>
        ) : (
          <p className="px-3 text-size-13 text-ink-soft">Elegí un grupo para ver sus pantallas.</p>
        )}
      </div>
      <div role="status" className="sr-only">
        {pinned ? `Pantallas de ${pinned.label}. Fijado` : ''}
      </div>
    </div>
  );
}
