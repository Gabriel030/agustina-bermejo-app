import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { PageContainer } from '@/components/PageContainer';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { CalendarioConfiguracion } from '@/features/evaluation/components/CalendarioConfiguracion';
import {
  useConfiguracionComisionStaff,
  useAprobarConfig,
  useRechazarConfig,
} from '@/features/evaluation/hooks/useEvaluationConfig';
import { useHolidays } from '@/features/evaluation/hooks/useEvaluationConfig';
import { ESTADO_CONFIG_LABEL, ROL_INSTRUCTOR_LABEL, formatearFecha } from '@/lib/comisiones';

function ValorFecha({ label, valor }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-xs font-semibold text-ink-soft">{label}</label>
      <Input value={formatearFecha(valor) ?? '—'} readOnly className="cursor-default text-[13.5px]" />
    </div>
  );
}

const COLUMNAS_PARCIALES = { 1: 'sm:grid-cols-1', 2: 'sm:grid-cols-2', 3: 'sm:grid-cols-3', 4: 'sm:grid-cols-4' };

export function DirectorRevisarConfiguracion() {
  const { sectionId } = useParams();
  const navigate = useNavigate();
  const { data: config, isLoading, isError } = useConfiguracionComisionStaff(sectionId);
  const { data: feriados } = useHolidays();
  const aprobar = useAprobarConfig(sectionId);
  const rechazar = useRechazarConfig(sectionId);
  const [motivo, setMotivo] = useState('');
  const [error, setError] = useState(null);

  if (isError) {
    return (
      <PageContainer>
        <GlassCard className="p-4">
          <EmptyState
            icon="🔎"
            title="No encontramos esta configuración"
            description="Puede que la comisión no exista o que el enlace esté mal."
            action={
              <Button variant="outline" asChild>
                <Link to="/director/aprobaciones/configuracion-evaluacion">Volver a la bandeja</Link>
              </Button>
            }
          />
        </GlassCard>
      </PageContainer>
    );
  }

  if (isLoading || !config) {
    return (
      <PageContainer>
        <Skeleton className="h-10" />
        <Skeleton className="h-40" />
        <Skeleton className="h-80" />
      </PageContainer>
    );
  }

  const pill = ESTADO_CONFIG_LABEL[config.status] ?? { label: config.status, tone: 'neutral' };
  const cantidadParciales = config.examCountExpected ?? 2;
  const camposParcial = Array.from({ length: cantidadParciales }, (_, i) => i + 1);
  const esDecidible = config.status === 'PENDING_APPROVAL';

  async function aprobarComision() {
    setError(null);
    try {
      await aprobar.mutateAsync();
      navigate('/director/aprobaciones');
    } catch (e) {
      setError(e.message);
    }
  }

  async function rechazarComision() {
    setError(null);
    if (!motivo.trim()) {
      setError('El motivo del rechazo es obligatorio.');
      return;
    }
    try {
      await rechazar.mutateAsync(motivo);
      navigate('/director/aprobaciones');
    } catch (e) {
      setError(e.message);
    }
  }

  return (
    <PageContainer>
      <div>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="font-heading text-2xl font-bold text-ink">
            {config.subjectName} · {config.sectionName}
          </h1>
          <StatusPill tone={pill.tone}>{pill.label}</StatusPill>
        </div>
        {config.instructors?.length > 0 && (
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <span className="text-[11.5px] font-semibold text-ink-faint">Instructores:</span>
            {config.instructors.map((i) => (
              <span key={i.name} className="rounded-full bg-accent-soft px-2.5 py-1 text-[11.5px] font-bold text-primary">
                {i.name} <span className="font-semibold opacity-75">· {ROL_INSTRUCTOR_LABEL[i.role] ?? i.role}</span>
              </span>
            ))}
          </div>
        )}
      </div>

      <GlassCard className="flex flex-wrap items-center gap-7 p-4">
        <div>
          <div className="text-[11px] font-semibold uppercase text-ink-faint">Apertura del cuatrimestre</div>
          <div className="text-sm font-bold text-ink">{formatearFecha(config.termStartDate) ?? '—'}</div>
        </div>
        <div>
          <div className="text-[11px] font-semibold uppercase text-ink-faint">Cierre del cuatrimestre</div>
          <div className="text-sm font-bold text-ink">{formatearFecha(config.termEndDate) ?? '—'}</div>
        </div>
        <div>
          <div className="text-[11px] font-semibold uppercase text-warning">Apertura de inscripción</div>
          <div className="text-sm font-bold text-ink">{formatearFecha(config.termEnrollmentStart) ?? '—'}</div>
        </div>
      </GlassCard>

      <GlassCard className="p-5">
        <div className="mb-4 flex items-center gap-2 font-heading text-[15px] font-semibold text-ink">
          Esquema institucional vigente
          <span className="text-xs font-normal text-ink-faint">· {config.evaluationSchemeName ?? 'Sin asignar'}</span>
        </div>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div>
            <div className="text-[11.5px] font-semibold uppercase text-ink-faint">Mínimo para quedar regular</div>
            <div className="font-heading text-xl font-bold text-ink">{config.minRegularGrade ?? '—'}</div>
          </div>
          <div>
            <div className="text-[11.5px] font-semibold uppercase text-ink-faint">Nota para aprobar el final</div>
            <div className="font-heading text-xl font-bold text-ink">{config.minApprovalGrade ?? '—'}</div>
          </div>
          <div>
            <div className="text-[11.5px] font-semibold uppercase text-ink-faint">¿Es promocional?</div>
            {config.promotionMinGrade ? (
              <div className="font-heading text-xl font-bold text-success">
                Sí <span className="text-sm font-semibold text-ink-faint">· corte {config.promotionMinGrade}</span>
              </div>
            ) : (
              <div className="font-heading text-xl font-bold text-ink-faint">No</div>
            )}
          </div>
          <div>
            <div className="text-[11.5px] font-semibold uppercase text-ink-faint">Parciales esperados</div>
            <div className="font-heading text-xl font-bold text-ink">{cantidadParciales}</div>
          </div>
        </div>
      </GlassCard>

      <GlassCard className="flex flex-col gap-5 p-5">
        <div className="font-heading text-[15px] font-semibold text-ink">Configuración propuesta por el Docente</div>

        <div className="flex flex-col gap-3">
          <label className="text-xs font-semibold text-ink-soft">Trabajos prácticos ({config.tpCount ?? '—'})</label>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            {[1, 2, 3].slice(0, config.tpCount ?? 0).map((n) => (
              <ValorFecha key={n} label={`Fecha de entrega — TP ${n}`} valor={config[`tp${n}DueDate`]} />
            ))}
          </div>
        </div>

        <div className="h-px bg-border" />

        <div className="flex flex-col gap-3">
          <label className="text-xs font-semibold text-ink-soft">Parciales</label>
          <div className={`grid grid-cols-1 gap-4 ${COLUMNAS_PARCIALES[Math.min(cantidadParciales, 4)]}`}>
            {camposParcial.map((n) => (
              <ValorFecha key={n} label={`Fecha ${n}° parcial`} valor={config[`exam${n}Date`]} />
            ))}
          </div>
        </div>

        <div className="h-px bg-border" />

        <div className="flex flex-col gap-3">
          <label className="text-xs font-semibold text-ink-soft">Finales</label>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <ValorFecha label="1er llamado a final" valor={config.finalCall1Date} />
            <ValorFecha label="2do llamado a final" valor={config.finalCall2Date} />
          </div>
        </div>

        <div className="h-px bg-border" />

        <div>
          <div className="mb-3 text-xs font-semibold text-ink-soft">Vista de calendario</div>
          <CalendarioConfiguracion config={config} form={config} feriados={feriados} />
        </div>

        {esDecidible && (
          <>
            <div className="h-px bg-border" />
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-semibold text-ink-soft">Motivo del rechazo (obligatorio solo si rechazás)</label>
              <textarea
                value={motivo}
                onChange={(e) => setMotivo(e.target.value)}
                placeholder="Ej: faltan fechas de TP2 y TP3..."
                rows={2}
                className="rounded-lg border border-border bg-white/70 px-3 py-2 text-[13.5px] text-ink dark:bg-white/10"
              />
            </div>
            {error && <div className="text-sm font-semibold text-danger">{error}</div>}
            <div className="flex justify-end gap-3">
              <Button
                variant="outline"
                className="border-danger/40 text-danger"
                onClick={rechazarComision}
                disabled={rechazar.isPending}
              >
                Rechazar
              </Button>
              <Button
                className="bg-success text-white hover:bg-success/90"
                onClick={aprobarComision}
                disabled={aprobar.isPending}
              >
                Aprobar
              </Button>
            </div>
          </>
        )}
      </GlassCard>
    </PageContainer>
  );
}
