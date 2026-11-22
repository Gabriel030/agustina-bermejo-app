import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Lock, SearchX } from 'lucide-react';
import { toast } from 'sonner';
import { describeMutationFailure } from '@/api/mutationErrorPolicy';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { TabCount } from '@/components/TabCount';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { TooltipProvider } from '@/components/ui/tooltip';
import { PermissionDiscrepanciesTab } from '@/features/permissions/components/PermissionDiscrepanciesTab';
import { PermissionModeDialog } from '@/features/permissions/components/PermissionModeDialog';
import { PendingChangesBar } from '@/features/permissions/components/PendingChangesBar';
import { GRID_COLUMNS, PermissionModuleSection } from '@/features/permissions/components/PermissionModuleSection';
import { PermissionModeLegend } from '@/features/permissions/components/PermissionModeLegend';
import { PermissionToolbar } from '@/features/permissions/components/PermissionToolbar';
import { ReviewChangesDialog } from '@/features/permissions/components/ReviewChangesDialog';
import { usePermissionCatalog, usePermissionGridData } from '@/features/permissions/hooks/usePermissionGrid';
import { usePermissionDiscrepancySummary } from '@/features/permissions/hooks/usePermissionDiscrepancies';
import { usePermissionDraft } from '@/features/permissions/hooks/usePermissionDraft';
import { useSavePermissionChanges } from '@/features/permissions/hooks/useSavePermissionChanges';
import { DIRECTOR_ROLE, GRID_FORBIDDEN_CODE, describeChangeCount, discrepancyCountFor, modeFor, permissionMatches, rolesPresentIn } from '@/features/permissions/lib/permissionGrid';

const PAGE_TITLE = 'Roles y permisos';
const PAGE_DESCRIPTION = 'Definí qué puede hacer cada rol en cada módulo. Cada cambio pide un motivo y queda en el registro de auditoría.';
const PAGE_DESCRIPTION_READ_ONLY = 'Mirá qué puede hacer cada rol en cada módulo.';

function PermissionsSkeleton() {
  return (
    <GlassCard className="p-0" role="status" aria-busy="true" aria-label="Cargando permisos">
      {Array.from({ length: 6 }, (_, index) => (
        <div key={index} className="flex items-center gap-3.5 border-b border-border p-4 last:border-b-0">
          <Skeleton className="h-4 w-4" />
          <div className="flex flex-1 flex-col gap-2">
            <Skeleton className={index % 2 ? "h-3.5 w-2/5" : "h-3.5 w-1/3"} />
            <Skeleton className="h-2.5 w-1/5" />
          </div>
          <Skeleton className="h-4 w-15" />
          <Skeleton className="h-4 w-15" />
        </div>
      ))}
    </GlassCard>
  );
}

function PermissionsGrid({ catalog, grid }) {
  const [query, setQuery] = useState('');
  const [moduleFilter, setModuleFilter] = useState('');
  const [roleFilter, setRoleFilter] = useState([]);
  const [openModules, setOpenModules] = useState(() => new Set());
  const [reviewing, setReviewing] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [tab, setTab] = useState('grid');
  const [modeTarget, setModeTarget] = useState(null);
  const [highlighted, setHighlighted] = useState(null);
  const draft = usePermissionDraft(grid);
  const save = useSavePermissionChanges();

  const catalogIndex = useMemo(() => new Map(catalog.modules.flatMap((module) => module.permissions.map((permission) => [permission.code, { ...permission, module: module.module }]))), [catalog]);
  const total = catalogIndex.size;
  const searching = query.trim() !== '';
  const gridRoles = useMemo(() => rolesPresentIn(grid), [grid]);
  const filterableRoles = gridRoles.filter((role) => role.id !== DIRECTOR_ROLE);
  const roles = roleFilter.length ? gridRoles.filter((role) => roleFilter.includes(role.id)) : gridRoles;
  const editable = grid.editable !== false;
  const summaryQuery = usePermissionDiscrepancySummary({ enabled: editable });
  const summary = summaryQuery.data;
  const totalOccurrences = summary?.modules.reduce((sum, entry) => sum + entry.total, 0) ?? 0;

  useEffect(() => {
    if (!highlighted) return undefined;
    document.getElementById(`permission-section-${highlighted}`)?.scrollIntoView?.({ block: 'start' });
    const timer = setTimeout(() => setHighlighted(null), 3000);
    return () => clearTimeout(timer);
  }, [highlighted]);

  const showModuleInGrid = (module) => {
    setQuery('');
    setModuleFilter('');
    setOpenModules((current) => new Set(current).add(module));
    setTab('grid');
    setHighlighted(module);
  };
  const hasUnsavedChangesFor = (target) => draft.changes.some((change) => change.role === target.role && catalogIndex.get(change.permissionCode)?.module === target.module);

  const visibleModules = catalog.modules
    .filter((module) => !moduleFilter || module.module === moduleFilter)
    .map((module) => ({ module, permissions: module.permissions.filter((permission) => permissionMatches(module, permission, query)) }))
    .filter((entry) => entry.permissions.length > 0);
  const shown = visibleModules.reduce((sum, entry) => sum + entry.permissions.length, 0);
  const allOpen = openModules.size >= catalog.modules.length;
  const moduleCount = new Set(draft.changes.map((change) => catalogIndex.get(change.permissionCode)?.module)).size;

  const toggleOpen = (module) =>
    setOpenModules((current) => {
      const next = new Set(current);
      if (next.has(module)) next.delete(module);
      else next.add(module);
      return next;
    });
  const toggleRole = (roleId) => setRoleFilter((current) => (current.includes(roleId) ? current.filter((id) => id !== roleId) : [...current, roleId]));
  const clearFilters = () => {
    setQuery('');
    setModuleFilter('');
    setRoleFilter([]);
  };

  const closeReview = () => {
    setReviewing(false);
    setSaveError(null);
  };

  const confirmSave = async (reason, sensitiveConfirmed) => {
    const count = draft.changes.length;
    let savedCount = 0;
    setSaveError(null);
    try {
      await save.mutateAsync({
        reason,
        changes: draft.changes,
        sensitiveConfirmed,
        isSensitive: (change) => Boolean(catalogIndex.get(change.permissionCode)?.sensitive),
        onChunkSaved: (chunk) => {
          savedCount += chunk.length;
          draft.settle(chunk);
        },
      });
      closeReview();
      toast.success(`Guardamos ${describeChangeCount(count)}. Quedaron en la auditoría.`);
    } catch (error) {
      setSaveError(
        error?.code === GRID_FORBIDDEN_CODE
          ? { title: 'No se guardó nada.', message: 'No tenés permiso para cambiar la grilla. Solo el Director puede editarla; si creés que es un error, pedíselo a él.' }
          : {
              title: 'No pudimos guardar todo.',
              message:
                savedCount > 0
                  ? `Se guardaron ${describeChangeCount(savedCount)}; quedan ${count - savedCount}. ${describeMutationFailure(error)}`
                  : describeMutationFailure(error),
            },
      );
    }
  };

  return (
    <>
      <TooltipProvider>
      <Tabs value={tab} onValueChange={setTab} className="gap-4">
        {editable && (
          <TabsList variant="segmented">
            <TabsTrigger value="grid">Permisos</TabsTrigger>
            <TabsTrigger value="discrepancies">
              Discrepancias
              <TabCount count={totalOccurrences} />
            </TabsTrigger>
          </TabsList>
        )}
        <TabsContent value="grid" forceMount className="flex flex-col gap-4 data-[state=inactive]:hidden">
          {!editable && (
            <div role="status" className="flex items-start gap-2.5 rounded-card bg-warning-bg px-3.5 py-3 text-size-13 text-warning-ink">
              <Lock className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              <p>
                <b>Estás viendo la grilla en modo lectura.</b> Solo el Director puede modificar los permisos. El Subdirector y el resto de los roles pueden verlos, no cambiarlos. Si necesitás un cambio, pedíselo al Director.
              </p>
            </div>
          )}
          <PermissionModeLegend />
          <PermissionToolbar
            modules={catalog.modules}
            filterableRoles={filterableRoles}
            query={query}
            onQueryChange={setQuery}
            moduleFilter={moduleFilter}
            onModuleFilterChange={setModuleFilter}
            roleFilter={roleFilter}
            onToggleRole={toggleRole}
            onClearRoles={() => setRoleFilter([])}
            allOpen={allOpen}
            onToggleAll={() => setOpenModules(allOpen ? new Set() : new Set(catalog.modules.map((module) => module.module)))}
            shown={shown}
            total={total}
          />
          <GlassCard className="overflow-clip p-0" style={{ '--role-count': roles.length }} role="region" aria-label="Permisos por módulo y rol">
            <div className={`hidden border-b border-border bg-table-head text-size-12 font-bold text-ink-soft ${GRID_COLUMNS}`}>
              <div className="px-4 py-2.5">Módulo y permiso</div>
              {roles.map((role) => (
                <div key={role.id} className="px-2 py-2.5 text-center">
                  {role.label}
                </div>
              ))}
            </div>
            {visibleModules.length ? (
              visibleModules.map(({ module, permissions }) => (
                <PermissionModuleSection
                  key={module.module}
                  module={module}
                  grid={grid}
                  roles={roles}
                  permissions={permissions}
                  open={searching || openModules.has(module.module)}
                  editable={editable}
                  highlighted={highlighted === module.module}
                  discrepancyCount={discrepancyCountFor(summary, module.module)}
                  onToggleOpen={() => toggleOpen(module.module)}
                  onChangeMode={(role, target) => setModeTarget({ role, module: target })}
                  draft={draft}
                />
              ))
            ) : (
              <EmptyState
                icon={<SearchX className="mx-auto size-6 text-ink-faint" aria-hidden="true" />}
                title="No hay permisos que coincidan"
                description="Probá con otra palabra o quitá los filtros de módulo y rol."
                action={
                  <Button type="button" variant="outline" onClick={clearFilters}>
                    Limpiar filtros
                  </Button>
                }
                className="py-12"
              />
            )}
          </GlassCard>
        </TabsContent>
        {editable && (
          <TabsContent value="discrepancies">
            {summaryQuery.isLoading ? (
              <PermissionsSkeleton />
            ) : summaryQuery.error ? (
              <LoadErrorState error={summaryQuery.error} resource="las discrepancias" onRetry={() => summaryQuery.refetch()} />
            ) : (
              <PermissionDiscrepanciesTab summary={summary} catalogIndex={catalogIndex} onShowInGrid={showModuleInGrid} />
            )}
          </TabsContent>
        )}
      </Tabs>
      </TooltipProvider>
      {editable && draft.changes.length > 0 && (
        <PendingChangesBar changeCount={draft.changes.length} moduleCount={moduleCount} onDiscard={draft.discard} onReview={() => setReviewing(true)} />
      )}
      {editable && modeTarget && (
        <PermissionModeDialog
          role={modeTarget.role}
          module={modeTarget.module}
          currentMode={modeFor(grid, modeTarget.role, modeTarget.module)}
          discrepancyCount={discrepancyCountFor(summary, modeTarget.module)}
          discrepancyStatus={summaryQuery.error ? 'error' : summary ? 'ready' : 'loading'}
          onRetryDiscrepancies={() => summaryQuery.refetch()}
          hasUnsavedChanges={hasUnsavedChangesFor(modeTarget)}
          onClose={() => setModeTarget(null)}
        />
      )}
      {editable && reviewing && draft.changes.length > 0 && (
        <ReviewChangesDialog
          open
          onClose={closeReview}
          changes={draft.changes}
          catalogIndex={catalogIndex}
          grid={grid}
          saving={save.isPending}
          error={saveError}
          onConfirm={confirmSave}
        />
      )}
    </>
  );
}

export function PermissionsPage() {
  const catalogQuery = usePermissionCatalog();
  const gridQuery = usePermissionGridData();
  const error = catalogQuery.error ?? gridQuery.error;
  const loading = catalogQuery.isLoading || gridQuery.isLoading;

  let body;
  if (error?.status === 403) {
    body = (
      <GlassCard className="flex flex-1 flex-col p-12" role="alert">
        <EmptyState
          icon={<Lock className="mx-auto size-6 text-ink-soft" aria-hidden="true" />}
          title="No tenés acceso a esta pantalla"
          description="Esta pantalla es para el Director, y el Subdirector puede verla sin editarla. Si creés que es un error, pedíselo a quien administra tu institución."
          action={
            <Button asChild variant="outline">
              <Link to="/">Volver al inicio</Link>
            </Button>
          }
        />
      </GlassCard>
    );
  } else if (loading) {
    body = <PermissionsSkeleton />;
  } else if (error) {
    body = (
      <LoadErrorState
        error={error}
        resource="los permisos"
        onRetry={() => {
          catalogQuery.refetch();
          gridQuery.refetch();
        }}
      />
    );
  } else if (!catalogQuery.data.modules.length) {
    body = (
      <GlassCard className="flex flex-1 flex-col p-12">
        <EmptyState icon="🗂️" title="Todavía no hay permisos cargados" description="El catálogo de permisos está vacío. Cuando el sistema lo cargue, vas a poder asignarlos desde acá." />
      </GlassCard>
    );
  } else {
    body = <PermissionsGrid catalog={catalogQuery.data} grid={gridQuery.data} />;
  }

  return (
    <PageContainer title={PAGE_TITLE} description={gridQuery.data?.editable === false ? PAGE_DESCRIPTION_READ_ONLY : PAGE_DESCRIPTION} className="pb-28">
      {body}
    </PageContainer>
  );
}
