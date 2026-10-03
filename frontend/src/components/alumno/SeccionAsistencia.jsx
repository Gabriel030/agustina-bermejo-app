import { useState } from 'react';
import { AlertTriangle, CheckCircle2, Upload } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { useMiAsistencia, useMiRiesgoDeAsistencia, useJustificarInasistencia } from '@/hooks/useAlumno';

const MESES = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
];

const COLOR_DIA = {
  PRESENT: 'bg-success-bg text-success',
  LATE: 'bg-success-bg text-success',
  ABSENT: 'cursor-pointer bg-danger-bg text-danger transition-opacity hover:opacity-70',
  JUSTIFICATION_PENDING: 'bg-warning-bg text-warning',
  JUSTIFIED: 'bg-success-bg text-success',
};

const TITULO_DIA = {
  PRESENT: 'Presente',
  LATE: 'Llegada tarde',
  ABSENT: 'Justificar',
  JUSTIFICATION_PENDING: 'Justificación pendiente de aprobación',
  JUSTIFIED: 'Ausencia justificada',
};

function agruparPorMes(registros) {
  const grupos = new Map();
  for (const r of [...registros].sort((a, b) => a.classDate.localeCompare(b.classDate))) {
    const [anio, mes] = r.classDate.split('-');
    const clave = `${anio}-${mes}`;
    if (!grupos.has(clave)) grupos.set(clave, { anio: Number(anio), mes: Number(mes), dias: [] });
    grupos.get(clave).dias.push(r);
  }
  return [...grupos.values()];
}

// Gráfico de torta con conic-gradient (sin librería de charts) — verde/rojo según
// si ya se excedió el máximo de ausentismo permitido por la comisión (política
// institucional real, ver AttendanceService.calcularRiesgo en el backend).
function GraficoDeTorta({ porcentaje, enRiesgo }) {
  const color = enRiesgo ? 'var(--danger)' : 'var(--success)';
  const deg = Math.round((porcentaje ?? 0) * 360);
  return (
    <div
      className="relative flex h-24 w-24 shrink-0 items-center justify-center rounded-full"
      style={{ background: `conic-gradient(${color} 0deg ${deg}deg, var(--neutral-bg) ${deg}deg 360deg)` }}
    >
      <div className="flex h-16 w-16 items-center justify-center rounded-full bg-surface">
        <span className="font-heading text-sm font-bold text-ink">{Math.round((porcentaje ?? 0) * 100)}%</span>
      </div>
    </div>
  );
}

function InasistenciasJustificadas({ registros }) {
  const justificadas = (registros ?? [])
    .filter((r) => r.status === 'JUSTIFICATION_PENDING' || r.status === 'JUSTIFIED')
    .sort((a, b) => b.classDate.localeCompare(a.classDate));

  if (justificadas.length === 0) return null;

  return (
    <div>
      <div className="mb-2 text-xs font-semibold text-ink-faint">Inasistencias justificadas</div>
      <div className="flex flex-col gap-1.5">
        {justificadas.map((r) => (
          <div key={r.id} className="flex items-center justify-between rounded-lg border border-border px-3 py-2 text-[13px]">
            <span className="text-ink">
              {new Date(`${r.classDate}T00:00:00`).toLocaleDateString('es-AR', { day: 'numeric', month: 'long', year: 'numeric' })}
            </span>
            <span className={`font-semibold ${r.status === 'JUSTIFIED' ? 'text-success' : 'text-warning'}`}>
              {r.status === 'JUSTIFIED' ? 'Aprobada' : 'Pendiente de aprobación'}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

export function SeccionAsistencia({ sectionEnrollmentId }) {
  const { data: registros, isLoading: cargandoAsistencia } = useMiAsistencia(sectionEnrollmentId);
  const { data: riesgo, isLoading: cargandoRiesgo } = useMiRiesgoDeAsistencia(sectionEnrollmentId);
  const justificar = useJustificarInasistencia();
  const [diaSeleccionado, setDiaSeleccionado] = useState(null);
  const [archivo, setArchivo] = useState(null);

  if (cargandoAsistencia || cargandoRiesgo) {
    return (
      <GlassCard className="flex flex-col gap-4 p-5">
        <div className="font-heading text-[15px] font-semibold text-ink">Asistencia</div>
        <Skeleton className="h-24" />
      </GlassCard>
    );
  }

  const total = registros?.length ?? 0;
  const presentes = (registros ?? []).filter((r) => r.status === 'PRESENT' || r.status === 'LATE').length;
  const porcentajeAsistencia = total > 0 ? presentes / total : 0;
  const meses = agruparPorMes(registros ?? []);
  const rawMax = riesgo?.maxAbsencePercentage;
  const normalizedMax = rawMax != null && rawMax > 1 ? rawMax / 100 : rawMax;
  const porcentajeRequerido = normalizedMax != null ? Math.round((1 - normalizedMax) * 100) : null;

  function cerrarModal(abierto) {
    if (!abierto) {
      setDiaSeleccionado(null);
      setArchivo(null);
    }
  }

  async function confirmarJustificacion() {
    if (!diaSeleccionado || !archivo) return;
    await justificar.mutateAsync({ attendanceId: diaSeleccionado.id, comprobante: archivo, sectionEnrollmentId });
    setDiaSeleccionado(null);
    setArchivo(null);
  }

  return (
    <GlassCard className="flex flex-col gap-5 p-5">
      <div className="font-heading text-[15px] font-semibold text-ink">Asistencia</div>

      {total === 0 ? (
        <div className="text-sm text-ink-faint">Todavía no hay clases registradas para esta materia.</div>
      ) : (
        <>
          <div className="flex flex-col items-center gap-4 sm:flex-row">
            <GraficoDeTorta porcentaje={porcentajeAsistencia} enRiesgo={!!riesgo?.libre} />
            <div className="flex flex-1 flex-col gap-3">
              <div className="grid grid-cols-3 gap-3">
                <div>
                  <div className="text-xs font-semibold tracking-wide text-ink-faint uppercase">Clases</div>
                  <div className="font-heading text-xl font-bold text-ink">{total}</div>
                </div>
                <div>
                  <div className="text-xs font-semibold tracking-wide text-ink-faint uppercase">Presentes</div>
                  <div className="font-heading text-xl font-bold text-ink">{presentes}</div>
                </div>
                <div>
                  <div className="text-xs font-semibold tracking-wide text-ink-faint uppercase">% Asistencia</div>
                  <div className="font-heading text-xl font-bold text-ink">{Math.round(porcentajeAsistencia * 100)}%</div>
                </div>
              </div>
              {riesgo?.calculable && porcentajeRequerido != null && (
                <div className={`flex items-center gap-1.5 text-xs font-semibold ${riesgo.libre ? 'text-danger' : 'text-success'}`}>
                  {riesgo.libre ? <AlertTriangle className="h-3.5 w-3.5" /> : <CheckCircle2 className="h-3.5 w-3.5" />}
                  {riesgo.libre
                    ? `Estás por debajo del ${porcentajeRequerido}% de asistencia requerido por la materia`
                    : `Cumplís el ${porcentajeRequerido}% de asistencia requerido por la materia`}
                </div>
              )}
            </div>
          </div>

          <div>
            <div className="mb-2 text-xs font-semibold text-ink-faint">Calendario de clases</div>
            <div className="flex gap-4 overflow-x-auto pb-2">
              {meses.map((grupo) => (
                <div key={`${grupo.anio}-${grupo.mes}`} className="flex w-56 shrink-0 flex-col gap-2 rounded-xl border border-border p-3">
                  <div className="text-xs font-bold text-ink-faint">
                    {MESES[grupo.mes - 1]} {grupo.anio}
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    {grupo.dias.map((dia) => (
                      <button
                        key={dia.id}
                        type="button"
                        disabled={dia.status !== 'ABSENT'}
                        onClick={() => setDiaSeleccionado(dia)}
                        title={TITULO_DIA[dia.status] ?? 'Justificar'}
                        className={`flex h-7 w-7 items-center justify-center rounded-lg text-[11px] font-bold ${COLOR_DIA[dia.status] ?? 'bg-neutral-bg text-ink-faint'}`}
                      >
                        {Number(dia.classDate.split('-')[2])}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <InasistenciasJustificadas registros={registros} />
        </>
      )}

      <Dialog open={!!diaSeleccionado} onOpenChange={cerrarModal}>
        <DialogContent className="max-w-sm rounded-xl border border-border bg-surface p-6 text-ink backdrop-blur-[28px] backdrop-saturate-[1.6]">
          <DialogHeader>
            <DialogTitle>Justificar inasistencia</DialogTitle>
          </DialogHeader>
          {diaSeleccionado && (
            <p className="text-sm text-ink-faint">
              {new Date(`${diaSeleccionado.classDate}T00:00:00`).toLocaleDateString('es-AR', {
                day: 'numeric',
                month: 'long',
                year: 'numeric',
              })}
            </p>
          )}
          <label
            className={`flex cursor-pointer flex-col items-center justify-center gap-1.5 rounded-xl border border-dashed px-4 py-8 text-center transition-colors hover:bg-neutral-bg ${
              archivo ? 'border-success bg-success-bg' : 'border-border'
            }`}
          >
            <Upload className="h-5 w-5 text-ink-faint" />
            <span className="text-[13px] font-semibold text-ink">{archivo ? archivo.name : 'Subir comprobante'}</span>
            <span className="text-xs text-ink-faint">PDF o JPG, hasta 5MB</span>
            <input type="file" accept="application/pdf,image/jpeg" className="hidden" onChange={(e) => setArchivo(e.target.files?.[0] ?? null)} />
          </label>
          {justificar.isError && (
            <div className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">
              No pudimos subir el comprobante. Confirmá que sea un PDF y probá de nuevo.
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" className="rounded-xl" onClick={() => cerrarModal(false)}>
              Cancelar
            </Button>
            <Button variant="gradient" onClick={confirmarJustificacion} disabled={!archivo || justificar.isPending}>
              {justificar.isPending ? 'Subiendo…' : 'Justificar'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </GlassCard>
  );
}
