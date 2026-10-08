import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { EstadoSolicitudBadge } from '@/features/enrollment/components/EstadoSolicitudBadge';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { apiFetch } from '@/api/client';
import { Button } from '@/components/ui/button';
import { formatFecha, formatMoney } from '@/lib/utils';

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
  const [previousDocuments, setPreviousDocuments] = useState({});
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
    apiFetch('/me/documents')
      .then((documents) =>
        setPreviousDocuments(Object.fromEntries((documents ?? []).map((studentDocument) => [studentDocument.type, studentDocument.uploadedAt]))),
      )
      .catch(() => setPreviousDocuments({}));
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
        <EstadoSolicitudBadge status={solicitud.status} admissionInvoice={solicitud.admissionInvoice} />
      </div>

      {solicitud.status === 'REJECTED' && (
        <GlassCard className="bg-danger-bg p-5 text-[13px] text-danger">
          <span className="font-bold">Solicitud rechazada.</span> {solicitud.rejectionReason}
        </GlassCard>
      )}

      {solicitud.status === 'APPROVED' && !solicitud.admissionInvoice && (
        <GlassCard className="bg-neutral-bg p-5 text-[13px] text-ink-soft">
          <span className="font-bold text-ink">Solicitud aprobada.</span> Estamos generando la factura de la matrícula de ingreso; en unos minutos
          va a aparecer acá y en Mis pagos. Si no aparece, avisá a Secretaría.
        </GlassCard>
      )}

      {solicitud.status === 'APPROVED' && solicitud.admissionInvoice?.paymentStatus === 'PAID' && (
        <GlassCard className="bg-success-bg p-5 text-[13px] text-success">
          <span className="font-bold">Solicitud aprobada.</span> Pagaste la matrícula de ingreso: tu inscripción a la carrera está confirmada.
        </GlassCard>
      )}

      {solicitud.status === 'APPROVED' && solicitud.admissionInvoice && solicitud.admissionInvoice.paymentStatus !== 'PAID' && (
        <GlassCard className="flex flex-col gap-3 p-5">
          <div className="text-[13px] text-ink-soft">
            <span className="font-bold text-ink">Solicitud aprobada.</span> Cuando confirmemos el pago, tu inscripción a la carrera queda confirmada.
          </div>
          <div className="flex items-center justify-between rounded-xl bg-neutral-bg px-4 py-3">
            <div>
              <div className="text-xs text-ink-faint">Matrícula de ingreso</div>
              <div className="font-heading text-lg font-bold text-ink">{formatMoney(solicitud.admissionInvoice.total)}</div>
              <div className="text-xs text-ink-faint">
                {solicitud.admissionInvoice.paymentStatus === 'OVERDUE' ? 'Venció' : 'Vence'} el {formatFecha(solicitud.admissionInvoice.dueDate)}
              </div>
            </div>
            {solicitud.admissionInvoice.receiptStatus === 'PENDING_REVIEW' ? (
              <div className="flex flex-col items-end gap-1.5">
                <StatusPill tone="warning">Pago en revisión</StatusPill>
                <button
                  type="button"
                  onClick={() => navigate(`/alumno/pagos/${solicitud.admissionInvoice.id}/transferencia`)}
                  className="text-[12.5px] font-semibold text-primary"
                >
                  Ver estado del pago
                </button>
              </div>
            ) : (
              <Button variant="gradient" onClick={() => navigate(`/alumno/pagos/${solicitud.admissionInvoice.id}/seleccion`)}>
                Pagar matrícula
              </Button>
            )}
          </div>
          {solicitud.admissionInvoice.receiptStatus === 'PENDING_REVIEW' && (
            <div className="text-xs text-ink-faint">
              Subiste el comprobante el {formatFecha(solicitud.admissionInvoice.receiptUploadedAt)}; cuando lo aprobemos tu inscripción queda confirmada.
            </div>
          )}
        </GlassCard>
      )}

      {solicitud.status !== 'REJECTED' && solicitud.status !== 'APPROVED' && (
        <>
          <GlassCard className="p-5 text-[13px] text-ink-soft">
            Tu solicitud está en revisión. No se te cobró nada: si la aprueban, vas a recibir la factura de la matrícula de ingreso y vas
            a tener 10 días para pagarla. Te avisamos por notificación cuando haya novedades.
          </GlassCard>

          <div className="font-heading text-[15px] font-semibold text-ink-soft">Documentación</div>

          {errorSubida && <div className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">{errorSubida}</div>}

          <GlassCard className="flex flex-col p-1">
            {TIPOS_DOCUMENTO.map(({ type, label }) => {
              const nombreArchivo = subidosEnSesion[type];
              const previousUploadDate = previousDocuments[type];
              const uploaded = Boolean(nombreArchivo || previousUploadDate);
              return (
                <div key={type} className="flex items-center justify-between border-b border-border px-4 py-4 last:border-b-0">
                  <div className="flex items-center gap-3.5">
                    <div
                      className={`flex h-9 w-9 items-center justify-center rounded-xl ${uploaded ? 'bg-success-bg' : 'bg-neutral-bg'}`}
                    >
                      {uploaded ? '✓' : '＋'}
                    </div>
                    <div>
                      <div className="text-[13.5px] font-bold text-ink">{label}</div>
                      <div className={`text-xs ${uploaded ? 'text-success' : 'text-ink-faint'}`}>
                        {nombreArchivo
                          ? `${nombreArchivo} · subido`
                          : previousUploadDate
                            ? `Subido el ${formatFecha(previousUploadDate)}`
                            : 'Sin subir todavía'}
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
                    {subiendo === type ? 'Subiendo…' : uploaded ? 'Reemplazar' : 'Subir archivo'}
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
