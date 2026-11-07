import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { InfoTip } from '@/components/charts/InfoTip';
import { Switch } from '@/components/ui/switch';
import { SETTING_CATALOG, stateLabel } from './paymentMethods';

const NOT_ENABLED_TIP = 'El instituto no tiene este medio habilitado. Lo habilita el dueño de la plataforma.';
const NOTIFICATIONS_TIP =
  'Interruptor maestro del instituto. Si está apagado, no sale ningún aviso de cobranza a los alumnos. Si está encendido, rigen los recordatorios y las preferencias de cada usuario.';

function SettingRow({ entry, readOnly, tip, onToggle }) {
  const catalog = SETTING_CATALOG[entry.key];
  const Icon = catalog.icon;
  const available = entry.platformEnabled;
  return (
    <li className="flex items-center gap-3 px-5 py-3">
      <span
        className={`flex size-10 shrink-0 items-center justify-center rounded-xl ${available ? 'bg-accent-soft text-primary' : 'bg-neutral-bg text-ink-faint'}`}
        aria-hidden="true"
      >
        <Icon className="size-5" />
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span id={`setting-${entry.key}-name`} className="font-heading text-[14px] font-semibold text-ink">
          {catalog.label}
        </span>
        <span className="text-[13px] text-ink-soft">{catalog.description}</span>
      </div>
      {!available && <StatusPill tone="neutral">No habilitado</StatusPill>}
      {available && readOnly && (
        <StatusPill tone={entry.enabled ? 'success' : 'neutral'} dot>
          {stateLabel(entry.enabled)}
        </StatusPill>
      )}
      {tip && <InfoTip label={`Más información sobre ${catalog.label.toLowerCase()}`}>{tip}</InfoTip>}
      {available && !readOnly && (
        <Switch checked={entry.enabled} aria-labelledby={`setting-${entry.key}-name`} onCheckedChange={() => onToggle(entry.key)} />
      )}
    </li>
  );
}

export function PaymentMethodsTab({ entries, readOnly, onToggle }) {
  return (
    <>
      {readOnly && (
        <div role="status" className="rounded-xl bg-accent-soft p-3 text-sm text-ink">
          Solo lectura. Los cambios los hace el director.
        </div>
      )}
      <GlassCard className="flex flex-col py-2">
        <h2 className="px-5 pb-1 pt-3 font-heading text-[17px] font-semibold text-ink">Medios de pago</h2>
        <ul className="divide-y divide-border">
          {entries.methods.map((entry) => (
            <SettingRow key={entry.key} entry={entry} readOnly={readOnly} tip={entry.platformEnabled ? null : NOT_ENABLED_TIP} onToggle={onToggle} />
          ))}
        </ul>
      </GlassCard>
      <GlassCard className="flex flex-col py-2">
        <h2 className="px-5 pb-1 pt-3 font-heading text-[17px] font-semibold text-ink">Avisos</h2>
        <ul>
          <SettingRow
            entry={entries.notifications}
            readOnly={readOnly}
            tip={entries.notifications.platformEnabled ? NOTIFICATIONS_TIP : NOT_ENABLED_TIP}
            onToggle={onToggle}
          />
        </ul>
      </GlassCard>
    </>
  );
}
