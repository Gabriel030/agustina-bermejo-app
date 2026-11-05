import { LoadErrorState } from '@/components/LoadErrorState';
import { ModuleDisabled } from '@/pages/ModuleDisabled';
import { useMyModules } from '@/hooks/useMyModules';
import { isModuleEnabled, moduleOfPath } from '@/routes/moduleGating';

export function ModuleGate({ role, path, children }) {
  const moduleCode = moduleOfPath(role, path);
  const modules = useMyModules({ enabled: Boolean(moduleCode) });

  if (!moduleCode) return children;
  if (modules.isLoading) {
    return (
      <div className="flex flex-1 items-center justify-center py-16" role="status" aria-label="Cargando">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-brand border-t-transparent" />
      </div>
    );
  }
  if (modules.isError) return <LoadErrorState resource="los módulos de tu instituto" onRetry={modules.refetch} error={modules.error} />;
  if (!isModuleEnabled(modules, moduleCode)) return <ModuleDisabled role={role} />;
  return children;
}
