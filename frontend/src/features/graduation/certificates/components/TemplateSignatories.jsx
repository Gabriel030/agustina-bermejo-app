import { Link } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { useActiveRole } from '@/hooks/useActiveRole';
import { TemplateSignatoryField } from '@/features/graduation/certificates/components/TemplateSignatoryField';
import { selectedSignatoriesOf } from '@/features/graduation/certificates/lib/certificateTemplate';

export function TemplateSignatories({ documentName, form, readOnly, signatories, errors, showRequired, onChange }) {
  const { roleBase } = useActiveRole();
  if (signatories.isPending) return (
      <div role="status" aria-label="Cargando firmantes">
        <Skeleton className="h-24" />
      </div>
    );
  if (signatories.isError) {
    return (
      <div role="alert" className="flex flex-col items-start gap-2">
        <p className="text-size-14 text-ink-soft">No pudimos cargar los firmantes.</p>
        <Button type="button" variant="outline" size="sm" className="min-h-11 md:min-h-7" onClick={() => signatories.refetch()}>
          Reintentar
        </Button>
      </div>
    );
  }
  const list = signatories.data;
  if (list.length === 0) {
    return (
      <GlassCard className="p-4">
        <EmptyState
          icon="👥"
          title="Nadie puede firmar este documento"
          description={
            readOnly
              ? `Pedile al Director que habilite a un firmante para «${documentName}».`
              : `Habilitá el permiso para «${documentName}» a un firmante, en Firmantes.`
          }
          action={
            readOnly ? null : (
              <Button asChild variant="outline" className="min-h-11 md:min-h-8">
                <Link to={`/${roleBase}/firmantes`}>Ir a Firmantes</Link>
              </Button>
            )
          }
        />
      </GlassCard>
    );
  }
  const missingFirst = showRequired ? errors.signatory1Id : undefined;
  const unsigned = selectedSignatoriesOf(form, list).filter(({ signatory }) => signatory && !signatory.hasSignature);
  return (
    <div className="flex flex-col gap-3">
      <div className="grid gap-3 md:grid-cols-2">
        <TemplateSignatoryField
          id="template-signatory-1"
          label="Primer firmante"
          emptyLabel="Elegir firmante"
          value={form.signatory1Id}
          otherValue={form.signatory2Id}
          signatories={list}
          readOnly={readOnly}
          error={missingFirst}
          onChange={(id) => onChange({ ...form, signatory1Id: id })}
        />
        <TemplateSignatoryField
          id="template-signatory-2"
          label="Segundo firmante (opcional)"
          emptyLabel="Sin segundo firmante"
          value={form.signatory2Id}
          otherValue={form.signatory1Id}
          signatories={list}
          readOnly={readOnly}
          error={errors.signatory2Id}
          onChange={(id) => onChange({ ...form, signatory2Id: id })}
        />
      </div>
      {unsigned.length > 0 ? (
        <p role="alert" className="text-size-12 font-medium text-danger">
          {unsigned.map(({ signatory }) => signatory.name).join(' y ')} {unsigned.length > 1 ? 'no tienen' : 'no tiene'} firma cargada. Cargala en Firmantes para poder publicar.
        </p>
      ) : (
        <p className="text-size-12 text-ink-faint">Solo aparecen firmantes con permiso para este documento.</p>
      )}
    </div>
  );
}
