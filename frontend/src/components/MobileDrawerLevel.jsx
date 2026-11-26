import { Link } from 'react-router-dom';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { containsTarget, countLinks } from '@/components/mobileDrawerLevels';

export const DRAWER_ROW =
  'flex min-h-12 w-full items-center gap-3 rounded-control px-3 text-left text-sm font-medium text-ink transition-colors hover:bg-(--row-hover) focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring';
const DRAWER_ROW_CURRENT = 'bg-accent-soft font-semibold text-(--accent) hover:bg-accent-soft';

function CurrentMark() {
  return <span className="shrink-0 text-size-13 font-semibold">· actual</span>;
}

function LinkRow({ entry, isCurrent, onNavigate }) {
  const { to, label, icon: Icon } = entry.item;
  return (
    <Link
      to={to}
      data-row-id={entry.id}
      aria-current={isCurrent ? 'page' : undefined}
      onClick={onNavigate}
      className={cn(DRAWER_ROW, isCurrent && DRAWER_ROW_CURRENT)}
    >
      {Icon && <Icon aria-hidden="true" className="h-4 w-4 shrink-0" />}
      <span className="min-w-0 flex-1 truncate">{label}</span>
      {isCurrent && <CurrentMark />}
    </Link>
  );
}

function GroupRow({ entry, isCurrent, onOpen }) {
  const total = countLinks(entry);
  return (
    <Button
      type="button"
      variant="ghost"
      data-row-id={entry.id}
      onClick={() => onOpen(entry.id)}
      className={cn(DRAWER_ROW, 'h-auto justify-start', isCurrent && DRAWER_ROW_CURRENT)}
    >
      <span className="min-w-0 flex-1 truncate">{entry.label}</span>
      {isCurrent && <CurrentMark />}
      {entry.showCount && (
        <span className={cn('shrink-0 text-size-13 font-normal', !isCurrent && 'text-ink-faint')}>
          {total} {total === 1 ? 'pantalla' : 'pantallas'}
        </span>
      )}
      <ChevronRight aria-hidden="true" className="h-4 w-4 shrink-0" />
    </Button>
  );
}

export function DrawerBackRow({ onBack }) {
  return (
    <Button
      type="button"
      variant="ghost"
      data-drawer-back
      onClick={onBack}
      className={cn(DRAWER_ROW, 'mb-2 h-auto justify-start bg-neutral-bg font-semibold text-(--accent)')}
    >
      <ChevronLeft aria-hidden="true" className="h-4 w-4 shrink-0" />
      Volver
    </Button>
  );
}

export function DrawerEntryList({ entries, target, onOpen, onNavigate }) {
  return (
    <ul className="flex flex-col gap-1">
      {entries.map((entry) => {
        const isCurrent = containsTarget(entry, target);
        return (
          <li key={entry.id}>
            {entry.type === 'link' ? (
              <LinkRow entry={entry} isCurrent={isCurrent} onNavigate={onNavigate} />
            ) : (
              <GroupRow entry={entry} isCurrent={isCurrent} onOpen={onOpen} />
            )}
          </li>
        );
      })}
    </ul>
  );
}
