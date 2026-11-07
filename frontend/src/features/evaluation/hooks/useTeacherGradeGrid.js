import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const SECTION_PATH = { PROGRAM: 'program-sections', COURSE: 'course-sections' };
const ENROLLMENT_PATH = { PROGRAM: 'section-enrollments', COURSE: 'course-section-enrollments' };

function gridKey(sectionId, kind, staff = false) {
  return [staff ? 'staff-grade-grid' : 'teacher-grade-grid', kind, String(sectionId)];
}

export function useTeacherGradeGrid(sectionId, kind, { staff = false } = {}) {
  return useQuery({
    queryKey: gridKey(sectionId, kind, staff),
    queryFn: () => apiFetch(`/${staff ? 'staff/' : ''}${SECTION_PATH[kind]}/${sectionId}/grade-grid`),
  });
}

export function useSaveGradeCells(sectionId, kind) {
  const queryClient = useQueryClient();

  async function saveGradeCells(jobs, onCellResult) {
    const results = await Promise.allSettled(
      jobs.map((job) =>
        apiFetch(`/${ENROLLMENT_PATH[kind]}/${job.enrollmentId}/grade-items`, {
          method: 'POST',
          body: JSON.stringify(job.payload),
        }).then(
          () => onCellResult(job.key, 'saved'),
          (error) => {
            onCellResult(job.key, 'error');
            throw error;
          },
        ),
      ),
    );
    await queryClient.invalidateQueries({ queryKey: gridKey(sectionId, kind) });
    const failed = results.filter((result) => result.status === 'rejected').length;
    return { saved: results.length - failed, failed, failedKeys: jobs.filter((_, index) => results[index].status === 'rejected').map((job) => job.key) };
  }

  return saveGradeCells;
}

export function usePublishGradeItem(sectionId, kind) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ gradeType, sequenceNumber }) =>
      apiFetch(`/${SECTION_PATH[kind]}/${sectionId}/grade-items/publish`, {
        method: 'POST',
        body: JSON.stringify({ gradeType, sequenceNumber }),
      }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: gridKey(sectionId, kind) }),
  });
}
