import { useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Notice } from '@/components/Notice';
import { PreceptorMultiSelect } from '@/components/PreceptorMultiSelect';
import { useReplaceSectionPreceptors } from '@/features/academic-structure/hooks/useReplaceSectionPreceptors';
import { describeImpact, sameIds } from '@/features/academic-structure/lib/sectionPreceptors';

export function SectionPreceptorsEditor({ section, cycleId, sectionKind, onClose }) {
  const current = (section.preceptors ?? []).map((p) => p.personId);
  const pendingRequestCount = Math.max(0, ...(section.preceptors ?? []).map((p) => p.pendingRequestCount));
  const [selected, setSelected] = useState(current);
  const [failed, setFailed] = useState(false);
  const replace = useReplaceSectionPreceptors(cycleId, sectionKind);
  const container = useRef(null);

  useEffect(() => {
    container.current?.querySelector('button')?.focus();
  }, []);
  const changed = !sameIds(current, selected);
  const impact = describeImpact(current, selected, pendingRequestCount);

  async function handleSave() {
    setFailed(false);
    try {
      await replace.mutateAsync({ sectionId: section.id, personIds: selected });
      onClose();
    } catch {
      setFailed(true);
    }
  }

  function handleKeyDown(event) {
    if (event.key === 'Escape' && !event.defaultPrevented && !replace.isPending) onClose();
  }

  return (
    <div ref={container} className="mt-3 flex flex-col gap-3" onKeyDown={handleKeyDown}>
      <PreceptorMultiSelect
        label="Preceptores"
        value={selected}
        onChange={setSelected}
        disabled={replace.isPending}
      />
      {failed && (
        <Notice tone="danger" role="alert" title="No pudimos guardar.">
          Tus cambios siguen acá. Reintentá.
        </Notice>
      )}
      <div aria-live="polite" aria-atomic="true" className="flex flex-col gap-2">
        {impact.length > 0 && (
          <Notice tone="warning">
            {impact.map((line) => (
              <span key={line.bold} className="block">
                <b className="font-bold">{line.bold}</b> {line.text}
              </span>
            ))}
          </Notice>
        )}
        {replace.isPending && <span className="sr-only">Guardando los preceptores…</span>}
      </div>
      <div className="flex justify-end gap-2">
        <Button variant="ghost" size="sm" onClick={onClose} disabled={replace.isPending}>
          Cancelar
        </Button>
        <Button
          variant="primary"
          size="sm"
          loading={replace.isPending}
          disabled={!changed && !failed}
          onClick={handleSave}
        >
          {failed ? 'Reintentar' : 'Guardar'}
        </Button>
      </div>
    </div>
  );
}
