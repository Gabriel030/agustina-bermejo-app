import { cn } from '@/lib/utils';

export function Stepper({ steps, currentStep, pasos, pasoActual, pasosVisitados, onStepClick }) {
  const pasosNorm = pasos ?? steps?.map((label, i) => ({ key: String(i), label })) ?? [];
  const pasoActualNorm = pasoActual ?? (currentStep != null ? String(currentStep) : '0');
  const indiceActual = pasosNorm.findIndex((p) => p.key === pasoActualNorm);

  return (
    <div className="flex items-center">
      {pasosNorm.map((paso, i) => {
        const completado = i < indiceActual;
        const activo = i === indiceActual;
        const navegable = onStepClick && pasosVisitados?.includes(paso.key) && !activo;

        return (
          <div key={paso.key} className={cn('flex items-center', i < pasosNorm.length - 1 && 'flex-1')}>
            <button
              type="button"
              disabled={!navegable}
              onClick={() => navegable && onStepClick(paso.key)}
              className={cn('flex flex-col items-center gap-1.5', navegable ? 'cursor-pointer' : 'cursor-default')}
            >
              <div
                aria-hidden="true"
                className={cn(
                  'flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[13px] font-bold transition-all duration-300 ease-[cubic-bezier(0.22,1,0.36,1)]',
                  (completado || activo) && 'bg-gradient-to-br from-brand to-accent-2 text-white',
                  activo && 'ring-4 ring-accent-soft',
                  !completado && !activo && 'bg-neutral-bg text-ink-faint',
                )}
              >
                {completado ? '✓' : i + 1}
              </div>
              <span
                className={cn(
                  'whitespace-nowrap text-[11px] font-semibold',
                  activo || completado ? 'text-ink' : 'text-ink-faint',
                )}
              >
                {paso.label}
              </span>
            </button>
            {i < pasosNorm.length - 1 && (
              <div className="mx-2 h-[2px] flex-1 -translate-y-2.5 overflow-hidden rounded-full bg-neutral-bg">
                <div
                  className={cn(
                    'h-full bg-gradient-to-r from-brand to-accent-2 transition-all duration-500 ease-[cubic-bezier(0.22,1,0.36,1)]',
                    completado ? 'w-full' : 'w-0',
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
