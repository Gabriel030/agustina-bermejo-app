import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { formatMoney } from '@/lib/utils';
import { ChevronLeft, Plus, Globe, GlobeOff, Pencil, Power, PowerOff, GripVertical } from 'lucide-react';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import {
  useCourse,
  useCourseSubjects,
  useDeactivateCourse,
  useActivateCourse,
  useSetCourseCatalogVisibility,
} from '@/features/academic-structure/hooks/useCourses';
import { useTerminos } from '@/features/academic-structure/hooks/useTerminos';
import { PanelModulo } from './PanelModulo';

const STATUS_CONFIG = {
  ACTIVE: { label: 'Activo', tone: 'success' },
  INACTIVE: { label: 'Inactivo', tone: 'neutral' },
};

const MODULE_STATUS_CONFIG = {
  ACTIVE: { label: 'Activo', tone: 'success' },
  INACTIVE: { label: 'Inactivo', tone: 'neutral' },
};

const TEACHING_MODE_LABELS = {
  IN_PERSON: 'Presencial',
  ONLINE: 'Virtual',
  HYBRID: 'Híbrido',
};

function ModuleRow({ mod }) {
  const cfg = MODULE_STATUS_CONFIG[mod.status] ?? { label: mod.status, tone: 'neutral' };
  return (
    <div className="flex w-full items-center gap-4 rounded-2xl px-4 py-3 transition-colors hover:bg-neutral-bg/60">
      <GripVertical className="h-4 w-4 shrink-0 text-ink-faint/40" />
      {mod.sortOrder != null && (
        <span className="w-5 shrink-0 text-center text-[12px] font-bold text-ink-faint">
          {mod.sortOrder}
        </span>
      )}
      <div className="min-w-0 flex-1">
        <span className="block truncate text-[14px] font-medium text-ink">{mod.name}</span>
        {mod.weeklyWorkload != null && (
          <span className="text-[12px] text-ink-faint">{mod.weeklyWorkload} hs/semana</span>
        )}
      </div>
      {mod.status === 'INACTIVE' && <StatusPill tone={cfg.tone}>{cfg.label}</StatusPill>}
    </div>
  );
}

export function CursoDetalle() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: course, isLoading: loadingCourse } = useCourse(id);
  const { data: modules, isLoading: loadingModules } = useCourseSubjects(id);
  const { data: terminos } = useTerminos();
  const deactivateCourse = useDeactivateCourse();
  const activateCourse = useActivateCourse();
  const setCatalogVisibility = useSetCourseCatalogVisibility();

  const [panelOpen, setPanelOpen] = useState(false);

  const term = (terminos ?? []).find((t) => String(t.id) === String(course?.termId));

  async function handleToggleCatalog() {
    const visible = !course?.visibleInCatalog;
    try {
      await setCatalogVisibility.mutateAsync({ id, visible });
      toast.success(
        visible
          ? 'El curso ahora aparece en el catálogo de inscripciones'
          : 'El curso fue retirado del catálogo. Los alumnos actuales no se ven afectados.'
      );
    } catch {
      toast.error('No se pudo cambiar la visibilidad en el catálogo.');
    }
  }

  async function handleToggleStatus() {
    const isActive = course?.status === 'ACTIVE';
    try {
      if (isActive) {
        await deactivateCourse.mutateAsync(id);
        toast.success('Curso desactivado');
      } else {
        await activateCourse.mutateAsync(id);
        toast.success('Curso activado');
      }
    } catch {
      toast.error('No se pudo cambiar el estado del curso.');
    }
  }

  if (loadingCourse) {
    return (
      <PageContainer>
        <Skeleton className="h-8 w-64 rounded-xl" />
      </PageContainer>
    );
  }

  if (!course) {
    return (
      <PageContainer>
        <EmptyState title="Curso no encontrado" />
      </PageContainer>
    );
  }

  const cfg = STATUS_CONFIG[course.status] ?? { label: course.status, tone: 'neutral' };
  const isActive = course.status === 'ACTIVE';

  const metaItems = [
    term ? (term.description ? `${term.year} — ${term.description}` : String(term.year)) : null,
    course.teachingMode ? TEACHING_MODE_LABELS[course.teachingMode] : null,
    course.durationMonths ? `${course.durationMonths} meses` : null,
    course.capacity ? `${course.capacity} cupos` : null,
    course.price != null ? formatMoney(course.price) : null,
  ].filter(Boolean);

  const sortedModules = [...(modules ?? [])].sort(
    (a, b) => (a.sortOrder ?? 999) - (b.sortOrder ?? 999)
  );

  return (
    <PageContainer>
      <div className="flex flex-col gap-4">
        <div>
          <Link
            to="/administrativo/gestion-academica?section=cursos"
            className="mb-1 flex items-center gap-1 text-[12px] text-ink-faint hover:text-ink"
          >
            <ChevronLeft className="h-3.5 w-3.5" />
            Cursos
          </Link>
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <h1 className="font-heading text-xl font-bold text-ink">{course.name}</h1>
                <StatusPill tone={cfg.tone}>{cfg.label}</StatusPill>
              </div>
              {metaItems.length > 0 && (
                <p className="mt-0.5 text-[13px] text-ink-faint">{metaItems.join(' · ')}</p>
              )}
            </div>
            <div className="flex shrink-0 items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => navigate(`/administrativo/gestion-academica/cursos/${id}/editar`)}
              >
                <Pencil className="mr-1.5 h-3.5 w-3.5" />
                Editar
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={handleToggleStatus}
                loading={deactivateCourse.isPending || activateCourse.isPending}
                className={isActive ? 'text-danger hover:border-danger/50 hover:text-danger' : ''}
              >
                {isActive ? (
                  <>
                    <PowerOff className="mr-1.5 h-3.5 w-3.5" />
                    Desactivar
                  </>
                ) : (
                  <>
                    <Power className="mr-1.5 h-3.5 w-3.5" />
                    Activar
                  </>
                )}
              </Button>
            </div>
          </div>
        </div>

        <GlassCard className="p-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <span className="text-[12px] font-medium uppercase tracking-widest text-ink-faint">Catálogo</span>
              <StatusPill tone={course.visibleInCatalog ? 'success' : 'neutral'}>
                {course.visibleInCatalog ? 'En catálogo' : 'No publicado'}
              </StatusPill>
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={handleToggleCatalog}
              loading={setCatalogVisibility.isPending}
              className={course.visibleInCatalog ? 'text-danger hover:border-danger/50 hover:text-danger' : ''}
            >
              {course.visibleInCatalog ? (
                <>
                  <GlobeOff className="mr-1.5 h-3.5 w-3.5" />
                  Retirar del catálogo
                </>
              ) : (
                <>
                  <Globe className="mr-1.5 h-3.5 w-3.5" />
                  Publicar en catálogo
                </>
              )}
            </Button>
          </div>
        </GlassCard>

        {course.schedule && (
          <GlassCard className="p-4">
            <h2 className="mb-1 text-[11px] font-bold uppercase tracking-widest text-ink-faint">Horario</h2>
            <p className="text-[13.5px] text-ink">{course.schedule}</p>
          </GlassCard>
        )}

        <div className="flex items-center justify-between">
          <h2 className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">Temario</h2>
          <Button variant="gradient" size="sm" onClick={() => setPanelOpen(true)}>
            <Plus className="mr-1.5 h-3.5 w-3.5" />
            Agregar módulo
          </Button>
        </div>

        {loadingModules ? (
          <Skeleton rows={3} />
        ) : sortedModules.length === 0 ? (
          <GlassCard className="p-6">
            <EmptyState
              title="Sin módulos"
              description="Agregá módulos para construir el temario del curso."
            />
          </GlassCard>
        ) : (
          <GlassCard className="p-2">
            <div className="divide-y divide-neutral-bg">
              {sortedModules.map((mod) => (
                <ModuleRow key={mod.id} mod={mod} />
              ))}
            </div>
          </GlassCard>
        )}
      </div>

      <PanelModulo
        open={panelOpen}
        onClose={() => setPanelOpen(false)}
        courseId={id}
      />
    </PageContainer>
  );
}
