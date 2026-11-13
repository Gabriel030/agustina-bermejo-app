import { useState } from 'react';
import { Download } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { apiFetchBlob } from '@/api/client';

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
  const [pending, setPending] = useState(false);

  async function download() {
    setPending(true);
    try {
      const { blob, filename } = await apiFetchBlob(`/me/program-enrollments/${carreraId}/certificates/approved-subjects`);
      saveBlob(blob, filename);
    } catch (error) {
      toast.error(error.message ?? 'No pudimos generar la constancia. Probá de nuevo.');
    } finally {
      setPending(false);
    }
  }

  return (
    <Button variant="outline" className="w-full sm:w-auto" loading={pending} onClick={download}>
      <Download aria-hidden="true" />
      Descargar
    </Button>
  );
}
