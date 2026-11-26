import { useRef } from 'react';
import { Pin } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { MenuLink } from '@/components/TopNavMenu';
import { GRID_ROW_BASE, GRID_ROW_PINNED, GRID_ZONE_TITLE } from '@/components/topNavGridStyles';
import { useGridPointing } from '@/hooks/useGridPointing';

export function TopNavGridPanel({ title, sections, menus }) {
  const { pinnedId, shownId, pointNow, pointAfterDelay, togglePin } = useGridPointing();
  const rootRef = useRef(null);

  const shown = sections.find((section) => section.id === shownId);
  const pinned = sections.find((section) => section.id === pinnedId);

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
        <p className={cn(GRID_ZONE_TITLE, 'px-3 pb-2 pt-1')}>{title}</p>
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
              className={cn(GRID_ROW_BASE, pinnedId === section.id ? GRID_ROW_PINNED : shownId === section.id && 'bg-neutral-bg')}
            >
              <span className="truncate">{section.label}</span>
              <span className="text-ink-faint">({section.items.length})</span>
            </Button>
          ))}
        </div>
      </div>
      <div className="p-2">
        <div className="flex items-center gap-2 px-3 pb-2 pt-1">
          <p className={GRID_ZONE_TITLE}>{shown ? shown.label : 'Pantallas'}</p>
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
