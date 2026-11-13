import { holidayTypeInfo } from '@/features/academic-structure/lib/holidays';
import { cn } from '@/lib/utils';

export function HolidayTypePill({ type, count, plain = false }) {
  const info = holidayTypeInfo(type);
  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-full border border-transparent py-0.5 pl-1 pr-2.5 text-xs font-semibold whitespace-nowrap', plain ? 'px-0 text-ink' : info.pillClass)}>
      <span
        aria-hidden="true"
        className={cn('grid h-5 min-w-5 place-items-center rounded-md border-[1.5px] border-current px-0.5 text-size-10-5 font-extrabold leading-none', plain && info.cellClass)}
      >
        {info.chip}
      </span>
      {info.label}
      {count !== undefined && <span className="tabular-nums opacity-75">{count}</span>}
    </span>
  );
}
