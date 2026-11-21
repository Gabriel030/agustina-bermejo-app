import { useRef, useState } from 'react';
import { ImageUp, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { describeError } from '@/api/describeError';
import { validateLogoFile } from '@/features/graduation/certificates/lib/certificateTemplate';
import { useTemplateImageUrl, useUploadTemplateLogo } from '@/features/graduation/certificates/hooks/useCertificateTemplate';

export function TemplateLogoField({ kind, logoImageId, readOnly, onChange }) {
  const inputRef = useRef(null);
  const [error, setError] = useState(null);
  const upload = useUploadTemplateLogo(kind);
  const imageUrl = useTemplateImageUrl(kind, logoImageId);
  const hasLogo = logoImageId != null;

  async function handleFile(event) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    const invalid = validateLogoFile(file);
    if (invalid) {
      setError(invalid);
      return;
    }
    setError(null);
    try {
      const image = await upload.mutateAsync(file);
      onChange(image.id);
    } catch (failure) {
      setError(describeError(failure).message);
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <span className="text-size-13 font-semibold text-ink-soft">Logo del instituto</span>
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex h-16 w-28 shrink-0 items-center justify-center overflow-hidden rounded-xl border border-dashed border-border bg-neutral-bg text-ink-faint">
          {imageUrl ? (
            <img src={imageUrl} alt="Logo actual del certificado" className="h-14 w-auto object-contain" />
          ) : (
            <ImageUp className="size-5" aria-hidden="true" />
          )}
        </div>
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          <span className="text-size-12-5 text-ink-faint">
            {hasLogo ? 'Logo cargado.' : 'Sin logo. PNG de hasta 2 MB.'}
          </span>
          {!readOnly && (
            <div className="flex flex-wrap gap-2">
              <input
                ref={inputRef}
                type="file"
                accept="image/png"
                aria-label="Archivo del logo"
                className="sr-only"
                tabIndex={-1}
                onChange={handleFile}
              />
              <Button type="button" variant="outline" size="sm" loading={upload.isPending} onClick={() => inputRef.current?.click()}>
                {hasLogo ? 'Reemplazar logo' : 'Subir logo'}
              </Button>
              {hasLogo && (
                <Button type="button" variant="ghost" size="sm" onClick={() => onChange(null)}>
                  <Trash2 className="size-4" aria-hidden="true" />
                  Quitar logo
                </Button>
              )}
            </div>
          )}
        </div>
      </div>
      {error && (
        <p role="alert" className="text-size-12-5 font-medium text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
