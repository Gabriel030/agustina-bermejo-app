import { useNavigate, useParams, Link } from 'react-router-dom';
import { ChevronLeft, Users, MapPin, Clock } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { useCurso } from '@/hooks/useCatalogo';
import { useCiclos, useCicloSecciones } from '@/features/oferta-academica/hooks/useCiclos';
import { ResumeCycleButton } from '@/features/oferta-academica/components/ResumeCycleButton';
import { RejectionReasonNotice } from '@/features/oferta-academica/components/RejectionReasonNotice';

const CICLO_STATUS_CONFIG = {
  DRAFT: { label: 'En construcción', tone: 'neutral' },
  PENDING_APPROVAL: { label: 'En revisión', tone: 'warning' },
  APPROVED: { label: 'Aprobado', tone: 'success' },
  REJECTED: { label: 'Rechazado', tone: 'danger' },
  CLOSED: { label: 'Cerrado', tone: 'neutral' },
};

const DIA_LABELS = {
  MONDAY: 'Lunes', TUESDAY: 'Martes', WEDNESDAY: 'Miércoles',
  THURSDAY: 'Jueves', FRIDAY: 'Viernes', SATURDAY: 'Sábado', SUNDAY: 'Domingo',
};

function formatHora(t) {
  return t?.slice(0, 5) ?? '—';
}

function SeccionDetalle({ ciclo }) {
  const { data: secciones, isLoading } = useCicloSecciones(ciclo.id);
  const cfg = CICLO_STATUS_CONFIG[ciclo.status] ?? { label: ciclo.status, tone: 'neutral' };

  if (isLoading) return <Skeleton className="h-[120px] w-full rounded-2xl" />;

  const seccion = secciones?.[0];

  return (
    <GlassCard className="flex flex-col gap-4 p-5">
      <div className="flex items-center justify-between">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-heading text-[15px] font-semibold text-ink">{ciclo.termName}</span>
            <StatusPill tone={cfg.tone}>{cfg.label}</StatusPill>
          </div>
          <div className="mt-0.5 text-[12px] text-ink-faint">Creado por {ciclo.createdByName}</div>
        </div>
        <ResumeCycleButton cycle={ciclo} />
      </div>

      <RejectionReasonNotice cycle={ciclo} />

      {!seccion ? (
        <EmptyState title="Este ciclo aún no tiene comisión cargada." className="py-4" />
      ) : (
        <div className="grid grid-cols-2 gap-x-8 gap-y-3">
          <Campo label="Comisión" valor={seccion.name} />
          <Campo
            label="Cupo"
            valor={
              <span className="flex items-center gap-1">
                <Users className="h-3.5 w-3.5" />
                {seccion.capacity} alumnos
              </span>
            }
          />
          {seccion.slots?.length > 0 && (
            <>
              <Campo
                label="Aula"
                valor={
                  <span className="flex items-center gap-1">
                    <MapPin className="h-3.5 w-3.5" />
                    {seccion.slots[0].classroomName}
                  </span>
                }
              />
              <Campo
                label="Horario"
                valor={
                  <span className="flex items-center gap-1">
                    <Clock className="h-3.5 w-3.5" />
                    {DIA_LABELS[seccion.slots[0].dayOfWeek]} {formatHora(seccion.slots[0].startTime)}–{formatHora(seccion.slots[0].endTime)}
                  </span>
                }
              />
            </>
          )}
          {seccion.instructors?.length > 0 && (
            <Campo
              label="Docente"
              valor={seccion.instructors.find((i) => i.role === 'LEAD')?.name ?? '—'}
            />
          )}
          {seccion.pendingApproval && (
            <div className="col-span-2">
              <span className="rounded-full bg-accent-soft px-3 py-1 text-[12px] font-medium text-brand">
                Modificación pendiente de aprobación
              </span>
            </div>
          )}
        </div>
      )}
    </GlassCard>
  );
}

function Campo({ label, valor }) {
  return (
    <div>
      <div className="text-[11px] font-medium uppercase tracking-wide text-ink-faint">{label}</div>
      <div className="mt-0.5 text-[13.5px] text-ink">{valor}</div>
    </div>
  );
}

export function PreceptorDetalleCurso() {
  const { cursoId } = useParams();
  const navigate = useNavigate();
  const { data: curso, isLoading: loadingCurso } = useCurso(cursoId);
  const { data: ciclos, isLoading: loadingCiclos } = useCiclos({ courseId: cursoId });

  const cicloActivo = (ciclos ?? []).find(
    (c) => c.status === 'DRAFT' || c.status === 'PENDING_APPROVAL' || c.status === 'APPROVED'
  ) ?? (ciclos ?? []).find((c) => c.status === 'REJECTED');

  if (loadingCurso) {
    return (
      <PageContainer>
        <Skeleton className="h-8 w-48 rounded-xl" />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <div className="flex flex-col gap-4">
        <div>
          <Link
            to="/preceptor/oferta-academica?tab=cursos"
            className="mb-1 flex items-center gap-1 text-[12px] text-ink-faint hover:text-ink"
          >
            <ChevronLeft className="h-3.5 w-3.5" />
            Oferta académica
          </Link>
          <div className="flex items-center justify-between">
            <h1 className="font-heading text-xl font-bold text-ink">{curso?.name}</h1>
            <Button variant="gradient" onClick={() => navigate('/preceptor/oferta-academica/nuevo-ciclo')}>
              Iniciar alta de ciclo
            </Button>
          </div>
          <div className="mt-0.5 text-[13px] text-ink-faint">{curso?.durationMonths} meses</div>
        </div>

        {loadingCiclos ? (
          <Skeleton className="h-[200px] w-full rounded-2xl" />
        ) : !cicloActivo ? (
          <GlassCard className="p-6">
            <EmptyState title="No hay ningún ciclo activo para este curso." />
          </GlassCard>
        ) : (
          <SeccionDetalle ciclo={cicloActivo} />
        )}
      </div>
    </PageContainer>
  );
}
