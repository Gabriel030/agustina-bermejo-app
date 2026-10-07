import { useState } from 'react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  useEvaluationSchemes,
  useCrearEsquema,
  useEditarEsquema,
} from '@/features/evaluation/hooks/useEvaluationConfig';

const FORM_INICIAL = { name: '', examCountExpected: 2, hasFinal: true, minRegularGrade: 4, minApprovalGrade: 6, esPromocional: false, promotionMinGrade: '' };

const MENSAJES_ERROR_ESQUEMA = {
  INVALID_EVALUATION_SCHEME: 'El esquema tiene notas fuera de 1 a 10 o una nota de promoción menor que la de aprobación.',
  EVALUATION_SCHEME_IN_USE: 'Ese esquema ya está asignado a alguna comisión y no se puede editar: creá uno nuevo.',
};

function enEscala(valor) {
  const numero = Number(valor);
  return valor !== '' && !Number.isNaN(numero) && numero >= 1 && numero <= 10;
}

function problemaDelEsquema(form) {
  const notas = [form.minRegularGrade, form.minApprovalGrade, ...(form.esPromocional && form.promotionMinGrade !== '' ? [form.promotionMinGrade] : [])];
  if (!notas.every(enEscala)) return 'Las notas van de 1 a 10.';
  if (form.esPromocional && form.promotionMinGrade !== '') {
    const corte = Number(form.promotionMinGrade);
    if (corte < Number(form.minApprovalGrade) || corte < Number(form.minRegularGrade)) {
      return 'La nota de promoción no puede ser menor que la de aprobación ni que la de regularidad.';
    }
  }
  return null;
}

function formDesdeEsquema(esquema) {
  return {
    name: esquema.name,
    examCountExpected: esquema.examCountExpected,
    hasFinal: esquema.hasFinal,
    minRegularGrade: esquema.minRegularGrade,
    minApprovalGrade: esquema.minApprovalGrade,
    esPromocional: esquema.promotionMinGrade != null,
    promotionMinGrade: esquema.promotionMinGrade ?? '',
  };
}

function Campo({ label, htmlFor, children }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={htmlFor} className="text-xs font-semibold text-ink-soft">{label}</label>
      {children}
    </div>
  );
}

function ChipSelector({ opciones, valor, onChange }) {
  return (
    <div className="flex gap-2">
      {opciones.map((op) => (
        <button
          key={op.value}
          type="button"
          onClick={() => onChange(op.value)}
          className={`rounded-lg px-4 py-2 text-sm font-bold ${
            valor === op.value ? 'bg-gradient-to-br from-primary to-brand text-white' : 'bg-neutral-bg text-ink-faint'
          }`}
        >
          {op.label}
        </button>
      ))}
    </div>
  );
}

export function DirectorEsquemasEvaluacion() {
  const { data: esquemas, isLoading } = useEvaluationSchemes();
  const crearEsquema = useCrearEsquema();
  const editarEsquema = useEditarEsquema();
  const [form, setForm] = useState(FORM_INICIAL);
  const [editando, setEditando] = useState(null);
  const [error, setError] = useState(null);
  const problema = problemaDelEsquema(form);
  const guardando = crearEsquema.isPending || editarEsquema.isPending;

  function empezarEdicion(esquema) {
    setError(null);
    setEditando(esquema);
    setForm(formDesdeEsquema(esquema));
  }

  function cancelarEdicion() {
    setError(null);
    setEditando(null);
    setForm(FORM_INICIAL);
  }

  async function guardar() {
    setError(null);
    const payload = {
      name: form.name,
      examCountExpected: Number(form.examCountExpected),
      hasFinal: form.hasFinal,
      finalWeight: editando?.finalWeight ?? null,
      aggregationMethod: editando?.aggregationMethod ?? 'SIMPLE_AVERAGE',
      minRegularGrade: Number(form.minRegularGrade),
      minApprovalGrade: Number(form.minApprovalGrade),
      promotionMinGrade: form.esPromocional ? Number(form.promotionMinGrade) : null,
      notes: editando?.notes ?? null,
    };
    try {
      if (editando) {
        await editarEsquema.mutateAsync({ id: editando.id, payload });
      } else {
        await crearEsquema.mutateAsync(payload);
      }
      setEditando(null);
      setForm(FORM_INICIAL);
    } catch (e) {
      setError(MENSAJES_ERROR_ESQUEMA[e.code] ?? e.message);
    }
  }

  return (
    <PageContainer>
      <div>
        <h1 className="font-heading text-2xl font-bold text-ink">Esquemas de evaluación</h1>
        <p className="mt-0.5 text-sm text-ink-soft">Política institucional: mínimos, promoción y régimen — se asignan a las comisiones al crearlas</p>
      </div>

      <div className="flex flex-col gap-5 lg:flex-row">
        <GlassCard className="flex-[1.4] p-2">
          <div className="grid grid-cols-2 gap-3 border-b border-border px-4 py-2.5 text-[10.5px] font-bold uppercase tracking-wide text-ink-faint md:grid-cols-5">
            <div>Nombre</div>
            <div>Regular</div>
            <div>Aprobar</div>
            <div>Promoción</div>
            <div>Estado</div>
          </div>
          {isLoading ? (
            <div className="flex flex-col gap-2 p-4">
              <Skeleton className="h-8" />
              <Skeleton className="h-8" />
            </div>
          ) : esquemas?.length === 0 ? (
            <EmptyState title="No hay esquemas todavía" description="Creá el primero con el formulario de al lado." className="p-6" />
          ) : (
            esquemas?.map((e) => (
              <div key={e.id} className="grid grid-cols-2 items-center gap-3 border-b border-border px-4 py-3 last:border-b-0 md:grid-cols-5">
                <div className="text-[13.5px] font-semibold text-ink">{e.name}</div>
                <div className="text-[13px] font-semibold text-ink">{e.minRegularGrade}</div>
                <div className="text-[13px] font-semibold text-ink">{e.minApprovalGrade}</div>
                <div className="text-[13px] font-semibold text-ink">{e.promotionMinGrade ? `Desde ${e.promotionMinGrade}` : 'No ofrece'}</div>
                <div className="flex flex-wrap items-center gap-2">
                  <StatusPill tone={e.inUse ? 'warning' : 'success'}>{e.inUse ? 'En uso' : 'Editable'}</StatusPill>
                  {!e.inUse && (
                    <Button variant="outline" size="sm" aria-label={`Editar ${e.name}`} onClick={() => empezarEdicion(e)}>
                      Editar
                    </Button>
                  )}
                </div>
              </div>
            ))
          )}
          <div className="px-4 py-3 text-[11.5px] text-ink-faint">
            Un esquema &ldquo;En uso&rdquo; ya está asignado a alguna comisión y no se puede editar más — creá uno nuevo si cambia la normativa.
          </div>
        </GlassCard>

        <GlassCard className="flex flex-1 flex-col gap-4 p-5">
          <div className="font-heading text-[15px] font-semibold text-ink">{editando ? 'Editar esquema' : 'Nuevo esquema'}</div>

          <Campo label="Nombre">
            <Input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} placeholder="Ej: Reglamento 2027..." />
          </Campo>

          <div className="grid grid-cols-2 gap-3">
            <Campo label="Mínimo para quedar regular" htmlFor="esquema-regular">
              <Input id="esquema-regular" type="number" step="0.1" min="0" max="10" value={form.minRegularGrade} onChange={(e) => setForm((f) => ({ ...f, minRegularGrade: e.target.value }))} />
            </Campo>
            <Campo label="Nota para aprobar el final" htmlFor="esquema-aprobar">
              <Input id="esquema-aprobar" type="number" step="0.1" min="0" max="10" value={form.minApprovalGrade} onChange={(e) => setForm((f) => ({ ...f, minApprovalGrade: e.target.value }))} />
            </Campo>
          </div>

          <Campo label="Cantidad de parciales esperados">
            <ChipSelector
              opciones={[1, 2, 3, 4].map((n) => ({ value: n, label: String(n) }))}
              valor={form.examCountExpected}
              onChange={(v) => setForm((f) => ({ ...f, examCountExpected: v }))}
            />
          </Campo>

          <Campo label="¿Tiene examen final?">
            <ChipSelector
              opciones={[{ value: true, label: 'Sí' }, { value: false, label: 'No' }]}
              valor={form.hasFinal}
              onChange={(v) => setForm((f) => ({ ...f, hasFinal: v }))}
            />
          </Campo>

          <Campo label="¿Es promocional?">
            <div className="flex items-center gap-3">
              <ChipSelector
                opciones={[{ value: true, label: 'Sí' }, { value: false, label: 'No' }]}
                valor={form.esPromocional}
                onChange={(v) => setForm((f) => ({ ...f, esPromocional: v }))}
              />
              {form.esPromocional && (
                <Input
                  type="number"
                  step="0.1"
                  min="0"
                  max="10"
                  placeholder="Nota de corte"
                  value={form.promotionMinGrade}
                  onChange={(e) => setForm((f) => ({ ...f, promotionMinGrade: e.target.value }))}
                  className="w-32"
                />
              )}
            </div>
          </Campo>

          {problema && <div className="text-sm font-semibold text-danger">{problema}</div>}
          {error && <div className="text-sm font-semibold text-danger">{error}</div>}

          <div className="flex flex-wrap gap-2">
            {editando && (
              <Button variant="outline" onClick={cancelarEdicion}>
                Cancelar
              </Button>
            )}
            <Button
              onClick={guardar}
              loading={guardando}
              disabled={guardando || !form.name || !!problema || (form.esPromocional && !form.promotionMinGrade)}
            >
              {editando ? 'Guardar cambios' : 'Crear esquema'}
            </Button>
          </div>
        </GlassCard>
      </div>
    </PageContainer>
  );
}
