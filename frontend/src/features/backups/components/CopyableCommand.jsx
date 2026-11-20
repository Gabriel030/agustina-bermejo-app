import { useEffect, useRef, useState } from 'react';
import { Copy } from 'lucide-react';
import { Button } from '@/components/ui/button';

const MESSAGES = { copied: 'Comando copiado', failed: 'Seleccioná y copiá con Ctrl+C' };

export function CopyableCommand({ stepNumber, stepTitle, actor, command }) {
  const [message, setMessage] = useState('');
  const [announcement, setAnnouncement] = useState(0);
  const timerRef = useRef(null);

  useEffect(() => () => clearTimeout(timerRef.current), []);

  async function copyCommand() {
    try {
      await navigator.clipboard.writeText(command);
      clearTimeout(timerRef.current);
      setMessage(MESSAGES.copied);
      setAnnouncement((count) => count + 1);
      timerRef.current = setTimeout(() => setMessage(''), 1600);
    } catch {
      setMessage(MESSAGES.failed);
      setAnnouncement((count) => count + 1);
    }
  }

  return (
    <div className="mt-2 flex min-w-0 flex-col gap-1.5">
      <div className="flex items-center justify-between gap-2 text-size-12 font-bold text-ink-soft">
        <span>{actor}</span>
        <Button type="button" variant="outline" size="xs" aria-label={`Copiar comandos del paso ${stepNumber}: ${stepTitle}`} onClick={copyCommand}>
          <Copy aria-hidden="true" />
          {message === MESSAGES.copied ? 'Copiado' : 'Copiar'}
        </Button>
      </div>
      <pre
        tabIndex={0}
        role="region"
        aria-label={`Comandos del paso ${stepNumber}: ${stepTitle}`}
        className="overflow-x-auto rounded-control bg-neutral-bg px-3 py-2.5 font-mono text-size-12-5 leading-relaxed text-ink"
      >
        {command}
      </pre>
      <span role="status" aria-live="polite" className="sr-only">
        <span key={announcement}>{message}</span>
      </span>
    </div>
  );
}
