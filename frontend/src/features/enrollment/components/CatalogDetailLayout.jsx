import { useId } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, EyeOff } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Button } from '@/components/ui/button';

export function CatalogEnrollAction({ onEnroll, disabled, reason, reasonTone = 'muted', onRetry }) {
  const reasonId = useId();
  const toneClass = reasonTone === 'warning' ? 'text-warning' : 'text-ink-faint';
  return (
    <div className="flex w-full flex-col items-end gap-1.5 md:w-auto">
      <Button
        onClick={onEnroll}
        disabled={disabled}
        aria-describedby={reason ? reasonId : undefined}
        variant="gradient"
        className="w-full px-6 py-5 text-size-13-5 md:w-auto"
      >
        Inscribirme
      </Button>
      {reason && (
        <p id={reasonId} className={`text-xs font-medium ${toneClass}`}>
          {reason}
        </p>
      )}
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry}>
          Reintentar
        </Button>
      )}
    </div>
  );
}

export function CatalogViewerNotice({ text, to, linkLabel }) {
  return (
    <div className="flex w-full flex-col items-start gap-1.5 md:w-auto md:items-end">
      <p className="text-sm font-semibold text-ink">{text}</p>
      {to && (
        <Button asChild variant="outline" size="sm">
          <Link to={to}>{linkLabel}</Link>
        </Button>
      )}
    </div>
  );
}

export function CatalogViewerAction({ viewer, previewText, notices, enrollAction }) {
  if (viewer.loading) return <Skeleton className="h-11 w-full md:w-40" />;
  const notice = viewer.state === 'PREVIEW' ? { text: previewText } : notices[viewer.state];
  if (notice) return <CatalogViewerNotice text={notice.text} to={notice.to} linkLabel={notice.linkLabel} />;
  return enrollAction;
}

export function CatalogDetailLayout({ backTo, title, summary, action, hiddenNotice, children }) {
  return (
    <PageContainer>
      <Link to={backTo} className="flex w-fit items-center gap-1.5 text-size-13 font-semibold text-ink-soft hover:text-ink">
        <ArrowLeft className="size-3.5" aria-hidden="true" />
        Catálogo
      </Link>

      {hiddenNotice && (
        <div role="status" className="flex items-center gap-2 rounded-xl border border-warning/40 bg-warning/10 px-4 py-3 text-sm font-semibold text-ink">
          <EyeOff className="size-4 shrink-0 text-warning" aria-hidden="true" />
          {hiddenNotice}
        </div>
      )}

      <div className="aspect-video w-full rounded-xl bg-gradient-to-br from-brand/20 to-accent-2/20 flex items-center justify-center">
        <span className="text-sm font-semibold text-ink-faint">Video introductorio próximamente</span>
      </div>

      <div className="flex flex-col items-start justify-between gap-4 md:flex-row md:items-center">
        <div>
          <h1 className="font-heading text-2xl font-bold tracking-tight text-ink">{title}</h1>
          <p className="mt-1 text-sm text-ink-faint">{summary}</p>
        </div>
        {action}
      </div>

      {children}
    </PageContainer>
  );
}

export function CatalogDetailLoading() {
  return (
    <PageContainer>
      <Skeleton className="h-9 w-72" />
      <Skeleton className="h-48" />
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Skeleton className="h-24" />
        <Skeleton className="h-24" />
        <Skeleton className="h-24" />
      </div>
    </PageContainer>
  );
}

export function CatalogDetailLoadError({ error, onRetry, resource, backTo }) {
  return (
    <PageContainer>
      <LoadErrorState error={error} onRetry={onRetry} resource={resource} backTo={backTo} backLabel="el catálogo" />
    </PageContainer>
  );
}
