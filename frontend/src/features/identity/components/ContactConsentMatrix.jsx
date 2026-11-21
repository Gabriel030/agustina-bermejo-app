import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { Skeleton } from '@/components/Skeleton';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { useContactConsents, useUpdateContactConsent } from '@/features/identity/hooks/useContactConsents';
import { usePerfilPropio } from '@/features/identity/hooks/useMiPerfil';
import {
  CONSENT_CATEGORIES,
  CONSENT_CATEGORY_LABELS,
  CONSENT_CHANNELS,
  CONSENT_CHANNEL_LABELS,
  PROMOTIONAL_CONSENT_ENABLED,
} from '@/features/identity/lib/contactConsentLabels';

const PHONE_TOOLTIP = 'Cargá tu teléfono en Mi perfil';
const PROMOTIONAL_TOOLTIP = 'Las comunicaciones promocionales se habilitan en breve.';
const INSTITUTIONAL_NOTE = 'La cobranza y los avisos obligatorios siguen llegando.';

const cellKey = (channel, category) => `${channel}:${category}`;

function ConsentSwitch({ label, checked, disabled, reason, onChange }) {
  const control = (
    <Switch checked={checked} onCheckedChange={onChange} disabled={disabled} aria-label={label} />
  );
  if (!reason) return control;
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span tabIndex={0} className="inline-flex">
          {control}
        </span>
      </TooltipTrigger>
      <TooltipContent>{reason}</TooltipContent>
    </Tooltip>
  );
}

export function ContactConsentMatrix() {
  const { data, isError, error, refetch } = useContactConsents();
  const { data: profile } = usePerfilPropio();
  const update = useUpdateContactConsent();
  const [pending, setPending] = useState({});
  const [saving, setSaving] = useState(false);

  const stored = (channel, category) =>
    (data ?? []).find((entry) => entry.channel === channel && entry.category === category);
  const isGranted = (channel, category) => {
    const key = cellKey(channel, category);
    if (key in pending) return pending[key];
    return stored(channel, category)?.granted ?? false;
  };
  const hasPhone = Boolean(profile?.telefono?.trim());
  const changedKeys = Object.keys(pending);
  const institutionalOff = CONSENT_CHANNELS.some(
    (channel) => cellKey(channel, 'INSTITUTIONAL') in pending && !pending[cellKey(channel, 'INSTITUTIONAL')],
  );

  function toggle(channel, category, value) {
    const key = cellKey(channel, category);
    const original = stored(channel, category)?.granted ?? false;
    setPending((current) => {
      const next = { ...current };
      if (value === original) delete next[key];
      else next[key] = value;
      return next;
    });
  }

  async function save() {
    setSaving(true);
    const failed = [];
    const remaining = {};
    for (const key of changedKeys) {
      const [channel, category] = key.split(':');
      try {
        await update.mutateAsync({ channel, category, granted: pending[key] });
      } catch {
        failed.push(`${CONSENT_CHANNEL_LABELS[channel]} · ${CONSENT_CATEGORY_LABELS[category]}`);
        remaining[key] = pending[key];
      }
    }
    setSaving(false);
    setPending(remaining);
    if (failed.length === 0) {
      toast.success('Guardamos tus preferencias de contacto.');
    } else {
      toast.error(`No se pudo guardar: ${failed.join(', ')}.`);
    }
  }

  if (!data && isError) {
    return <LoadErrorState bare error={error} onRetry={refetch} resource="tus preferencias de contacto" />;
  }

  if (!data) {
    return (
      <div className="flex flex-col gap-2">
        {[...Array(2)].map((_, i) => (
          <Skeleton key={i} className="h-14 w-full" />
        ))}
      </div>
    );
  }

  return (
    <TooltipProvider>
      <section aria-label="Consentimiento de contacto" className="flex flex-col gap-4">
        <h3 className="font-heading text-base font-bold text-ink">Comunicaciones de la institución</h3>
        <div className="flex flex-col gap-3">
          <div className="hidden grid-cols-3 gap-3 px-3 text-xs font-bold text-ink-soft min-[560px]:grid">
            <span>Canal</span>
            {CONSENT_CATEGORIES.map((category) => (
              <span key={category} className="text-center">
                {CONSENT_CATEGORY_LABELS[category]}
              </span>
            ))}
          </div>
          {CONSENT_CHANNELS.map((channel) => {
            const phoneMissing = channel === 'WHATSAPP' && !hasPhone;
            return (
              <div
                key={channel}
                className="grid grid-cols-2 items-center gap-3 rounded-xl bg-neutral-bg/55 p-3 min-[560px]:grid-cols-3"
              >
                <span className="col-span-2 font-semibold text-ink min-[560px]:col-span-1">
                  {CONSENT_CHANNEL_LABELS[channel]}
                </span>
                {CONSENT_CATEGORIES.map((category) => {
                  const entry = stored(channel, category);
                  const label = `${CONSENT_CHANNEL_LABELS[channel]} — ${CONSENT_CATEGORY_LABELS[category]}`;
                  const promotionalBlocked = category === 'PROMOTIONAL' && !PROMOTIONAL_CONSENT_ENABLED;
                  return (
                    <div key={category} className="flex items-center justify-between gap-2 min-[560px]:justify-center">
                      <span className="text-xs text-ink-soft min-[560px]:sr-only">
                        {CONSENT_CATEGORY_LABELS[category]}
                      </span>
                      {entry ? (
                        <ConsentSwitch
                          label={`${label}: ${isGranted(channel, category) ? 'activado' : 'desactivado'}`}
                          checked={isGranted(channel, category)}
                          disabled={saving || promotionalBlocked || phoneMissing}
                          reason={promotionalBlocked ? PROMOTIONAL_TOOLTIP : phoneMissing ? PHONE_TOOLTIP : null}
                          onChange={(value) => toggle(channel, category, value)}
                        />
                      ) : (
                        <span aria-label={`${label}: no aplica`} className="text-ink-faint">
                          —
                        </span>
                      )}
                    </div>
                  );
                })}
              </div>
            );
          })}
        </div>
        {institutionalOff && (
          <p role="status" className="rounded-xl bg-neutral-bg px-3 py-2 text-xs text-ink-soft">
            {INSTITUTIONAL_NOTE}
          </p>
        )}
        {changedKeys.length > 0 && (
          <div className="flex items-center justify-end gap-2">
            <Button size="sm" variant="ghost" onClick={() => setPending({})} disabled={saving}>
              Cancelar
            </Button>
            <Button size="sm" variant="primary" onClick={save} loading={saving}>
              Guardar
            </Button>
          </div>
        )}
      </section>
    </TooltipProvider>
  );
}
