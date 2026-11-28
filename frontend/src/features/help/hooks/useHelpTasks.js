import { useQuery } from '@tanstack/react-query';
import { INCLUDE_DRAFTS, loadHelpTasks } from '@/features/help/lib/helpContentSource';
import { isVisibleStatus } from '@/features/help/lib/helpTasks';

export function useHelpTasks() {
  return useQuery({
    queryKey: ['help', 'tasks'],
    queryFn: loadHelpTasks,
    staleTime: Infinity,
    select: (tasks) => tasks.filter((task) => isVisibleStatus(task, INCLUDE_DRAFTS)),
  });
}
