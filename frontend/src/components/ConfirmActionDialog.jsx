import { Button } from '@/components/ui/button';
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';

export default function ConfirmActionDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  pending = false,
  confirmVariant = 'destructive',
  confirmDisabled = false,
  cancelLabel = 'Cancelar',
  buttonClassName,
  children,
  onConfirm,
}) {
  function handleOpenChange(next) {
    if (pending && !next) return;
    onOpenChange(next);
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription>{description}</AlertDialogDescription>
        </AlertDialogHeader>
        {children}
        <AlertDialogFooter>
          <AlertDialogCancel asChild>
            <Button variant="outline" className={buttonClassName} disabled={pending}>
              {cancelLabel}
            </Button>
          </AlertDialogCancel>
          <Button variant={confirmVariant} className={buttonClassName} loading={pending} disabled={confirmDisabled} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
