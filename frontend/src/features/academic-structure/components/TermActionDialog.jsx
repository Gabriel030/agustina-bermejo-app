import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { useTermAction } from '@/features/academic-structure/hooks/useTerms';
import { TERM_ACTIONS, termErrorMessage } from '@/features/academic-structure/lib/terms';

export function TermActionDialog({ term, action, onClose }) {
  const termAction = useTermAction();
  const [errorMessage, setErrorMessage] = useState(null);
  const definition = TERM_ACTIONS[action];

  function handleConfirm() {
    termAction.mutate(
      { termId: term.id, path: definition.path(term) },
      {
        onSuccess: () => {
          toast.success(definition.success(term));
          onClose();
        },
        onError: (error) => setErrorMessage(termErrorMessage(error, 'No se pudo aplicar el cambio. Probá de nuevo.')),
      },
    );
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>
            {definition.title(term)}: {term.description} {term.year}
          </DialogTitle>
          <DialogDescription>{definition.description(term)}</DialogDescription>
        </DialogHeader>
        {errorMessage && (
          <p role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger">
            {errorMessage}
          </p>
        )}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            type="button"
            variant={definition.destructive?.(term) ? 'destructive' : 'default'}
            disabled={termAction.isPending}
            onClick={handleConfirm}
          >
            Confirmar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
