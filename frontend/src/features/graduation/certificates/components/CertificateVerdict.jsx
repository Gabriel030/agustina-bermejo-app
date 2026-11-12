import { Ban, CircleCheck, CircleHelp, Clock, Hourglass, WifiOff } from 'lucide-react';
import { cn } from '@/lib/utils';

const ICONS = { ok: CircleCheck, clock: Clock, ban: Ban, unknown: CircleHelp, wait: Hourglass, offline: WifiOff };

const TONES = {
  success: 'bg-success-bg text-success-ink',
  warning: 'bg-warning-bg text-warning-ink',
  danger: 'bg-danger-bg text-danger-ink',
  neutral: 'bg-neutral-bg text-neutral-ink',
};

export function CertificateVerdict({ icon, tone, title, message, role = 'status', children }) {
  const Icon = ICONS[icon];
  return (
    <div role={role} className={cn('flex items-center gap-4 px-4 py-[18px] sm:px-5 sm:py-[22px]', TONES[tone])}>
      <div className="flex size-16 shrink-0 items-center justify-center rounded-full bg-dialog-bg">
        <Icon className="size-[34px]" strokeWidth={2.2} aria-hidden="true" />
      </div>
      <div className="min-w-0">
        <h1 className="font-heading text-[23px] font-bold text-balance sm:text-size-26">{title}</h1>
        <p className="mt-1 text-size-14-5">{message}</p>
        {children}
      </div>
    </div>
  );
}
