import { ChevronDown } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Notice } from '@/components/Notice';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { CopyableCommand } from '@/features/backups/components/CopyableCommand';
import { RECOVERY_PLAIN_LINES, REPLACEMENT_PLAIN_LINES, recoverySteps, replacementSteps } from '@/features/backups/lib/restoreSteps';

const PLATFORM_GUIDE_URL = 'https://github.com/Gabriel030/agustina-bermejo-app/blob/develop/docs/deploy/backup-and-restore.md';

function PlainLines({ lines }) {
  return (
    <dl className="grid grid-cols-1 gap-x-4 gap-y-0.5 px-5 pb-4 text-size-14 md:grid-cols-[max-content_minmax(0,1fr)] md:gap-y-2">
      {lines.map(([term, definition]) => (
        <div key={term} className="contents">
          <dt className="pt-0.5 text-size-11 font-bold tracking-wide text-ink-soft uppercase">{term}</dt>
          <dd className="mb-2 leading-snug md:mb-0">{definition}</dd>
        </div>
      ))}
    </dl>
  );
}

function CommandSteps({ steps }) {
  return (
    <ol className="flex flex-col gap-4 px-5 pb-5">
      {steps.map((step, index) => (
        <li key={step.title} className="grid grid-cols-[1.75rem_minmax(0,1fr)] gap-x-3">
          <span className="grid size-7 place-items-center rounded-full bg-accent-soft text-size-13 font-bold text-primary" aria-hidden="true">
            {index + 1}
          </span>
          <div className="min-w-0">
            <div className="pt-1 text-size-14 font-bold">{step.title}</div>
            {step.description && <div className="text-size-13-5 leading-snug text-ink-soft">{step.description}</div>}
            {step.checks && (
              <ul aria-label="Chequeos antes de abrir" className="mt-1.5 flex flex-col gap-1 text-size-13-5 text-ink">
                {step.checks.map((check) => (
                  <li key={check} className="flex items-start gap-2">
                    <span className="mt-0.5 size-3.5 shrink-0 rounded-sm border-[1.5px] border-ink-soft" aria-hidden="true" />
                    {check}
                  </li>
                ))}
              </ul>
            )}
            {step.command && <CopyableCommand stepNumber={index + 1} stepTitle={step.title} actor={step.actor} command={step.command} />}
          </div>
        </li>
      ))}
    </ol>
  );
}

function RestoreSection({ title, subtitle, defaultOpen, children }) {
  return (
    <Collapsible defaultOpen={defaultOpen} asChild>
      <GlassCard className="overflow-hidden p-0">
        <CollapsibleTrigger className="group flex w-full items-center justify-between gap-3 px-5 py-4 text-left focus-visible:outline-2 focus-visible:-outline-offset-3 focus-visible:outline-brand">
          <span>
            <span className="block font-heading text-size-16 font-bold text-ink">{title}</span>
            <span className="mt-0.5 block text-size-12-5 text-ink-soft">{subtitle}</span>
          </span>
          <ChevronDown className="size-4 shrink-0 transition-transform group-data-[state=open]:rotate-180 motion-reduce:transition-none" aria-hidden="true" />
        </CollapsibleTrigger>
        <CollapsibleContent>{children}</CollapsibleContent>
      </GlassCard>
    </Collapsible>
  );
}

export function BackupHowToRestore({ isPlatformAdmin }) {
  return (
    <div className="flex flex-col gap-4">
      <Notice>
        <b>Esta pantalla no restaura nada.</b> Restaurar lo hace la persona técnica en el servidor.
      </Notice>
      <RestoreSection title="Recuperar algo que se perdió" subtitle="La app sigue andando." defaultOpen>
        {isPlatformAdmin ? <CommandSteps steps={recoverySteps()} /> : <PlainLines lines={RECOVERY_PLAIN_LINES} />}
      </RestoreSection>
      <RestoreSection
        title="Pasar a trabajar con la base restaurada"
        subtitle="Cuando la base en uso se dañó. Unos minutos con la app parada."
        defaultOpen={false}
      >
        {isPlatformAdmin ? <CommandSteps steps={replacementSteps()} /> : <PlainLines lines={REPLACEMENT_PLAIN_LINES} />}
      </RestoreSection>
      <GlassCard className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 px-5 py-3.5 text-size-14">
        <span>
          <b className="font-bold">Si no estás seguro, no restaures:</b> avisá a soporte técnico.
        </span>
        {isPlatformAdmin && (
          <a className="font-semibold text-primary underline underline-offset-3" href={PLATFORM_GUIDE_URL} target="_blank" rel="noopener noreferrer">
            Ver guía completa
          </a>
        )}
      </GlassCard>
    </div>
  );
}
