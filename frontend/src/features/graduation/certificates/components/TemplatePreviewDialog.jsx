import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';

export function TemplatePreviewDialog({ preview, onClose }) {
  return (
    <Dialog open={Boolean(preview)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>Vista previa de la plantilla</DialogTitle>
          <DialogDescription>
            {preview?.source === 'DRAFT' ? 'Se ve el borrador guardado' : 'Se ve la versión publicada'} con datos de ejemplo y marca de agua.
            No se emite ni queda registrada.
          </DialogDescription>
        </DialogHeader>
        {preview && <iframe src={preview.url} title="Vista previa del certificado" className="h-[70vh] w-full rounded-md border-0" />}
      </DialogContent>
    </Dialog>
  );
}
