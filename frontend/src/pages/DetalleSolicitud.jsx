import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { EstadoSolicitudBadge } from '@/components/EstadoSolicitudBadge';
import { Skeleton } from '@/components/Skeleton';
import { apiFetch } from '@/api/client';
import { formatFecha } from '@/lib/utils';

// DocumentType del backend (entity.DocumentType) — mismos 3 tipos que pide el
// wizard de inscripción (InscripcionWizard.jsx): DNI frente/dorso, analítico.
const TIPOS_DOCUMENTO = [
  { type: 'DNI_FRENTE', label: 'DNI — frente' },
  { type: 'DNI_DORSO', label: 'DNI — dorso' },
  { type: 'ANALITICO_SECUNDARIO', label: 'Analítico del secundario' },
];

// Mapeo 1:1 a Detalle de solicitud.dc.html.
//
// No existe GET /me/program-enrollment-applications/{id} (solo la lista) ni
// ningún GET que devuelva los documentos ya subidos de una solicitud — ver
// "Deuda técnica y gaps de QA" entrada 9. Por eso:
// 1. El detalle se resuelve buscando por id dentro de la misma lista que ya
//    usa MisSolicitudes.jsx (GET /me/program-enrollment-applications), no
//    hay endpoint de detalle propio.
// 2. El estado "subido" de cada documento vive solo en memoria de este
//    componente (subidosEnSesion) — no sobrevive un reload. El backend sí
//    guarda el archivo; la UI simplemente no tiene de dónde leerlo de vuelta.
export function DetalleSolicitud() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [solicitud, setSolicitud] = useState(null);
  const [noEncontrada, setNoEncontrada] = useState(false);
  const [errorCarga, setErrorCarga] = useState(false);
  const [subidosEnSesion, setSubidosEnSesion] = useState({});
  const [subiendo, setSubiendo] = useState(null);
  const [errorSubida, setErrorSubida] = useState(null);
  const fileInputRefs = useRef({});

  useEffect(() => {
    apiFetch('/me/program-enrollment-applications')
      .then((solicitudes) => {
        const propia = solicitudes.find((s) => String(s.id) === id);
        if (!propia) {
          setNoEncontrada(true);
          return;
        }
        setSolicitud(propia);
      })
      .catch(() => setErrorCarga(true));
  }, [id]);

  function elegirArchivo(type) {
    fileInputRefs.current[type]?.click();
  }

  async function subirArchivo(type, file) {
    if (!file) return;
    setSubiendo(type);
    setErrorSubida(null);
    try {
      const formData = new FormData();
      formData.append('file', file);
      await apiFetch(`/me/program-enrollment-applications/${id}/documents?type=${type}`, {
        method: 'POST',
        body: formData,
      });
      setSubidosEnSesion((prev) => ({ ...prev, [type]: file.name }));
    } catch {
      setErrorSubida('No pudimos subir el archivo. Probá de nuevo.');
    } finally {
      setSubiendo(null);
    }
  }

  if (errorCarga) {
    return <GlassCard className="p-6 text-sm text-danger">No pudimos cargar la solicitud. Probá recargar la página.</GlassCard>;
  }

  if (noEncontrada) {
    return (
      <GlassCard className="flex flex-1 flex-col items-center justify-center gap-2 p-12 text-center">
        <div className="font-heading text-xl font-semibold text-ink">Solicitud no encontrada</div>
        <button type="button" onClick={() => navigate('/mis-solicitudes')} className="text-sm font-semibold text-primary">
          Volver a Mis solicitudes
        </button>
      </GlassCard>
    );
  }

  if (!solicitud) {
    return (
      <PageContainer>
        <Skeleton className="h-9 w-72" />
        <Skeleton className="h-40" />
        <Skeleton className="h-40" />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <button
        type="button"
        onClick={() => navigate('/mis-solicitudes')}
        className="flex w-fit items-center gap-1.5 text-[13px] font-semibold text-ink-soft hover:text-ink"
      >
        ← Mis solicitudes
      </button>

      <div className="flex items-start justify-between">
        <div>
          <div className="font-heading text-2xl font-bold tracking-tight text-ink">{solicitud.programName}</div>
          <div className="mt-1 text-[13px] text-ink-faint">Solicitada el {formatFecha(solicitud.requestedAt)}</div>
        </div>
        <EstadoSolicitudBadge status={solicitud.status} />
      </div>

      {solicitud.status === 'REJECTED' ? (
        <GlassCard className="bg-danger-bg p-5 text-[13px] text-danger">
          <span className="font-bold">Solicitud rechazada.</span> {solicitud.rejectionReason}
        </GlassCard>
      ) : (
        <>
          <GlassCard className="p-5 text-[13px] text-ink-soft">
            Subí los 3 documentos para que Preceptoría revise tu solicitud. Podés volver más tarde a completar los que falten.
          </GlassCard>

          <div className="font-heading text-[15px] font-semibold text-ink-soft">Documentación</div>

          {errorSubida && <div className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">{errorSubida}</div>}

          <GlassCard className="flex flex-col p-1">
            {TIPOS_DOCUMENTO.map(({ type, label }) => {
              const nombreArchivo = subidosEnSesion[type];
              return (
                <div key={type} className="flex items-center justify-between border-b border-border px-4 py-4 last:border-b-0">
                  <div className="flex items-center gap-3.5">
                    <div
                      className={`flex h-9 w-9 items-center justify-center rounded-xl ${nombreArchivo ? 'bg-success-bg' : 'bg-neutral-bg'}`}
                    >
                      {nombreArchivo ? '✓' : '＋'}
                    </div>
                    <div>
                      <div className="text-[13.5px] font-bold text-ink">{label}</div>
                      <div className={`text-xs ${nombreArchivo ? 'text-success' : 'text-ink-faint'}`}>
                        {nombreArchivo ? `${nombreArchivo} · subido` : 'Sin subir todavía'}
                      </div>
                    </div>
                  </div>
                  <input
                    ref={(el) => (fileInputRefs.current[type] = el)}
                    type="file"
                    className="hidden"
                    onChange={(e) => subirArchivo(type, e.target.files?.[0])}
                  />
                  <button
                    type="button"
                    disabled={subiendo === type}
                    onClick={() => elegirArchivo(type)}
                    className="rounded-xl border border-border bg-surface px-4 py-2 text-[12.5px] font-bold text-ink disabled:opacity-60"
                  >
                    {subiendo === type ? 'Subiendo…' : nombreArchivo ? 'Reemplazar' : 'Subir archivo'}
                  </button>
                </div>
              );
            })}
          </GlassCard>
        </>
      )}
    </PageContainer>
  );
}
