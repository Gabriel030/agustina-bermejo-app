import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { EstadoSolicitudBadge } from '@/features/enrollment/components/EstadoSolicitudBadge';
import { useMisSolicitudesCarrera, useMisSolicitudesCurso } from '@/features/enrollment/hooks/useCatalogo';

// Combina solicitudes de carrera (ProgramEnrollmentApplication, status propio)
// y de curso (CourseEnrollmentApplication, paymentStatus del PaymentReceipt) en
// una sola bandeja — dos vocabularios de estado distintos, un mismo componente
// visual (EstadoSolicitudBadge ya sabe mapear ambos). Solo las filas de carrera
// navegan a un detalle: no existe pantalla de detalle de solicitud de curso
// (no hace falta — no tiene documentación que subir, ver spec funcional).
export function TabMisSolicitudes() {
  const navigate = useNavigate();
  const { data: solicitudesCarrera, isLoading: cargandoCarrera, isError: errorCarrera } = useMisSolicitudesCarrera();
  const { data: solicitudesCurso, isLoading: cargandoCurso, isError: errorCurso } = useMisSolicitudesCurso();

  const filas = useMemo(() => {
    const carrera = (solicitudesCarrera ?? []).map((s) => ({
      key: `carrera-${s.id}`,
      tipo: 'Carrera',
      nombre: s.programName,
      status: s.status,
      admissionInvoice: s.admissionInvoice,
      rejectionReason: s.rejectionReason,
      onClick: () => navigate(`/mis-solicitudes/${s.id}`),
    }));
    const curso = (solicitudesCurso ?? []).map((s) => ({
      key: `curso-${s.id}`,
      tipo: 'Curso',
      nombre: s.courseName,
      status: s.courseEnrollmentStatus === 'WAITLISTED' ? 'WAITLISTED' : s.paymentStatus,
      rejectionReason: s.rejectionReason,
      onClick: null,
    }));
    return [...carrera, ...curso];
  }, [solicitudesCarrera, solicitudesCurso, navigate]);

  if (errorCarrera || errorCurso) {
    return <GlassCard className="p-6 text-sm text-danger">No pudimos cargar tus solicitudes. Probá recargar la página.</GlassCard>;
  }
  if (cargandoCarrera || cargandoCurso) {
    return <div className="text-sm text-ink-faint">Cargando…</div>;
  }
  if (filas.length === 0) {
    return (
      <GlassCard className="p-6 text-sm text-ink-faint">
        Todavía no pediste el alta en ninguna carrera ni te inscribiste a un curso — elegí uno en las otras tabs para empezar.
      </GlassCard>
    );
  }

  return (
    <GlassCard className="flex flex-col p-2 pb-3">
      <div className="grid grid-cols-[0.8fr_2fr_1.3fr] gap-3 border-b border-border px-5 pb-3 pt-4 text-[11px] font-bold uppercase tracking-wide text-ink-faint">
        <div>Tipo</div>
        <div>Nombre</div>
        <div>Estado</div>
      </div>
      {filas.map((fila) => {
        const Envoltorio = fila.onClick ? 'button' : 'div';
        return (
          <Envoltorio
            key={fila.key}
            type={fila.onClick ? 'button' : undefined}
            onClick={fila.onClick ?? undefined}
            className={`grid grid-cols-[0.8fr_2fr_1.3fr] items-center gap-3 border-b border-border px-5 py-4 text-left last:border-b-0 ${fila.onClick ? 'hover:bg-surface' : ''}`}
          >
            {/* min-w-0: los items de grid, igual que los de flex, no se achican
                por debajo del ancho intrínseco de su contenido por default —
                sin esto el pill de estado empuja la fila (y la página) más
                allá del viewport en mobile. */}
            <div className="min-w-0 text-xs font-semibold text-ink-faint">{fila.tipo}</div>
            <div className="min-w-0 truncate text-[13.5px] font-bold text-ink">{fila.nombre}</div>
            <div className="min-w-0">
              <EstadoSolicitudBadge status={fila.status} admissionInvoice={fila.admissionInvoice} />
            </div>
          </Envoltorio>
        );
      })}
    </GlassCard>
  );
}
