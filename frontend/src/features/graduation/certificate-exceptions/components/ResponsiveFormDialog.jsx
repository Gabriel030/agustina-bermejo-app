import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { useMediaQuery } from '@/hooks/useMediaQuery';

export function ResponsiveFormDialog({ title, description, pending = false, onClose, children }) {
  const compact = useMediaQuery('(max-width: 767px)');
  const onOpenChange = (open) => {
    if (!open && !pending) onClose();
  };

  if (compact) {
    return (
      <Sheet open onOpenChange={onOpenChange}>
        <SheetContent side="bottom" className="max-h-[90dvh] overflow-y-auto rounded-t-2xl p-4">
          <SheetHeader className="p-0">
            <SheetTitle>{title}</SheetTitle>
            {description && <SheetDescription>{description}</SheetDescription>}
          </SheetHeader>
          {children}
        </SheetContent>
      </Sheet>
    );
  }

  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        {children}
      </DialogContent>
    </Dialog>
  );
}
