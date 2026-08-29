import { useParams } from 'react-router-dom';
import { InscripcionWizard } from '@/components/catalogo/InscripcionWizard';
import { usePrograma, useProgramaPricing, useProgramaCohorts } from '@/hooks/useCatalogo';

// Página completa (no modal, feedback explícito del usuario) del wizard de
// inscripción a una carrera — mismo patrón de página fina que DetalleCarrera.
export function InscripcionCarrera() {
  const { id } = useParams();
  const { data: programa, isLoading, isError } = usePrograma(id);
  const { data: pricing } = useProgramaPricing(id);
  const { data: cohorts } = useProgramaCohorts(id);

  if (isError) {
    return <div className="p-6 text-sm text-danger">No pudimos cargar la carrera. Probá recargar la página.</div>;
  }
  if (isLoading || !programa) {
    return <div className="p-6 text-sm text-ink-faint">Cargando…</div>;
  }

  return (
    <InscripcionWizard
      tipo="carrera"
      target={{ id: programa.id, name: programa.name }}
      cohorts={cohorts}
      pricing={pricing}
      volverA={`/catalogo/carreras/${id}`}
    />
  );
}
