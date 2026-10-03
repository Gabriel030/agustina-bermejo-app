import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ChevronLeft, Plus, GitBranch, Globe, GlobeOff, Pencil, Power, PowerOff, Trash2, RotateCcw } from 'lucide-react';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import {
  useProgram,
  useProgramSubjects,
  useDeactivateProgram,
  useActivateProgram,
  useSetProgramCatalogVisibility,
  useDeactivateProgramSubject,
  useActivateProgramSubject,
} from '@/features/gestion-academica/hooks/usePrograms';
import { PanelAgregarMateria } from './PanelAgregarMateria';
import { PanelCorrelativas } from './PanelCorrelativas';
import { CohortesSection } from '@/features/gestion-academica/components/CohortesSection';

const CATEGORY_LABELS = {
  MANDATORY: 'Obligatoria',
  ADDITIONAL_MANDATORY: 'Obligatoria adicional',
  ELECTIVE: 'Electiva',
};

const TERM_LABELS = {
  FIRST: '1.° cuatrimestre',
  SECOND: '2.° cuatrimestre',
  ANNUAL: 'Anual',
};

const LEVEL_LABELS = {
  UNDERGRADUATE: 'Pregrado',
  GRADUATE: 'Posgrado',
  EXTENSION: 'Extensión',
  DIPLOMA: 'Diplomatura',
};

const TEACHING_MODE_LABELS = {
  IN_PERSON: 'Presencial',
  ONLINE: 'Virtual',
  HYBRID: 'Híbrido',
};

const STATUS_CONFIG = {
  ACTIVE: { label: 'Activa', tone: 'success' },
  INACTIVE: { label: 'Inactiva', tone: 'neutral' },
};

const PS_STATUS_CONFIG = {
  ACTIVE: { label: 'Activa', tone: 'success' },
  INACTIVE: { label: 'Inactiva', tone: 'neutral' },
};

function SubjectRow({ ps, onCorrelativas, onRemove, onRestore, isRestoring }) {
  const cfg = PS_STATUS_CONFIG[ps.status] ?? { label: ps.status, tone: 'neutral' };
  const meta = [
    ps.curricularTerm ? TERM_LABELS[ps.curricularTerm] : null,
    ps.credits != null ? `${ps.credits} créditos` : null,
    CATEGORY_LABELS[ps.category],
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <div className="flex w-full items-center justify-between gap-4 rounded-2xl px-4 py-3 transition-colors hover:bg-neutral-bg/60">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">{ps.subjectCode}</span>
          {ps.status === 'INACTIVE' && <StatusPill tone={cfg.tone}>{cfg.label}</StatusPill>}
        </div>
        <span className="block truncate text-[14px] font-medium text-ink">{ps.subjectName}</span>
        {meta && <span className="text-[12px] text-ink-faint">{meta}</span>}
      </div>
      <div className="flex shrink-0 items-center gap-1">
        <button
          type="button"
          onClick={() => onCorrelativas(ps)}
          className="flex items-center gap-1 rounded-lg px-2 py-1 text-[12px] text-ink-faint transition-colors hover:bg-neutral-bg hover:text-ink"
          title="Correlativas"
        >
          <GitBranch className="h-3.5 w-3.5" />
          Correlativas
        </button>
        {ps.status === 'INACTIVE' ? (
          <button
            type="button"
            onClick={() => onRestore(ps)}
            disabled={isRestoring}
            aria-label={`Volver a agregar ${ps.subjectName} al plan`}
            title="Volver a agregar al plan"
            className="rounded-lg p-1.5 text-ink-faint transition-colors hover:bg-neutral-bg hover:text-ink"
          >
            <RotateCcw className="h-3.5 w-3.5" />
          </button>
        ) : (
          <button
            type="button"
            onClick={() => onRemove(ps)}
            aria-label={`Quitar ${ps.subjectName} del plan`}
            title="Quitar del plan"
            className="rounded-lg p-1.5 text-ink-faint transition-colors hover:bg-danger/10 hover:text-danger"
          >
            <Trash2 className="h-3.5 w-3.5" />
          </button>
        )}
      </div>
    </div>
  );
}

function yearBlockTitle(year, totalYears) {
  if (year === 0) return 'Sin año asignado';
  if (year > totalYears) return `${year}.° año (fuera de la duración de la carrera)`;
  return `${year}.° año`;
}

function YearBlock({ year, totalYears, subjects, onAgregar, onCorrelativas, onRemove, onRestore, isRestoring }) {
  const outOfRange = year > totalYears;
  return (
    <GlassCard className="p-4" data-year-block={year}>
      <div className="mb-3 flex items-center justify-between">
        <h3 className={`text-[11px] font-bold uppercase tracking-widest ${outOfRange ? 'text-danger' : 'text-ink-faint'}`}>
          {yearBlockTitle(year, totalYears)}
        </h3>
        {!outOfRange && (
          <Button variant="outline" size="xs" onClick={() => onAgregar(year)}>
            <Plus className="mr-1 h-3 w-3" />
            Agregar materia
          </Button>
        )}
      </div>
      {outOfRange && (
        <p className="mb-2 text-[12px] text-ink-faint">
          Estas materias quedaron en un año mayor a la duración de la carrera. Quitalas del plan y volvé a agregarlas en el año correcto.
        </p>
      )}
      {subjects.length === 0 ? (
        <p className="py-2 text-[13px] text-ink-faint">Sin materias en este año.</p>
      ) : (
        <div className="divide-y divide-neutral-bg">
          {subjects.map((ps) => (
            <SubjectRow
              key={ps.id}
              ps={ps}
              onCorrelativas={onCorrelativas}
              onRemove={onRemove}
              onRestore={onRestore}
              isRestoring={isRestoring}
            />
          ))}
        </div>
      )}
    </GlassCard>
  );
}

export function CarreraDetalle() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: program, isLoading: loadingProgram } = useProgram(id);
  const { data: subjects, isLoading: loadingSubjects } = useProgramSubjects(id);
  const deactivateProgram = useDeactivateProgram();
  const activateProgram = useActivateProgram();
  const setCatalogVisibility = useSetProgramCatalogVisibility();
  const deactivateProgramSubject = useDeactivateProgramSubject();
  const activateProgramSubject = useActivateProgramSubject();

  const [panelOpen, setPanelOpen] = useState(false);
  const [panelYear, setPanelYear] = useState(null);
  const [panelSession, setPanelSession] = useState(0);
  const [subjectToRemove, setSubjectToRemove] = useState(null);
  const [correlativasOpen, setCorrelativasOpen] = useState(false);
  const [correlativaPs, setCorrelativaPs] = useState(null);

  function handleAgregar(year) {
    setPanelYear(year === 0 ? null : year);
    setPanelSession((n) => n + 1);
    setPanelOpen(true);
  }

  async function handleConfirmRemove() {
    try {
      await deactivateProgramSubject.mutateAsync(subjectToRemove.id);
      toast.success(`${subjectToRemove.subjectName} se quitó del plan`);
      setSubjectToRemove(null);
    } catch {
      toast.error('No se pudo quitar la materia del plan.');
    }
  }

  async function handleRestore(ps) {
    try {
      await activateProgramSubject.mutateAsync(ps.id);
      toast.success(`${ps.subjectName} volvió al plan`);
    } catch {
      toast.error('No se pudo volver a agregar la materia al plan.');
    }
  }

  function handleCorrelativas(ps) {
    setCorrelativaPs(ps);
    setCorrelativasOpen(true);
  }

  async function handleToggleCatalog() {
    const visible = !program?.visibleInCatalog;
    try {
      await setCatalogVisibility.mutateAsync({ id, visible });
      toast.success(
        visible
          ? 'La carrera ahora aparece en el catálogo de inscripciones'
          : 'La carrera fue retirada del catálogo. Los alumnos actuales no se ven afectados.'
      );
    } catch {
      toast.error('No se pudo cambiar la visibilidad en el catálogo.');
    }
  }

  async function handleToggleStatus() {
    const isActive = program?.status === 'ACTIVE';
    try {
      if (isActive) {
        await deactivateProgram.mutateAsync(id);
        toast.success('Carrera desactivada');
      } else {
        await activateProgram.mutateAsync(id);
        toast.success('Carrera activada');
      }
    } catch {
      toast.error('No se pudo cambiar el estado de la carrera.');
    }
  }

  if (loadingProgram) {
    return (
      <PageContainer>
        <Skeleton className="h-8 w-64 rounded-xl" />
      </PageContainer>
    );
  }

  if (!program) {
    return (
      <PageContainer>
        <EmptyState title="Carrera no encontrada" />
      </PageContainer>
    );
  }

  const cfg = STATUS_CONFIG[program.status] ?? { label: program.status, tone: 'neutral' };
  const isActive = program.status === 'ACTIVE';

  const metaItems = [
    program.level ? LEVEL_LABELS[program.level] : null,
    program.teachingMode ? TEACHING_MODE_LABELS[program.teachingMode] : null,
    program.totalYears ? `${program.totalYears} ${program.totalYears === 1 ? 'año' : 'años'}` : null,
    program.totalCreditsRequired ? `${program.totalCreditsRequired} créditos` : null,
  ].filter(Boolean);

  const allSubjects = subjects ?? [];

  const subjectsByYear = allSubjects.reduce((acc, ps) => {
    const year = ps.curricularYear ?? 0;
    if (!acc[year]) acc[year] = [];
    acc[year].push(ps);
    return acc;
  }, {});

  const totalYears = program.totalYears ?? 1;
  const yearBlocks = [];
  for (let y = 1; y <= totalYears; y++) {
    yearBlocks.push(y);
  }
  Object.keys(subjectsByYear)
    .map(Number)
    .filter((y) => y > totalYears)
    .sort((a, b) => a - b)
    .forEach((y) => yearBlocks.push(y));
  if (subjectsByYear[0]) yearBlocks.push(0);

  return (
    <PageContainer>
      <div className="flex flex-col gap-4">
        <div>
          <Link
            to="/administrativo/gestion-academica?section=carreras"
            className="mb-1 flex items-center gap-1 text-[12px] text-ink-faint hover:text-ink"
          >
            <ChevronLeft className="h-3.5 w-3.5" />
            Carreras
          </Link>
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <h1 className="font-heading text-xl font-bold text-ink">{program.name}</h1>
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
                onClick={() => navigate(`/administrativo/gestion-academica/carreras/${id}/editar`)}
              >
                <Pencil className="mr-1.5 h-3.5 w-3.5" />
                Editar
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={handleToggleStatus}
                loading={deactivateProgram.isPending || activateProgram.isPending}
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
              <StatusPill tone={program.visibleInCatalog ? 'success' : 'neutral'}>
                {program.visibleInCatalog ? 'En catálogo' : 'No publicada'}
              </StatusPill>
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={handleToggleCatalog}
              loading={setCatalogVisibility.isPending}
              className={program.visibleInCatalog ? 'text-danger hover:border-danger/50 hover:text-danger' : ''}
            >
              {program.visibleInCatalog ? (
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

        {program.graduationProfile && (
          <GlassCard className="p-4">
            <h2 className="mb-1 text-[11px] font-bold uppercase tracking-widest text-ink-faint">
              Perfil del egresado
            </h2>
            <p className="text-[13.5px] text-ink">{program.graduationProfile}</p>
          </GlassCard>
        )}

        <Tabs defaultValue="plan">
          <TabsList variant="segmented">
            <TabsTrigger value="plan">Plan de estudios</TabsTrigger>
            <TabsTrigger value="cohortes">Cohortes</TabsTrigger>
          </TabsList>

          <TabsContent value="plan">
            <div className="flex flex-col gap-4 pt-2">
              <div className="flex items-center justify-between">
                <h2 className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">Plan de estudios</h2>
                <Button variant="gradient" size="sm" onClick={() => handleAgregar(1)}>
                  <Plus className="mr-1.5 h-3.5 w-3.5" />
                  Agregar materia
                </Button>
              </div>

              {loadingSubjects ? (
                <Skeleton rows={4} />
              ) : (
                <div className="flex flex-col gap-3">
                  {yearBlocks.map((y) => (
                    <YearBlock
                      key={y}
                      year={y}
                      totalYears={totalYears}
                      subjects={subjectsByYear[y] ?? []}
                      onAgregar={handleAgregar}
                      onCorrelativas={handleCorrelativas}
                      onRemove={setSubjectToRemove}
                      onRestore={handleRestore}
                      isRestoring={activateProgramSubject.isPending}
                    />
                  ))}
                  {yearBlocks.length === 0 && (
                    <GlassCard className="p-6">
                      <EmptyState
                        title="Plan de estudios vacío"
                        description="Agregá materias para construir el plan."
                      />
                    </GlassCard>
                  )}
                </div>
              )}
            </div>
          </TabsContent>

          <TabsContent value="cohortes">
            <div className="pt-2">
              <CohortesSection programId={id} />
            </div>
          </TabsContent>
        </Tabs>
      </div>

      <PanelAgregarMateria
        key={panelSession}
        open={panelOpen}
        onClose={() => setPanelOpen(false)}
        programId={id}
        defaultYear={panelYear}
        totalYears={program.totalYears}
      />

      <PanelCorrelativas
        open={correlativasOpen}
        onClose={() => setCorrelativasOpen(false)}
        programId={id}
        programSubjectId={correlativaPs?.id}
        subjectName={correlativaPs?.subjectName}
      />

      <Dialog open={subjectToRemove !== null} onOpenChange={(o) => !o && setSubjectToRemove(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Quitar materia del plan</DialogTitle>
            <DialogDescription>
              {subjectToRemove?.subjectName} va a quedar inactiva en el plan de estudios. Podés volver a agregarla más adelante.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setSubjectToRemove(null)}>
              Cancelar
            </Button>
            <Button variant="destructive" loading={deactivateProgramSubject.isPending} onClick={handleConfirmRemove}>
              Quitar del plan
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
