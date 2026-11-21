import { Notice } from '@/components/Notice';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { TemplateSignatoryField } from '@/features/graduation/certificates/components/TemplateSignatoryField';

export function TemplateSignatories({ form, readOnly, signatories, errors, onChange }) {
  if (signatories.isPending) return <Skeleton className="h-40" />;
  if (signatories.isError) {
    return (
      <div role="alert" className="flex flex-col items-start gap-2">
        <p className="text-size-14 text-ink-soft">No pudimos cargar los firmantes.</p>
        <Button type="button" variant="outline" size="sm" onClick={() => signatories.refetch()}>
          Reintentar
        </Button>
      </div>
    );
  }
  const list = signatories.data;
  if (list.length === 0) {
    return (
      <Notice tone="neutral" title="Ningún firmante tiene permiso para este documento.">
        {readOnly
          ? 'Pedile al Director que habilite firmantes para los certificados de egreso.'
          : 'Habilitá firmantes para los certificados de egreso en la pantalla de Firmantes.'}
      </Notice>
    );
  }
  return (
    <div className="grid gap-3 md:grid-cols-2">
      <TemplateSignatoryField
        position={1}
        label="Firmante 1"
        hint="Izquierda"
        value={form.signatory1Id}
        otherValue={form.signatory2Id}
        signatories={list}
        readOnly={readOnly}
        error={errors.signatory1Id}
        onChange={(id) => onChange({ ...form, signatory1Id: id })}
      />
      <TemplateSignatoryField
        position={2}
        label="Firmante 2"
        hint="Derecha"
        value={form.signatory2Id}
        otherValue={form.signatory1Id}
        signatories={list}
        readOnly={readOnly}
        error={errors.signatory2Id}
        onChange={(id) => onChange({ ...form, signatory2Id: id })}
      />
    </div>
  );
}
