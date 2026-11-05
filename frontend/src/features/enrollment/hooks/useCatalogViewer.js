import { useAuth } from '@/context/AuthContext';
import { isCourseEnrollmentInProgress } from '@/features/enrollment/lib/courseEnrollmentStatus';
import {
  useMisSolicitudesCarrera,
  useMisSolicitudesCurso,
} from '@/features/enrollment/hooks/useCatalogo';
import { useMisCursos, useMisMatriculaciones } from '@/features/enrollment/hooks/useStudentEnrollment';

const ACTIVE_PROGRAM_ENROLLMENT = ['ENROLLED', 'PAUSED'];

export function useCanApplyFromCatalog() {
  const { roles, cargando } = useAuth();
  return { loading: cargando, canApply: roles.length === 0 || roles.includes('ALUMNO') };
}

export function useProgramViewerStatus(programId) {
  const { loading: authLoading, canApply } = useCanApplyFromCatalog();
  const applications = useMisSolicitudesCarrera(canApply);
  const enrollments = useMisMatriculaciones(canApply);
  const loading = authLoading || (canApply && (applications.isLoading || enrollments.isLoading));
  const enrollment = (enrollments.data ?? []).find(
    (item) => item.programId === Number(programId) && ACTIVE_PROGRAM_ENROLLMENT.includes(item.status),
  );
  const application = (applications.data ?? []).find(
    (item) => item.programId === Number(programId) && item.status === 'PENDING',
  );
  if (loading) return { loading, canApply, state: null };
  if (!canApply) return { loading, canApply, state: 'PREVIEW' };
  if (enrollment) return { loading, canApply, state: 'ENROLLED' };
  if (application) return { loading, canApply, state: 'PENDING_APPLICATION', applicationId: application.id };
  return { loading, canApply, state: null };
}

export function useCourseViewerStatus(courseId) {
  const { loading: authLoading, canApply } = useCanApplyFromCatalog();
  const applications = useMisSolicitudesCurso(canApply);
  const enrollments = useMisCursos(canApply);
  const loading = authLoading || (canApply && (applications.isLoading || enrollments.isLoading));
  const enrollment = (enrollments.data ?? []).find(
    (item) => item.courseId === Number(courseId) && isCourseEnrollmentInProgress(item),
  );
  const pendingReceipt = (applications.data ?? []).find(
    (item) => item.courseId === Number(courseId) && item.paymentStatus === 'PENDING_REVIEW' && !item.courseEnrollmentStatus,
  );
  if (loading) return { loading, canApply, state: null };
  if (!canApply) return { loading, canApply, state: 'PREVIEW' };
  if (enrollment) {
    return { loading, canApply, state: enrollment.status === 'WAITLISTED' ? 'WAITLISTED' : 'ENROLLED' };
  }
  if (pendingReceipt) return { loading, canApply, state: 'PENDING_APPLICATION' };
  return { loading, canApply, state: null };
}
