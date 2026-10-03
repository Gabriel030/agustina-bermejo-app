import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { BannerCarousel } from '@/components/BannerCarousel';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { useAuth } from '@/context/AuthContext';
import { usePaymentStanding, useMisCursos } from '@/hooks/useAlumno';
import { useNovedades } from '@/hooks/useNovedades';
import { CATEGORIA_AVISO_LABEL } from '@/lib/avisos';
import { tiempoRelativo } from '@/lib/notificaciones';

const ESTADOS_CURSO_ACTIVO = new Set(['ENROLLED', 'IN_PROGRESS', 'WAITLISTED', 'REGULAR']);

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

function AvisoItem({ aviso, expandidoPorUrl, refProp }) {
  const [expandido, setExpandido] = useState(false);
  const bodyRef = useRef(null);
  const [desborda, setDesborda] = useState(false);

  useEffect(() => {
    const el = bodyRef.current;
    if (!el) return;
    setDesborda(el.scrollHeight > el.clientHeight + 2);
  }, [aviso.body]);

  useEffect(() => {
    if (expandidoPorUrl) setExpandido(true);
  }, [expandidoPorUrl]);

  return (
    <div
      ref={refProp}
      data-aviso-id={aviso.id}
      className={`rounded-xl px-3 py-3 transition-colors ${expandidoPorUrl ? 'animate-[stagger-in_0.3s_ease-out_both] bg-accent-soft/40' : ''}`}
    >
      <div className="flex items-start justify-between gap-2 flex-wrap">
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="text-[12px] font-semibold text-ink-soft">{aviso.subjectName}</span>
          {aviso.category && (
            <span className="text-[11px] text-ink-faint">· {CATEGORIA_AVISO_LABEL[aviso.category] ?? aviso.category}</span>
          )}
        </div>
        <div className="flex items-center gap-1.5 flex-wrap">
          {aviso.urgent && <StatusPill tone="danger">Urgente</StatusPill>}
          <span className="text-[10.5px] text-ink-faint">
            {fueEditado(aviso) ? `editado ${tiempoRelativo(aviso.updatedAt)}` : tiempoRelativo(aviso.createdAt)}
          </span>
        </div>
      </div>
      <div className="mt-1 text-[13.5px] font-semibold text-ink">{aviso.title}</div>
      <div
        ref={bodyRef}
        className={`mt-1 text-[13px] text-ink-soft leading-relaxed overflow-hidden ${!expandido ? 'line-clamp-6' : ''}`}
      >
        {aviso.body}
      </div>
      {desborda && (
        <button
          type="button"
          onClick={() => setExpandido((v) => !v)}
          className="mt-1 text-[12px] font-semibold text-primary"
        >
          {expandido ? 'Ver menos' : 'Ver más'}
        </button>
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
  const { data: novedadesRaw, isLoading: cargandoNovedades, isError: errorNovedades } = useNovedades();
  const [searchParams] = useSearchParams();
  const [verTodos, setVerTodos] = useState(false);
  const avisoResaltadoRef = useRef(null);

  const cargandoPago = cargandoStanding;
  const pago = standing ? estadoDePago(standing.standing) : null;
  const cursosActivos = (cursos ?? []).filter((c) => ESTADOS_CURSO_ACTIVO.has(c.status));

  const avisoUrlId = searchParams.get('aviso') ? Number(searchParams.get('aviso')) : null;
  const novedades = [...(novedadesRaw ?? [])].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
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

      {/* Identidad + estado de pago */}
      <GlassCard className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3.5">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-accent-2 to-brand font-heading text-base font-bold text-white">
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
            <StatusPill tone={pago.tone} dot className="px-3.5 py-1.5 text-[12px]">
              {pago.label}
            </StatusPill>
          )
        )}
      </GlassCard>

      {cursosActivos.length > 0 && (
        <GlassCard className="flex flex-wrap items-center justify-between gap-3 p-4">
          <span className="text-[13px] font-semibold text-ink-soft">
            Tenés {cursosActivos.length} curso{cursosActivos.length === 1 ? '' : 's'} en curso
          </span>
          <Link to="/alumno/mis-cursos" className="text-xs font-bold text-primary hover:underline">
            Ver mis cursos →
          </Link>
        </GlassCard>
      )}

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[2fr_1fr]">
        <GlassCard className="flex flex-col p-5">
          <div className="mb-3 font-heading text-[15px] font-semibold text-ink">Novedades</div>
          {cargandoNovedades ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16 w-full" />
              <Skeleton className="h-16 w-full" />
            </div>
          ) : errorNovedades ? (
            <div className="rounded-xl bg-danger-bg px-4 py-3 text-[13px] text-danger">
              No se pudieron cargar las novedades.
            </div>
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
                  />
                );
              })}
              {novedades.length > NOVEDADES_INICIAL && (
                <button
                  type="button"
                  onClick={() => setVerTodos((v) => !v)}
                  className="mt-1 self-start text-[12.5px] font-semibold text-primary"
                >
                  {verTodos ? 'Mostrar menos avisos' : `Ver todos (${novedades.length})`}
                </button>
              )}
            </div>
          )}
        </GlassCard>

        <GlassCard className="flex flex-col gap-3 p-5">
          <div className="font-heading text-[15px] font-semibold text-ink">Estado de cuenta</div>
          {cargandoPago ? (
            <Skeleton className="h-14 w-full" />
          ) : standing?.standing === 'MOROSO' ? (
            <div className="rounded-xl bg-warning-bg px-4 py-3">
              <div className="text-xs font-semibold text-warning">Saldo pendiente</div>
              <div className="text-[13px] text-ink-soft mt-1">Revisá tus cuotas en la sección de pagos.</div>
            </div>
          ) : (
            <div className="rounded-xl bg-success-bg px-4 py-3 text-[13px] font-semibold text-success">
              No tenés saldo pendiente.
            </div>
          )}
        </GlassCard>
      </div>

      <GlassCard className="p-5">
        <div className="flex items-center justify-between">
          <div className="font-heading text-[15px] font-semibold text-ink">De tu interés</div>
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
