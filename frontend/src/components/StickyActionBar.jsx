import { Children, Fragment, isValidElement, useId } from 'react';
import { Ellipsis } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { cn } from '@/lib/utils';

const COMPACT_VIEWPORT_QUERY = '(width < 48rem)';

const BAR_CLASSES =
  'sticky bottom-[calc(14px+env(safe-area-inset-bottom,0px))] z-5 flex flex-wrap items-center gap-3.5 px-4 py-3.5 ' +
  'max-md:-bottom-5 sm:max-md:-bottom-6 max-md:-mx-4 max-md:flex-nowrap max-md:gap-1.5 max-md:rounded-none max-md:border-x-0 max-md:border-b-0 max-md:px-3 max-md:pt-2 ' +
  'max-md:pb-[calc(8px+env(safe-area-inset-bottom,0px))] max-md:shadow-none sm:max-md:-mx-8';

const ROW_BAR_CLASSES = 'max-md:flex-col max-md:items-stretch';
const MENU_BAR_CLASSES = 'max-md:flex-row max-md:items-center max-md:gap-2 max-md:pt-1.5 max-md:pb-[calc(6px+env(safe-area-inset-bottom,0px))]';

const MAX_ROW_ACTIONS = 2;

const ACTIONS_BASE_CLASSES = 'flex w-full gap-2 md:w-auto md:flex-row md:items-center';
const ACTIONS_ROW_CLASSES =
  'flex-row flex-wrap md:flex-nowrap max-md:[&>*]:min-w-fit max-md:[&>*]:flex-1 max-md:[&>[data-variant=primary]]:flex-[1.7]';
const ACTIONS_STACKED_CLASSES = 'flex-col-reverse';

function countActions(children) {
  return Children.toArray(children).reduce(
    (total, child) => total + (isValidElement(child) && child.type === Fragment ? countActions(child.props.children) : 1),
    0,
  );
}

function isDisabled(action) {
  return Boolean(action.disabled) || action['aria-disabled'] === true || action['aria-disabled'] === 'true';
}

function SecondaryButton({ action }) {
  const { label, icon: Icon, onSelect, variant = 'outline', className, disabled, 'aria-disabled': ariaDisabled } = action;
  return (
    <Button type="button" variant={variant} className={cn('min-h-11', className)} onClick={onSelect} disabled={disabled} aria-disabled={ariaDisabled}>
      {Icon && <Icon aria-hidden="true" />}
      {label}
    </Button>
  );
}

function PrimaryButton({ action, describedBy }) {
  const { label, loading, loadingLabel = 'Guardando…', className, ...rest } = action;
  const disabled = isDisabled(action);
  return (
    <Button
      type="button"
      variant="primary"
      className={cn('min-h-11', className)}
      loading={loading}
      aria-describedby={disabled && describedBy ? describedBy : undefined}
      {...rest}
    >
      {loading ? loadingLabel : label}
    </Button>
  );
}

function MoreMenu({ actions, label }) {
  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild>
        <Button type="button" variant="outline" size="icon" className="size-11 shrink-0" aria-label={label}>
          <Ellipsis aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" side="top" className="w-auto min-w-52">
        {actions.map((action) => (
          <DropdownMenuItem key={action.key ?? action.label} disabled={isDisabled(action)} variant={action.destructive ? 'destructive' : 'default'} onSelect={action.onSelect}>
            {action.icon && <action.icon aria-hidden="true" />}
            {action.label}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function MenuLayout({ summary, hint, hintId, compactSummary, compactHint, primary, secondary, moreLabel }) {
  const hasShortText = compactSummary !== undefined || compactHint !== undefined;
  return (
    <>
      <div className="min-w-0 flex-1">
        {hasShortText ? (
          <>
            <div aria-hidden="true" className="min-w-0">
              <b className="block truncate text-base leading-5 tabular-nums text-ink">{compactSummary ?? summary}</b>
              <small className="block truncate text-xs leading-4 text-ink-faint">{compactHint ?? hint}</small>
            </div>
            <div className="sr-only">
              <b>{summary}</b>
              <small id={hintId}>{hint}</small>
            </div>
          </>
        ) : (
          <>
            <b className="block truncate text-base leading-5 tabular-nums text-ink">{summary}</b>
            {hint && (
              <small id={hintId} className="block truncate text-xs leading-4 text-ink-faint">
                {hint}
              </small>
            )}
          </>
        )}
      </div>
      {secondary.length > 0 && <MoreMenu actions={secondary} label={moreLabel} />}
      <PrimaryButton action={primary} describedBy={hint ? hintId : undefined} />
    </>
  );
}

export function StickyActionBar({
  summary,
  hint,
  children,
  className,
  compact = 'row',
  primary,
  secondary = [],
  compactSummary,
  compactHint,
  moreLabel = 'Más acciones',
  busy = false,
  busyLabel = 'Guardando…',
  role = 'region',
  'aria-label': ariaLabel = 'Acciones de la página',
  ...props
}) {
  const hintId = useId();
  const isCompactViewport = useMediaQuery(COMPACT_VIEWPORT_QUERY);
  const menuLayout = compact === 'menu' && isCompactViewport;
  const rowActions = compact === 'menu' || countActions(children) <= MAX_ROW_ACTIONS;
  const isBusy = busy || Boolean(primary?.loading);

  const desktopActions =
    compact === 'menu' ? (
      <>
        {secondary.map((action) => (
          <SecondaryButton key={action.key ?? action.label} action={action} />
        ))}
        <PrimaryButton action={primary} describedBy={hint ? hintId : undefined} />
      </>
    ) : (
      children
    );

  return (
    <GlassCard
      role={role}
      aria-label={ariaLabel}
      aria-busy={isBusy || undefined}
      className={cn(BAR_CLASSES, menuLayout ? MENU_BAR_CLASSES : ROW_BAR_CLASSES, className)}
      {...props}
    >
      <span className="sr-only" aria-live="polite">
        {isBusy ? busyLabel : ''}
      </span>
      {menuLayout ? (
        <MenuLayout
          summary={summary}
          hint={hint}
          hintId={hintId}
          compactSummary={compactSummary}
          compactHint={compactHint}
          primary={primary}
          secondary={secondary}
          moreLabel={moreLabel}
        />
      ) : (
        <>
          <div className="min-w-44 flex-1 max-md:flex max-md:min-w-0 max-md:flex-wrap max-md:items-center max-md:gap-x-2">
            <b className="text-lg tabular-nums text-ink max-md:text-sm">{summary}</b>
            {hint && (
              <small id={hintId} className="block text-xs text-ink-faint max-md:inline">
                {hint}
              </small>
            )}
          </div>
          <div className={cn(ACTIONS_BASE_CLASSES, rowActions ? ACTIONS_ROW_CLASSES : ACTIONS_STACKED_CLASSES)}>{desktopActions}</div>
        </>
      )}
    </GlassCard>
  );
}
