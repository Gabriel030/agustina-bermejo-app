import { AlertTriangle, Check, Undo2 } from 'lucide-react';
import { StickyActionBar } from '@/components/StickyActionBar';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

function barSummary({ hasPendingChanges, changedCount, saved, canceled }) {
  if (hasPendingChanges) {
    const label = changedCount === 0 ? 'Cambios sin guardar' : `${changedCount} ${changedCount === 1 ? 'cambio' : 'cambios'} sin guardar`;
    return (
      <StatusPill tone="accent">
        <AlertTriangle className="size-3.5" aria-hidden="true" />
        {label}
      </StatusPill>
    );
  }
  if (saved || canceled) {
    return (
      <StatusPill tone="success">
        <Check className="size-3.5" aria-hidden="true" />
        Todo guardado
      </StatusPill>
    );
  }
  return <span className="text-size-14 font-semibold text-ink-faint">Todavía no marcaste asistencia</span>;
}

function barHint({ canceled, hasError, hasPendingChanges, unmarkedCount }) {
  if (canceled) return 'La clase está cancelada: no se guarda asistencia.';
  if (hasError) return 'No se guardó: tus cambios siguen en este dispositivo.';
  if (hasPendingChanges) return unmarkedCount > 0 ? `Quedan ${unmarkedCount} sin marcar: se pueden guardar igual.` : 'Todos los alumnos están marcados.';
  return unmarkedCount > 0 ? `Quedan ${unmarkedCount} sin marcar.` : 'Marcá o corregí un alumno para habilitar Guardar.';
}

export function AttendanceSaveBar({ hasPendingChanges, changedCount, saved, canceled, hasError, unmarkedCount, saving, onSave, onDiscard }) {
  return (
    <StickyActionBar
      summary={barSummary({ hasPendingChanges, changedCount, saved, canceled })}
      hint={<span className={cn(hasError && 'font-bold text-danger-ink')}>{barHint({ canceled, hasError, hasPendingChanges, unmarkedCount })}</span>}
    >
      <Button type="button" variant="outline" className="min-h-11" onClick={onDiscard} disabled={!hasPendingChanges || saving}>
        <Undo2 aria-hidden="true" />
        Descartar
      </Button>
      <Button type="button" variant="primary" className="min-h-11" onClick={onSave} disabled={!hasPendingChanges || saving || canceled}>
        Guardar asistencia
      </Button>
    </StickyActionBar>
  );
}
