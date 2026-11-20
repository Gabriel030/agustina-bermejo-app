import { cn } from '@/lib/utils';

const TONES = {
  success: 'bg-success-bg text-success',
  danger: 'bg-danger-bg text-danger',
  info: 'bg-accent-soft text-brand',
  warning: 'bg-warning-bg text-pill-warning',
};

export function EmailChangeResultCard({ tone, icon: Icon, title, description, action }) {
  return (
    <>
      <div className={cn('flex h-16 w-16 items-center justify-center rounded-full', TONES[tone])}>
        <Icon size={32} aria-hidden="true" />
      </div>
      <div>
        <p className="font-heading text-lg font-bold text-ink">{title}</p>
        {description && <p className="mt-1 text-size-13 leading-snug text-ink-soft">{description}</p>}
      </div>
      {action}
    </>
  );
}
