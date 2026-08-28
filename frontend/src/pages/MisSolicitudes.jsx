import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { EstadoSolicitudBadge } from '@/components/EstadoSolicitudBadge';
import { apiFetch } from '@/api/client';
import { formatFecha } from '@/lib/utils';

// Mapeo 1:1 a Mis solicitudes.dc.html. Sin tabs Carreras/Cursos (a diferencia
// de la bandeja de Oferta Académica) — ProgramEnrollmentApplicationController
// solo cubre carreras, la inscripción a curso es directa y no genera solicitud.
export function MisSolicitudes() {
  const [solicitudes, setSolicitudes] = useState(null);
  const [errorCarga, setErrorCarga] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    apiFetch('/me/program-enrollment-applications')
      .then(setSolicitudes)
      .catch(() => setErrorCarga(true));
  }, []);

  return (
    <div className="flex flex-1 flex-col gap-7">
      <div>
        <div className="font-heading text-2xl font-bold tracking-tight text-ink">Mis solicitudes</div>
        <div className="mt-1 text-sm text-ink-faint">
          Estado de cada carrera que pediste — hacé click para ver el detalle y subir documentación.
        </div>
      </div>

      {errorCarga && (
        <GlassCard className="p-6 text-sm text-danger">No pudimos cargar tus solicitudes. Probá recargar la página.</GlassCard>
      )}

      {solicitudes === null && !errorCarga && <div className="text-sm text-ink-faint">Cargando…</div>}

      {solicitudes?.length === 0 && (
        <GlassCard className="p-6 text-sm text-ink-faint">
          Todavía no pediste el alta en ninguna carrera — elegí una en el Catálogo para empezar.
        </GlassCard>
      )}

      {solicitudes?.length > 0 && (
        <GlassCard className="flex flex-col p-2 pb-3">
          <div className="grid grid-cols-[2.2fr_1fr_1.3fr] gap-3 border-b border-border px-5 pb-3 pt-4 text-[11px] font-bold uppercase tracking-wide text-ink-faint">
            <div>Carrera</div>
            <div>Solicitada</div>
            <div>Estado</div>
          </div>
          {solicitudes.map((solicitud) => (
            <button
              key={solicitud.id}
              type="button"
              onClick={() => navigate(`/mis-solicitudes/${solicitud.id}`)}
              className="grid grid-cols-[2.2fr_1fr_1.3fr] items-center gap-3 border-b border-border px-5 py-4 text-left last:border-b-0 hover:bg-surface"
            >
              <div className="text-[13.5px] font-bold text-ink">{solicitud.programName}</div>
              <div className="text-[12.5px] text-ink-soft">{formatFecha(solicitud.requestedAt)}</div>
              <div>
                <EstadoSolicitudBadge status={solicitud.status} />
              </div>
            </button>
          ))}
        </GlassCard>
      )}
    </div>
  );
}
