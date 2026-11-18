import { GlassCard } from '@/components/GlassCard';
import { ChangesTable } from '@/components/ChangesTable';
import { CHANNEL_LABELS } from '../lib/broadcastWizard';

export function ReviewStep({ state, preview }) {
  const included = preview?.included ?? 0;
  const excluded = (preview?.total ?? 0) - included;
  const rows = [
    { key: 'included', label: 'Reciben', today: '0', next: String(included), difference: `+${included}` },
    { key: 'excluded', label: 'Excluidos', today: '0', next: String(excluded), difference: `+${excluded}` },
  ];
  return (
    <GlassCard className="flex flex-col gap-5 p-5">
      <dl className="grid gap-2 text-size-13 sm:grid-cols-[140px_1fr]">
        <dt className="text-ink-soft">Nombre</dt>
        <dd className="text-ink">{state.name}</dd>
        <dt className="text-ink-soft">Canal</dt>
        <dd className="text-ink">{CHANNEL_LABELS[state.channel]}</dd>
        <dt className="text-ink-soft">Categoría</dt>
        <dd className="text-ink">Institucional</dd>
        {state.channel === 'EMAIL' && (
          <>
            <dt className="text-ink-soft">Asunto</dt>
            <dd className="text-ink">{state.subject}</dd>
          </>
        )}
        <dt className="text-ink-soft">Mensaje</dt>
        <dd className="whitespace-pre-wrap text-ink">{state.bodyText}</dd>
      </dl>
      <ChangesTable rows={rows} />
    </GlassCard>
  );
}
