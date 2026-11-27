import { useId } from 'react';
import { cn } from '@/lib/utils';

const GROUP_HEADING = 'text-size-11 font-bold uppercase tracking-[0.08em] text-ink-faint';

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

function GroupItems({ group, startIndex, renderItem, ...listProps }) {
  if (!group.sections) return <ItemList items={group.items} startIndex={startIndex} renderItem={renderItem} {...listProps} />;
  const offsets = group.sections.reduce((acc, section, index) => [...acc, acc[index] + section.items.length], [startIndex]);
  return (
    <div role="group" className="flex flex-col gap-2" {...listProps}>
      {group.sections.map((section, index) => (
        <div key={section.id} className="flex flex-col gap-1">
          <p className={cn(GROUP_HEADING, 'px-3 pt-1')}>{section.label}</p>
          <ItemList items={section.items} startIndex={offsets[index]} renderItem={renderItem} aria-label={section.label} />
        </div>
      ))}
    </div>
  );
}

function StaticGroup({ group, startIndex, renderItem }) {
  const headingId = useId();
  return (
    <div className="mt-3 flex flex-col gap-1 first:mt-0">
      <p id={headingId} className={cn(GROUP_HEADING, 'px-3 pb-1 pt-1')}>
        {group.label}
      </p>
      <GroupItems group={group} startIndex={startIndex} renderItem={renderItem} aria-labelledby={headingId} />
    </div>
  );
}

export function NavGroups({ groups, renderItem }) {
  const startIndexes = groups.reduce((acc, group, index) => [...acc, acc[index] + group.items.length], [0]);
  return (
    <div className="flex flex-col">
      {groups.map((group, index) => {
        const groupStart = startIndexes[index];
        if (!group.label) {
          return <ItemList key={group.id} items={group.items} startIndex={groupStart} renderItem={renderItem} />;
        }
        return <StaticGroup key={group.id} group={group} startIndex={groupStart} renderItem={renderItem} />;
      })}
    </div>
  );
}
