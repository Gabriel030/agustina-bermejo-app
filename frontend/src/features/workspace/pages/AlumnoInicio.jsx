import { Button } from '@/components/ui/button';
import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { BannerCarousel } from '@/components/BannerCarousel';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { LoadErrorState } from '@/components/LoadErrorState';
import { useAuth } from '@/context/AuthContext';
import { usePaymentStanding, AdmissionInvoicePendingCard, useAdmissionInvoicesPending } from '@/features/billing';
import { useMisCursos } from '@/features/enrollment';
import { useNovedades, useMarcarAvisoLeido } from '@/features/announcements';
import { ScheduleCard } from '@/features/schedule';
import { StaleStamp } from '@/components/offline/StaleStamp';
import { CATEGORIA_AVISO_LABEL } from '@/lib/avisos';
import { tiempoRelativo } from '@/lib/notificaciones';

const ESTADOS_CURSO_ACTIVO = new Set(['ENROLLED', 'IN_PROGRESS', 'REGULAR']);

function resumenDeCursos(activos, enEspera) {
  const partes = [];
  if (activos > 0) partes.push(`Tenés ${activos} curso${activos === 1 ? '' : 's'} en curso`);
  if (enEspera > 0) {
    const espera = `lista de espera de ${enEspera} curso${enEspera === 1 ? '' : 's'}`;
    partes.push(activos > 0 ? `y estás en ${espera}` : `Estás en ${espera}`);
  }
  return partes.join(' ');
}

const NOVEDADES_INICIAL = 3;

function estadoDePago(standing) {
  if (standing === 'MOROSO') return { label: 'Atrasado', tone: 'danger' };
  if (standing === 'AL_DIA') return { label: 'Al día', tone: 'success' };
  return { label: 'Sin información', tone: 'neutral' };
}

function iniciales(nombre, apellido) {
  return `${nombre?.[0] ?? ''}${apellido?.[0] ?? ''}`.toUpperCase();
}

function fueEditado(aviso) {
  if (!aviso.updatedAt || !aviso.createdAt) return false;
  return new Date(aviso.updatedAt) - new Date(aviso.createdAt) > 60_000;
}

function AvisoItem({ aviso, expandidoPorUrl, refProp, onAbrir }) {
  const [expandido, setExpandido] = useState(false);
  const bodyRef = useRef(null);
  const [desborda, setDesborda] = useState(false);

  useEffect(() => {
    const el = bodyRef.current;
    if (!el) return;
    setDesborda(el.scrollHeight > el.clientHeight + 2);
  }, [aviso.body]);

  useEffect(() => {
    if (expandidoPorUrl) {
      setExpandido(true);
      onAbrir(aviso);
    }
  }, [expandidoPorUrl, aviso, onAbrir]);

  function alternar() {
    if (!expandido) onAbrir(aviso);
    setExpandido((v) => !v);
  }

  return (
    <div
      ref={refProp}
      data-aviso-id={aviso.id}
      className={`rounded-xl px-3 py-3 transition-colors ${expandidoPorUrl ? 'animate-[stagger-in_0.3s_ease-out_both] bg-accent-soft/40' : ''}`}
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
          <span className="text-size-10-5 text-ink-faint">
            {fueEditado(aviso) ? `editado ${tiempoRelativo(aviso.updatedAt)}` : tiempoRelativo(aviso.createdAt)}
          </span>
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
          className="w-full text-left text-size-13-5 font-semibold text-ink"
        >
          {aviso.title}
        </button>
      </div>
      <div
        ref={bodyRef}
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
          className="mt-1 text-size-12 font-semibold"
          >
          {expandido ? 'Ver menos' : 'Ver más'}
        </Button>
      )}
    </div>
  );
}

// Mapeo al mockup Alumno · Inicio (Vidrio Líquido), con la carrera sacada del
// todo — decisión de producto: Inicio no se ata a ninguna carrera puntual
// (ver Spec - Núcleo académico del alumno, Épica 2 Fase 1). Banner, Novedades
// y "De tu interés" quedan con contenido placeholder (features sin definir
// todavía), pero con el mismo tratamiento visual del mockup — no un párrafo
// de texto suelto.
export function AlumnoInicio() {
  const { usuario } = useAuth();
  const { data: standing, isLoading: cargandoStanding } = usePaymentStanding();
  const { data: cursos } = useMisCursos();
  const { pendientes: matriculasPendientes } = useAdmissionInvoicesPending();
  const { data: novedadesRaw, isLoading: cargandoNovedades, isError: errorNovedades, isFetching: actualizandoNovedades, refetch: refetchNovedades, dataUpdatedAt: novedadesActualizadas } = useNovedades();
  const [searchParams] = useSearchParams();
  const [verTodos, setVerTodos] = useState(false);
  const marcarLeido = useMarcarAvisoLeido();
  const avisoResaltadoRef = useRef(null);

  const cargandoPago = cargandoStanding;
  const pago = standing ? estadoDePago(standing.standing) : null;
  const cursosActivos = (cursos ?? []).filter((c) => ESTADOS_CURSO_ACTIVO.has(c.status));
  const cursosEnEspera = (cursos ?? []).filter((c) => c.status === 'WAITLISTED');

  const avisoUrlId = searchParams.get('aviso') ? Number(searchParams.get('aviso')) : null;
  const novedades = [...(novedadesRaw ?? [])].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  const sinLeer = novedades.filter((aviso) => !aviso.readAt).length;
  const novedadesVisibles = verTodos ? novedades : novedades.slice(0, NOVEDADES_INICIAL);

  useEffect(() => {
    if (!avisoUrlId || !avisoResaltadoRef.current) return;
    avisoResaltadoRef.current.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, [avisoUrlId, novedades.length]);

  return (
    <PageContainer>
      {/* Contenido de cada slide: placeholder genérico por ahora; el carrusel
          configurable por Administrativo/Director es una feature aparte (ver spec).
          La mecánica (auto-avance, flechas, dots) sí es real. */}
      <BannerCarousel />

      <AdmissionInvoicePendingCard />

      {/* Identidad + estado de pago */}
      <GlassCard className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3.5">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-accent-2 to-brand font-heading text-base font-bold text-on-accent">
            {iniciales(usuario?.nombre, usuario?.apellido)}
          </div>
          <div>
            <div className="font-heading text-lg font-bold tracking-tight text-ink">
              {usuario?.nombre} {usuario?.apellido}
            </div>
            {standing?.studentId && <div className="text-xs text-ink-faint">Legajo N° {standing.studentId}</div>}
          </div>
        </div>

        {cargandoPago ? (
          <Skeleton className="h-7 w-32" />
        ) : (
          pago && (
            <StatusPill tone={pago.tone} dot className="px-3.5 py-1.5 text-size-12">
              {pago.label}
            </StatusPill>
          )
        )}
      </GlassCard>

      <ScheduleCard />

      {cursosActivos.length + cursosEnEspera.length > 0 && (
        <GlassCard className="flex flex-wrap items-center justify-between gap-3 p-4">
          <span className="text-size-13 font-semibold text-ink-soft">{resumenDeCursos(cursosActivos.length, cursosEnEspera.length)}</span>
          <Link to="/alumno/mis-cursos" className="text-xs font-bold text-primary hover:underline">
            Ver mis cursos →
          </Link>
        </GlassCard>
      )}

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[2fr_1fr]">
        <GlassCard className="flex flex-col p-5">
          <div className="mb-3 flex items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <div className="font-heading text-size-15 font-semibold text-ink">Novedades</div>
              <StaleStamp dataUpdatedAt={novedadesActualizadas} isFetching={actualizandoNovedades} isError={errorNovedades} hasData={novedadesRaw !== undefined} />
            </div>
            {sinLeer > 0 && <StatusPill tone="accent">{sinLeer} sin leer</StatusPill>}
          </div>
          {cargandoNovedades ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16 w-full" />
              <Skeleton className="h-16 w-full" />
            </div>
          ) : errorNovedades ? (
            <LoadErrorState bare resource="las novedades" onRetry={refetchNovedades} />
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
                  onClick={() => setVerTodos((v) => !v)}
                  className="mt-1 self-start text-size-12-5 font-semibold"
                  >
                  {verTodos ? 'Mostrar menos avisos' : `Ver todos (${novedades.length})`}
                </Button>
              )}
            </div>
          )}
        </GlassCard>

        <GlassCard className="flex flex-col gap-3 p-5">
          <div className="font-heading text-size-15 font-semibold text-ink">Estado de cuenta</div>
          {cargandoPago ? (
            <Skeleton className="h-14 w-full" />
          ) : standing?.standing === 'MOROSO' ? (
            <div className="rounded-xl bg-warning-bg px-4 py-3">
              <div className="text-xs font-semibold text-warning">Saldo pendiente</div>
              <div className="text-size-13 text-ink-soft mt-1">Revisá tus cuotas en la sección de pagos.</div>
            </div>
          ) : matriculasPendientes.length > 0 ? (
            <div className="rounded-xl bg-warning-bg px-4 py-3">
              <div className="text-xs font-semibold text-warning">Matrícula de ingreso pendiente</div>
            </div>
          ) : (
            <div className="rounded-xl bg-success-bg px-4 py-3 text-size-13 font-semibold text-success">
              No tenés saldo pendiente.
            </div>
          )}
        </GlassCard>
      </div>

      <GlassCard className="p-5">
        <div className="flex items-center justify-between">
          <div className="font-heading text-size-15 font-semibold text-ink">De tu interés</div>
          <Link to="/catalogo?tab=cursos" className="text-xs font-bold text-primary hover:underline">
            Ver todos los cursos →
          </Link>
        </div>
        <div className="mt-4 grid grid-cols-2 gap-3 md:grid-cols-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="flex flex-col gap-2 rounded-xl border border-dashed border-border p-4">
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-accent-soft text-sm">✨</span>
              <span className="text-xs font-semibold text-ink-faint">Próximamente</span>
            </div>
          ))}
        </div>
      </GlassCard>
    </PageContainer>
  );
}
