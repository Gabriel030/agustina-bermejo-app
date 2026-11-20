import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { previewLabel } from '@/features/graduation/certificates/lib/staffGraduationCertificates';

export function EligiblePreviewDialog({ preview, onClose }) {
  return (
    <Dialog open={Boolean(preview)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>Vista previa</DialogTitle>
          <DialogDescription>Es solo una vista previa: no se emite ni queda registrada.</DialogDescription>
        </DialogHeader>
        {preview && <iframe src={preview.url} title={previewLabel(preview.row)} className="h-[70vh] w-full rounded-md border-0" />}
      </DialogContent>
    </Dialog>
  );
}
