import { Link } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { AREA_LABELS, ADMISSION_BADGES } from '@/features/enrollment/lib/catalogOffering';

function AdmissionBadge({ item, badge }) {
  if (item.kind === 'course') return <StatusPill tone={ADMISSION_BADGES.unknown.tone}>{ADMISSION_BADGES.unknown.label}</StatusPill>;
  if (!badge) return <Skeleton className="h-6 w-32 rounded-full" />;
  return <StatusPill tone={badge.tone}>{badge.label}</StatusPill>;
}

export function OfferingCard({ item, badge }) {
  const areaLabel = AREA_LABELS[item.area];
  const meta = [item.duration, item.modality].filter(Boolean);
  return (
    <li>
      <GlassCard className="flex h-full min-w-0 flex-col gap-2 p-4">
        <div className="flex flex-wrap items-center gap-1.5">
          <StatusPill tone="neutral">{item.typeLabel}</StatusPill>
          {areaLabel && <StatusPill tone="accent">{areaLabel}</StatusPill>}
          <AdmissionBadge item={item} badge={badge} />
        </div>
        <h3 className="font-heading text-size-15 font-semibold text-ink">{item.name}</h3>
        {meta.length > 0 && (
          <div className="flex flex-wrap gap-x-3 gap-y-1 text-size-13 text-ink-soft">
            {meta.map((part) => (
              <span key={part}>{part}</span>
            ))}
          </div>
        )}
        <div className="mt-auto flex justify-end pt-1 max-sm:[&>a]:w-full">
          <Button asChild variant="outline" className="min-h-11 sm:min-h-9">
            <Link to={item.to} aria-label={`Ver detalle de ${item.name}`}>
              Ver detalle
            </Link>
          </Button>
        </div>
      </GlassCard>
    </li>
  );
}
