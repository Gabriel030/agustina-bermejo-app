import { Button } from '@/components/ui/button';
import { GlassCard } from '@/components/GlassCard';
import { eligibleKey, batchFailureMessage } from '@/features/graduation/certificates/lib/staffGraduationCertificates';

export function EligibleBatchResult({ response, rows, onDismiss }) {
  const failures = response.results.filter((item) => item.outcome === 'FAILED');
  return (
    <GlassCard className="flex flex-col gap-3 p-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="font-heading text-size-16 font-semibold text-ink">Resultado de la emisión</h2>
        <Button type="button" variant="ghost" size="sm" onClick={onDismiss}>
          Cerrar
        </Button>
      </div>
      <p className="text-size-14 text-ink">
        Emitidos <span className="font-semibold tabular-nums">{response.issued}</span> de{' '}
        <span className="tabular-nums">{response.requested}</span>
      </p>
      {failures.length > 0 && (
        <ul className="flex flex-col gap-2 text-size-13 text-ink">
          {failures.map((item) => {
            const row = rows.find((candidate) => eligibleKey(candidate) === eligibleKey(item));
            return (
              <li key={eligibleKey(item)}>
                <span className="font-semibold">{row?.studentFullName ?? 'Alumno'}:</span> {batchFailureMessage(item.errorCode)}
              </li>
            );
          })}
        </ul>
      )}
    </GlassCard>
  );
}
