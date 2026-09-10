import { useRef } from 'react';
import { Upload, CheckCircle2, FileText } from 'lucide-react';
import { useSubirDocumentoProspecto } from '../hooks/useProspectos';

const TIPOS = [
  { key: 'DNI_FRENTE', label: 'DNI frente' },
  { key: 'DNI_DORSO', label: 'DNI dorso' },
  { key: 'ANALITICO_SECUNDARIO', label: 'Analítico secundario' },
];

export function DocumentosStep({ studentId, documentosSubidos, onDocumentoSubido }) {
  const subir = useSubirDocumentoProspecto();

  function handleFileChange(type, e) {
    const file = e.target.files?.[0];
    if (!file) return;
    subir.mutate(
      { studentId, type, file },
      {
        onSuccess: (doc) => onDocumentoSubido(type, doc),
      }
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-base font-semibold text-ink">Documentos</h2>
      <p className="text-[13px] text-muted-foreground">
        Subí los documentos del alumno. Todos son opcionales — podés avanzar sin ellos.
      </p>
      <div className="flex flex-col gap-3">
        {TIPOS.map(({ key, label }) => {
          const subido = documentosSubidos?.[key];
          return (
            <DocumentoRow
              key={key}
              label={label}
              subido={subido}
              isPending={subir.isPending}
              onChange={(e) => handleFileChange(key, e)}
            />
          );
        })}
      </div>
      {subir.isError && (
        <p className="text-[12px] text-danger">No se pudo subir el archivo. Intentá de nuevo.</p>
      )}
    </div>
  );
}

function DocumentoRow({ label, subido, isPending, onChange }) {
  const inputRef = useRef(null);

  return (
    <div className="flex items-center justify-between rounded-lg border border-border px-4 py-3">
      <div className="flex items-center gap-3">
        {subido ? (
          <CheckCircle2 size={18} className="text-green-500 flex-shrink-0" />
        ) : (
          <FileText size={18} className="text-muted-foreground flex-shrink-0" />
        )}
        <div>
          <p className="text-[13px] font-medium text-ink">{label}</p>
          {subido && (
            <p className="text-[11px] text-muted-foreground truncate max-w-[180px]">
              Cargado
            </p>
          )}
        </div>
      </div>
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={isPending}
        className="flex items-center gap-1.5 text-[12px] font-medium text-blue-600 hover:text-blue-700 disabled:opacity-50"
      >
        <Upload size={13} />
        {subido ? 'Reemplazar' : 'Subir'}
      </button>
      <input
        ref={inputRef}
        type="file"
        accept=".pdf,.jpg,.jpeg,.png"
        className="hidden"
        onChange={onChange}
      />
    </div>
  );
}
