import { Copy } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { formatCertificateCode } from '../lib/certificateCode';

export function CodeWithCopy({ code }) {
  async function copyCode() {
    try {
      await navigator.clipboard.writeText(code);
      toast.success('Código copiado');
    } catch {
      toast.error('No pudimos copiar el código.');
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="text-size-13 text-ink-faint">Código</span>
      <code className="rounded-sm bg-neutral-bg px-2.5 py-1.5 text-size-13-5 tracking-[0.04em] break-all text-ink tabular-nums">
        {formatCertificateCode(code)}
      </code>
      <Button variant="text" size="inline" type="button" aria-label="Copiar código" onClick={copyCode}>
        <Copy aria-hidden="true" />
        Copiar
      </Button>
    </div>
  );
}
