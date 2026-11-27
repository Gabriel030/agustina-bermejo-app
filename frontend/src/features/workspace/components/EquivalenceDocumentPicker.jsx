import { Button } from '@/components/ui/button';
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select';
import { DOCUMENT_LABELS, useDocumentosAlumno } from '@/features/identity';
import { formatDate } from '@/lib/billing';

function describeDocument(document) {
  return `${DOCUMENT_LABELS[document.type] ?? document.type} · subido el ${formatDate(document.uploadedAt)}`;
}

function PickerNote({ id, children, action }) {
  return (
    <div id={id} className="flex flex-wrap items-center gap-x-2 gap-y-1 text-size-12 text-ink-soft">
      <span>{children}</span>
      {action}
    </div>
  );
}

export function EquivalenceDocumentPicker({ studentId, id, value, onChange, disabled, invalid, describedBy }) {
  const { data, isLoading, isError, error, refetch } = useDocumentosAlumno(studentId);
  const forbidden = isError && error?.status === 403;
  const documents = data ?? [];
  const noteId = `${id}-note`;
  const selectable = !isLoading && !isError && documents.length > 0;
  const describedIds = [describedBy, !selectable ? noteId : null].filter(Boolean).join(' ') || undefined;

  return (
    <div className="flex flex-col gap-1.5">
      <NativeSelect
        id={id}
        value={selectable ? value : ''}
        onChange={(event) => onChange(event.target.value)}
        disabled={disabled || !selectable}
        aria-busy={isLoading || undefined}
        aria-invalid={invalid || undefined}
        aria-describedby={describedIds}
        className="[&_select]:h-11"
      >
        <NativeSelectOption value="">{isLoading ? 'Cargando documentos…' : 'Sin documento'}</NativeSelectOption>
        {documents.map((document) => (
          <NativeSelectOption key={document.id} value={String(document.id)}>
            {describeDocument(document)}
          </NativeSelectOption>
        ))}
      </NativeSelect>
      {forbidden && <PickerNote id={noteId}>No tenés permiso para ver los documentos del legajo. Podés cargar la equivalencia sin documento.</PickerNote>}
      {isError && !forbidden && (
        <PickerNote
          id={noteId}
          action={
            <Button type="button" variant="outline" className="h-11" disabled={disabled} onClick={() => refetch()}>
              Reintentar
            </Button>
          }
        >
          No se pudieron cargar los documentos. Podés cargar la equivalencia sin documento.
        </PickerNote>
      )}
      {!isLoading && !isError && documents.length === 0 && <PickerNote id={noteId}>El legajo todavía no tiene documentos subidos.</PickerNote>}
    </div>
  );
}
