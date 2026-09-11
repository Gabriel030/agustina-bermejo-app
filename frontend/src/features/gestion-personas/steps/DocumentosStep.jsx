import { useRef } from 'react';
import { Upload, CheckCircle2, FileText } from 'lucide-react';

const TIPOS = [
  { key: 'DNI_FRENTE', label: 'DNI frente' },
  { key: 'DNI_DORSO', label: 'DNI dorso' },
  { key: 'ANALITICO_SECUNDARIO', label: 'Analítico secundario' },
];

export function DocumentosStep({ documentosSubidos, onFileSelected, isUploading, error, errorValidacion }) {
  const subidos = TIPOS.filter(({ key }) => !!documentosSubidos?.[key]).length;

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-base font-semibold text-ink">Documentos</h2>
      <p className="text-[13px] text-muted-foreground">
        Subí los documentos del alumno para continuar.{' '}
        <span className="text-ink-soft">{subidos}/{TIPOS.length} subidos</span>
      </p>
      <div className="flex flex-col gap-3">
        {TIPOS.map(({ key, label }) => (
          <DocumentoRow
            key={key}
            label={label}
            subido={!!documentosSubidos?.[key]}
            isUploading={isUploading}
            onChange={(file) => onFileSelected(key, file)}
          />
        ))}
      </div>
      {error && <p className="text-[12px] text-danger">No se pudo subir el archivo. Intentá de nuevo.</p>}
      {errorValidacion && <p className="text-[12px] text-danger">{errorValidacion}</p>}
    </div>
  );
}

function DocumentoRow({ label, subido, isUploading, onChange }) {
  const inputRef = useRef(null);

  return (
    <div className="flex items-center justify-between rounded-lg border border-border px-4 py-3">
      <div className="flex items-center gap-3">
        {subido ? (
          <CheckCircle2 size={18} className="text-green-500 flex-shrink-0" />
        ) : (
          <FileText size={18} className="text-muted-foreground flex-shrink-0" />
        )}
        <p className="text-[13px] font-medium text-ink">{label}</p>
      </div>
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={isUploading}
        className="flex items-center gap-1.5 text-[12px] font-medium text-blue-600 hover:text-blue-700 disabled:opacity-50"
      >
        <Upload size={13} />
        {subido ? 'Reemplazar' : 'Subir'}
      </button>
      <input
        ref={inputRef}
        type="file"
        accept=".pdf,.jpg,.jpeg"
        capture="environment"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) onChange(file);
        }}
      />
    </div>
  );
}
