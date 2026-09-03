import { cn } from '@/lib/utils';

// Barra de progreso horizontal del wizard de inscripción — feedback explícito
// del usuario: "una barra de seguimiento de status del flow" en la parte
// superior de la página, avanzando a medida que se completan los pasos.
// Clickeable hacia cualquier paso ya visitado, sea antes o después del actual
// (`pasosVisitados`) — pedido explícito de poder "ir para atrás y para
// adelante" sin perder lo ya cargado, que vive en el estado del wizard (no se
// resetea al cambiar de paso). Un paso nunca visitado no es clickeable — evita
// saltar validaciones y mandar una solicitud a medio cargar.
// `pasos`: [{ key, label }]. `pasoActual`: key del paso activo.
export function Stepper({ pasos, pasoActual, pasosVisitados, onStepClick }) {
  const indiceActual = pasos.findIndex((p) => p.key === pasoActual);

  return (
    <div className="flex items-center">
      {pasos.map((paso, i) => {
        const completado = i < indiceActual;
        const activo = i === indiceActual;
        const navegable = onStepClick && pasosVisitados?.includes(paso.key) && !activo;
        return (
          <div key={paso.key} className={cn('flex items-center', i < pasos.length - 1 && 'flex-1')}>
            <button
              type="button"
              disabled={!navegable}
              onClick={() => navegable && onStepClick(paso.key)}
              className={cn('flex flex-col items-center gap-1.5', navegable ? 'cursor-pointer' : 'cursor-default')}
            >
              {/* aria-hidden: el número/check es redundante con la posición del paso —
                  sin esto se cuela en el nombre accesible del botón ("1 Cohorte" en vez
                  de "Cohorte"), rompiendo cualquier getByRole(..., { name: paso.label }). */}
              <div
                aria-hidden="true"
                className={cn(
                  'flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[13px] font-bold transition-all duration-300 ease-[cubic-bezier(0.22,1,0.36,1)]',
                  completado && 'bg-gradient-to-br from-brand to-accent-2 text-white',
                  activo && 'bg-gradient-to-br from-brand to-accent-2 text-white ring-4 ring-accent-soft',
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
            {i < pasos.length - 1 && (
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
