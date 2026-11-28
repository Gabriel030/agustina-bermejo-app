import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useActiveRole } from '@/hooks/useActiveRole';
import { useHelpTasks } from '@/features/help/hooks/useHelpTasks';
import {
  browsableTasks,
  groupBySection,
  searchTasks,
  tasksForRole,
} from '@/features/help/lib/helpTasks';
import { canReadHelp } from '@/lib/helpAccess';

export function useHelpBrowser() {
  const { roleBase } = useActiveRole();
  const tasksQuery = useHelpTasks();
  const [searchParams, setSearchParams] = useSearchParams();
  const [query, setQuery] = useState('');
  const [showAllRoles, setShowAllRoles] = useState(false);

  const sectionId = searchParams.get('seccion');
  const taskId = searchParams.get('tarea');
  const allowed = canReadHelp(roleBase);

  const visibleTasks = useMemo(
    () => browsableTasks(tasksForRole(tasksQuery.data ?? [], roleBase, showAllRoles)),
    [tasksQuery.data, roleBase, showAllRoles],
  );
  const sections = useMemo(() => groupBySection(visibleTasks), [visibleTasks]);
  const results = useMemo(() => searchTasks(visibleTasks, query), [visibleTasks, query]);
  const selectedTask = useMemo(
    () => (taskId ? (tasksQuery.data ?? []).find((task) => task.id === taskId) ?? null : null),
    [tasksQuery.data, taskId],
  );

  const updateParams = (changes) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(changes).forEach(([key, value]) => (value ? next.set(key, value) : next.delete(key)));
    setSearchParams(next);
  };

  return {
    allowed,
    isPending: tasksQuery.isPending,
    error: tasksQuery.error,
    retry: tasksQuery.refetch,
    query,
    setQuery,
    showAllRoles,
    setShowAllRoles,
    searching: query.trim() !== '',
    results,
    sections,
    hasTasks: visibleTasks.length > 0,
    activeSection: sections.find((section) => section.id === sectionId && !section.underRedesign) ?? null,
    selectedTask,
    openSection: (id) => updateParams({ seccion: id, tarea: null }),
    closeSection: () => updateParams({ seccion: null, tarea: null }),
    openTask: (id) => updateParams({ tarea: id }),
    closeTask: () => updateParams({ tarea: null }),
  };
}
