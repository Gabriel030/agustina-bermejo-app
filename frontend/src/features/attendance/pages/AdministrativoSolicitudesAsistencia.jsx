import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { useAprobarSolicitud, useRechazarSolicitud, useSolicitudesPendientes } from '@/features/attendance/hooks/useSolicitudesAsistencia';

const ESTADO_LABEL = { PRESENT: 'Presente', LATE: 'Tarde', ABSENT: 'Ausente', JUSTIFICATION_PENDING: 'Justificación pendiente', JUSTIFIED: 'Justificada' };

export function AdministrativoSolicitudesAsistencia() {
  const { data: solicitudes, isLoading } = useSolicitudesPendientes();
  const aprobar = useAprobarSolicitud();
  const rechazar = useRechazarSolicitud();

  return (
    <PageContainer>
      <div>
        <h1 className="font-heading text-2xl font-bold text-ink">Solicitudes de asistencia</h1>
        <p className="mt-0.5 text-sm text-ink-soft">Modificaciones pedidas por docentes sobre asistencia ya cargada</p>
      </div>

      {isLoading ? (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-16" />
          <Skeleton className="h-16" />
        </div>
      ) : (solicitudes ?? []).length === 0 ? (
        <GlassCard className="p-4">
          <EmptyState title="Sin solicitudes pendientes" description="Cuando un docente pida modificar una asistencia, aparece acá." />
        </GlassCard>
      ) : (
        <div className="flex flex-col gap-3">
          {solicitudes.map((s) => (
            <GlassCard key={s.id} className="flex flex-col gap-3 p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="font-heading text-[14.5px] font-semibold text-ink">{s.subjectName} · {s.sectionName}</div>
                <div className="text-[12.5px] text-ink-faint">Solicitud #{s.id}</div>
              </div>

              <div className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-[12.5px] sm:grid-cols-4">
                <div>
                  <div className="text-ink-faint">Docente</div>
                  <div className="font-medium text-ink">{s.requestedByName}</div>
                </div>
                <div>
                  <div className="text-ink-faint">Alumno</div>
                  <div className="font-medium text-ink">{s.studentName}</div>
                </div>
                <div>
                  <div className="text-ink-faint">Clase</div>
                  <div className="font-medium text-ink">{s.classDate}</div>
                </div>
                <div>
                  <div className="text-ink-faint">Cambio pedido</div>
                  <div className="font-medium text-ink">{ESTADO_LABEL[s.previousStatus] ?? s.previousStatus} → {ESTADO_LABEL[s.requestedStatus] ?? s.requestedStatus}</div>
                </div>
              </div>

              <div>
                <div className="text-[12.5px] text-ink-faint">Motivo</div>
                <div className="text-[13px] text-ink">{s.reason}</div>
              </div>

              <div className="flex justify-end gap-2">
                <Button variant="outline" size="sm" onClick={() => rechazar.mutate(s.id)} disabled={rechazar.isPending}>Rechazar</Button>
                <Button variant="gradient" size="sm" onClick={() => aprobar.mutate(s.id)} disabled={aprobar.isPending}>Aprobar</Button>
              </div>
            </GlassCard>
          ))}
        </div>
      )}
    </PageContainer>
  );
}
