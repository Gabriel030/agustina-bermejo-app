import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useCourses(status) {
  const params = status ? `?status=${status}` : '';
  return useQuery({
    queryKey: ['admin-courses', status ?? 'all'],
    queryFn: () => apiFetch(`/courses${params}`),
  });
}

export function useCourse(id) {
  return useQuery({
    queryKey: ['admin-course', id],
    queryFn: () => apiFetch(`/courses/${id}`),
    enabled: !!id,
  });
}

export function useCreateCourse() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => apiFetch('/courses', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-courses'] }),
  });
}

export function useUpdateCourse(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => apiFetch(`/courses/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-courses'] });
      queryClient.invalidateQueries({ queryKey: ['admin-course', id] });
    },
  });
}

export function useDeactivateCourse() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/courses/${id}/deactivate`, { method: 'POST' }),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ['admin-courses'] });
      queryClient.invalidateQueries({ queryKey: ['admin-course', String(id)] });
    },
  });
}

export function useActivateCourse() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/courses/${id}/activate`, { method: 'POST' }),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ['admin-courses'] });
      queryClient.invalidateQueries({ queryKey: ['admin-course', String(id)] });
    },
  });
}

export function useCourseSubjects(courseId) {
  return useQuery({
    queryKey: ['course-subjects', courseId],
    queryFn: () => apiFetch(`/courses/${courseId}/subjects`),
    enabled: !!courseId,
  });
}

export function useAddCourseSubject(courseId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) =>
      apiFetch(`/courses/${courseId}/subjects`, { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['course-subjects', courseId] }),
  });
}

export function useUpdateCourseSubject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...body }) =>
      apiFetch(`/course-subjects/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    onSuccess: (data) =>
      queryClient.invalidateQueries({ queryKey: ['course-subjects', String(data.courseId)] }),
  });
}

export function useDeactivateCourseSubject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id }) => apiFetch(`/course-subjects/${id}/deactivate`, { method: 'POST' }),
    onSuccess: (_, { courseId }) =>
      queryClient.invalidateQueries({ queryKey: ['course-subjects', String(courseId)] }),
  });
}

export function useActivateCourseSubject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id }) => apiFetch(`/course-subjects/${id}/activate`, { method: 'POST' }),
    onSuccess: (_, { courseId }) =>
      queryClient.invalidateQueries({ queryKey: ['course-subjects', String(courseId)] }),
  });
}
