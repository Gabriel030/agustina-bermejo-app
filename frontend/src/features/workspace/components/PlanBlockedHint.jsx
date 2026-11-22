import { Info } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { describeBlockingPrerequisite } from '@/features/workspace/lib/curriculumPlan';

export function PlanBlockedHint({ subjectName, blockedBy }) {
  if (blockedBy.length === 0) return null;
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={`Por qué ${subjectName} está bloqueada`}
          className="-my-2 h-11 w-11 rounded-full text-ink-faint hover:text-ink"
        >
          <Info className="h-4 w-4" aria-hidden="true" />
        </Button>
      </TooltipTrigger>
      <TooltipContent>
        <ul className="flex flex-col gap-0.5">
          {blockedBy.map((prerequisite) => (
            <li key={prerequisite.subjectId}>{describeBlockingPrerequisite(prerequisite)}</li>
          ))}
        </ul>
      </TooltipContent>
    </Tooltip>
  );
}
