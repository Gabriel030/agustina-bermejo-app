import { GlassCard } from '@/components/GlassCard';
import { ChoiceChip } from '@/components/ChoiceChip';
import { Input } from '@/components/ui/input';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { CHANNEL_LABELS } from '../lib/broadcastWizard';

export function ChannelStep({ state, onChange }) {
  return (
    <GlassCard className="flex flex-col gap-5 p-5">
      <div className="flex flex-col gap-1.5">
        <label htmlFor="broadcast-name" className="text-size-13 font-semibold text-ink">
          Nombre interno
        </label>
        <Input id="broadcast-name" value={state.name} onChange={(event) => onChange({ name: event.target.value })} />
      </div>
      <div className="flex flex-col gap-1.5">
        <span className="text-size-13 font-semibold text-ink">Canal</span>
        <div role="group" aria-label="Canal" className="flex flex-wrap gap-2">
          {Object.entries(CHANNEL_LABELS).map(([key, label]) => (
            <ChoiceChip key={key} selected={state.channel === key} onClick={() => onChange({ channel: key })}>
              {label}
            </ChoiceChip>
          ))}
        </div>
      </div>
      <div className="flex flex-col gap-1.5">
        <span className="text-size-13 font-semibold text-ink">Categoría</span>
        <div role="group" aria-label="Categoría" className="flex flex-wrap gap-2">
          <ChoiceChip selected onClick={() => {}}>
            Institucional
          </ChoiceChip>
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <span tabIndex={0}>
                  <ChoiceChip selected={false} disabled>
                    Promocional
                  </ChoiceChip>
                </span>
              </TooltipTrigger>
              <TooltipContent>Se habilita al validar el marco legal. Hasta entonces solo salen avisos institucionales.</TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </div>
      </div>
    </GlassCard>
  );
}
