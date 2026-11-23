import { Ban, CircleAlert, CircleCheck, CircleHelp, Hourglass, WifiOff } from 'lucide-react';
import { StatusPill } from '@/components/StatusPill';
import { cn } from '@/lib/utils';

const ICONS = { ok: CircleCheck, alert: CircleAlert, ban: Ban, unknown: CircleHelp, wait: Hourglass, offline: WifiOff };

const SURFACES = {
  success: 'bg-success-bg text-success-ink',
  warning: 'bg-warning-bg text-warning-ink',
  danger: 'bg-danger-bg text-danger-ink',
  neutral: 'bg-neutral-bg text-neutral-ink',
};

export function CertificateVerdict({ icon, tone, badge, title, message, role = 'status', children }) {
  const Icon = ICONS[icon];
  return (
    <div role={role} className={cn('flex flex-col items-start gap-2.5 px-5 pt-5 pb-4 sm:px-[22px]', SURFACES[tone])}>
      <StatusPill tone={tone} className="items-center gap-2 border-[1.5px] border-current bg-transparent px-3 py-1 tracking-[0.08em] uppercase">
        <Icon className="size-4" strokeWidth={2.4} aria-hidden="true" />
        {badge}
      </StatusPill>
      <h1 className="font-heading text-size-22 font-bold text-balance text-ink">{title}</h1>
      <p className="text-size-14 text-ink">{message}</p>
      {children}
    </div>
  );
}
