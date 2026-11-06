import { useCycle, useCicloSecciones, useRoleBasePath, describeSection } from '@/features/academic-structure';

export function useStaffSectionContext(cycleId, sectionId) {
  const basePath = useRoleBasePath();
  const { data: cycle } = useCycle(cycleId);
  const { data: sections } = useCicloSecciones(cycleId);
  const section = sections?.find((candidate) => String(candidate.id) === String(sectionId));
  const sectionType = cycle ? (cycle.courseId ? 'COURSE' : 'PROGRAM') : undefined;
  const offeringName = cycle?.courseId ? cycle.courseName : cycle?.programName;
  const offeringPath = cycle?.courseId
    ? `${basePath}/oferta-academica/cursos/${cycle.courseId}`
    : `${basePath}/oferta-academica/carreras/${cycle?.programId}`;
  const rosterPath = `${basePath}/oferta-academica/ciclos/${cycleId}/comisiones/${sectionId}/inscriptos`;
  const announcementsPath = `${basePath}/oferta-academica/ciclos/${cycleId}/comisiones/${sectionId}/avisos`;
  const breadcrumb = [
    { label: 'Oferta académica', to: `${basePath}/oferta-academica` },
    ...(cycle ? [{ label: offeringName, to: offeringPath }] : []),
    { label: 'Inscriptos y asistencia', to: rosterPath },
  ];
  return {
    basePath,
    cycle,
    sectionType,
    sectionLabel: describeSection(section),
    rosterPath,
    announcementsPath,
    breadcrumb,
  };
}
