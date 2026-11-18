import { Link } from 'react-router-dom';
import { ChevronRight, ClipboardCheck, FileText, GraduationCap, Inbox, Info, Lock, Receipt, RefreshCw, TriangleAlert, UserCheck, UserPlus, UserX, Wallet, CalendarClock } from 'lucide-react';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';
import { ageText, ageTone } from '@/features/workspace/lib/homeCards';

const ICONS = {
  inbox: Inbox,
  lock: Lock,
  alert: TriangleAlert,
  calendar: CalendarClock,
  receipt: Receipt,
  cap: GraduationCap,
  file: FileText,
  userPlus: UserPlus,
  clip: ClipboardCheck,
  userCheck: UserCheck,
  wallet: Wallet,
  risk: UserX,
};

const ICON_TONE = {
  danger: 'bg-danger-bg text-danger-ink',
  warning: 'bg-warning-bg text-warning-ink',
  neutral: 'bg-neutral-bg text-neutral-ink',
};

const FOOT_TONE = {
  danger: 'text-danger-ink',
  warning: 'text-warning-ink',
  neutral: 'text-ink-faint',
};

function InfoTip({ text }) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button type="button" variant="subtle" aria-label="Qué significa" className="relative z-10 h-9 w-9 p-0">
          <Info className="h-4 w-4" />
        </Button>
      </TooltipTrigger>
      <TooltipContent>{text}</TooltipContent>
    </Tooltip>
  );
}

function CardFrame({ card, tone, children, className }) {
  const Icon = ICONS[card.icon];
  return (
    <article
      aria-label={card.title}
      className={cn(
        'relative grid grid-cols-[auto_minmax(0,1fr)_auto] items-start gap-x-3 rounded-card border border-border bg-surface p-4',
        card.wide && 'sm:col-span-2',
        className,
      )}
    >
      <span className={cn('grid h-9 w-9 place-items-center rounded-control', ICON_TONE[tone])}>
        <Icon className="h-[18px] w-[18px]" />
      </span>
      {children}
    </article>
  );
}

function LoadingCard({ card }) {
  return (
    <CardFrame card={card} tone="neutral">
      <div className="col-span-2 flex flex-col gap-2" aria-busy="true">
        <Skeleton className="mt-2 h-4 w-3/4" />
        <Skeleton className="h-8 w-2/5" />
        <Skeleton className="h-3.5 w-3/5" />
      </div>
    </CardFrame>
  );
}

function ErrorCard({ card }) {
  return (
    <CardFrame card={card} tone="neutral" className="border-danger">
      <div className="col-span-2 flex flex-col gap-2">
        <h3 className="flex min-h-9 items-center font-heading text-size-15 font-semibold text-ink">{card.title}</h3>
        <div role="alert" className="flex items-center gap-2 text-size-13-5 font-semibold text-danger-ink">
          <TriangleAlert className="h-[18px] w-[18px]" />
          <span>No pudimos cargar esta tarjeta</span>
        </div>
        <div>
          <Button type="button" variant="text" onClick={card.retry}>
            <RefreshCw className="h-4 w-4" />
            Reintentar
          </Button>
        </div>
      </div>
    </CardFrame>
  );
}

function BreakdownRows({ rows }) {
  return (
    <div className="relative z-10 col-span-3 mt-3 flex flex-col">
      {rows.map((row) => (
        <Link key={row.to} to={row.to} className="flex min-h-10 items-center justify-between gap-2 border-t border-border px-1 text-size-13-5 font-medium text-ink hover:bg-neutral-bg/60">
          <span>{row.label}</span>
          <span className="flex items-center gap-1 font-semibold tabular-nums text-ink-soft">
            {row.count}
            <ChevronRight className="h-4 w-4" />
          </span>
        </Link>
      ))}
    </div>
  );
}

export function HomeTaskCard({ card }) {
  if (card.state === 'loading') return <LoadingCard card={card} />;
  if (card.state === 'error') return <ErrorCard card={card} />;
  const tone = card.tone ?? ageTone(card.ageDays);
  const foot = card.foot ?? ageText(card.ageDays);
  return (
    <CardFrame card={card} tone={tone} className="transition-colors focus-within:border-primary hover:border-primary">
      <div className="flex min-w-0 flex-col gap-2">
        <h3 className="flex min-h-9 items-center font-heading text-size-15 font-semibold leading-tight text-ink">
          <Link to={card.to} className="after:absolute after:inset-0 after:rounded-card focus-visible:outline-none">
            {card.title}
          </Link>
        </h3>
        <div className="flex min-h-8 items-baseline gap-1.5">
          <span className="font-heading text-3xl font-semibold leading-none tabular-nums text-ink">{card.count}</span>
          <span className="text-size-13 text-ink-faint">{card.unit}</span>
        </div>
        {foot && (
          <div className={cn('flex items-center gap-1.5 text-size-12-5 font-semibold', FOOT_TONE[tone])}>
            <span className="h-1.5 w-1.5 rounded-full bg-current" />
            {foot}
          </div>
        )}
      </div>
      <div className="col-start-3 flex h-9 items-center">
        <InfoTip text={card.tip} />
      </div>
      {card.rows && <BreakdownRows rows={card.rows} />}
    </CardFrame>
  );
}

export function ComingSoonCard({ title, tip }) {
  return (
    <CardFrame card={{ icon: 'risk' }} tone="neutral" className="border-dashed bg-transparent">
      <div className="flex min-w-0 flex-col gap-2">
        <h3 className="flex min-h-9 items-center font-heading text-size-15 font-semibold text-ink">{title}</h3>
        <div className="flex min-h-8 items-baseline text-sm text-ink-faint">Próximamente</div>
      </div>
      <div className="col-start-3 flex h-9 items-center">
        <InfoTip text={tip} />
      </div>
    </CardFrame>
  );
}
