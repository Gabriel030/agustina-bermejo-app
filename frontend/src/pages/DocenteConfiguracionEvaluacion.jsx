import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Check } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { CalendarioConfiguracion } from '@/components/docente/CalendarioConfiguracion';
import { useConfiguracionComision, useGuardarBorradorComision, useEnviarComisionAAprobacion, useHolidays } from '@/hooks/useDocente';
import { ESTADO_CONFIG_LABEL, ROL_INSTRUCTOR_LABEL, formatearFecha } from '@/lib/comisiones';

function campoFecha(valor) {
  return valor ?? '';
}

const COLUMNAS_PARCIALES = { 1: 'sm:grid-cols-1', 2: 'sm:grid-cols-2', 3: 'sm:grid-cols-3', 4: 'sm:grid-cols-4' };

function CampoFecha({ label, value, onChange, disabled }) {
  const completo = !!value;
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-xs font-semibold text-ink-soft">{label}</label>
      <div className="relative">
        <input
          type="date"
          value={campoFecha(value)}
          onChange={(e) => onChange(e.target.value || null)}
          disabled={disabled}
          className={`w-full rounded-lg border px-3 py-2 pr-8 text-[13.5px] text-ink disabled:bg-neutral-bg disabled:text-ink-faint ${
            completo && !disabled ? 'border-success bg-success-bg/30' : 'border-border bg-white/70'
          }`}
        />
        {completo && !disabled && <Check className="pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-success" />}
      </div>
    </div>
  );
}

export function DocenteConfiguracionEvaluacion() {
  const { sectionId } = useParams();
  const { data: config, isLoading } = useConfiguracionComision(sectionId);
  const { data: feriados } = useHolidays();
  const guardarBorrador = useGuardarBorradorComision(sectionId);
  const enviarAAprobacion = useEnviarComisionAAprobacion(sectionId);

  const [form, setForm] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (config) {
      setForm({
        tpCount: config.tpCount,
        tp1DueDate: config.tp1DueDate,
        tp2DueDate: config.tp2DueDate,
        tp3DueDate: config.tp3DueDate,
        exam1Date: config.exam1Date,
        exam2Date: config.exam2Date,
        exam3Date: config.exam3Date,
        exam4Date: config.exam4Date,
        finalCall1Date: config.finalCall1Date,
        finalCall2Date: config.finalCall2Date,
      });
    }
  }, [config]);

  if (isLoading || !form) {
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
  const soloLectura = !config.canEdit;

  const camposTpFaltantes = [1, 2, 3].slice(0, form.tpCount ?? 0).filter((n) => !form[`tp${n}DueDate`]).length;
  const camposParcialFaltantes = camposParcial.filter((n) => !form[`exam${n}Date`]).length;
  const camposFinalFaltantes = [form.finalCall1Date, form.finalCall2Date].filter((v) => !v).length;
  const faltantes = (form.tpCount ? camposTpFaltantes : 1) + camposParcialFaltantes + camposFinalFaltantes;
  const formularioCompleto = faltantes === 0;

  async function guardar() {
    setError(null);
    try {
      await guardarBorrador.mutateAsync(form);
    } catch (e) {
      setError(e.message);
    }
  }

  async function enviar() {
    setError(null);
    try {
      await guardarBorrador.mutateAsync(form);
      await enviarAAprobacion.mutateAsync();
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
        {config.rejectionReason && (
          <div className="mt-1.5 text-sm font-semibold text-danger">Rechazada: &ldquo;{config.rejectionReason}&rdquo;</div>
        )}
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
        <div className="mt-3.5 text-xs text-ink-faint">Estos valores los define Dirección y no se pueden editar desde acá.</div>
        <div className="mt-2 rounded-lg bg-neutral-bg/60 p-3 text-xs text-ink-soft">
          <span className="font-semibold text-ink">Cómo se calcula la nota final:</span> por promoción, se promedian las notas
          de los parciales; por final regular, la nota del examen final es la nota de la materia.
        </div>
      </GlassCard>

      <GlassCard className="flex flex-col gap-5 p-5">
        <div className="font-heading text-[15px] font-semibold text-ink">Configuración de esta comisión</div>

        <div className="flex flex-col gap-3">
          <label className="text-xs font-semibold text-ink-soft">Trabajos prácticos</label>

          <div className="flex flex-col gap-1.5">
            <span className="text-[11px] font-semibold text-ink-faint">Cantidad</span>
            <div className="flex gap-2">
              {[1, 2, 3].map((n) => (
                <button
                  key={n}
                  type="button"
                  disabled={soloLectura}
                  onClick={() => setForm((f) => ({ ...f, tpCount: n }))}
                  className={`rounded-lg px-4 py-2 text-sm font-bold ${
                    form.tpCount === n
                      ? 'bg-gradient-to-br from-primary to-accent text-white ring-2 ring-success ring-offset-2'
                      : 'bg-neutral-bg text-ink-faint'
                  } disabled:opacity-60 disabled:ring-0`}
                >
                  {n}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            {[1, 2, 3].slice(0, form.tpCount ?? 0).map((n) => (
              <CampoFecha
                key={n}
                label={`Fecha de entrega — TP ${n}`}
                value={form[`tp${n}DueDate`]}
                onChange={(v) => setForm((f) => ({ ...f, [`tp${n}DueDate`]: v }))}
                disabled={soloLectura}
              />
            ))}
          </div>
        </div>

        <div className="h-px bg-border" />

        <div className="flex flex-col gap-3">
          <label className="text-xs font-semibold text-ink-soft">Parciales</label>
          <div className={`grid grid-cols-1 gap-4 ${COLUMNAS_PARCIALES[Math.min(cantidadParciales, 4)]}`}>
            {camposParcial.map((n) => (
              <CampoFecha
                key={n}
                label={`Fecha ${n}° parcial`}
                value={form[`exam${n}Date`]}
                onChange={(v) => setForm((f) => ({ ...f, [`exam${n}Date`]: v }))}
                disabled={soloLectura}
              />
            ))}
          </div>
        </div>

        <div className="h-px bg-border" />

        <div className="flex flex-col gap-3">
          <label className="text-xs font-semibold text-ink-soft">Finales</label>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <CampoFecha
              label="1er llamado a final"
              value={form.finalCall1Date}
              onChange={(v) => setForm((f) => ({ ...f, finalCall1Date: v }))}
              disabled={soloLectura}
            />
            <CampoFecha
              label="2do llamado a final"
              value={form.finalCall2Date}
              onChange={(v) => setForm((f) => ({ ...f, finalCall2Date: v }))}
              disabled={soloLectura}
            />
          </div>
        </div>

        <div className="h-px bg-border" />

        <div>
          <div className="mb-3 text-xs font-semibold text-ink-soft">Vista de calendario</div>
          <CalendarioConfiguracion config={config} form={form} feriados={feriados} />
        </div>

        {error && <div className="text-sm font-semibold text-danger">{error}</div>}

        {!soloLectura && (
          <div className="flex items-center justify-end gap-3">
            {!formularioCompleto && <span className="text-xs font-semibold text-warning">Faltan {faltantes} datos para poder enviar</span>}
            <Button variant="outline" onClick={guardar} disabled={guardarBorrador.isPending}>
              Guardar borrador
            </Button>
            <Button onClick={enviar} disabled={!formularioCompleto || guardarBorrador.isPending || enviarAAprobacion.isPending}>
              Enviar a aprobación
            </Button>
          </div>
        )}
      </GlassCard>
    </PageContainer>
  );
}
