import { FileCheck2, Plus, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

function formatFileSize(bytes) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`;
}

export function ReceiptDropzone({ file, error, errorId, onSelect, onRemove }) {
  function handleChange(event) {
    const selected = event.target.files?.[0] ?? null;
    event.target.value = '';
    if (selected) onSelect(selected);
  }

  return (
    <div className="flex flex-col gap-2">
      {file ? (
        <div className="flex items-center gap-3 rounded-xl border border-border bg-neutral-bg/40 px-4 py-3">
          <FileCheck2 className="size-5 shrink-0 text-success" aria-hidden="true" />
          <div className="flex min-w-0 flex-1 flex-col">
            <span className="truncate text-sm font-semibold text-ink">{file.name}</span>
            <span className="text-xs text-ink-faint">{formatFileSize(file.size)}</span>
          </div>
          <Button type="button" variant="ghost" size="sm" onClick={onRemove} aria-label={`Quitar ${file.name}`}>
            <X className="size-4" aria-hidden="true" />
            Quitar
          </Button>
        </div>
      ) : (
        <label
          className={cn(
            'flex cursor-pointer flex-col items-center gap-1.5 rounded-xl border-2 border-dashed px-4 py-8 text-center transition-colors focus-within:ring-2 focus-within:ring-ring/50 hover:border-brand/60',
            error ? 'border-danger' : 'border-border',
          )}
        >
          <Plus className="size-6 text-ink-faint" aria-hidden="true" />
          <span className="text-sm font-semibold text-ink">Subí tu comprobante</span>
          <span className="text-xs text-ink-faint">PDF, JPG o PNG · máximo 5 MB</span>
          <input
            type="file"
            accept=".pdf,.jpg,.jpeg,.png"
            className="sr-only"
            aria-label="Comprobante de transferencia"
            aria-invalid={Boolean(error)}
            aria-describedby={error ? errorId : undefined}
            onChange={handleChange}
          />
        </label>
      )}
      {error && (
        <p id={errorId} role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
