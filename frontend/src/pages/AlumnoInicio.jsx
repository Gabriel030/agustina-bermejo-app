import { Link } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { BannerCarousel } from '@/components/BannerCarousel';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { useAuth } from '@/context/AuthContext';
import { usePaymentStanding, useDebtSummary, useMisCursos } from '@/hooks/useAlumno';

// Estados "activos" de un CourseEnrollment — mismos que puede setear
// CourseEnrollmentService (ENROLLED por default, IN_PROGRESS/WAITLISTED/REGULAR
// variantes en curso); APPROVED/FAILED/WITHDRAWN son terminales, no cuentan.
const ESTADOS_CURSO_ACTIVO = new Set(['ENROLLED', 'IN_PROGRESS', 'WAITLISTED', 'REGULAR']);

// 3 estados de pago (decisión de producto, Épica 2 Fase 1) — derivados de
// combinar payment-standing (AL_DIA/MOROSO/SIN_INFORMACION) con
// debt-summary.tieneDeuda, sin necesitar un endpoint nuevo: AL_DIA con deuda
// pendiente (no vencida) es un estado propio ("Cuota pendiente"), distinto de
// AL_DIA sin deuda ("Al día") — el backend no distingue los dos, la UI sí.
function estadoDePago(standing, tieneDeuda) {
  if (standing === 'MOROSO') return { label: 'Atrasado', tone: 'danger' };
  if (standing === 'AL_DIA' && tieneDeuda) return { label: 'Cuota pendiente', tone: 'warning' };
  if (standing === 'AL_DIA') return { label: 'Al día', tone: 'success' };
  return { label: 'Sin información', tone: 'neutral' };
}

function iniciales(nombre, apellido) {
  return `${nombre?.[0] ?? ''}${apellido?.[0] ?? ''}`.toUpperCase();
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
  const { data: deuda, isLoading: cargandoDeuda } = useDebtSummary();
  const { data: cursos } = useMisCursos();

  const cargandoPago = cargandoStanding || cargandoDeuda;
  const pago = standing && deuda ? estadoDePago(standing.standing, deuda.tieneDeuda) : null;
  const cursosActivos = (cursos ?? []).filter((c) => ESTADOS_CURSO_ACTIVO.has(c.status));

  return (
    <PageContainer>
      {/* Contenido de cada slide: placeholder genérico por ahora; el carrusel
          configurable por Administrativo/Director es una feature aparte (ver spec).
          La mecánica (auto-avance, flechas, dots) sí es real. */}
      <BannerCarousel />

      {/* Identidad + estado de pago */}
      <GlassCard className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3.5">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-accent-2 to-accent font-heading text-base font-bold text-white">
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
          <div className="mb-1 font-heading text-[15px] font-semibold text-ink">Novedades</div>
          <EmptyState
            icon="🔔"
            title="Todavía no hay novedades"
            description="Acá vas a ver avisos de tus docentes y recordatorios de tu institución."
          />
        </GlassCard>

        <GlassCard className="flex flex-col gap-3 p-5">
          <div className="font-heading text-[15px] font-semibold text-ink">Estado de cuenta</div>
          {cargandoDeuda ? (
            <Skeleton className="h-14 w-full" />
          ) : deuda?.tieneDeuda ? (
            <div className="rounded-xl bg-warning-bg px-4 py-3">
              <div className="text-xs font-semibold text-warning">Saldo pendiente</div>
              <div className="font-heading text-xl font-bold text-ink">
                ${deuda.montoAdeudado?.toLocaleString('es-AR')}
              </div>
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
