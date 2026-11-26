import { Button } from '@/components/ui/button';
import { GlassCard } from '@/components/GlassCard';
import { reviewFailureMessage } from '@/features/graduation/certificates/lib/importedReview';

export function ImportedReviewBatchResult({ response, decision, rows, onDismiss }) {
  const failures = response.results.filter((item) => item.outcome === 'FAILED');
  const verb = decision === 'ENABLE' ? 'Habilitados' : 'Deshabilitados';
  return (
    <GlassCard className="flex flex-col gap-3 p-4" role="status">
      <div className="flex items-center justify-between gap-3">
        <h2 className="font-heading text-size-16 font-semibold text-ink">Resultado de la revisión</h2>
        <Button type="button" variant="ghost" size="sm" onClick={onDismiss}>
          Cerrar
        </Button>
      </div>
      <p className="text-size-14 text-ink">
        {verb} <span className="font-semibold tabular-nums">{response.decided}</span> de{' '}
        <span className="tabular-nums">{response.requested}</span>
      </p>
      {failures.length > 0 && (
        <ul className="flex flex-col gap-2 text-size-13 text-ink">
          {failures.map((item) => {
            const row = rows.find((candidate) => candidate.graduationRequestId === item.graduationRequestId);
            return (
              <li key={item.graduationRequestId}>
                <span className="font-semibold">{row?.studentFullName ?? 'Alumno'}:</span> {reviewFailureMessage(item.errorCode)}
              </li>
            );
          })}
        </ul>
      )}
    </GlassCard>
  );
}
