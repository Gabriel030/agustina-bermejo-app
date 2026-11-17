import { StatusPill } from '@/components/StatusPill';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { verdictPresentation } from '@/features/enrollment/lib/waitlist';

export function WaitlistVerdictPill({ verdict }) {
  const { label, tip, tone } = verdictPresentation(verdict);
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <span tabIndex={0} className="inline-flex cursor-help">
            <StatusPill tone={tone} dot>
              {label}
            </StatusPill>
          </span>
        </TooltipTrigger>
        <TooltipContent className="max-w-60">{tip}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
