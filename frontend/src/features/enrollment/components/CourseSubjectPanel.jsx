import { useState } from 'react';
import { AlertTriangle, CheckCircle2, ChevronDown } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import {
  classifyAttendanceRisk,
  describeAttendanceStatus,
  describeGradeType,
  formatIsoDate,
  formatPercent,
  sortByClassDateDescending,
  summarizeAttendance,
} from '@/features/enrollment/lib/courseSubjectDetail';
import { OwnWaitlistPosition } from '@/features/enrollment/components/WaitlistPositionCard';
import { smoothScrollBehavior } from '@/lib/scrollBehavior';

const RECENT_ATTENDANCE_LIMIT = 5;

const SECTION_STATUS = {
  APPROVED: { label: 'Aprobada', tone: 'success' },
  FAILED: { label: 'Desaprobada', tone: 'danger' },
  WITHDRAWN: { label: 'Baja', tone: 'neutral' },
  WAITLISTED: { label: 'En lista de espera', tone: 'warning' },
  WAITLIST_EXPIRED: { label: 'Lista de espera vencida', tone: 'neutral' },
};

const GRADED_STATUSES = new Set(['APPROVED', 'FAILED']);

const RISK_TEXT_CLASS = { danger: 'text-danger', warning: 'text-warning', success: 'text-success' };

function describeSectionStatus(sectionEnrollment) {
  if (GRADED_STATUSES.has(sectionEnrollment.status) && sectionEnrollment.finalScore != null) {
    return { label: `Nota final ${Number(sectionEnrollment.finalScore).toLocaleString('es-AR')}`, tone: 'success' };
  }
  return SECTION_STATUS[sectionEnrollment.status] ?? { label: 'En curso', tone: 'success', dot: true };
}

function describeRisk(level, risk) {
  const percent = formatPercent(risk.absencePercent);
  const max = formatPercent(risk.maxAbsencePercent);
  if (level === 'danger') return `Inasistencias: ${percent}, por encima del máximo de ${max}. Quedaste libre en esta materia. Consultá con tu preceptor.`;
  if (level === 'warning') return `Inasistencias: ${percent} (máximo ${max}). Justificá tus inasistencias a tiempo.`;
  return `Inasistencias: ${percent} (máximo ${max}). Estás en regla.`;
}

function AttendanceRisk({ risk }) {
  const level = classifyAttendanceRisk(risk);
  if (!level) return null;
  const Icon = level === 'success' ? CheckCircle2 : AlertTriangle;
  return (
    <div role="status" data-risk={level} className={`flex items-start gap-2.5 text-size-13 font-semibold ${RISK_TEXT_CLASS[level]}`}>
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <span>{describeRisk(level, risk)}</span>
    </div>
  );
}

function Stat({ label, value }) {
  return (
    <div className="flex flex-col gap-0.5 bg-surface px-3.5 py-2.5">
      <span className="text-size-11 font-semibold uppercase tracking-wide text-ink-faint">{label}</span>
      <span className="font-heading text-xl font-bold text-ink">{value}</span>
    </div>
  );
}

function AttendanceTable({ attendance, subjectName }) {
  const [showAll, setShowAll] = useState(false);
  const sorted = sortByClassDateDescending(attendance);
  const visible = showAll ? sorted : sorted.slice(0, RECENT_ATTENDANCE_LIMIT);
  if (sorted.length === 0) return <p className="text-sm text-ink-faint">Todavía no hay clases registradas.</p>;
  return (
    <div className="flex flex-col gap-2">
      <div tabIndex={0} role="region" aria-label={`Asistencia de ${subjectName}`} className="overflow-x-auto rounded-lg border border-border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Fecha</TableHead>
              <TableHead>Estado</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {visible.map((record) => {
              const { label, tone } = describeAttendanceStatus(record.status);
              return (
                <TableRow key={record.id}>
                  <TableCell>{formatIsoDate(record.classDate)}</TableCell>
                  <TableCell>
                    <StatusPill tone={tone}>{label}</StatusPill>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
      {sorted.length > RECENT_ATTENDANCE_LIMIT && (
        <Button variant="text" size="inline" type="button" className="self-start text-sm font-semibold" onClick={() => setShowAll((value) => !value)}>
          {showAll ? 'Ver menos' : `Ver todas (${sorted.length})`}
        </Button>
      )}
    </div>
  );
}

function GradesTable({ gradeItems, subjectName }) {
  if (gradeItems.length === 0) return <p className="text-sm text-ink-faint">Todavía no hay notas cargadas.</p>;
  return (
    <div tabIndex={0} role="region" aria-label={`Notas de ${subjectName}`} className="overflow-x-auto rounded-lg border border-border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Evaluación</TableHead>
            <TableHead>Fecha</TableHead>
            <TableHead className="text-right">Nota</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {gradeItems.map((grade) => (
            <TableRow key={grade.id}>
              <TableCell>{describeGradeType(grade.type, grade.sequenceNumber)}</TableCell>
              <TableCell>{formatIsoDate(grade.date)}</TableCell>
              <TableCell className="text-right font-bold text-ink">{Number(grade.score).toLocaleString('es-AR')}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

function Detail({ label, value }) {
  return (
    <div>
      <dt className="text-xs font-semibold uppercase tracking-wide text-ink-faint">{label}</dt>
      <dd className="mt-0.5 text-size-13-5 font-semibold text-ink">{value}</dd>
    </div>
  );
}

export function CourseSubjectPanel({ subject, open, onOpenChange }) {
  const { sectionEnrollment, attendance, attendanceRisk, gradeItems } = subject;
  const subjectName = sectionEnrollment.courseSubjectName;
  const status = describeSectionStatus(sectionEnrollment);
  const summary = summarizeAttendance(attendance);
  const panelId = `subject-panel-${sectionEnrollment.id}`;
  const triggerId = `${panelId}-trigger`;

  function handleOpenChange(next) {
    onOpenChange(next);
    if (next) {
      requestAnimationFrame(() => {
        const trigger = document.getElementById(triggerId);
        trigger?.focus({ preventScroll: true });
        trigger?.scrollIntoView?.({ block: 'nearest', behavior: smoothScrollBehavior() });
      });
    }
  }

  return (
    <Collapsible open={open} onOpenChange={handleOpenChange} asChild>
      <GlassCard className="overflow-hidden">
        <h2>
          <CollapsibleTrigger
            id={triggerId}
            className="flex min-h-14 w-full items-center gap-3 px-4 py-3.5 text-left transition-colors hover:bg-neutral-bg focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brand sm:px-5"
          >
            <span className="min-w-0 flex-1">
              <span className="block font-heading text-size-15 font-semibold text-ink">{subjectName}</span>
              {sectionEnrollment.sectionName && <span className="block text-size-13 text-ink-faint">{sectionEnrollment.sectionName}</span>}
            </span>
            <StatusPill tone={status.tone} dot={status.dot}>
              {status.label}
            </StatusPill>
            <ChevronDown className={`size-4 shrink-0 text-ink-faint transition-transform motion-reduce:transition-none ${open ? 'rotate-180' : ''}`} aria-hidden="true" />
          </CollapsibleTrigger>
        </h2>
        <CollapsibleContent>
          <div className="flex flex-col gap-5 border-t border-border p-4 sm:p-5">
            {sectionEnrollment.status === 'WAITLISTED' && (
              <OwnWaitlistPosition kind="COURSE_SECTION" enrollmentId={sectionEnrollment.id} showName={false} />
            )}
            <dl className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <Detail label="Docente titular" value={sectionEnrollment.instructorTitular ?? 'A confirmar'} />
              {sectionEnrollment.instructorAdjunto && <Detail label="Docente adjunto" value={sectionEnrollment.instructorAdjunto} />}
              <Detail label="Horario" value={sectionEnrollment.schedule ?? 'A confirmar'} />
            </dl>
            <section className="flex flex-col gap-2.5">
              <h3 className="font-heading text-sm font-semibold text-ink">Asistencia</h3>
              <div className="grid grid-cols-3 gap-px overflow-hidden rounded-lg border border-border bg-border">
                <Stat label="Clases" value={summary.classes} />
                <Stat label="Ausentes" value={summary.absent} />
                <Stat label="Justificadas" value={summary.justified} />
              </div>
              <AttendanceRisk risk={attendanceRisk} />
              <AttendanceTable attendance={attendance} subjectName={subjectName} />
            </section>
            <section className="flex flex-col gap-2.5">
              <h3 className="font-heading text-sm font-semibold text-ink">Notas</h3>
              <GradesTable gradeItems={gradeItems ?? []} subjectName={subjectName} />
            </section>
          </div>
        </CollapsibleContent>
      </GlassCard>
    </Collapsible>
  );
}
