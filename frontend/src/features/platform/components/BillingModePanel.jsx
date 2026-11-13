import { Check, Minus } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { BILLING_EFFECT_QUESTIONS, BILLING_MODES, BILLING_MODE_INFO, modeAvailability } from '@/features/platform/lib/featureCatalog';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

export function BillingModePanel({ billing, onSelectMode }) {
  return (
    <GlassCard className="flex flex-col gap-4 p-5">
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 font-heading text-size-15 font-semibold text-ink">Modo de Facturación</legend>
        <div className="grid gap-2 md:grid-cols-3">
          {BILLING_MODES.map((mode) => {
            const info = BILLING_MODE_INFO[mode];
            const selected = billing.mode === mode;
            const { available, reason } = modeAvailability(billing.mode, mode);
            const whyId = `billing-mode-${mode}-why`;
            return (
              <label
                key={mode}
                className={`flex flex-col gap-1 rounded-xl border p-3 text-sm ${selected ? 'border-primary bg-accent-soft' : 'border-line'} ${available ? 'cursor-pointer' : 'cursor-not-allowed opacity-70'}`}
              >
                <span className="flex items-center gap-2 font-semibold text-ink">
                  <input
                    type="radio"
                    name="billing-mode"
                    value={mode}
                    checked={selected}
                    disabled={!available}
                    aria-describedby={reason ? whyId : undefined}
                    onChange={() => onSelectMode(mode)}
                  />
                  {info.label}
                  {selected && <span className="text-xs font-medium text-primary">Actual</span>}
                </span>
                <span className="text-ink-soft">{info.description}</span>
                {reason && <span id={whyId} className="text-xs font-semibold text-warning">{reason}</span>}
              </label>
            );
          })}
        </div>
      </fieldset>
      <Table className="w-full text-left text-sm">
        <TableHeader>
          <TableRow className="text-xs text-ink-faint">
            <TableHead scope="col" className="py-1 pr-2"><span className="sr-only">Pregunta</span></TableHead>
            {BILLING_MODES.map((mode) => (
              <TableHead key={mode} scope="col" className="py-1 text-center font-semibold">{BILLING_MODE_INFO[mode].label}</TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {BILLING_EFFECT_QUESTIONS.map((question, index) => (
            <TableRow key={question} className="border-t border-line">
              <TableHead scope="row" className="h-auto py-1.5 pr-2 text-sm font-medium normal-case tracking-normal text-ink whitespace-normal">{question}</TableHead>
              {BILLING_MODES.map((mode) => {
                const yes = BILLING_MODE_INFO[mode].effects[index];
                return (
                  <TableCell key={mode} className="py-1.5 text-center">
                    {yes ? <Check className="mx-auto h-4 w-4 text-success" aria-hidden="true" /> : <Minus className="mx-auto h-4 w-4 text-ink-faint" aria-hidden="true" />}
                    <span className="sr-only">{yes ? 'Sí' : 'No'}</span>
                  </TableCell>
                );
              })}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </GlassCard>
  );
}
