import { useParams } from 'react-router-dom';
import { Breadcrumb } from '@/components/Breadcrumb';
import { PageContainer } from '@/components/PageContainer';
import { useCicloSecciones, useCycle, describeSection } from '@/features/academic-structure';
import { DirectorGradeGrid } from '../components/DirectorGradeGrid';

export function DirectorSectionGrades() {
  const { cycleId, sectionId } = useParams();
  const { data: cycle } = useCycle(cycleId);
  const { data: sections } = useCicloSecciones(cycleId);
  const kind = cycle?.courseId ? 'COURSE' : 'PROGRAM';
  const offeringName = cycle?.courseId ? cycle.courseName : cycle?.programName;
  const offeringPath = cycle?.courseId ? `/director/oferta-academica/cursos/${cycle.courseId}` : `/director/oferta-academica/carreras/${cycle?.programId}`;
  const section = sections?.find((candidate) => String(candidate.id) === String(sectionId));
  const breadcrumb = [
    { label: 'Oferta académica', to: '/director/oferta-academica' },
    ...(cycle
      ? [
          { label: offeringName, to: offeringPath },
          { label: cycle.termName, to: `/director/oferta-academica/ciclos/${cycleId}/comisiones/${sectionId}/inscriptos` },
          { label: 'Notas' },
        ]
      : []),
  ];

  return (
    <PageContainer className="gap-3 md:gap-6">
      <div className="flex flex-col gap-1.5">
        <Breadcrumb items={breadcrumb} />
        <h1 className="font-heading text-xl font-bold text-ink md:text-2xl">Notas de la comisión</h1>
        {section && <p className="text-size-12-5 text-ink-faint">{describeSection(section)}</p>}
      </div>
      {cycle && <DirectorGradeGrid sectionId={sectionId} kind={kind} />}
    </PageContainer>
  );
}
