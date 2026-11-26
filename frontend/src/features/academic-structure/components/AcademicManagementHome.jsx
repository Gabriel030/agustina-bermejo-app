import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AlertTriangle, BookOpen, CalendarDays, ChevronRight, GraduationCap, Layers, Plus } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { StatCard } from '@/components/StatCard';
import { StatusPill } from '@/components/StatusPill';
import { Notice } from '@/components/Notice';
import { Skeleton } from '@/components/Skeleton';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Button } from '@/components/ui/button';
import { useAcademicManagementSummary } from '@/features/academic-structure/hooks/useAcademicManagementSummary';
import { MAX_VISIBLE_EVENTS, describeEventDate } from '@/features/academic-structure/lib/academicManagementSummary';
import { NuevaCohortDialog } from './NuevaCohortDialog';
import { CohortProgramPickerDialog } from './CohortProgramPickerDialog';

const BASE_PATH = '/administrativo/gestion-academica';
const MAX_VISIBLE_ALERTS = 3;

const EVENT_PRESENTATION = {
  ADMISSION_CLOSE: { label: 'Cierre de admisión', tone: 'warning' },
  CLASSES_START: { label: 'Inicio de cursada', tone: 'accent' },
};

function SectionHeading({ id, className = '', children }) {
  return (
    <h2 id={id} className={`font-heading text-size-15 font-semibold text-ink ${className}`}>
      {children}
    </h2>
  );
}

function KpiValue({ value, hint, highlighted = false }) {
  return (
    <div className="flex flex-col gap-1">
      <span className={`font-heading text-size-26 font-bold ${highlighted ? 'text-brand' : 'text-ink'}`}>{value}</span>
      <span className="text-size-12 text-ink-faint">{hint}</span>
    </div>
  );
}

function KpiGrid({ summary }) {
  const noPrograms = summary.activeProgramsCount === 0;
  const hasOpenCohorts = summary.openCohortsCount > 0;
  return (
    <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
      <StatCard label="Carreras activas" className="p-3 md:p-5">
        <div className="flex items-start justify-between gap-2">
          <KpiValue
            value={summary.activeProgramsCount}
            hint={noPrograms ? 'Todavía no cargaste carreras' : `${summary.publishedProgramsCount} publicadas en el catálogo`}
          />
          <BookOpen className="size-4 shrink-0 text-ink-faint" aria-hidden="true" />
        </div>
      </StatCard>
      <StatCard label="Cursos activos" className="p-3 md:p-5">
        <div className="flex items-start justify-between gap-2">
          <KpiValue
            value={summary.activeCoursesCount}
            hint={summary.activeCoursesCount === 0 ? 'Todavía no cargaste cursos' : 'En el catálogo'}
          />
          <GraduationCap className="size-4 shrink-0 text-ink-faint" aria-hidden="true" />
        </div>
      </StatCard>
      <StatCard label="Materias en el banco" className="p-3 md:p-5">
        <div className="flex items-start justify-between gap-2">
          <KpiValue
            value={summary.subjectsCount}
            hint={summary.subjectsCount === 0 ? 'El banco está vacío' : 'Materias activas'}
          />
          <Layers className="size-4 shrink-0 text-ink-faint" aria-hidden="true" />
        </div>
      </StatCard>
      <StatCard label="Cohortes abiertas" className="p-3 md:p-5">
        <div className="flex items-start justify-between gap-2">
          <KpiValue
            value={summary.openCohortsCount}
            highlighted={hasOpenCohorts}
            hint={hasOpenCohorts ? 'Con admisión vigente' : 'Sin cohortes'}
          />
          <CalendarDays className="size-4 shrink-0 text-ink-faint" aria-hidden="true" />
        </div>
      </StatCard>
    </div>
  );
}

function KpiGridSkeleton() {
  return (
    <div className="grid grid-cols-2 gap-3 md:grid-cols-4" aria-busy="true" data-testid="management-home-skeleton">
      {[0, 1, 2, 3].map((index) => (
        <GlassCard key={index} className="flex flex-col gap-3 p-3 md:p-5">
          <Skeleton className="h-3 w-3/5" />
          <Skeleton className="h-7 w-2/5" />
        </GlassCard>
      ))}
    </div>
  );
}

function PanelSkeleton() {
  return (
    <GlassCard className="flex flex-col gap-3 p-4" aria-busy="true">
      <Skeleton className="h-4 w-1/3" />
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-4/5" />
    </GlassCard>
  );
}

function PublishedWithoutCohortsAlerts({ programs, onAddCohort }) {
  if (programs.length === 0) return null;
  const visible = programs.slice(0, MAX_VISIBLE_ALERTS);
  const hidden = programs.length - visible.length;
  return (
    <div className="flex flex-col gap-2">
      {visible.map((program) => (
        <Notice
          key={program.id}
          tone="warning"
          role="status"
          className="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-card px-4 py-3 text-size-13"
        >
          <AlertTriangle className="size-4 shrink-0" aria-hidden="true" />
          <span className="min-w-48 flex-1">
            <b className="font-bold">{program.name}</b> está publicada pero no tiene cohortes de ingreso abiertas.
          </span>
          <Button variant="outline" size="sm" className="min-h-11 md:min-h-0" onClick={() => onAddCohort(program.id)}>
            <Plus className="mr-1 h-3.5 w-3.5" />
            Agregar cohorte
          </Button>
        </Notice>
      ))}
      {hidden > 0 && (
        <p className="px-1 text-size-12 text-ink-faint">
          {hidden === 1 ? 'Hay 1 carrera publicada más' : `Hay ${hidden} carreras publicadas más`} sin cohortes abiertas.
        </p>
      )}
    </div>
  );
}

function UpcomingEventRow({ event }) {
  const { day, month } = describeEventDate(event.date);
  const presentation = EVENT_PRESENTATION[event.kind];
  return (
    <li className="border-t border-border first:border-t-0">
      <Link
        to={`${BASE_PATH}/carreras/${event.programId}?tab=cohortes`}
        className="flex min-h-11 items-start gap-3 rounded-control px-1.5 py-2.5 hover:bg-neutral-bg/60"
      >
        <span className="flex w-13 shrink-0 flex-col items-center rounded-control border border-border py-1 leading-tight">
          <b className="font-heading text-size-17 font-bold tabular-nums text-ink">{day}</b>
          <span className="text-size-10-5 font-bold uppercase tracking-widest text-ink-faint">{month}</span>
        </span>
        <span className="flex min-w-0 flex-col gap-1">
          <StatusPill tone={presentation.tone} icon={null}>
            {presentation.label}
          </StatusPill>
          <span className="text-size-13 text-ink-soft">
            {event.cohortLabel} · {event.programName}
          </span>
        </span>
      </Link>
    </li>
  );
}

function UpcomingEventsPanel({ events }) {
  const [showAll, setShowAll] = useState(false);
  const hasMore = events.length > MAX_VISIBLE_EVENTS;
  const visible = showAll ? events : events.slice(0, MAX_VISIBLE_EVENTS);
  return (
    <GlassCard className="flex min-w-0 flex-col gap-3 p-4" aria-labelledby="management-home-events" role="region">
      <SectionHeading id="management-home-events">Próximos eventos</SectionHeading>
      {events.length === 0 ? (
        <div className="flex flex-col items-center gap-1 py-6 text-center">
          <span className="font-heading text-size-14 font-semibold text-ink">No hay eventos próximos</span>
          <span className="max-w-xs text-size-13 text-ink-faint">No hay eventos programados en los próximos 2 meses.</span>
        </div>
      ) : (
        <>
          <ul className="flex flex-col">
            {visible.map((event) => (
              <UpcomingEventRow key={event.id} event={event} />
            ))}
          </ul>
          {hasMore && !showAll && (
            <div>
              <Button variant="text" className="min-h-11 md:min-h-0" onClick={() => setShowAll(true)}>
                Ver todo
                <ChevronRight className="ml-1 h-4 w-4" aria-hidden="true" />
              </Button>
            </div>
          )}
        </>
      )}
    </GlassCard>
  );
}

function RecentPublishedPrograms({ programs, onViewAll }) {
  if (programs.length === 0) {
    return <p className="py-3 text-size-13 text-ink-faint">Todavía no hay carreras publicadas.</p>;
  }
  return (
    <>
      <ul className="flex flex-col">
        {programs.map((program) => (
          <li key={program.id} className="border-t border-border first:border-t-0">
            <Link
              to={`${BASE_PATH}/carreras/${program.id}`}
              className="flex min-h-11 items-center justify-between gap-2 text-size-14 font-semibold text-ink hover:text-brand"
            >
              <span className="min-w-0 truncate">{program.name}</span>
              <ChevronRight className="h-4 w-4 shrink-0 text-ink-faint" aria-hidden="true" />
            </Link>
          </li>
        ))}
      </ul>
      <div>
        <Button variant="text" className="min-h-11 md:min-h-0" onClick={onViewAll}>
          Ver todas las carreras publicadas
          <ChevronRight className="ml-1 h-4 w-4" aria-hidden="true" />
        </Button>
      </div>
    </>
  );
}

function QuickActionsPanel({ summary, onNewCohort, onViewAllPublished }) {
  const navigate = useNavigate();
  return (
    <GlassCard className="flex min-w-0 flex-col gap-3 p-4" aria-labelledby="management-home-actions" role="region">
      <SectionHeading id="management-home-actions">Acciones frecuentes</SectionHeading>
      <div className="grid grid-cols-2 gap-2">
        <Button variant="outline" className="min-h-11 justify-start md:min-h-8" onClick={() => navigate(`${BASE_PATH}/nueva`)}>
          <Plus className="mr-1.5 h-3.5 w-3.5" />
          Nueva materia
        </Button>
        <Button variant="outline" className="min-h-11 justify-start md:min-h-8" onClick={() => navigate(`${BASE_PATH}/carreras/nueva`)}>
          <Plus className="mr-1.5 h-3.5 w-3.5" />
          Nueva carrera
        </Button>
        <Button variant="outline" className="min-h-11 justify-start md:min-h-8" onClick={() => navigate(`${BASE_PATH}/cursos/nueva`)}>
          <Plus className="mr-1.5 h-3.5 w-3.5" />
          Nuevo curso
        </Button>
        <Button
          variant="primary"
          className="min-h-11 justify-start md:min-h-8"
          disabled={summary.activePrograms.length === 0}
          onClick={onNewCohort}
        >
          <Plus className="mr-1.5 h-3.5 w-3.5" />
          Nueva cohorte
        </Button>
      </div>
      <SectionHeading id="management-home-recent" className="mt-2">Carreras publicadas recientemente</SectionHeading>
      <RecentPublishedPrograms programs={summary.recentPublishedPrograms} onViewAll={onViewAllPublished} />
    </GlassCard>
  );
}

export function AcademicManagementHome({ onViewAllPublishedPrograms }) {
  const { summary, isLoading, isError, refetch } = useAcademicManagementSummary();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [cohortProgramId, setCohortProgramId] = useState(null);

  if (isLoading) {
    return (
      <div className="flex flex-col gap-4">
        <KpiGridSkeleton />
        <div className="grid gap-4 lg:grid-cols-[55fr_45fr]">
          <PanelSkeleton />
          <PanelSkeleton />
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <GlassCard className="p-4">
        <LoadErrorState onRetry={refetch} resource="el resumen" bare />
      </GlassCard>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <KpiGrid summary={summary} />
      <PublishedWithoutCohortsAlerts programs={summary.publishedWithoutOpenCohorts} onAddCohort={setCohortProgramId} />
      <div className="grid gap-4 lg:grid-cols-[55fr_45fr]">
        <UpcomingEventsPanel events={summary.upcomingEvents} />
        <QuickActionsPanel
          summary={summary}
          onNewCohort={() => setPickerOpen(true)}
          onViewAllPublished={onViewAllPublishedPrograms}
        />
      </div>
      <CohortProgramPickerDialog
        open={pickerOpen}
        programs={summary.activePrograms}
        onClose={() => setPickerOpen(false)}
        onPick={(programId) => {
          setPickerOpen(false);
          setCohortProgramId(programId);
        }}
      />
      <NuevaCohortDialog
        open={cohortProgramId !== null}
        onClose={() => setCohortProgramId(null)}
        programId={cohortProgramId}
      />
    </div>
  );
}
