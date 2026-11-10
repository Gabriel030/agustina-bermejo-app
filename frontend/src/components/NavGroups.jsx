import { useId, useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';

const GROUP_HEADING = 'text-[11px] font-bold uppercase tracking-[0.08em] text-ink-faint';

function isActivePath(item, pathname) {
  return pathname === item.to || pathname.startsWith(`${item.to}/`);
}

function ItemList({ items, startIndex, renderItem, ...listProps }) {
  return (
    <ul className="flex flex-col gap-1" {...listProps}>
      {items.map((item, offset) => (
        <li key={item.to} className="flex flex-col">
          {renderItem(item, startIndex + offset)}
        </li>
      ))}
    </ul>
  );
}

function StaticGroup({ group, startIndex, renderItem }) {
  const headingId = useId();
  return (
    <div className="mt-3 flex flex-col gap-1 first:mt-0">
      <p id={headingId} className={cn(GROUP_HEADING, 'px-3 pb-1 pt-1')}>
        {group.label}
      </p>
      <ItemList items={group.items} startIndex={startIndex} renderItem={renderItem} aria-labelledby={headingId} />
    </div>
  );
}

function CollapsibleGroup({ group, startIndex, renderItem, open, onToggle }) {
  const headingId = useId();
  const listId = useId();
  return (
    <div className="mt-1 flex flex-col gap-1 first:mt-0">
      <button
        type="button"
        id={headingId}
        aria-expanded={open}
        aria-controls={listId}
        onClick={onToggle}
        className={cn(GROUP_HEADING, 'flex min-h-11 w-full items-center justify-between rounded-control px-3 text-left')}
      >
        {group.label}
        <ChevronDown aria-hidden="true" className={cn('h-4 w-4 transition-transform', open && 'rotate-180')} />
      </button>
      <ItemList
        items={group.items}
        startIndex={startIndex}
        renderItem={renderItem}
        id={listId}
        aria-labelledby={headingId}
        hidden={!open}
      />
    </div>
  );
}

export function NavGroups({ groups, renderItem, collapsible = false, pathname = '' }) {
  const [openIds, setOpenIds] = useState(
    () => new Set(groups.filter((group) => group.items.some((item) => isActivePath(item, pathname))).map((group) => group.id)),
  );

  function toggle(id) {
    setOpenIds((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  let startIndex = 0;
  return (
    <div className="flex flex-col">
      {groups.map((group) => {
        const groupStart = startIndex;
        startIndex += group.items.length;
        if (!group.label) {
          return <ItemList key={group.id} items={group.items} startIndex={groupStart} renderItem={renderItem} />;
        }
        if (collapsible) {
          return (
            <CollapsibleGroup
              key={group.id}
              group={group}
              startIndex={groupStart}
              renderItem={renderItem}
              open={openIds.has(group.id)}
              onToggle={() => toggle(group.id)}
            />
          );
        }
        return <StaticGroup key={group.id} group={group} startIndex={groupStart} renderItem={renderItem} />;
      })}
    </div>
  );
}
