import { useState } from 'react';
import { toast } from 'sonner';
import { GlassCard } from '@/components/GlassCard';
import { StickyActionBar } from '@/components/StickyActionBar';
import { Button } from '@/components/ui/button';
import { IMPORT_BLOCKS } from '../importBlocks';
import { useUploadImportFile, useValidateDataImport } from '../hooks/useDataImports';
import { MAX_IMPORT_FILE_BYTES } from '../importLabels';
import { ImportBlockRow, UpcomingBlockRow } from './ImportBlockRow';

export function ImportFilesStep({ batch, onValidated }) {
  const upload = useUploadImportFile(batch.id);
  const validate = useValidateDataImport(batch.id);
  const [uploadingType, setUploadingType] = useState(null);
  const filesByType = new Map(batch.files.map((file) => [file.entityType, file]));
  const loadedBlocks = filesByType.size;

  async function handleSelectFile(block, file) {
    if (!file.name.toLowerCase().endsWith('.csv')) {
      toast.error('El archivo tiene que ser un CSV. Descargá la plantilla y guardala en ese formato.');
      return;
    }
    if (file.size > MAX_IMPORT_FILE_BYTES) {
      toast.error('El archivo es demasiado grande: el máximo es 5 MB.');
      return;
    }
    setUploadingType(block.entityType);
    try {
      await upload.mutateAsync({ entityType: block.entityType, file });
      toast.success(`Archivo de ${block.label} cargado`);
    } catch (error) {
      toast.error(error.message || 'No pudimos cargar el archivo. Probá de nuevo.');
    } finally {
      setUploadingType(null);
    }
  }

  async function handleValidate() {
    try {
      await validate.mutateAsync();
      onValidated();
    } catch (error) {
      toast.error(error.message || 'No pudimos revisar los archivos. Probá de nuevo.');
    }
  }

  return (
    <>
      <GlassCard className="p-0">
        <ul className="divide-y divide-border" aria-label="Bloques de la importación">
          {IMPORT_BLOCKS.map((block) =>
            block.upcoming ? (
              <UpcomingBlockRow key={block.key} block={block} />
            ) : (
              <ImportBlockRow
                key={block.entityType}
                block={block}
                file={filesByType.get(block.entityType)}
                editable
                uploading={uploadingType === block.entityType}
                onSelectFile={handleSelectFile}
              />
            ),
          )}
        </ul>
      </GlassCard>
      <StickyActionBar
        summary={loadedBlocks === 0 ? 'Todavía no cargaste archivos' : `${loadedBlocks} ${loadedBlocks === 1 ? 'bloque cargado' : 'bloques cargados'}`}
        hint="Revisar no escribe nada: solo te muestra qué se crearía."
      >
        <Button type="button" disabled={loadedBlocks === 0} loading={validate.isPending} onClick={handleValidate}>
          Revisar archivos
        </Button>
      </StickyActionBar>
    </>
  );
}
