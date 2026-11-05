import { GlassCard } from '@/components/GlassCard';

export function CatalogItemList({ heading, items }) {
  return (
    <GlassCard className="flex flex-col p-1">
      {heading && <h3 className="px-4 pt-3 text-[12.5px] font-bold uppercase tracking-wide text-ink-faint">{heading}</h3>}
      {items.map((item) => (
        <div key={item.id} className="flex items-center justify-between gap-3 border-b border-border px-4 py-3 last:border-b-0">
          <span className="flex flex-wrap items-center gap-2">
            <span className="text-[13.5px] font-semibold text-ink">{item.title}</span>
            {item.tag && <span className="rounded-full bg-brand/10 px-2 py-0.5 text-[11px] font-semibold text-ink-soft">{item.tag}</span>}
          </span>
          {item.detail && <span className="shrink-0 text-xs text-ink-faint">{item.detail}</span>}
        </div>
      ))}
    </GlassCard>
  );
}
