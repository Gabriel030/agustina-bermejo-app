import { useState } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { ChevronLeft, ChevronRight, Users, Calendar } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { usePrograma } from '@/hooks/useCatalogo';
import { useCiclos, useCicloSecciones } from '@/features/oferta-academica/hooks/useCiclos';

const CICLO_STATUS_CONFIG = {
  DRAFT: { label: 'En construcción', tone: 'neutral' },
  PENDING_APPROVAL: { label: 'En revisión', tone: 'warning' },
  APPROVED: { label: 'Aprobado', tone: 'success' },
  REJECTED: { label: 'Rechazado', tone: 'danger' },
  CLOSED: { label: 'Cerrado', tone: 'neutral' },
};

const SECCION_STATUS_CONFIG = {
  ACTIVE: { label: 'Activa', tone: 'success' },
  INACTIVE: { label: 'Inactiva', tone: 'neutral' },
};

const DIA_LABELS = {
  MONDAY: 'Lun', TUESDAY: 'Mar', WEDNESDAY: 'Mié',
  THURSDAY: 'Jue', FRIDAY: 'Vie', SATURDAY: 'Sáb', SUNDAY: 'Dom',
};

function formatHora(t) {
  return t?.slice(0, 5) ?? '—';
}

function FilaSeccion({ seccion, onVerCalendario }) {
  const cfg = SECCION_STATUS_CONFIG[seccion.status] ?? { label: seccion.status, tone: 'neutral' };
  const titular = seccion.instructors?.find((i) => i.role === 'LEAD');
  const slotsPrimero = seccion.slots?.[0];

  return (
    <div className="rounded-2xl border border-surface-border px-4 py-3">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="font-heading text-[13.5px] font-semibold text-ink">{seccion.subjectName}</span>
            <span className="text-[12px] text-ink-faint">· {seccion.name}</span>
            {seccion.pendingApproval && (
              <span className="rounded-full bg-accent-soft px-2 py-0.5 text-[11px] font-medium text-accent">
                Modificación pendiente
              </span>
            )}
          </div>
          {titular && (
            <div className="mt-0.5 text-[12px] text-ink-faint">Titular: {titular.name}</div>
          )}
          {slotsPrimero && (
            <div className="mt-0.5 text-[12px] text-ink-soft">
              {DIA_LABELS[slotsPrimero.dayOfWeek]} {formatHora(slotsPrimero.startTime)}–{formatHora(slotsPrimero.endTime)} · {slotsPrimero.classroomName}
            </div>
          )}
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <div className="flex items-center gap-1 text-[12px] text-ink-faint">
            <Users className="h-3.5 w-3.5" />
            {seccion.capacity}
          </div>
          <StatusPill tone={cfg.tone}>{cfg.label}</StatusPill>
        </div>
      </div>
    </div>
  );
}

function PanelCiclo({ ciclo, programaId }) {
  const navigate = useNavigate();
  const cfg = CICLO_STATUS_CONFIG[ciclo.status] ?? { label: ciclo.status, tone: 'neutral' };
  const { data: secciones, isLoading } = useCicloSecciones(ciclo.id);

  return (
    <GlassCard className="flex flex-col gap-4 p-4">
      <div className="flex items-center justify-between">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-heading text-[15px] font-semibold text-ink">{ciclo.termName}</span>
            <StatusPill tone={cfg.tone}>{cfg.label}</StatusPill>
          </div>
          <div className="mt-0.5 text-[12px] text-ink-faint">Creado por {ciclo.createdByName}</div>
        </div>
        <div className="flex gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => navigate(`/preceptor/oferta-academica/calendario`)}
          >
            <Calendar className="mr-1.5 h-3.5 w-3.5" />
            Calendario
          </Button>
        </div>
      </div>

      {isLoading ? (
        <div className="flex flex-col gap-2">
          {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-[72px] w-full rounded-2xl" />)}
        </div>
      ) : !secciones?.length ? (
        <EmptyState title="Este ciclo aún no tiene comisiones." className="py-6" />
      ) : (
        <div className="flex flex-col gap-2">
          {secciones.map((s) => <FilaSeccion key={s.id} seccion={s} />)}
        </div>
      )}
    </GlassCard>
  );
}

export function PreceptorDetalleCarrera() {
  const { programaId } = useParams();
  const navigate = useNavigate();
  const { data: programa, isLoading: loadingPrograma } = usePrograma(programaId);
  const { data: ciclos, isLoading: loadingCiclos } = useCiclos({ programId: programaId });
  const [verAnteriores, setVerAnteriores] = useState(false);

  const ciclosActivos = (ciclos ?? []).filter(
    (c) => c.status === 'DRAFT' || c.status === 'PENDING_APPROVAL' || c.status === 'APPROVED'
  );
  const ciclosAnteriores = (ciclos ?? []).filter(
    (c) => c.status === 'REJECTED' || c.status === 'CLOSED'
  );
  const ciclosMostrados = verAnteriores ? ciclosAnteriores : ciclosActivos;

  if (loadingPrograma) {
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
            to="/preceptor/oferta-academica?tab=carreras"
            className="mb-1 flex items-center gap-1 text-[12px] text-ink-faint hover:text-ink"
          >
            <ChevronLeft className="h-3.5 w-3.5" />
            Oferta académica
          </Link>
          <div className="flex items-center justify-between">
            <h1 className="font-heading text-xl font-bold text-ink">{programa?.name}</h1>
            <Button variant="gradient" onClick={() => navigate('/preceptor/oferta-academica/nuevo-ciclo')}>
              Iniciar alta de ciclo
            </Button>
          </div>
          <div className="mt-0.5 text-[13px] text-ink-faint">
            {programa?.totalYears} {programa?.totalYears === 1 ? 'año' : 'años'}
          </div>
        </div>

        <div className="flex gap-2 border-b border-surface-border pb-2">
          <button
            type="button"
            onClick={() => setVerAnteriores(false)}
            className={`rounded-full px-3 py-1 text-[13px] transition-colors ${!verAnteriores ? 'bg-accent-soft font-medium text-accent' : 'text-ink-faint hover:text-ink'}`}
          >
            Actuales
          </button>
          <button
            type="button"
            onClick={() => setVerAnteriores(true)}
            className={`rounded-full px-3 py-1 text-[13px] transition-colors ${verAnteriores ? 'bg-accent-soft font-medium text-accent' : 'text-ink-faint hover:text-ink'}`}
          >
            Anteriores
          </button>
        </div>

        {loadingCiclos ? (
          <div className="flex flex-col gap-3">
            <Skeleton className="h-[200px] w-full rounded-2xl" />
          </div>
        ) : !ciclosMostrados.length ? (
          <GlassCard className="p-6">
            <EmptyState
              title={verAnteriores ? 'No hay ciclos anteriores.' : 'No hay ciclos activos para esta carrera.'}
            />
          </GlassCard>
        ) : (
          <div className="flex flex-col gap-4">
            {ciclosMostrados.map((ciclo) => (
              <PanelCiclo key={ciclo.id} ciclo={ciclo} programaId={programaId} />
            ))}
          </div>
        )}
      </div>
    </PageContainer>
  );
}
