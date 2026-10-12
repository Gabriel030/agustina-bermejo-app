import { Check, X, Lock, Clock } from 'lucide-react';
import { cn } from '@/lib/utils';

export function Stepper({
  pasos,
  pasoActual,
  pasosVisitados,
  onStepClick,
  stepValidity,
  approvalStepKey,
  approvalStepStatus,
}) {
  const indiceActual = pasos.findIndex((p) => p.key === pasoActual);

  function resolveState(paso, i) {
    if (i === indiceActual) return 'active';

    if (paso.key === approvalStepKey && approvalStepStatus) {
      if (approvalStepStatus === 'pending') return 'approvalPending';
      if (approvalStepStatus === 'approved') return 'valid';
      if (approvalStepStatus === 'rejected') return 'invalid';
      return 'locked';
    }

    if (stepValidity) {
      if (pasosVisitados?.includes(paso.key)) {
        const v = stepValidity[paso.key];
        if (v === true) return 'valid';
        if (v === false) return 'invalid';
        if (v === null) return 'visitedNeutral';
      }
      return 'future';
    }

    return i < indiceActual ? 'valid' : 'future';
  }

  function connectorFilled(paso, i) {
    if (stepValidity) return stepValidity[paso.key] === true;
    return i < indiceActual;
  }

  return (
    <div className="flex items-center">
      {pasos.map((paso, i) => {
        const state = resolveState(paso, i);
        const navegable = onStepClick && pasosVisitados?.includes(paso.key) && i !== indiceActual;
        return (
          <div key={paso.key} className={cn('flex items-center', i < pasos.length - 1 && 'flex-1')}>
            <button
              type="button"
              disabled={!navegable}
              aria-current={i === indiceActual ? 'step' : undefined}
              onClick={() => navegable && onStepClick(paso.key)}
              className={cn('flex flex-col items-center gap-1.5', navegable ? 'cursor-pointer' : 'cursor-default')}
            >
              <div
                aria-hidden="true"
                className={cn(
                  'flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[13px] font-bold transition-all duration-300 ease-[cubic-bezier(0.22,1,0.36,1)]',
                  state === 'valid' && 'bg-success text-white',
                  state === 'active' && 'bg-gradient-to-br from-brand to-accent-2 text-white animate-[pulse-ring_1.8s_ease-in-out_infinite]',
                  state === 'invalid' && 'bg-danger text-white',
                  state === 'visitedNeutral' && 'bg-neutral-bg text-ink',
                  (state === 'future' || state === 'locked') && 'bg-neutral-bg text-ink-faint',
                  state === 'approvalPending' && 'bg-warning text-white animate-[pulse-ring_1.8s_ease-in-out_infinite]',
                )}
              >
                {state === 'valid' && <Check className="h-4 w-4 animate-[pop-in_0.3s_cubic-bezier(0.22,1,0.36,1)]" />}
                {state === 'invalid' && <X className="h-4 w-4 animate-[pop-in_0.3s_cubic-bezier(0.22,1,0.36,1)]" />}
                {state === 'locked' && <Lock className="h-3.5 w-3.5" />}
                {state === 'approvalPending' && <Clock className="h-4 w-4" />}
                {!['valid', 'invalid', 'locked', 'approvalPending'].includes(state) && i + 1}
              </div>
              <span
                className={cn(
                  'text-center text-[11px] font-semibold sm:whitespace-nowrap',
                  state === 'future' || state === 'locked' ? 'text-ink-faint' : 'text-ink',
                )}
              >
                {paso.label}
              </span>
            </button>
            {i < pasos.length - 1 && (
              <div className="mx-2 h-[2px] flex-1 -translate-y-2.5 overflow-hidden rounded-full bg-neutral-bg">
                <div
                  className={cn(
                    'h-full bg-success transition-all duration-500 ease-[cubic-bezier(0.22,1,0.36,1)]',
                    connectorFilled(paso, i) ? 'w-full' : 'w-0',
                  )}
                />
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
