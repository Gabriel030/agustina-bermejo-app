import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { StatusPill } from '@/components/StatusPill';
import { OVERRIDE_KEYS, SETTINGS_LABELS, formatSettingValue, inheritsAllOverrides } from './reminderSchedule';

const BOOLEAN_KEYS = ['suspensionNoticeEnabled', 'dailyDigestEnabled'];
const TIME_KEYS = ['emailWindowStart', 'emailWindowEnd'];

function Field({ name, value, onChange, disabled, inherited }) {
  const id = `setting-${name}`;
  const label = SETTINGS_LABELS[name];
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id} className="flex items-center gap-2">
        {label}
        {inherited === false && <StatusPill tone="accent">Propio</StatusPill>}
      </Label>
      {BOOLEAN_KEYS.includes(name) ? (
        <Switch id={id} checked={Boolean(value)} disabled={disabled} onCheckedChange={(checked) => onChange(name, checked)} />
      ) : (
        <Input
          id={id}
          type={TIME_KEYS.includes(name) ? 'time' : 'number'}
          min={name === 'postWithdrawalSummaryMonths' ? 1 : 0}
          max={name === 'postWithdrawalSummaryMonths' ? 24 : undefined}
          value={value ?? ''}
          disabled={disabled}
          onChange={(event) => onChange(name, TIME_KEYS.includes(name) ? event.target.value : Number(event.target.value))}
        />
      )}
    </div>
  );
}

export function GeneralSettingsForm({ values, onChange, disabled }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {Object.keys(SETTINGS_LABELS).map((name) => (
        <Field key={name} name={name} value={values[name]} onChange={onChange} disabled={disabled} />
      ))}
    </div>
  );
}

export function SendingOverrides({ overrides, general, onChange, disabled }) {
  const inheritsAll = inheritsAllOverrides(overrides);

  if (inheritsAll) {
    return (
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-ink-soft">Los ajustes de envío se heredan del general.</p>
        {!disabled && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => onChange(Object.fromEntries(OVERRIDE_KEYS.map((key) => [key, general?.[key] ?? null])))}
          >
            Personalizar
          </Button>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="grid gap-4 sm:grid-cols-2">
        {OVERRIDE_KEYS.map((name) => (
          <div key={name} className="flex flex-col gap-1">
            <Field
              name={name}
              value={overrides[name] ?? general?.[name]}
              inherited={overrides[name] == null}
              disabled={disabled}
              onChange={(key, value) => onChange({ ...overrides, [key]: value })}
            />
            <span className="text-xs text-ink-faint">General: {formatSettingValue(name, general?.[name])}</span>
          </div>
        ))}
      </div>
      {!disabled && (
        <div>
          <Button variant="ghost" size="sm" onClick={() => onChange(Object.fromEntries(OVERRIDE_KEYS.map((key) => [key, null])))}>
            Volver a heredar todo
          </Button>
        </div>
      )}
    </div>
  );
}
