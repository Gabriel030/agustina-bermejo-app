import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { apiFetchBlob } from '@/api/client';

const CERTIFICATES = [
  { kind: 'regular', path: 'regular', label: 'Constancia de alumno regular' },
  { kind: 'approved', path: 'approved-subjects', label: 'Constancia de materias aprobadas' },
];

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export function CertificateActions({ carreraId }) {
  const [pending, setPending] = useState(null);

  async function download(certificate) {
    setPending(certificate.kind);
    try {
      const { blob, filename } = await apiFetchBlob(`/me/program-enrollments/${carreraId}/certificates/${certificate.path}`);
      saveBlob(blob, filename);
    } catch (error) {
      toast.error(error.message ?? 'No pudimos generar la constancia. Probá de nuevo.');
    } finally {
      setPending(null);
    }
  }

  return (
    <div className="flex flex-col gap-2 sm:flex-row">
      {CERTIFICATES.map((certificate) => (
        <Button
          key={certificate.kind}
          variant="outline"
          className="w-full sm:w-auto"
          loading={pending === certificate.kind}
          disabled={pending !== null}
          onClick={() => download(certificate)}
        >
          {certificate.label}
        </Button>
      ))}
    </div>
  );
}
