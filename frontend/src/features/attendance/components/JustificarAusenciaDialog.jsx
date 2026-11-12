import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { ReceiptDropzone } from '@/components/ReceiptDropzone';
import { describeError } from '@/api/describeError';
import { useAdjuntarComprobanteDeJustificacion } from '@/features/attendance/hooks/useDocenteAsistencia';

const MAX_PROOF_BYTES = 5 * 1024 * 1024;
const ACCEPTED_PROOF_TYPES = ['application/pdf', 'image/jpeg'];

function proofProblem(file) {
  if (!ACCEPTED_PROOF_TYPES.includes(file.type)) return 'El comprobante tiene que ser un PDF o un JPG.';
  if (file.size > MAX_PROOF_BYTES) return 'El comprobante supera los 5 MB.';
  return null;
}

function formatDeadline(isoDate) {
  const [year, month, day] = isoDate.split('-');
  return `${day}/${month}/${year}`;
}

export function JustificarAusenciaDialog({ type, sectionId, absence, onClose }) {
  const [file, setFile] = useState(null);
  const [fileError, setFileError] = useState(null);
  const [submitError, setSubmitError] = useState(null);
  const attach = useAdjuntarComprobanteDeJustificacion(type, sectionId);

  function selectFile(selected) {
    setSubmitError(null);
    setFile(selected);
    setFileError(proofProblem(selected));
  }

  function removeFile() {
    setFile(null);
    setFileError(null);
    setSubmitError(null);
  }

  async function submit() {
    setSubmitError(null);
    try {
      await attach.mutateAsync({ attendanceId: absence.attendanceId, proof: file });
      toast.success('Comprobante enviado. La falta queda como justificación pendiente.');
      onClose();
    } catch (error) {
      setSubmitError(describeError(error, { fallback: 'No pudimos enviar el comprobante. Probá de nuevo.' }).message);
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Adjuntar comprobante</DialogTitle>
          <DialogDescription>
            {absence.studentName} · clase del {formatDeadline(absence.classDate)}
            {absence.justificationDeadline && ` · podés justificar hasta el ${formatDeadline(absence.justificationDeadline)}`}
          </DialogDescription>
        </DialogHeader>
        <ReceiptDropzone
          file={file}
          error={fileError}
          errorId="justification-proof-error"
          onSelect={selectFile}
          onRemove={removeFile}
          title="Arrastrá el archivo o tocá para elegirlo"
          hint="PDF o JPG · máximo 5 MB"
          accept=".pdf,.jpg,.jpeg"
          inputLabel="Comprobante de justificación"
        />
        <p className="text-size-13 text-ink-faint">Queda pendiente de revisión del preceptor. Mientras tanto la falta sigue contando.</p>
        {submitError && (
          <div role="alert" className="rounded-xl bg-danger-bg px-4 py-2 text-size-13 font-semibold text-danger">
            {submitError}
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancelar</Button>
          <Button variant="gradient" onClick={submit} disabled={!file || !!fileError || attach.isPending}>
            {attach.isPending ? 'Enviando…' : 'Enviar comprobante'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
