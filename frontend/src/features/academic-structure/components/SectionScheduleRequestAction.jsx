import { useState } from 'react';
import { CalendarClock } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { SectionScheduleRequestSheet } from '@/features/academic-structure/components/SectionScheduleRequestSheet';
import {
  canRequestScheduleChange,
  hasPendingModification,
} from '@/features/academic-structure/lib/sectionScheduleRequest';

export function SectionScheduleRequestAction({ section, cycle }) {
  const [open, setOpen] = useState(false);
  if (!canRequestScheduleChange(cycle)) return null;
  const pending = hasPendingModification(section);

  const button = (
    <Button type="button" variant="outline" size="sm" className="min-h-11" disabled={pending} onClick={() => setOpen(true)}>
      <CalendarClock className="mr-1.5 h-3.5 w-3.5" />
      Modificar horario
    </Button>
  );

  return (
    <>
      {pending ? (
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              <span tabIndex={0} className="inline-flex">{button}</span>
            </TooltipTrigger>
            <TooltipContent>Ya hay un pedido pendiente</TooltipContent>
          </Tooltip>
        </TooltipProvider>
      ) : (
        button
      )}
      {open && (
        <SectionScheduleRequestSheet section={section} cycleId={cycle.id} open={open} onClose={() => setOpen(false)} />
      )}
    </>
  );
}
