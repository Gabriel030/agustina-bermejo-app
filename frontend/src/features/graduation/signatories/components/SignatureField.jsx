import { useRef, useState } from 'react';
import { Button } from '@/components/ui/button';
import { useBlobUrl } from '@/features/graduation/signatories/hooks/useBlobUrl';
import { signatureProblemOf } from '@/features/graduation/signatories/lib/certificateSignatories';

export function SignatureField({ blob, loading, editable, pending, onSelect, onRemove, signatoryName }) {
  const inputRef = useRef(null);
  const [problem, setProblem] = useState(null);
  const url = useBlobUrl(blob);

  function handleChange(event) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    const found = signatureProblemOf(file);
    setProblem(found);
    if (!found) onSelect(file);
  }

  return (
    <div className="flex flex-col gap-2">
      <span className="text-size-13 font-semibold text-ink-soft">Firma escaneada</span>
      <div className="flex flex-wrap items-center gap-4">
        <div className="flex h-24 w-48 items-center justify-center rounded-xl border border-dashed border-border bg-neutral-bg/60 p-2">
          {url ? (
            <img src={url} alt={signatoryName ? `Firma de ${signatoryName}` : 'Firma cargada'} className="max-h-full max-w-full object-contain [[data-theme=dark]_&]:invert" />
          ) : (
            <span className="text-size-12 text-ink-faint">{loading ? 'Cargando firma…' : 'Sin firma'}</span>
          )}
        </div>
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          <span className="text-size-12 text-ink-faint">PNG con fondo transparente, hasta 2 MB.</span>
          {editable && (
            <div className="flex flex-wrap gap-2">
              <input
                ref={inputRef}
                type="file"
                accept="image/png"
                className="sr-only"
                tabIndex={-1}
                aria-label="Archivo de firma"
                onChange={handleChange}
              />
              <Button type="button" variant="outline" size="sm" loading={pending} onClick={() => inputRef.current?.click()}>
                {blob ? 'Reemplazar firma' : 'Subir firma'}
              </Button>
              {blob && (
                <Button type="button" variant="ghost-destructive" size="sm" disabled={pending} onClick={onRemove}>
                  Quitar firma
                </Button>
              )}
            </div>
          )}
          {problem && (
            <p role="alert" className="text-size-12 text-danger">
              {problem}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
