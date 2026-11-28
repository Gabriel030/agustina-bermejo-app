import { ChevronRight } from 'lucide-react';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';

export function HelpResultItem({ title, snippet, draft = false, onOpen }) {
  return (
    <Button type="button" variant="ghost" size="row" onClick={onOpen} className="justify-start gap-3 px-3">
      <span className="min-w-0 flex-1">
        <span className="block text-size-14 font-medium text-ink">{title}</span>
        {snippet && <span className="mt-0.5 block text-size-13 font-normal text-ink-faint">{snippet}</span>}
      </span>
      {draft && <StatusPill tone="neutral">Borrador</StatusPill>}
      <ChevronRight aria-hidden="true" className="h-4 w-4 shrink-0 text-ink-faint" />
    </Button>
  );
}
