import { useState } from 'react';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { Input } from '@/components/ui/input';

export function TypedConfirmDialog({ open, onOpenChange, title, description, word, confirmLabel, pending, onConfirm, children }) {
  const [typed, setTyped] = useState('');
  const matches = typed.trim().toUpperCase() === word;

  function handleOpenChange(next) {
    if (!next) setTyped('');
    onOpenChange(next);
  }

  return (
    <ConfirmActionDialog
      open={open}
      onOpenChange={handleOpenChange}
      title={title}
      description={description}
      confirmLabel={confirmLabel}
      pending={pending}
      confirmDisabled={!matches}
      onConfirm={() => onConfirm(typed)}
    >
      {children}
      <div className="flex flex-col gap-1.5">
        <label htmlFor="import-confirm-word" className="text-sm font-semibold text-ink">
          Para confirmar, escribí {word}
        </label>
        <Input id="import-confirm-word" autoComplete="off" value={typed} onChange={(event) => setTyped(event.target.value)} />
      </div>
    </ConfirmActionDialog>
  );
}
