import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { LoadErrorState } from '@/components/LoadErrorState';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { JustificarFaltaDialog } from '@/features/attendance/components/JustificarFaltaDialog';
import { useEnrollmentAttendance } from '@/features/attendance/hooks/useStaffAbsences';
import {
  countComputedAbsences,
  describeAbsenceStatus,
  describeDeadline,
  formatIsoDate,
  listAbsences,
} from '@/features/attendance/lib/absenceRows';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

function DeadlineCell({ entry }) {
  if (entry.status === 'JUSTIFIED') return <span className="text-ink-faint">Resuelta el {formatIsoDate(entry.justifiedAt)}</span>;
  const deadline = describeDeadline(entry);
  if (!deadline) return <span className="text-ink-faint">—</span>;
  return (
    <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
      <StatusPill tone={deadline.open ? 'success' : 'danger'}>{deadline.label}</StatusPill>
      <span className="text-ink-faint">{deadline.detail}</span>
    </span>
  );
}

function StatusCell({ entry }) {
  const status = describeAbsenceStatus(entry);
  return (
    <div className="flex flex-col gap-1">
      <StatusPill tone={status.tone}>{status.label}</StatusPill>
      {entry.status === 'ABSENT' && entry.justificationRejectionReason && (
        <span className="text-xs text-ink-faint">Motivo del rechazo: {entry.justificationRejectionReason}</span>
      )}
    </div>
  );
}

function ActionCell({ entry, canJustify, solicitudesPath, onJustify, studentName }) {
  if (!canJustify) return <span className="text-xs text-ink-faint">Solo lectura</span>;
  if (entry.status === 'JUSTIFIED') return <span className="text-xs text-ink-faint">Sin acción</span>;
  if (entry.status === 'JUSTIFICATION_PENDING') {
    return (
      <Button asChild variant="outline">
        <Link to={solicitudesPath}>Revisar solicitud</Link>
      </Button>
    );
  }
  return (
    <Button
      variant="outline"
      aria-label={`Justificar la falta del ${formatIsoDate(entry.classDate)} de ${studentName}`}
      onClick={() => onJustify(entry)}
    >
      Justificar
    </Button>
  );
}

export function FaltasDeMateria({ sectionEnrollmentId, studentName, subjectLabel, canJustify, authorName, roleBase }) {
  const attendance = useEnrollmentAttendance(sectionEnrollmentId);
  const [selected, setSelected] = useState(null);
  const compact = useMediaQuery('(max-width: 767px)');
  const solicitudesPath = `/${roleBase}/solicitudes-de-asistencia?tab=justificaciones`;

  if (attendance.isLoading) {
    return (
      <div className="flex flex-col gap-2 p-3" aria-busy="true" aria-label="Cargando faltas">
        <Skeleton className="h-10 w-full" />
        <Skeleton className="h-10 w-full" />
      </div>
    );
  }
  if (attendance.isError) {
    return <LoadErrorState bare error={attendance.error} onRetry={() => attendance.refetch()} resource="las faltas" />;
  }

  const absences = listAbsences(attendance.data);
  const cell = { canJustify, solicitudesPath, studentName, onJustify: setSelected };

  return (
    <div className="flex flex-col gap-2 p-3">
      <h3 className="font-heading text-[13px] font-semibold text-ink">Faltas</h3>
      {absences.length === 0 ? (
        <p className="text-[13px] text-ink-faint">Sin faltas registradas.</p>
      ) : compact ? (
        <ul className="flex flex-col gap-2">
          {absences.map((entry) => (
            <li key={entry.id} className="flex flex-col gap-2 rounded-xl border border-border p-3 text-[13px]">
              <div className="flex items-center justify-between gap-2">
                <b>Clase del {formatIsoDate(entry.classDate)}</b>
                <StatusCell entry={entry} />
              </div>
              <DeadlineCell entry={entry} />
              <div>
                <ActionCell entry={entry} {...cell} />
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <Table className="w-full text-[13px]">
          <TableHeader>
            <TableRow className="text-left text-xs uppercase text-ink-faint">
              <TableHead className="py-2 pr-3 font-semibold">Clase</TableHead>
              <TableHead className="py-2 pr-3 font-semibold">Estado</TableHead>
              <TableHead className="py-2 pr-3 font-semibold">Plazo</TableHead>
              <TableHead className="py-2 font-semibold">
                <span className="sr-only">Acción</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {absences.map((entry) => (
              <TableRow key={entry.id} className="border-t border-border align-top">
                <TableCell className="py-2 pr-3 font-semibold">{formatIsoDate(entry.classDate)}</TableCell>
                <TableCell className="py-2 pr-3">
                  <StatusCell entry={entry} />
                </TableCell>
                <TableCell className="py-2 pr-3">
                  <DeadlineCell entry={entry} />
                </TableCell>
                <TableCell className="py-2 text-right">
                  <ActionCell entry={entry} {...cell} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      {selected && (
        <JustificarFaltaDialog
          sectionEnrollmentId={sectionEnrollmentId}
          absence={selected}
          studentName={studentName}
          subjectLabel={subjectLabel}
          computedAbsences={countComputedAbsences(attendance.data)}
          authorName={authorName}
          onClose={() => setSelected(null)}
        />
      )}
    </div>
  );
}
