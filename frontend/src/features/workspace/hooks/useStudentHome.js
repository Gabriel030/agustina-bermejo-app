import { useMisInscripciones, useMisMatriculaciones, useMiProgreso } from '@/features/enrollment';
import { describeRegularity, useMyRegularity } from '@/features/identity';
import { activeSubjectsCount, currentProgram, listedSubjects } from '@/features/workspace/lib/studentHome';

export function useStudentHome() {
  const enrollments = useMisMatriculaciones();
  const sectionEnrollments = useMisInscripciones();
  const program = currentProgram(enrollments.data);
  const graduated = program?.status === 'COMPLETED';
  const progress = useMiProgreso(program?.id);
  const regularity = useMyRegularity('PROGRAM', graduated ? null : program?.id);

  return {
    enrollments,
    sectionEnrollments,
    program,
    progress,
    regularityView: regularity.data ? describeRegularity(regularity.data) : null,
    activeCount: activeSubjectsCount(sectionEnrollments.data),
    subjects: listedSubjects(sectionEnrollments.data),
  };
}
