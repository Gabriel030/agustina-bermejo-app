import { ChevronRight } from 'lucide-react';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { StatusPill } from '@/components/StatusPill';
import { SignatoryForm } from '@/features/graduation/signatories/components/SignatoryForm';
import { activityOf } from '@/features/graduation/signatories/lib/certificateSignatories';

export function SignatoryAccordionItem({ signatory, open, canEdit, onOpenChange }) {
  const activity = activityOf(signatory);
  return (
    <Collapsible open={open} onOpenChange={onOpenChange} className="rounded-2xl">
      <CollapsibleTrigger className="group flex min-h-14 w-full items-center gap-3 rounded-2xl px-4 py-3 text-left transition-colors hover:bg-neutral-bg/60">
        <span className="flex min-w-0 flex-1 flex-col">
          <span className={`truncate font-semibold ${signatory.active ? 'text-ink' : 'text-ink-faint'}`}>{signatory.name}</span>
          <span className="truncate text-size-13 text-ink-soft">{signatory.role}</span>
        </span>
        <StatusPill tone={activity.tone}>{activity.label}</StatusPill>
        <ChevronRight aria-hidden="true" className="size-4 shrink-0 text-ink-faint transition-transform group-data-[state=open]:rotate-90" />
      </CollapsibleTrigger>
      <CollapsibleContent className="px-4 pb-3">
        <SignatoryForm signatory={signatory} canEdit={canEdit} onClose={() => onOpenChange(false)} />
      </CollapsibleContent>
    </Collapsible>
  );
}
