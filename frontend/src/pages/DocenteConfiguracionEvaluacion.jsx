import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Check } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { CalendarioConfiguracion } from '@/components/docente/CalendarioConfiguracion';
import { useConfiguracionComision, useGuardarBorradorComision, useEnviarComisionAAprobacion, useHolidays } from '@/hooks/useDocente';
import { formatearFecha } from '@/lib/comisiones';

function campoFecha(valor) {
  return valor ?? '';
}

const COLUMNAS_PARCIALES = { 1: 'sm:grid-cols-1', 2: 'sm:grid-cols-2', 3: 'sm:grid-cols-3', 4: 'sm:grid-cols-4' };

const MENSAJES_ERROR_CONFIGURACION = {
  EVALUATION_DATE_OUTSIDE_TERM: 'Una de las fechas cae fuera del cuatrimestre. Revisá las fechas marcadas.',
  EVALUATION_DATE_ON_HOLIDAY: 'Una de las fechas cae en un feriado. Revisá las fechas marcadas.',
  SUBMISSION_WINDOW_CLOSED: 'Venció el plazo para enviar la configuración: el cuatrimestre ya empezó. Si necesitás un ajuste, pedíselo a Dirección.',
  INVALID_SECTION_EVALUATION_CONFIG_TRANSITION: 'La configuración ya no se puede modificar en su estado actual. Recargá la página.',
};

function soloDia(fechaIso) {
  return fechaIso ? fechaIso.slice(0, 10) : null;
}

function problemaDeFecha(valor, config, feriados) {
  if (!valor) return null;
  const inicio = soloDia(config.termStartDate);
  const fin = soloDia(config.termEndDate);
  if ((inicio && valor < inicio) || (fin && valor > fin)) return 'Fuera del cuatrimestre';
  if (feriados?.some((f) => f.date === valor)) return 'Cae en un feriado';
  return null;
}

function CampoFecha({ label, value, onChange, disabled, error, min, max }) {
  const completo = !!value && !error;
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-xs font-semibold text-ink-soft">{label}</label>
      <div className="relative">
        <input
          type="date"
          aria-label={label}
          value={campoFecha(value)}
          min={min ?? undefined}
          max={max ?? undefined}
          onChange={(e) => onChange(e.target.value || null)}
          disabled={disabled}
          aria-invalid={!!error}
          className={`w-full rounded-lg border px-3 py-2 pr-8 text-[13.5px] text-ink disabled:bg-neutral-bg disabled:text-ink-faint ${
            error
              ? 'border-danger bg-danger-bg/30'
              : completo && !disabled
                ? 'border-success bg-success-bg/30'
                : 'border-border bg-white/70 dark:bg-white/10'
          }`}
        />
        {completo && !disabled && <Check className="pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-success" />}
      </div>
      {error && <span className="text-[11.5px] font-semibold text-danger">{error}</span>}
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
      <>
        <Skeleton className="h-40" />
        <Skeleton className="h-80" />
      </>
    );
  }

  const cantidadParciales = config.examCountExpected ?? 2;
  const camposParcial = Array.from({ length: cantidadParciales }, (_, i) => i + 1);
  const soloLectura = !config.canEdit;
  const tieneFinal = config.hasFinal !== false;
  const plazoVencido = config.canEdit && !config.canSubmit;
  const minimo = soloDia(config.termStartDate);
  const maximo = soloDia(config.termEndDate);
  const errorDe = (campo) => (soloLectura ? null : problemaDeFecha(form[campo], config, feriados));
  const camposConFecha = [
    ...[1, 2, 3].slice(0, form.tpCount ?? 0).map((n) => `tp${n}DueDate`),
    ...camposParcial.map((n) => `exam${n}Date`),
    ...(tieneFinal ? ['finalCall1Date', 'finalCall2Date'] : []),
  ];
  const hayFechasInvalidas = camposConFecha.some((campo) => errorDe(campo));

  const camposTpFaltantes = [1, 2, 3].slice(0, form.tpCount ?? 0).filter((n) => !form[`tp${n}DueDate`]).length;
  const camposParcialFaltantes = camposParcial.filter((n) => !form[`exam${n}Date`]).length;
  const camposFinalFaltantes = tieneFinal ? [form.finalCall1Date, form.finalCall2Date].filter((v) => !v).length : 0;
  const faltantes = (form.tpCount ? camposTpFaltantes : 1) + camposParcialFaltantes + camposFinalFaltantes;
  const formularioCompleto = faltantes === 0;

  function mensajeDeError(e) {
    return MENSAJES_ERROR_CONFIGURACION[e.code] ?? e.message;
  }

  async function guardar() {
    setError(null);
    try {
      await guardarBorrador.mutateAsync(form);
    } catch (e) {
      setError(mensajeDeError(e));
    }
  }

  async function enviar() {
    setError(null);
    try {
      await guardarBorrador.mutateAsync(form);
      await enviarAAprobacion.mutateAsync();
    } catch (e) {
      setError(mensajeDeError(e));
    }
  }

  return (
    <>
      {config.rejectionReason && (
        <div className="text-sm font-semibold text-danger">Rechazada: &ldquo;{config.rejectionReason}&rdquo;</div>
      )}

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
                      ? 'bg-gradient-to-br from-primary to-brand text-white ring-2 ring-success ring-offset-2'
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
                error={errorDe(`tp${n}DueDate`)}
                min={minimo}
                max={maximo}
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
                error={errorDe(`exam${n}Date`)}
                min={minimo}
                max={maximo}
              />
            ))}
          </div>
        </div>

        <div className="h-px bg-border" />

        {tieneFinal && (
          <>
            <div className="flex flex-col gap-3">
              <label className="text-xs font-semibold text-ink-soft">Finales</label>
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <CampoFecha
                  label="1er llamado a final"
                  value={form.finalCall1Date}
                  onChange={(v) => setForm((f) => ({ ...f, finalCall1Date: v }))}
                  disabled={soloLectura}
                  error={errorDe('finalCall1Date')}
                  min={minimo}
                  max={maximo}
                />
                <CampoFecha
                  label="2do llamado a final"
                  value={form.finalCall2Date}
                  onChange={(v) => setForm((f) => ({ ...f, finalCall2Date: v }))}
                  disabled={soloLectura}
                  error={errorDe('finalCall2Date')}
                  min={minimo}
                  max={maximo}
                />
              </div>
            </div>

            <div className="h-px bg-border" />
          </>
        )}

        <div>
          <div className="mb-3 text-xs font-semibold text-ink-soft">Vista de calendario</div>
          <CalendarioConfiguracion config={config} form={form} feriados={feriados} />
        </div>

        {error && <div className="text-sm font-semibold text-danger">{error}</div>}

        {plazoVencido && (
          <div className="rounded-lg bg-warning-bg/60 p-3 text-xs text-ink-soft">
            Venció el plazo para enviarla: el cuatrimestre empezó el {formatearFecha(config.termStartDate)}. Podés guardar cambios,
            pero si necesitás que rija una configuración, pedíselo a Dirección.
          </div>
        )}

        {!soloLectura && (
          <div className="flex flex-wrap items-center justify-end gap-3">
            {!formularioCompleto && <span className="text-xs font-semibold text-warning">Faltan {faltantes} datos para poder enviar</span>}
            <Button variant="outline" onClick={guardar} disabled={guardarBorrador.isPending}>
              Guardar borrador
            </Button>
            <Button
              variant="gradient"
              onClick={enviar}
              disabled={
                !formularioCompleto || hayFechasInvalidas || !config.canSubmit || guardarBorrador.isPending || enviarAAprobacion.isPending
              }
            >
              {config.status === 'REJECTED' ? 'Reenviar a aprobación' : 'Enviar a aprobación'}
            </Button>
          </div>
        )}
      </GlassCard>
    </>
  );
}
