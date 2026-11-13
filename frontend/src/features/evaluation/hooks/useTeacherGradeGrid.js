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

export function applySavedCell(grid, enrollmentId, item, comments) {
  if (!grid?.students) return grid;
  return {
    ...grid,
    students: grid.students.map((student) => {
      if (student.enrollmentId !== enrollmentId) return student;
      const sameCell = (grade) => grade.type === item.type && grade.sequenceNumber === item.sequenceNumber;
      const existing = student.grades.find(sameCell);
      const cell = { ...existing, gradeItemId: item.id, type: item.type, sequenceNumber: item.sequenceNumber, score: item.score, date: item.date, comments: comments ?? null, published: existing?.published ?? false, corrected: existing?.corrected ?? false };
      return { ...student, grades: existing ? student.grades.map((grade) => (sameCell(grade) ? cell : grade)) : [...student.grades, cell] };
    }),
  };
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
          (item) => {
            queryClient.setQueryData(gridKey(sectionId, kind), (grid) => applySavedCell(grid, job.enrollmentId, item, job.payload.comments));
            onCellResult(job.key, 'saved');
          },
          (error) => {
            onCellResult(job.key, 'error');
            throw error;
          },
        ),
      ),
    );
    const failed = results.filter((result) => result.status === 'rejected').length;
    if (failed) await queryClient.invalidateQueries({ queryKey: gridKey(sectionId, kind) });
    else queryClient.invalidateQueries({ queryKey: gridKey(sectionId, kind), refetchType: 'none' });
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
