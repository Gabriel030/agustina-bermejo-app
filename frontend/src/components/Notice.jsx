import { cn } from '@/lib/utils';

const TONES = {
  neutral: 'bg-neutral-bg text-ink-soft',
  warning: 'bg-warning-bg text-ink',
  success: 'bg-success-bg text-ink',
};

export function Notice({ tone = 'neutral', title, children, className }) {
  return (
    <div role="note" className={cn('rounded-xl px-3 py-2.5 text-size-12-5 leading-snug', TONES[tone], className)}>
      {title && <b className="font-bold">{title} </b>}
      {children}
    </div>
  );
}
