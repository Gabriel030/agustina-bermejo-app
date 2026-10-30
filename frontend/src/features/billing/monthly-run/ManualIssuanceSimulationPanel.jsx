import { useState } from 'react';
import { Check, FlaskConical, RefreshCw, Send, TriangleAlert, X } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { formatDate, formatMoney, todayIsoDate } from '@/lib/billing';
import { useNightlyRuns } from '../hooks/useNightlyRuns';
import { ManualIssuanceConfirmDialog } from './ManualIssuanceConfirmDialog';
import {
  ENROLLMENT_TYPE_LABELS,
  SIMULATION_LINE_LABELS,
  SIMULATION_LINE_TONES,
  dueDateText,
  formatPeriod,
  isNightlyCompletedOn,
  isSimulationValid,
  summarizeSimulation,
} from './monthlyRunLabels';

function Kpi({ label, children }) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5 rounded-xl bg-neutral-bg px-4 py-3">
      <span className="text-xs text-ink-faint">{label}</span>
      <span className="text-xl font-semibold tabular-nums text-ink">{children}</span>
    </div>
  );
}

function Preconditions({ checks }) {
  return (
    <ul className="flex flex-col gap-1.5 text-sm" aria-label="Precondiciones de la emisión">
      {checks.map(({ key, ok, text }) => (
        <li key={key} data-testid={`precondition-${key}`} data-ok={ok} className={`flex items-center gap-2 ${ok ? 'text-ink-soft' : 'text-warning'}`}>
          {ok ? <Check className="size-4 shrink-0 text-success" aria-hidden="true" /> : <X className="size-4 shrink-0 text-warning" aria-hidden="true" />}
          <span>{text}</span>
        </li>
      ))}
    </ul>
  );
}

function SimulationLines({ lines }) {
  return (
    <details className="rounded-xl border border-border-soft px-4 py-2 text-sm">
      <summary className="cursor-pointer font-medium text-ink">Ver detalle por inscripción</summary>
      <ul className="mt-2 flex max-h-72 flex-col divide-y divide-border-soft overflow-y-auto">
        {lines.map((line) => (
          <li key={line.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 py-2">
            <span className="min-w-0 flex-1 text-ink">
              {ENROLLMENT_TYPE_LABELS[line.enrollmentType] ?? line.enrollmentType} · Inscripción #{line.enrollmentId}
              {line.message && <span className="block text-xs text-ink-faint">{line.message}</span>}
            </span>
            <StatusPill tone={SIMULATION_LINE_TONES[line.result] ?? 'neutral'}>{SIMULATION_LINE_LABELS[line.result] ?? line.result}</StatusPill>
            <span className="tabular-nums text-ink-soft">{line.result === 'ISSUED' ? formatMoney(line.total) : '—'}</span>
            <span className="text-xs text-ink-faint">{line.result === 'ISSUED' ? (line.dueDate ? `Vence ${formatDate(line.dueDate)}` : 'Sin vencimiento') : ''}</span>
          </li>
        ))}
      </ul>
    </details>
  );
}

export function ManualIssuanceSimulationPanel({ period, run, isCurrentPeriod, isDirector, simulation, onRerun, onClose, onIssued }) {
  const [confirming, setConfirming] = useState(false);
  const nightly = useNightlyRuns();
  const today = todayIsoDate();
  const result = simulation.data;
  const summary = summarizeSimulation(result);
  const simulationValid = isSimulationValid(result, period, today);
  const nightlyDone = nightly.isSuccess && isNightlyCompletedOn(nightly.data, today);
  const checks = [
    { key: 'current-period', ok: isCurrentPeriod, text: isCurrentPeriod ? 'Es el mes en curso' : 'Solo se puede emitir el mes en curso' },
    { key: 'nightly', ok: nightlyDone, text: nightlyDone ? 'Corrida nocturna de hoy finalizada' : nightly.isError ? 'No pudimos verificar la corrida nocturna' : 'Falta que termine la corrida nocturna de hoy' },
    { key: 'no-run', ok: !run, text: run ? 'Ya existe corrida del período' : 'Sin corrida previa del período' },
    { key: 'simulation', ok: simulationValid, text: simulationValid ? 'Simulación de hoy lista para emitir' : 'Falta una simulación válida de hoy' },
  ];
  const canIssue = isDirector && checks.every((check) => check.ok);

  return (
    <GlassCard className="flex flex-col gap-4 p-4" aria-label="Simulación de la emisión" data-testid="simulation-panel">
      <div className="flex flex-wrap items-center gap-2">
        <FlaskConical className="size-5 text-brand" aria-hidden="true" />
        <h2 className="font-heading text-base font-semibold text-ink">Simulación de {formatPeriod(period)}</h2>
        {simulationValid && <StatusPill tone="success">Válida hoy</StatusPill>}
        <div className="ml-auto">
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>
            Cerrar
          </Button>
        </div>
      </div>

      {simulation.isPending && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4" data-testid="simulation-loading">
          {[0, 1, 2, 3].map((index) => (
            <Skeleton key={index} className="h-16 w-full rounded-xl" />
          ))}
        </div>
      )}

      {simulation.isError && (
        <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger">
          <span>No pudimos simular la emisión.</span>
          <Button type="button" variant="outline" size="sm" onClick={onRerun}>
            Reintentar
          </Button>
        </div>
      )}

      {result && (
        <>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Kpi label="A emitir">{summary.issuableCount}</Kpi>
            <Kpi label="Cuotas">{summary.installmentCount}</Kpi>
            <Kpi label="Total">{formatMoney(summary.total)}</Kpi>
            <Kpi label="Vencen">{dueDateText(summary)}</Kpi>
          </div>
          {summary.failingCount > 0 && (
            <div role="alert" className="flex items-start gap-3 rounded-2xl border border-warning/40 bg-warning-bg p-3 text-sm text-ink">
              <TriangleAlert className="mt-0.5 size-4 shrink-0 text-warning" aria-hidden="true" />
              <span>{summary.failingCount === 1 ? '1 inscripción fallaría y no se emitiría.' : `${summary.failingCount} inscripciones fallarían y no se emitirían.`}</span>
            </div>
          )}
          {result.alerts.map((alert) => (
            <div key={alert.id} className="flex items-start gap-3 rounded-2xl border border-warning/40 bg-warning-bg p-3 text-sm text-ink">
              <TriangleAlert className="mt-0.5 size-4 shrink-0 text-warning" aria-hidden="true" />
              <span>{alert.message}</span>
            </div>
          ))}
          <Preconditions checks={checks} />
          {result.lines.length > 0 && <SimulationLines lines={result.lines} />}
        </>
      )}

      {!result && !simulation.isPending && !simulation.isError && <Preconditions checks={checks} />}

      <div className="flex flex-wrap items-center justify-end gap-3">
        {!isDirector && <span className="mr-auto text-sm text-ink-soft">La emisión la hace dirección.</span>}
        <Button type="button" variant="ghost" onClick={onRerun} disabled={simulation.isPending}>
          <RefreshCw aria-hidden="true" />
          Volver a simular
        </Button>
        {isDirector && (
          <Button type="button" disabled={!canIssue} onClick={() => setConfirming(true)}>
            <Send aria-hidden="true" />
            Emitir {summary.installmentCount} cuotas
          </Button>
        )}
      </div>

      {confirming && (
        <ManualIssuanceConfirmDialog
          period={period}
          simulationRunId={result.runId}
          summary={summary}
          onClose={() => setConfirming(false)}
          onIssued={() => {
            setConfirming(false);
            onIssued();
          }}
        />
      )}
    </GlassCard>
  );
}
