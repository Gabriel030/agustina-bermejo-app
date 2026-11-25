import { AlertTriangle, Check, Undo2 } from 'lucide-react';
import { StickyActionBar } from '@/components/StickyActionBar';
import { StatusPill } from '@/components/StatusPill';
import { cn } from '@/lib/utils';

function barSummary({ hasPendingChanges, changedCount, saved, canceled }) {
  if (hasPendingChanges) {
    const label = changedCount === 0 ? 'Cambios sin guardar' : `${changedCount} ${changedCount === 1 ? 'cambio' : 'cambios'} sin guardar`;
    return (
      <StatusPill tone="accent" icon={AlertTriangle}>
        {label}
      </StatusPill>
    );
  }
  if (saved || canceled) {
    return (
      <StatusPill tone="success" icon={Check}>
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

function compactSummaryOf({ hasPendingChanges, changedCount, saved, canceled, hasError }) {
  if (hasError) return 'No se guardó';
  if (hasPendingChanges) return changedCount === 0 ? 'Cambios sin guardar' : `${changedCount} ${changedCount === 1 ? 'cambio' : 'cambios'}`;
  if (saved || canceled) return 'Todo guardado';
  return 'Sin cambios';
}

function compactHintOf({ canceled, hasError, hasPendingChanges, unmarkedCount }) {
  if (canceled) return 'Clase cancelada';
  if (hasError) return 'Quedan acá';
  if (unmarkedCount > 0) return `Quedan ${unmarkedCount} sin marcar`;
  return hasPendingChanges ? 'Todos marcados' : 'Marcá un alumno';
}

export function AttendanceSaveBar({ hasPendingChanges, changedCount, saved, canceled, hasError, unmarkedCount, saving, onSave, onDiscard }) {
  const state = { hasPendingChanges, changedCount, saved, canceled, hasError, unmarkedCount };
  return (
    <StickyActionBar
      compact="menu"
      aria-label="Guardado de asistencia"
      summary={barSummary(state)}
      hint={<span className={cn(hasError && 'font-bold text-danger-ink')}>{barHint(state)}</span>}
      compactSummary={compactSummaryOf(state)}
      compactHint={compactHintOf(state)}
      busy={saving}
      primary={{
        label: 'Guardar asistencia',
        onClick: onSave,
        disabled: !hasPendingChanges || saving || canceled,
        loading: saving,
      }}
      secondary={[
        { key: 'discard', label: 'Descartar', icon: Undo2, destructive: true, onSelect: onDiscard, disabled: !hasPendingChanges || saving },
      ]}
    />
  );
}
