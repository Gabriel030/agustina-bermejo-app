import { useParams } from 'react-router-dom';
import { InscripcionWizard } from '@/features/enrollment/components/InscripcionWizard';
import { useCurso, useCursoPricing } from '@/features/enrollment/hooks/useCatalogo';

export function InscripcionCurso() {
  const { id } = useParams();
  const { data: curso, isLoading, isError } = useCurso(id);
  const { data: pricing } = useCursoPricing(id);

  if (isError) {
    return <div className="p-6 text-sm text-danger">No pudimos cargar el curso. Probá recargar la página.</div>;
  }
  if (isLoading || !curso) {
    return <div className="p-6 text-sm text-ink-faint">Cargando…</div>;
  }

  return (
    <InscripcionWizard
      tipo="curso"
      target={{ id: curso.id, name: curso.name }}
      pricing={pricing}
      volverA={`/catalogo/cursos/${id}`}
    />
  );
}
