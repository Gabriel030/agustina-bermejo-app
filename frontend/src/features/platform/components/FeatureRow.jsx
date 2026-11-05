import { StatusPill } from '@/components/StatusPill';
import { Switch } from '@/components/ui/switch';
import { BILLING_CODE, BILLING_MODE_INFO, FEATURE_CATALOG } from '@/features/platform/lib/featureCatalog';

function statusOf(code, feature, available) {
  const catalog = FEATURE_CATALOG[code];
  if (catalog.blocked) return { tone: 'warning', text: 'Bloqueado: importación sin definir' };
  if (!available) return { tone: 'neutral', text: 'No disponible · Necesita Facturación completa' };
  if (code === BILLING_CODE) return { tone: BILLING_MODE_INFO[feature.mode].tone, text: BILLING_MODE_INFO[feature.mode].label };
  return feature.enabled ? { tone: 'success', text: 'Habilitado' } : { tone: 'neutral', text: 'Deshabilitado' };
}

export function FeatureRow({ feature, available, onToggle }) {
  const catalog = FEATURE_CATALOG[feature.code];
  const Icon = catalog.icon;
  const status = statusOf(feature.code, feature, available);
  const isBilling = feature.code === BILLING_CODE;
  const switchable = !isBilling && available && !catalog.blocked;
  return (
    <li className="flex items-center gap-3 px-4 py-3">
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-accent-soft text-primary" aria-hidden="true">
        <Icon className="h-[18px] w-[18px]" />
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span id={`feature-${feature.code}-name`} className="font-heading text-[14px] font-semibold text-ink">{catalog.label}</span>
        <span className="text-[13px] text-ink-soft">{catalog.description}</span>
        <StatusPill tone={status.tone} className="mt-1">{status.text}</StatusPill>
      </div>
      {!isBilling && (
        <Switch
          checked={feature.enabled}
          disabled={!switchable}
          aria-labelledby={`feature-${feature.code}-name`}
          onCheckedChange={(next) => onToggle(feature.code, next)}
        />
      )}
    </li>
  );
}
