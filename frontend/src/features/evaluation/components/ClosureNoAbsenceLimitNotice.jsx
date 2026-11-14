import { Notice } from '@/components/Notice';
import { hasAbsenceLimit } from '../lib/closureSheet';

export const NO_ABSENCE_LIMIT_MESSAGE = 'Esta comisión no tiene límite de inasistencias configurado: nadie queda libre por inasistencias.';

export function ClosureNoAbsenceLimitNotice({ sheet, className }) {
  if (hasAbsenceLimit(sheet)) return null;
  return (
    <Notice tone="warning" className={className}>
      {NO_ABSENCE_LIMIT_MESSAGE}
    </Notice>
  );
}
