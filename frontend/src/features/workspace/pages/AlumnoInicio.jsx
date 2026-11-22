import { Button } from '@/components/ui/button';
import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { PageContainer } from '@/components/PageContainer';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { useAuth } from '@/context/AuthContext';
import { useMyModules } from '@/hooks/useMyModules';
import { AdmissionInvoicePendingCard, usePaymentStanding } from '@/features/billing';
import { useMisCursos } from '@/features/enrollment';
import { Link } from 'react-router-dom';
import { StudentBalanceNotice } from '@/features/workspace/components/StudentBalanceNotice';
import { coursesSummary } from '@/features/workspace/lib/studentHome';
import { useNovedades, useMarcarAvisoLeido } from '@/features/announcements';
import { TooltipProvider } from '@/components/ui/tooltip';
import { Bell } from 'lucide-react';
import { StudentHomeIdentity } from '@/features/workspace/components/StudentHomeIdentity';
import { StudentHomeKpis } from '@/features/workspace/components/StudentHomeKpis';
import { StudentCurrentSubjects } from '@/features/workspace/components/StudentCurrentSubjects';
import { UpcomingEventsCard } from '@/features/workspace/components/UpcomingEventsCard';
import { useStudentHome } from '@/features/workspace/hooks/useStudentHome';
import { useStudentUpcomingEvents } from '@/features/workspace/hooks/useStudentUpcomingEvents';
import { StaleStamp } from '@/components/offline/StaleStamp';
import { CATEGORIA_AVISO_LABEL, parseAnnouncementId } from '@/lib/avisos';
import { describeTime } from '@/lib/notificaciones';
import { smoothScrollBehavior } from '@/lib/scrollBehavior';

const NOVEDADES_INICIAL = 3;

function fueEditado(aviso) {
  if (!aviso.updatedAt || !aviso.createdAt) return false;
  return new Date(aviso.updatedAt) - new Date(aviso.createdAt) > 60_000;
}

function timeMeta(aviso) {
  if (!fueEditado(aviso)) return { text: describeTime(aviso.createdAt).text, isoDate: aviso.createdAt };
  const { text, isRelative } = describeTime(aviso.updatedAt);
  return { text: isRelative ? `editado ${text}` : `editado el ${text}`, isoDate: aviso.updatedAt };
}

const VISIBLE_LINES = 6;

function bodyOverflows(el, expandido) {
  if (!expandido) return el.scrollHeight > el.clientHeight + 2;
  const lineHeightPx = parseFloat(window.getComputedStyle(el).lineHeight);
  return Number.isFinite(lineHeightPx) && el.scrollHeight > lineHeightPx * VISIBLE_LINES + 2;
}

function AvisoItem({ aviso, expandidoPorUrl, refProp, onAbrir }) {
  const [expandido, setExpandido] = useState(false);
  const [desborda, setDesborda] = useState(false);
  const [previousExpandidoPorUrl, setPreviousExpandidoPorUrl] = useState(false);

  function measureBody(el) {
    if (el) setDesborda(bodyOverflows(el, expandido));
  }

  if (expandidoPorUrl !== previousExpandidoPorUrl) {
    setPreviousExpandidoPorUrl(expandidoPorUrl);
    if (expandidoPorUrl) setExpandido(true);
  }

  useEffect(() => {
    if (expandidoPorUrl) onAbrir(aviso);
  }, [expandidoPorUrl, aviso, onAbrir]);

  function alternar() {
    if (!expandido) onAbrir(aviso);
    setExpandido((v) => !v);
  }

  const meta = timeMeta(aviso);

  return (
    <div
      ref={refProp}
      data-aviso-id={aviso.id}
      tabIndex={expandidoPorUrl ? -1 : undefined}
      aria-current={expandidoPorUrl ? 'true' : undefined}
      className={`rounded-card px-3 py-3 transition-colors outline-none ${expandidoPorUrl ? 'animate-[stagger-in_0.3s_ease-out_both] bg-accent-soft/40' : ''}`}
    >
      <div className="flex items-start justify-between gap-2 flex-wrap">
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="text-size-12 font-semibold text-ink-soft">{aviso.subjectName}</span>
          {aviso.category && (
            <span className="text-size-11 text-ink-faint">· {CATEGORIA_AVISO_LABEL[aviso.category] ?? aviso.category}</span>
          )}
        </div>
        <div className="flex items-center gap-1.5 flex-wrap">
          {aviso.urgent && <StatusPill tone="danger">Urgente</StatusPill>}
          <time dateTime={meta.isoDate} className="text-size-10-5 text-ink-faint">
            {meta.text}
          </time>
        </div>
      </div>
      <div className="mt-1 flex items-center gap-2">
        {!aviso.readAt && (
          <>
            <span className="size-2 shrink-0 rounded-full bg-brand" aria-hidden="true" />
            <span className="sr-only">Sin leer</span>
          </>
        )}
        <button
          type="button"
          aria-expanded={expandido}
          onClick={alternar}
          className="min-h-11 w-full text-left text-size-13-5 font-semibold text-ink"
        >
          {aviso.title}
        </button>
      </div>
      <div
        ref={measureBody}
        className={`mt-1 text-size-13 text-ink-soft leading-relaxed overflow-hidden ${!expandido ? 'line-clamp-6' : ''}`}
      >
        {aviso.body}
      </div>
      {desborda && (
        <Button
          variant="text"
          size="inline"
          type="button"
          onClick={alternar}
          className="mt-1 min-h-11 text-size-12 font-semibold"
          >
          {expandido ? 'Ver menos' : 'Ver más'}
        </Button>
      )}
    </div>
  );
}

export function AlumnoInicio() {
  const { usuario } = useAuth();
  const modules = useMyModules();
  const home = useStudentHome();
  const standing = usePaymentStanding({ enabled: modules.billingEnabled });
  const courses = useMisCursos();
  const coursesText = coursesSummary(courses.data);
  const upcoming = useStudentUpcomingEvents();
  const { data: novedadesRaw, isLoading: cargandoNovedades, isError: errorNovedades, error: errorDeNovedades, isFetching: actualizandoNovedades, refetch: refetchNovedades, dataUpdatedAt: novedadesActualizadas } = useNovedades();
  const [searchParams, setSearchParams] = useSearchParams();
  const [verTodos, setVerTodos] = useState(false);
  const marcarLeido = useMarcarAvisoLeido();
  const avisoResaltadoRef = useRef(null);

  const hasAnnouncementParam = searchParams.has('aviso');
  const avisoUrlId = hasAnnouncementParam ? parseAnnouncementId(searchParams.getAll('aviso')) : null;
  const novedades = [...(novedadesRaw ?? [])].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  const sinLeer = novedades.filter((aviso) => !aviso.readAt).length;
  const deepLinkedAnnouncementIndex = avisoUrlId ? novedades.findIndex((aviso) => aviso.id === avisoUrlId) : -1;
  const deepLinkedAnnouncementUnavailable = hasAnnouncementParam && novedadesRaw !== undefined && !actualizandoNovedades && deepLinkedAnnouncementIndex === -1;
  const deepLinkedAnnouncementFound = deepLinkedAnnouncementIndex !== -1;
  const showingAll = verTodos || deepLinkedAnnouncementIndex >= NOVEDADES_INICIAL;
  const novedadesVisibles = showingAll ? novedades : novedades.slice(0, NOVEDADES_INICIAL);
  const identityLoading = home.enrollments.isLoading;
  const accessDenied = home.enrollments.error?.status === 403;
  const programLoading = identityLoading || (home.program && home.progress.isLoading);

  useEffect(() => {
    const highlighted = avisoResaltadoRef.current;
    if (!avisoUrlId || !deepLinkedAnnouncementFound || !highlighted) return;
    highlighted.focus({ preventScroll: true });
    highlighted.scrollIntoView({ behavior: smoothScrollBehavior(), block: 'center' });
  }, [avisoUrlId, deepLinkedAnnouncementFound]);

  function toggleFullList() {
    if (!showingAll) {
      setVerTodos(true);
      return;
    }
    setVerTodos(false);
    if (deepLinkedAnnouncementIndex >= NOVEDADES_INICIAL) {
      const nextParams = new URLSearchParams(searchParams);
      nextParams.delete('aviso');
      setSearchParams(nextParams, { replace: true });
    }
  }

  if (accessDenied) {
    return (
      <PageContainer>
        <LoadErrorState distinguishForbidden error={home.enrollments.error} resource="tus datos" />
      </PageContainer>
    );
  }

  return (
    <TooltipProvider>
      <PageContainer>
        {modules.billingEnabled && <AdmissionInvoicePendingCard />}

        {modules.billingEnabled && (
          <StudentBalanceNotice
            standing={standing.data?.standing}
            isLoading={standing.isLoading}
            isError={standing.isError}
            onRetry={standing.refetch}
          />
        )}

        {home.enrollments.isError ? (
          <LoadErrorState distinguishForbidden error={home.enrollments.error} resource="tus datos" onRetry={home.enrollments.refetch} />
        ) : (
          <>
            <StudentHomeIdentity user={usuario} program={home.program} regularityView={home.regularityView} isLoading={identityLoading} />
            <StudentHomeKpis
              activeCount={home.activeCount}
              progress={home.progress.data}
              programName={home.program?.programName}
              hasSeveralPrograms={home.hasSeveralPrograms}
              progressFailed={home.progress.isError}
              onRetryProgress={home.progress.refetch}
              isLoading={programLoading || home.sectionEnrollments.isLoading}
            />
            {coursesText && (
              <GlassCard className="flex flex-wrap items-center justify-between gap-3 p-4">
                <span className="text-size-13 font-semibold text-ink-soft">{coursesText}</span>
                <Link to="/alumno/mis-cursos" className="inline-flex min-h-11 items-center text-xs font-bold text-primary hover:underline">
                  Ver mis cursos →
                </Link>
              </GlassCard>
            )}
          </>
        )}

        <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
          <UpcomingEventsCard card={upcoming} />

          <GlassCard className="flex flex-col p-5" aria-labelledby="student-news-title">
            <div className="mb-3 flex items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <h2 id="student-news-title" className="flex items-center gap-2 font-heading text-size-15 font-semibold text-ink">
                  <Bell className="h-4 w-4" aria-hidden="true" />
                  Novedades
                </h2>
                <StaleStamp dataUpdatedAt={novedadesActualizadas} isFetching={actualizandoNovedades} isError={errorNovedades} hasData={novedadesRaw !== undefined} />
              </div>
              {sinLeer > 0 && <StatusPill tone="accent">{sinLeer} sin leer</StatusPill>}
            </div>
            {deepLinkedAnnouncementUnavailable && (
              <p role="status" className="mb-2 rounded-control bg-warning-bg px-3 py-2 text-size-12-5 text-warning-ink">
                Ese aviso ya no está disponible.
              </p>
            )}
            {cargandoNovedades ? (
              <div className="flex flex-col gap-2" role="status" aria-busy="true">
                <span className="sr-only">Cargando novedades</span>
                <Skeleton className="h-16 w-full" />
                <Skeleton className="h-16 w-full" />
              </div>
            ) : errorNovedades ? (
              <LoadErrorState bare distinguishForbidden error={errorDeNovedades} resource="las novedades" onRetry={refetchNovedades} />
            ) : novedades.length === 0 ? (
              <EmptyState
                icon="🔔"
                title="Todavía no hay novedades"
                description="Acá vas a ver avisos de tus docentes y recordatorios de tu institución."
              />
            ) : (
              <div className="flex flex-col gap-1">
                {novedadesVisibles.map((aviso) => {
                  const esResaltado = aviso.id === avisoUrlId;
                  return (
                    <AvisoItem
                      key={aviso.id}
                      aviso={aviso}
                      expandidoPorUrl={esResaltado}
                      refProp={esResaltado ? avisoResaltadoRef : null}
                      onAbrir={marcarLeido}
                    />
                  );
                })}
                {novedades.length > NOVEDADES_INICIAL && (
                  <Button
                    variant="text"
                    size="inline"
                    type="button"
                    onClick={toggleFullList}
                    className="mt-1 min-h-11 self-start text-size-12-5 font-semibold"
                  >
                    {showingAll ? 'Mostrar menos avisos' : `Ver todos (${novedades.length})`}
                  </Button>
                )}
              </div>
            )}
          </GlassCard>
        </div>

        <StudentCurrentSubjects
          subjects={home.subjects}
          hasProgram={Boolean(home.program)}
          isLoading={home.sectionEnrollments.isLoading}
          isError={home.sectionEnrollments.isError}
          error={home.sectionEnrollments.error}
          onRetry={home.sectionEnrollments.refetch}
        />
      </PageContainer>
    </TooltipProvider>
  );
}
