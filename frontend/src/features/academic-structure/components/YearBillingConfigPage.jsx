import { useMemo, useState } from 'react';
import { toast } from 'sonner';
import { ChangesTable } from '@/components/ChangesTable';
import { GlassCard } from '@/components/GlassCard';
import { Stepper } from '@/components/Stepper';
import { InfoTip } from '@/components/charts/InfoTip';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useUpdateYearBillingConfiguration } from '@/features/academic-structure/hooks/useCohorts';
import { MESES } from '@/features/academic-structure/lib/aniosLectivos';
import {
  advanceOpeningError,
  billingTimeline,
  diffBillingForm,
  emissionMonthOf,
  formatIso,
  toBillingForm,
  toBillingPayload,
} from '@/features/academic-structure/lib/billingConfigForm';
import { NativeSelect } from '@/components/ui/native-select';

const STEPS = [
  { key: 'changes', label: 'Cambios' },
  { key: 'confirm', label: 'Confirmar' },
];

function FieldLabel({ htmlFor, tip, tipLabel, children }) {
  return (
    <div className="flex items-center gap-1">
      <Label htmlFor={htmlFor}>{children}</Label>
      {tip && <InfoTip label={tipLabel}>{tip}</InfoTip>}
    </div>
  );
}

function Timeline({ events, title }) {
  if (events.length === 0) return null;
  return (
    <GlassCard className="flex flex-col gap-3 p-5">
      <h2 className="font-heading text-lg font-semibold text-ink">{title}</h2>
      <ol className="flex flex-col gap-2">
        {events.map((event) => (
          <li key={event.key} className="flex flex-wrap items-baseline gap-x-3 text-sm">
            <span className="w-24 shrink-0 font-semibold tabular-nums text-ink">{formatIso(event.date)}</span>
            <span className="text-ink-soft">{event.label}</span>
          </li>
        ))}
      </ol>
    </GlassCard>
  );
}

function leadMonthsTip(term, form) {
  if (!term?.enrollmentStart) return 'Cuántos meses antes de que abra la inscripción se emite la matrícula regular.';
  const emission = emissionMonthOf(term, form.enrollmentFeeLeadMonths);
  const opening = formatIso(term.enrollmentStart);
  const closing = formatIso(term.enrollmentEnd);
  return `Es cuántos meses antes de que abra la inscripción se emite la matrícula regular. Con ${form.enrollmentFeeLeadMonths} y la inscripción abriendo el ${opening}, la matrícula se genera en ${MESES[emission.month - 1].toLowerCase()} de ${emission.year}, entre el 1 y el 5. Vence al cierre de la ventana (${closing}), no a los 10 días.`;
}

function advanceTip(term) {
  const closing = term?.enrollmentEnd ? formatIso(term.enrollmentEnd) : 'el cierre de la ventana';
  const opening = term?.enrollmentStart ? formatIso(term.enrollmentStart) : 'el inicio de la ventana';
  return `Es la fecha desde la que se puede generar la matrícula del año siguiente para quien quiera adelantarla. «Con la inscripción» la abre el ${opening}. Una fecha anterior, por ejemplo 01/09, la adelanta. No puede pasar del ${closing}. Si queda después de la emisión regular, nadie va a poder adelantarla.`;
}

export function YearBillingConfigPage({ template, title, term, onClose }) {
  const update = useUpdateYearBillingConfiguration();
  const base = useMemo(() => toBillingForm(template), [template]);
  const [form, setForm] = useState(base);
  const [step, setStep] = useState('changes');
  const [opensFromDate, setOpensFromDate] = useState(base.advanceEnrollmentFeeOpensOn !== '');
  const [violations, setViolations] = useState([]);
  const twoFees = form.enrollmentFeeCount === '2';
  const rows = diffBillingForm(base, form);
  const openingError = advanceOpeningError(term, form.advanceEnrollmentFeeOpensOn);
  const secondMonthError = twoFees && Number(form.secondEnrollmentFeeMonth) <= Number(form.firstEnrollmentFeeMonth);
  const timeline = billingTimeline({ term, form });

  function set(key, value) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function review() {
    if (secondMonthError) {
      toast.error('El mes de la segunda matrícula tiene que ser posterior al de la primera.');
      return;
    }
    setStep('confirm');
  }

  async function save() {
    setViolations([]);
    try {
      await update.mutateAsync({ templateId: template.id, ...toBillingPayload(form) });
      toast.success('Configuración del año lectivo guardada.');
      onClose();
    } catch (error) {
      setViolations(error?.violations ?? []);
      toast.error('No se pudo guardar la configuración.');
    }
  }

  const stepper = <Stepper pasos={STEPS} pasoActual={step} pasosVisitados={step === 'confirm' ? ['changes', 'confirm'] : ['changes']} onStepClick={(key) => key === 'changes' && setStep('changes')} />;

  const violationList = violations.length > 0 && (
    <ul role="alert" className="list-disc space-y-1 pl-5 text-sm text-danger">
      {violations.map((violation) => (
        <li key={violation.code}>{violation.message}</li>
      ))}
    </ul>
  );

  if (step === 'confirm') {
    return (
      <div className="flex flex-col gap-5">
        <h2 className="font-heading text-xl font-semibold text-ink">Configurar cobro — {title}</h2>
        {stepper}
        <GlassCard className="flex flex-col gap-4 p-5">
          <h3 className="font-heading text-lg font-semibold text-ink">Qué cambia</h3>
          {rows.length === 0 ? <p className="text-sm text-ink-soft">No hay cambios para confirmar.</p> : <ChangesTable rows={rows} />}
        </GlassCard>
        {rows.length > 0 && <Timeline events={timeline} title="Qué va a pasar" />}
        {rows.length > 0 && (
          <GlassCard className="flex flex-col gap-2 p-5">
            <h3 className="font-heading text-lg font-semibold text-ink">Para tener en cuenta</h3>
            <ul className="flex list-disc flex-col gap-1 pl-5 text-sm text-ink-soft">
              <li>La matrícula anticipada se factura al precio de la versión cargada para el año siguiente y queda fija en la factura.</li>
              <li>No reserva vacante: pagarla no garantiza lugar.</li>
              <li>Si queda impaga al cierre de la ventana, se anula sola. Si está paga, no se devuelve.</li>
            </ul>
          </GlassCard>
        )}
        {violationList}
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="outline" onClick={() => setStep('changes')} disabled={update.isPending}>
            Volver a los cambios
          </Button>
          <Button variant="gradient" onClick={save} disabled={rows.length === 0} loading={update.isPending}>
            Guardar cambios
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-5">
      <h2 className="font-heading text-xl font-semibold text-ink">Configurar cobro — {title}</h2>
      {stepper}
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
        <GlassCard className="flex flex-col gap-4 p-5">
          <div className="flex flex-col gap-1.5">
            <FieldLabel htmlFor="anio-apertura" tipLabel="Qué significa Matrícula anticipada · Abre" tip={advanceTip(term)}>
              Matrícula anticipada · Abre
            </FieldLabel>
            <NativeSelect
              id="anio-apertura"
              value={opensFromDate ? 'date' : 'window'}
              onChange={(event) => {
                const fromDate = event.target.value === 'date';
                setOpensFromDate(fromDate);
                set('advanceEnrollmentFeeOpensOn', fromDate ? (term?.enrollmentStart?.split('T')[0] ?? '') : '');
              }}
            >
              <option value="window">Con la inscripción</option>
              <option value="date">Desde una fecha</option>
            </NativeSelect>
            {opensFromDate && (
              <Input
                aria-label="Fecha de apertura de la matrícula anticipada"
                type="date"
                max={term?.enrollmentEnd?.split('T')[0]}
                value={form.advanceEnrollmentFeeOpensOn}
                onChange={(event) => set('advanceEnrollmentFeeOpensOn', event.target.value)}
              />
            )}
            {openingError && <p role="alert" className="text-size-12 text-danger">{openingError}</p>}
          </div>

          <div className="flex flex-col gap-1.5">
            <FieldLabel
              htmlFor="anio-cantidad"
              tipLabel="Qué significa Cantidad de matrículas por año"
              tip="Con 1 por año se cobra una sola matrícula que cubre los dos cuatrimestres. Con 2 se cobra una por cuatrimestre."
            >
              Cantidad de matrículas por año
            </FieldLabel>
            <NativeSelect id="anio-cantidad" value={form.enrollmentFeeCount} onChange={(event) => set('enrollmentFeeCount', event.target.value)}>
              <option value="1">1 por año (cubre los dos cuatrimestres)</option>
              <option value="2">2 por año (una por cuatrimestre)</option>
            </NativeSelect>
          </div>

          <div className="flex flex-col gap-1.5">
            <FieldLabel
              htmlFor="anio-primer-mes"
              tipLabel="Qué significa Mes de la matrícula"
              tip="Es el mes al que corresponde la matrícula. La emisión se calcula a partir de la apertura de la inscripción y de los meses de anticipación."
            >
              Mes de la {twoFees ? 'primera ' : ''}matrícula
            </FieldLabel>
            <NativeSelect id="anio-primer-mes" value={form.firstEnrollmentFeeMonth} onChange={(event) => set('firstEnrollmentFeeMonth', event.target.value)}>
              {MESES.map((name, i) => <option key={name} value={String(i + 1)}>{name}</option>)}
            </NativeSelect>
          </div>

          {twoFees && (
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="anio-segundo-mes">Mes de la segunda matrícula</Label>
              <NativeSelect id="anio-segundo-mes" value={form.secondEnrollmentFeeMonth} onChange={(event) => set('secondEnrollmentFeeMonth', event.target.value)}>
                {MESES.map((name, i) => <option key={name} value={String(i + 1)}>{name}</option>)}
              </NativeSelect>
            </div>
          )}

          <div className="flex flex-col gap-1.5">
            <FieldLabel htmlFor="anio-anticipacion" tipLabel="Qué significa Meses de anticipación" tip={leadMonthsTip(term, form)}>
              Meses de anticipación a la inscripción
            </FieldLabel>
            <Input id="anio-anticipacion" type="number" min="0" max="6" value={form.enrollmentFeeLeadMonths} onChange={(event) => set('enrollmentFeeLeadMonths', event.target.value)} />
          </div>

          <div className="flex flex-col gap-1.5">
            <FieldLabel
              htmlFor="anio-dias-tardia"
              tipLabel="Qué significa Días de vencimiento fuera de ventana"
              tip="Solo aplica si la matrícula se genera cuando la ventana de inscripción ya cerró: vence esa cantidad de días después de generarse. Con 10, una matrícula generada el 20/03 vence el 30/03."
            >
              Días de vencimiento de una matrícula emitida fuera de ventana
            </FieldLabel>
            <Input id="anio-dias-tardia" type="number" min="1" max="60" value={form.lateEnrollmentFeeDueDays} onChange={(event) => set('lateEnrollmentFeeDueDays', event.target.value)} />
          </div>

          <fieldset className="flex flex-col gap-2">
            <legend className="flex items-center gap-1 text-sm font-medium text-ink">
              Qué bloquea la matrícula impaga
              <InfoTip label="Qué significa Qué bloquea la matrícula impaga">
                Si la matrícula está impaga, la alumna no puede hacer lo que esté tildado acá hasta que la pague.
              </InfoTip>
            </legend>
            {[
              ['blocksEnrollment', 'Inscripción a materias'],
              ['blocksExamRegistration', 'Inscripción a mesas de examen'],
              ['blocksCertificates', 'Pedido de certificados'],
            ].map(([key, label]) => (
              <label key={key} className="flex items-center gap-2 text-size-13 text-ink">
                <Checkbox checked={form[key]} onCheckedChange={(checked) => set(key, checked === true)} />
                {label}
              </label>
            ))}
          </fieldset>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="anio-solo-finales">Alumnos que solo rinden finales: ¿deben pagar matrícula?</Label>
            <NativeSelect id="anio-solo-finales" value={form.requireEnrollmentFeeForFinalExamOnlyStudents ? 'si' : 'no'} onChange={(event) => set('requireEnrollmentFeeForFinalExamOnlyStudents', event.target.value === 'si')}>
              <option value="no">No</option>
              <option value="si">Sí</option>
            </NativeSelect>
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="anio-paga-pausa">Alumnos en pausa académica: ¿pagan cuotas y matrícula?</Label>
            <NativeSelect id="anio-paga-pausa" value={form.paysWhilePaused ? 'si' : 'no'} onChange={(event) => set('paysWhilePaused', event.target.value === 'si')}>
              <option value="no">No</option>
              <option value="si">Sí</option>
            </NativeSelect>
          </div>
        </GlassCard>

        <Timeline events={timeline} title={`Así queda el año ${title.replace('Año lectivo ', '')}`} />
      </div>
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="outline" onClick={onClose}>
          Cancelar
        </Button>
        <Button variant="gradient" onClick={review} disabled={rows.length === 0 || Boolean(openingError)}>
          Revisar cambios
        </Button>
      </div>
    </div>
  );
}
