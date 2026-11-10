import { useState } from 'react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import {
  MINIMUM_MODULES,
  buildModules,
  selectionRange,
  toggleModule,
} from '@/features/academic-structure/lib/classroomModules';

export function ClassroomModuleSelector({ shift, selection, onChange }) {
  const [hint, setHint] = useState(null);
  const modules = buildModules(shift);
  const range = selectionRange(modules, selection);

  function handleToggle(index) {
    const result = toggleModule(selection, index);
    setHint(result.hint);
    if (result.selection !== selection) onChange(result.selection);
  }

  function handleClear() {
    setHint(null);
    onChange([]);
  }

  return (
    <div className="flex flex-col gap-2">
      <div role="group" aria-label="Módulos del turno" className="grid grid-cols-2 gap-1.5 sm:grid-cols-3 lg:grid-cols-6">
        {modules.map((module) => {
          const selected = selection.includes(module.index);
          return (
            <button
              key={module.index}
              type="button"
              aria-pressed={selected}
              aria-label={`Módulo ${module.index + 1}, ${module.startTime} a ${module.endTime}`}
              onClick={() => handleToggle(module.index)}
              className={cn(
                'flex min-h-11 flex-col items-center justify-center rounded-lg border px-2 py-1 text-[12px] transition-colors',
                selected
                  ? 'border-accent bg-accent text-on-accent'
                  : 'border-surface-border bg-surface/50 text-ink hover:border-accent',
              )}
            >
              <span className="font-semibold">Módulo {module.index + 1}</span>
              <span className="opacity-80">
                {module.startTime}–{module.endTime}
              </span>
              {module.recessAfter && <span className="text-[10px] opacity-60">recreo después</span>}
            </button>
          );
        })}
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2 text-[12px] text-ink-faint">
        <span aria-live="polite">
          {range
            ? `${range.startTime} a ${range.endTime} · ${selection.length} de ${MINIMUM_MODULES} módulos mínimos`
            : 'Tocá un módulo para empezar. Tocá de nuevo un módulo elegido para quitarlo.'}
        </span>
        {selection.length > 0 && (
          <Button type="button" variant="ghost" size="sm" onClick={handleClear}>
            Limpiar selección
          </Button>
        )}
      </div>
      {hint && (
        <p role="status" className="text-[12px] text-warning">
          {hint}
        </p>
      )}
    </div>
  );
}
