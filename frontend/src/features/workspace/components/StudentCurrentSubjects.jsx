import { Link } from 'react-router-dom';
import { ArrowRight, BookOpen, Clock } from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { PlanStatusPill } from '@/features/workspace/components/PlanStatusPill';
import { planStatusOf } from '@/features/workspace/lib/studentHome';

const SUBJECT_LINK_CLASS =
  'inline-flex min-h-11 items-center font-semibold text-primary hover:underline';

function subjectPath(sectionEnrollment) {
  return `/alumno/mis-carreras/materias/${sectionEnrollment.id}`;
}

function SubjectStatus({ sectionEnrollment }) {
  const status = planStatusOf(sectionEnrollment);
  if (status === 'waitlisted') {
    return (
      <StatusPill tone="warning" icon={Clock}>
        En lista de espera
      </StatusPill>
    );
  }
  return <PlanStatusPill status={status} />;
}

function SubjectsTable({ subjects }) {
  return (
    <div className="hidden md:block">
      <Table className="table-fixed">
        <caption className="sr-only">Materias en curso</caption>
        <colgroup>
          <col className="w-[26%]" />
          <col className="w-[14%]" />
          <col className="w-[22%]" />
          <col className="w-[16%]" />
          <col className="w-[22%]" />
        </colgroup>
        <TableHeader>
          <TableRow>
            <TableHead scope="col">Materia</TableHead>
            <TableHead scope="col">Comisión</TableHead>
            <TableHead scope="col">Horario</TableHead>
            <TableHead scope="col">Docente</TableHead>
            <TableHead scope="col">Estado</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {subjects.map((subject) => (
            <TableRow key={subject.id}>
              <TableCell className="whitespace-normal">
                <Link to={subjectPath(subject)} className={SUBJECT_LINK_CLASS}>
                  {subject.subjectName}
                </Link>
              </TableCell>
              <TableCell className="whitespace-normal">{subject.sectionName}</TableCell>
              <TableCell className="whitespace-normal tabular-nums">{subject.schedule ?? '—'}</TableCell>
              <TableCell className="whitespace-normal">{subject.instructorTitular ?? '—'}</TableCell>
              <TableCell className="whitespace-normal">
                <SubjectStatus sectionEnrollment={subject} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

function SubjectCards({ subjects }) {
  return (
    <ul className="flex flex-col gap-3 md:hidden">
      {subjects.map((subject) => (
        <li key={subject.id} className="flex flex-col gap-1 rounded-card border border-border p-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <Link to={subjectPath(subject)} className={SUBJECT_LINK_CLASS}>
              {subject.subjectName}
            </Link>
            <SubjectStatus sectionEnrollment={subject} />
          </div>
          <div className="flex justify-between gap-3 text-size-13 text-ink-soft">
            <span>{subject.sectionName}</span>
            <span className="tabular-nums">{subject.schedule ?? '—'}</span>
          </div>
          {subject.instructorTitular && <div className="text-size-13 text-ink-soft">Docente · {subject.instructorTitular}</div>}
        </li>
      ))}
    </ul>
  );
}

export function StudentCurrentSubjects({ subjects, hasProgram, isLoading, isError, error, onRetry }) {
  let body;
  if (isLoading) {
    body = (
      <div className="flex flex-col gap-2.5" role="status" aria-busy="true">
        <span className="sr-only">Cargando tus materias</span>
        <Skeleton className="h-10" />
        <Skeleton className="h-12" />
        <Skeleton className="h-12" />
      </div>
    );
  } else if (isError) {
    body = <LoadErrorState bare distinguishForbidden error={error} resource="tus materias" onRetry={onRetry} />;
  } else if (subjects.length === 0) {
    body = (
      <EmptyState
        icon="📚"
        title="Sin materias en curso"
        description={hasProgram ? 'Inscribite a una materia desde tu plan de estudio.' : 'Cuando te matricules en una carrera, tus materias aparecen acá.'}
        action={hasProgram && (
          <Button asChild variant="outline" className="h-11">
            <Link to="/alumno/mis-carreras">Ver plan de estudio</Link>
          </Button>
        )}
      />
    );
  } else {
    body = (
      <>
        <SubjectsTable subjects={subjects} />
        <SubjectCards subjects={subjects} />
      </>
    );
  }

  return (
    <GlassCard className="flex flex-col gap-3 p-5" aria-labelledby="student-subjects-title">
      <div className="flex items-center justify-between gap-2">
        <h2 id="student-subjects-title" className="flex items-center gap-2 font-heading text-size-15 font-semibold text-ink">
          <BookOpen className="h-4 w-4" aria-hidden="true" />
          Mis materias
        </h2>
        <Link to="/alumno/mis-carreras" className="inline-flex min-h-11 items-center gap-1 text-xs font-bold text-primary hover:underline">
          Plan de estudio
          <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
        </Link>
      </div>
      {body}
    </GlassCard>
  );
}
