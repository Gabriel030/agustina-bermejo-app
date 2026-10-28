import { ChevronRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { formatDate } from '@/lib/billing';
import { canWithdrawRequest, detailPath, displayStatus, dueInfo, requesterRoleLabel } from './authorizationRequests';

export function SubjectCell({ request, roleBase }) {
  const primary = request.studentName ?? request.summary;
  return (
    <div className="flex min-w-0 flex-col">
      <Link to={detailPath(roleBase, request.id)} className="font-semibold text-ink hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
        {primary}
      </Link>
      {request.studentName && <span className="text-xs text-ink-faint">{request.summary}</span>}
    </div>
  );
}

export function TypeCell({ request }) {
  return (
    <div className="flex flex-col">
      <span className="font-semibold text-ink">{request.operationLabel}</span>
      <span className="text-xs text-ink-faint">{request.familyLabel}</span>
    </div>
  );
}

export function RequesterCell({ request }) {
  return (
    <div className="flex flex-col">
      <span className="font-semibold text-ink">{request.requestedBy?.name ?? '—'}</span>
      <span className="text-xs text-ink-faint">{requesterRoleLabel(request.requestedBy?.roles)}</span>
    </div>
  );
}

export function DueCell({ request }) {
  const due = dueInfo(request.expiresOn);
  if (due.key === 'none') return <span className="text-ink-faint">{due.label}</span>;
  return <StatusPill tone={due.tone}>{due.label}</StatusPill>;
}

export function StatusCell({ request }) {
  const status = displayStatus(request);
  return (
    <div className="flex flex-col items-start gap-1">
      <StatusPill tone={status.tone}>{status.label}</StatusPill>
      {request.decidedAt && <span className="text-xs text-ink-faint">{formatDate(request.decidedAt)}</span>}
    </div>
  );
}

export function ActionsCell({ request, user, onWithdraw }) {
  return (
    <div className="flex items-center justify-end gap-1">
      {canWithdrawRequest(request, user) && (
        <Button type="button" variant="ghost" size="sm" onClick={() => onWithdraw(request)}>
          Retirar
        </Button>
      )}
      <ChevronRight className="size-4 text-ink-faint" aria-hidden="true" />
    </div>
  );
}
