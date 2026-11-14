import { useState } from 'react';
import { useActiveRole } from '@/hooks/useActiveRole';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { ChangesTable } from '@/components/ChangesTable';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useAuth } from '@/context/AuthContext';
import { useLegajoAlumno } from '@/features/identity';
import {
  useEnrollStudentInCourse,
  useEnrollStudentInProgram,
  useRefreshCourseOccupancy,
  useStaffCourseModules,
  useStaffCourses,
  useStaffOpenCohorts,
  useStaffPrograms,
} from '@/features/enrollment/hooks/useStaffEnrollment';
import { ModuleSectionPicker, OfferRadioGroup, OptionCard } from '@/features/enrollment/components/StaffEnrollmentOptions';

const OTHER_TAB = { career: 'course', course: 'career' };

const FULL_SECTION_MESSAGE = 'La comisión se llenó mientras elegías. Elegí otra comisión para continuar.';
const GENERIC_ERROR_MESSAGE = 'No pudimos registrar la inscripción. Probá de nuevo.';
const PROGRAM_CONFLICT_MESSAGE =
  'No pudimos registrar la inscripción. Revisá que el alumno no esté ya inscripto y que la cohorte siga abierta.';

function matches(name, query) {
  return (name ?? '').toLowerCase().includes(query.trim().toLowerCase());
}

function isModuleResolved(module, overrides) {
  if (module.sections.length <= 1) return true;
  const chosen = overrides[module.id];
  return module.sections.some((section) => section.id === chosen && !section.full);
}

function buildCourseRows({ course, modules, overrides }) {
  const rows = [
    {
      key: 'enrollment',
      label: 'Inscripción',
      today: 'Sin inscripción en este curso',
      next: course.name,
      difference: '+1 inscripción',
    },
  ];
  modules.forEach((module) => {
    const section = module.sections.length === 1 ? module.sections[0] : module.sections.find((s) => s.id === overrides[module.id]);
    if (!section) return;
    rows.push({
      key: `section-${module.id}`,
      label: `Comisión · ${module.name}`,
      today: '—',
      next: `${section.code} · ${section.name}`,
      difference: section.full ? 'Lista de espera' : 'Se asigna',
    });
    if (section.capacity == null) {
      rows.push({
        key: `seats-${module.id}`,
        label: `Cupo · ${module.name}`,
        today: 'Sin límite de cupo',
        next: 'Sin límite de cupo',
        difference: 'Sin cambios',
        unchanged: true,
      });
    } else if (!section.full) {
      rows.push({
        key: `seats-${module.id}`,
        label: `Cupo · ${module.name}`,
        today: `${section.enrolled}/${section.capacity}`,
        next: `${section.enrolled + 1}/${section.capacity}`,
        difference: `+1 ocupado, ${section.available - 1} libres`,
      });
    }
  });
  return rows;
}

function ProgramPicker({ query, programId, cohortId, onProgramChange, onCohortChange, onSwitchTab }) {
  const programs = useStaffPrograms();
  const cohorts = useStaffOpenCohorts(programId);

  if (programs.isLoading) return <Skeleton className="h-32 w-full" />;
  if (programs.isError) return <LoadErrorState error={programs.error} onRetry={programs.refetch} resource="las carreras" bare />;
  const visible = (programs.data ?? []).filter((program) => matches(program.name, query));
  if ((programs.data ?? []).length === 0) {
    return (
      <EmptyState
        icon="▤"
        title="No hay carreras con inscripción abierta"
        description="Cuando se abra un período de inscripción, la oferta va a aparecer acá."
        action={<Button variant="outline" onClick={onSwitchTab}>Ver cursos</Button>}
      />
    );
  }

  return (
    <>
      <div className="font-heading text-sm font-semibold text-ink">1 · Elegí la carrera</div>
      <OfferRadioGroup label="Carrera" value={programId} onValueChange={(next) => onProgramChange(Number(next))}>
        {visible.map((program) => (
          <OptionCard key={program.id} value={program.id}>
            <span className="font-medium text-ink">{program.name}</span>
            {program.totalYears != null && <span className="text-xs text-ink-faint">{program.totalYears} años</span>}
          </OptionCard>
        ))}
      </OfferRadioGroup>
      {programId && (
        <>
          <div className="mt-2 font-heading text-sm font-semibold text-ink">2 · Elegí la cohorte</div>
          {cohorts.isLoading && <Skeleton className="h-16 w-full" />}
          {cohorts.isError && <LoadErrorState error={cohorts.error} onRetry={cohorts.refetch} resource="las cohortes" bare />}
          {cohorts.data && cohorts.data.length === 0 && (
            <EmptyState title="Esta carrera no tiene cohortes con admisión abierta" description="Probá con otra carrera." />
          )}
          {cohorts.data && cohorts.data.length > 0 && (
            <OfferRadioGroup label="Cohorte" value={cohortId} onValueChange={(next) => onCohortChange(Number(next))}>
              {cohorts.data.map((cohort) => (
                <OptionCard key={cohort.id} value={cohort.id}>
                  <span className="font-medium text-ink">{cohort.label}</span>
                  {cohort.cursadaStart && (
                    <span className="text-xs text-ink-faint">
                      Inicio de cursada: {cohort.cursadaStart.split('-').reverse().join('/')}
                    </span>
                  )}
                </OptionCard>
              ))}
            </OfferRadioGroup>
          )}
        </>
      )}
    </>
  );
}

function CoursePicker({ query, courseId, modulesState, overrides, onCourseChange, onOverrideChange, onSwitchTab, courses }) {
  if (courses.isLoading) return <Skeleton className="h-32 w-full" />;
  if (courses.isError) return <LoadErrorState error={courses.error} onRetry={courses.refetch} resource="los cursos" bare />;
  if ((courses.data ?? []).length === 0) {
    return (
      <EmptyState
        icon="▤"
        title="No hay cursos con inscripción abierta"
        description="Cuando se abra un período de inscripción, la oferta va a aparecer acá."
        action={<Button variant="outline" onClick={onSwitchTab}>Ver carreras</Button>}
      />
    );
  }
  const visible = courses.data.filter((course) => matches(course.name, query));

  return (
    <>
      <div className="font-heading text-sm font-semibold text-ink">1 · Elegí el curso</div>
      <OfferRadioGroup label="Curso" value={courseId} onValueChange={(next) => onCourseChange(Number(next))}>
        {visible.map((course) => (
          <OptionCard key={course.id} value={course.id}>
            <span className="font-medium text-ink">{course.name}</span>
            {course.schedule && <span className="text-xs text-ink-faint">{course.schedule}</span>}
          </OptionCard>
        ))}
      </OfferRadioGroup>
      {courseId && (
        <>
          <div className="mt-2 font-heading text-sm font-semibold text-ink">2 · Elegí la comisión de cada módulo</div>
          {modulesState.isLoading && <Skeleton className="h-24 w-full" />}
          {modulesState.isError && (
            <LoadErrorState error={modulesState.error} onRetry={modulesState.refetch} resource="las comisiones" bare />
          )}
          {!modulesState.isLoading && !modulesState.isError && modulesState.modules.length === 0 && (
            <EmptyState title="Este curso no tiene módulos activos" />
          )}
          {!modulesState.isLoading && !modulesState.isError && (
            <div className="flex flex-col gap-4">
              {modulesState.modules.map((module) => (
                <ModuleSectionPicker key={module.id} module={module} value={overrides[module.id]} onChange={onOverrideChange} />
              ))}
            </div>
          )}
        </>
      )}
    </>
  );
}

function SuccessScreen({ student, offerLabel, waitlisted, rolBase, studentId, auditName }) {
  const navigate = useNavigate();
  return (
    <GlassCard className="flex flex-col items-center gap-3 p-6 text-center">
      <div className="text-3xl" aria-hidden="true">✓</div>
      <h2 className="font-heading text-lg font-bold text-ink">
        {waitlisted ? `${student} quedó en lista de espera` : `${student} quedó inscripto/a`}
      </h2>
      <p className="text-sm text-ink-soft">{offerLabel}</p>
      {waitlisted && <StatusPill tone="warning">En lista de espera</StatusPill>}
      {auditName && <p className="text-xs text-ink-faint">Inscripción registrada por {auditName}</p>}
      <div className="flex flex-wrap justify-center gap-2">
        <Button variant="outline" onClick={() => navigate(`/${rolBase}/alumnos/nuevo`)}>Dar de alta a otro alumno</Button>
        <Button variant="gradient" onClick={() => navigate(`/${rolBase}/alumnos/${studentId}`)}>Ir al legajo</Button>
      </div>
    </GlassCard>
  );
}

export function InscribirAlumno() {
  const { id } = useParams();
  const { usuario } = useAuth();
  const { roleBase: rolBase } = useActiveRole();
  const auditName = usuario ? [usuario.nombre, usuario.apellido].filter(Boolean).join(' ') : '';
  const legajo = useLegajoAlumno(id);
  const studentName = legajo.data ? `${legajo.data.nombre} ${legajo.data.apellido}` : '';

  const [tab, setTab] = useState('career');
  const [query, setQuery] = useState('');
  const [programId, setProgramId] = useState(null);
  const [cohortId, setCohortId] = useState(null);
  const [courseId, setCourseId] = useState(null);
  const [overrides, setOverrides] = useState({});
  const [reviewing, setReviewing] = useState(false);
  const [banner, setBanner] = useState(null);
  const [result, setResult] = useState(null);

  const programs = useStaffPrograms();
  const courses = useStaffCourses();
  const cohorts = useStaffOpenCohorts(programId);
  const course = courses.data?.find((item) => item.id === courseId);
  const modulesState = useStaffCourseModules(course);
  const refreshOccupancy = useRefreshCourseOccupancy();
  const enrollInProgram = useEnrollStudentInProgram(id);
  const enrollInCourse = useEnrollStudentInCourse(id);

  const program = programs.data?.find((item) => item.id === programId);
  const cohort = cohorts.data?.find((item) => item.id === cohortId);
  const isBusy = enrollInProgram.isPending || enrollInCourse.isPending;

  const careerReady = tab === 'career' && !!program && !!cohort;
  const courseReady =
    tab === 'course' &&
    !!course &&
    !modulesState.isLoading &&
    !modulesState.isError &&
    modulesState.modules.length > 0 &&
    modulesState.modules.every((module) => module.sections.length === 0 || isModuleResolved(module, overrides));
  const ready = careerReady || courseReady;

  function changeTab(next) {
    setTab(next);
    setQuery('');
    setProgramId(null);
    setCohortId(null);
    setCourseId(null);
    setOverrides({});
    setBanner(null);
  }

  function changeProgram(next) {
    setProgramId(next);
    setCohortId(null);
    setBanner(null);
  }

  function changeCourse(next) {
    setCourseId(next);
    setOverrides({});
    setBanner(null);
  }

  function changeOverride(courseSubjectId, sectionId) {
    setOverrides((current) => ({ ...current, [courseSubjectId]: sectionId }));
    setBanner(null);
  }

  function handleFailure(error) {
    setReviewing(false);
    if (tab === 'course' && error?.status === 409) {
      setOverrides({});
      refreshOccupancy();
      setBanner(FULL_SECTION_MESSAGE);
      return;
    }
    setBanner(tab === 'career' && error?.status === 409 ? PROGRAM_CONFLICT_MESSAGE : GENERIC_ERROR_MESSAGE);
  }

  function confirm() {
    setBanner(null);
    if (tab === 'career') {
      enrollInProgram.mutate(
        { programId, cohortId },
        {
          onSuccess: () => setResult({ offerLabel: `${program.name} · ${cohort.label}`, waitlisted: false }),
          onError: handleFailure,
        },
      );
      return;
    }
    const sectionOverrides = {};
    modulesState.modules.forEach((module) => {
      if (module.sections.length > 1) sectionOverrides[module.id] = overrides[module.id];
    });
    enrollInCourse.mutate(
      { courseId, sectionOverrides },
      {
        onSuccess: (response) =>
          setResult({
            offerLabel: course.name,
            waitlisted: response?.status === 'WAITLISTED' || response?.courseEnrollmentStatus === 'WAITLISTED',
          }),
        onError: handleFailure,
      },
    );
  }

  const header = (
    <div className="flex flex-col gap-1">
      <Link to={`/${rolBase}/alumnos/${id}`} className="text-sm text-ink-soft hover:text-ink">← Volver al legajo</Link>
      <h1 className="font-heading text-xl font-bold text-ink">
        {studentName ? `Inscribir a ${studentName}` : 'Inscribir alumno'}
      </h1>
      {legajo.data?.dni && <span className="text-size-13 text-ink-faint">DNI {legajo.data.dni}</span>}
    </div>
  );

  if (legajo.isError) {
    return (
      <PageContainer>
        <LoadErrorState error={legajo.error} onRetry={legajo.refetch} resource="el legajo" backTo={`/${rolBase}/alumnos`} backLabel="el padrón" />
      </PageContainer>
    );
  }

  if (result) {
    return (
      <PageContainer>
        <div className="flex flex-col gap-4">
          {header}
          <SuccessScreen
            student={studentName}
            offerLabel={result.offerLabel}
            waitlisted={result.waitlisted}
            rolBase={rolBase}
            studentId={id}
            auditName={auditName}
          />
        </div>
      </PageContainer>
    );
  }

  const offerName = tab === 'career' ? program?.name : course?.name;
  const rows =
    tab === 'career'
      ? [
          {
            key: 'enrollment',
            label: 'Inscripción',
            today: 'Sin inscripción en esta carrera',
            next: program && cohort ? `${program.name} · ${cohort.label}` : '—',
            difference: '+1 inscripción',
          },
        ]
      : courseReady
        ? buildCourseRows({ course, modules: modulesState.modules, overrides })
        : [];

  return (
    <PageContainer>
      <div className="flex flex-col gap-4">
        {header}
        {banner && (
          <div role="alert" className="rounded-xl border border-danger/30 bg-danger-bg p-3 text-sm text-danger">
            {banner}
          </div>
        )}
        {reviewing ? (
          <GlassCard className="flex flex-col gap-4 p-5">
            <h2 className="font-heading text-base font-semibold text-ink">Revisar inscripción</h2>
            <ChangesTable rows={rows} />
            {auditName && <p className="text-xs text-ink-faint">Se registra a nombre de {auditName}</p>}
            <div className="flex flex-wrap justify-between gap-2">
              <Button variant="ghost" disabled={isBusy} onClick={() => setReviewing(false)}>Volver a editar</Button>
              <Button variant="gradient" disabled={isBusy} onClick={confirm}>
                {isBusy ? 'Inscribiendo…' : 'Confirmar inscripción'}
              </Button>
            </div>
          </GlassCard>
        ) : (
          <div className="grid gap-4 lg:grid-cols-[1fr_320px]">
            <GlassCard className="flex flex-col gap-3 p-5">
              <Tabs value={tab} onValueChange={changeTab}>
                <TabsList variant="segmented" className="w-full">
                  <TabsTrigger value="career" className="flex-1">Carrera</TabsTrigger>
                  <TabsTrigger value="course" className="flex-1">Curso</TabsTrigger>
                </TabsList>
              </Tabs>
              <Input
                type="search"
                aria-label="Buscar en la oferta"
                placeholder="Buscar en la oferta…"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
              />
              {tab === 'career' ? (
                <ProgramPicker
                  query={query}
                  programId={programId}
                  cohortId={cohortId}
                  onProgramChange={changeProgram}
                  onCohortChange={setCohortId}
                  onSwitchTab={() => changeTab(OTHER_TAB[tab])}
                />
              ) : (
                <CoursePicker
                  query={query}
                  courseId={courseId}
                  courses={courses}
                  modulesState={modulesState}
                  overrides={overrides}
                  onCourseChange={changeCourse}
                  onOverrideChange={changeOverride}
                  onSwitchTab={() => changeTab(OTHER_TAB[tab])}
                />
              )}
            </GlassCard>
            <GlassCard className="flex h-fit flex-col gap-3 p-5">
              <h2 className="font-heading text-sm font-semibold text-ink">Resumen</h2>
              <dl className="flex flex-col gap-1 text-sm">
                <div className="flex justify-between gap-2"><dt className="text-ink-faint">Alumno</dt><dd className="text-ink">{studentName || '—'}</dd></div>
                <div className="flex justify-between gap-2"><dt className="text-ink-faint">{tab === 'career' ? 'Carrera' : 'Curso'}</dt><dd className="text-ink">{offerName ?? '—'}</dd></div>
                {tab === 'career' && (
                  <div className="flex justify-between gap-2"><dt className="text-ink-faint">Cohorte</dt><dd className="text-ink">{cohort?.label ?? '—'}</dd></div>
                )}
              </dl>
              <Button variant="gradient" disabled={!ready} onClick={() => setReviewing(true)}>Revisar inscripción</Button>
            </GlassCard>
          </div>
        )}
      </div>
    </PageContainer>
  );
}
