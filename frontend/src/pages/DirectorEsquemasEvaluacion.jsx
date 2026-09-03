import { useState } from 'react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { useEvaluationSchemes, useCrearEsquema } from '@/hooks/useDirector';

const FORM_INICIAL = { name: '', examCountExpected: 2, hasFinal: true, minRegularGrade: 4, minApprovalGrade: 6, esPromocional: false, promotionMinGrade: '' };

function Campo({ label, children }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-xs font-semibold text-ink-soft">{label}</label>
      {children}
    </div>
  );
}

function Input(props) {
  return <input {...props} className="rounded-lg border border-border bg-white/70 px-3 py-2 text-[13.5px] text-ink" />;
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
  const [form, setForm] = useState(FORM_INICIAL);
  const [error, setError] = useState(null);

  async function crear() {
    setError(null);
    try {
      await crearEsquema.mutateAsync({
        name: form.name,
        examCountExpected: Number(form.examCountExpected),
        hasFinal: form.hasFinal,
        finalWeight: null,
        aggregationMethod: null,
        minRegularGrade: Number(form.minRegularGrade),
        minApprovalGrade: Number(form.minApprovalGrade),
        promotionMinGrade: form.esPromocional ? Number(form.promotionMinGrade) : null,
        notes: null,
      });
      setForm(FORM_INICIAL);
    } catch (e) {
      setError(e.message);
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
          <div className="grid grid-cols-5 gap-3 border-b border-border px-4 py-2.5 text-[10.5px] font-bold uppercase tracking-wide text-ink-faint">
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
              <div key={e.id} className="grid grid-cols-5 items-center gap-3 border-b border-border px-4 py-3 last:border-b-0">
                <div className="text-[13.5px] font-semibold text-ink">{e.name}</div>
                <div className="text-[13px] font-semibold text-ink">{e.minRegularGrade}</div>
                <div className="text-[13px] font-semibold text-ink">{e.minApprovalGrade}</div>
                <div className="text-[13px] font-semibold text-ink">{e.promotionMinGrade ? `Desde ${e.promotionMinGrade}` : 'No ofrece'}</div>
                <div>
                  <StatusPill tone={e.inUse ? 'warning' : 'success'}>{e.inUse ? 'En uso' : 'Editable'}</StatusPill>
                </div>
              </div>
            ))
          )}
          <div className="px-4 py-3 text-[11.5px] text-ink-faint">
            Un esquema &ldquo;En uso&rdquo; ya está asignado a alguna comisión y no se puede editar más — creá uno nuevo si cambia la normativa.
          </div>
        </GlassCard>

        <GlassCard className="flex flex-1 flex-col gap-4 p-5">
          <div className="font-heading text-[15px] font-semibold text-ink">Nuevo esquema</div>

          <Campo label="Nombre">
            <Input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} placeholder="Ej: Reglamento 2027..." />
          </Campo>

          <div className="grid grid-cols-2 gap-3">
            <Campo label="Mínimo para quedar regular">
              <Input type="number" step="0.1" value={form.minRegularGrade} onChange={(e) => setForm((f) => ({ ...f, minRegularGrade: e.target.value }))} />
            </Campo>
            <Campo label="Nota para aprobar el final">
              <Input type="number" step="0.1" value={form.minApprovalGrade} onChange={(e) => setForm((f) => ({ ...f, minApprovalGrade: e.target.value }))} />
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
                  placeholder="Nota de corte"
                  value={form.promotionMinGrade}
                  onChange={(e) => setForm((f) => ({ ...f, promotionMinGrade: e.target.value }))}
                  className="w-32 rounded-lg border border-border bg-white/70 px-3 py-2 text-[13.5px] text-ink"
                />
              )}
            </div>
          </Campo>

          {error && <div className="text-sm font-semibold text-danger">{error}</div>}

          <Button
            onClick={crear}
            disabled={crearEsquema.isPending || !form.name || (form.esPromocional && !form.promotionMinGrade)}
          >
            Crear esquema
          </Button>
        </GlassCard>
      </div>
    </PageContainer>
  );
}
