import { Link } from 'react-router-dom';
import { ClipboardCheck } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { formatPeriod } from './monthlyRunLabels';
import { useStaffBasePath } from './useStaffBasePath';

export function RunReviewBanner({ period, run }) {
  const base = useStaffBasePath();
  if (run?.status !== 'AWAITING_REVIEW') return null;
  return (
    <div role="status" className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-warning/40 bg-warning-bg p-4 text-sm text-ink">
      <div className="flex items-start gap-3">
        <ClipboardCheck className="mt-0.5 size-5 shrink-0 text-warning" aria-hidden="true" />
        <div className="flex flex-col gap-0.5">
          <span className="font-semibold">Los borradores de {formatPeriod(period)} esperan revisión</span>
          <span className="text-ink-soft">Los alumnos no los ven hasta que se aprueben.</span>
        </div>
      </div>
      <Button asChild variant="outline">
        <Link to={`${base}/emision-mensual/${run.id}/borradores`}>Revisar borradores</Link>
      </Button>
    </div>
  );
}
