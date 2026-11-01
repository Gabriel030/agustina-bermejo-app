import { MoreHorizontal } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { TERM_ACTIONS } from '@/features/academic-structure/lib/terms';

export function TermRowMenu({ term, onEdit, onAction }) {
  const isActive = term.status === 'ACTIVE';
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button type="button" variant="ghost" size="icon" aria-label={`Acciones de ${term.description} ${term.year}`}>
          <MoreHorizontal aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60 bg-dialog-bg backdrop-blur-[28px] backdrop-saturate-[1.6]">
        <DropdownMenuItem onSelect={() => onEdit(term)}>Editar período</DropdownMenuItem>
        <DropdownMenuItem onSelect={() => onAction(term, 'toggle')}>{TERM_ACTIONS.toggle.title(term)}</DropdownMenuItem>
        {isActive && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem onSelect={() => onAction(term, 'open')}>Abrir inscripción ahora</DropdownMenuItem>
            <DropdownMenuItem onSelect={() => onAction(term, 'close')}>Cerrar inscripción ahora</DropdownMenuItem>
            <DropdownMenuItem onSelect={() => onAction(term, 'clear')}>Quitar apertura manual</DropdownMenuItem>
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
