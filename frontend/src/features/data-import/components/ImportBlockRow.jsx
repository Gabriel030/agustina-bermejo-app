import { Download, FileCheck2, Upload } from 'lucide-react';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { toast } from 'sonner';
import { downloadImportTemplate } from '../hooks/useDataImports';
import { formatSize, saveBlobAs } from '../importLabels';

function BlockInfo({ block }) {
  return (
    <div className="flex min-w-0 flex-1 flex-col gap-0.5">
      <span className="font-semibold text-ink">{block.label}</span>
      <span className="text-sm text-ink-soft">{block.description}</span>
    </div>
  );
}

export function ImportBlockRow({ block, file, editable, uploading, onSelectFile }) {
  const inputId = `import-file-${block.entityType}`;

  function handleChange(event) {
    const selected = event.target.files?.[0] ?? null;
    event.target.value = '';
    if (selected) onSelectFile(block, selected);
  }

  async function handleTemplate() {
    try {
      const { blob } = await downloadImportTemplate(block.entityType);
      saveBlobAs(blob, `plantilla-${block.label.toLowerCase()}.csv`);
    } catch (error) {
      toast.error(error.message || 'No pudimos descargar la plantilla. Probá de nuevo.');
    }
  }

  return (
    <li className="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:gap-4" data-testid={`block-${block.entityType}`}>
      <BlockInfo block={block} />
      <div className="flex min-w-0 flex-wrap items-center gap-3">
        {file ? (
          <span className="flex min-w-0 items-center gap-2 text-sm text-ink">
            <FileCheck2 className="size-4 shrink-0 text-success" aria-hidden="true" />
            <span className="truncate">{file.originalName}</span>
            <span className="text-xs text-ink-faint tabular-nums">
              {file.rowCount.toLocaleString('es-AR')} filas · {formatSize(file.sizeBytes)}
            </span>
          </span>
        ) : (
          <StatusPill tone="warning">Falta cargar</StatusPill>
        )}
        <Button className="min-h-11 md:min-h-8" type="button" variant="ghost" size="sm" onClick={handleTemplate}>
          <Download className="size-4" aria-hidden="true" />
          Plantilla
        </Button>
        {editable && (
          <Button type="button" variant={file ? 'outline' : 'default'} size="sm" asChild>
            <label htmlFor={inputId} className="cursor-pointer">
              <Upload className="size-4" aria-hidden="true" />
              {uploading ? 'Subiendo…' : file ? 'Reemplazar' : 'Subir archivo'}
              <input
                id={inputId}
                type="file"
                accept=".csv,text/csv"
                className="sr-only"
                aria-label={`Archivo de ${block.label}`}
                disabled={uploading}
                onChange={handleChange}
              />
            </label>
          </Button>
        )}
      </div>
    </li>
  );
}
