import { useState } from 'react';
import { ArrowRight, GraduationCap, RefreshCw } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { GraduationTab } from '../../components/GraduationTab';
import { useApprovedProcedures } from '../hooks/useStudentProcedures';
import { PROCEDURES, procedureOf } from '../lib/procedures';
import { ApprovedProceduresList } from './ApprovedProceduresList';
import { ChangeMySectionFlow } from './ChangeMySectionFlow';
import { ProcedureDetail } from './ProcedureDetail';
import { ProcedureRow } from './ProcedureRow';

function LinkRow({ icon: Icon, title, description, children }) {
  return (
    <li className="flex flex-wrap items-center gap-3 px-4 py-3.5">
      <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-accent-soft text-brand">
        <Icon aria-hidden="true" className="size-[18px]" />
      </span>
      <div className="flex min-w-[200px] flex-1 flex-col gap-0.5">
        <span className="font-semibold text-ink">{title}</span>
        <span className="text-sm text-ink-soft">{description}</span>
      </div>
      {children}
    </li>
  );
}

export function StudentProceduresSection({ carreraId, onSeeStudyPlan }) {
  const [section, setSection] = useState('procedures');
  const [openType, setOpenType] = useState(null);
  const [showGraduation, setShowGraduation] = useState(false);
  const [changingSection, setChangingSection] = useState(false);
  const approved = useApprovedProcedures();

  if (approved.isLoading) {
    return (
      <div className="flex flex-col gap-3" aria-busy="true" aria-label="Cargando trámites">
        {PROCEDURES.map((procedure) => (
          <Skeleton key={procedure.type} className="h-[68px]" />
        ))}
      </div>
    );
  }
  if (approved.error?.status === 403) {
    return (
      <GlassCard className="p-4">
        <EmptyState icon="🔒" title="No tenés permiso para ver los trámites" description="Esta sección es solo para alumnos." />
      </GlassCard>
    );
  }
  if (approved.isError) {
    return <LoadErrorState error={approved.error} onRetry={approved.refetch} resource="tus trámites" />;
  }

  const items = approved.data ?? [];
  const validCount = items.filter((item) => item.status === 'VALID').length;

  if (openType) {
    return <ProcedureDetail procedure={procedureOf(openType)} carreraId={carreraId} onBack={() => setOpenType(null)} />;
  }

  if (showGraduation) {
    return (
      <div className="flex flex-col gap-3">
        <Button variant="subtle" className="self-start" onClick={() => setShowGraduation(false)}>
          Trámites
        </Button>
        <GraduationTab carreraId={carreraId} onSeeStudyPlan={onSeeStudyPlan} />
      </div>
    );
  }

  return (
    <Tabs value={section} onValueChange={setSection} className="flex flex-col gap-3">
      <TabsList variant="line">
        <TabsTrigger value="procedures">Trámites</TabsTrigger>
        <TabsTrigger value="approved">Trámites aprobados{validCount > 0 ? ` (${validCount})` : ''}</TabsTrigger>
      </TabsList>
      <TabsContent value="procedures">
        <GlassCard className="p-0">
          <ul className="flex flex-col divide-y divide-border">
            {PROCEDURES.map((procedure) => (
              <ProcedureRow
                key={procedure.type}
                procedure={procedure}
                carreraId={carreraId}
                onOpen={setOpenType}
                onShowApproved={() => setSection('approved')}
              />
            ))}
            <LinkRow icon={RefreshCw} title="Cambio de comisión" description="Pasá de una comisión a otra en una materia que cursás.">
              <Button variant="outline" className="h-auto min-h-11 w-full sm:w-auto md:h-8 md:min-h-0" onClick={() => setChangingSection(true)}>
                Abrir
              </Button>
            </LinkRow>
            <LinkRow icon={GraduationCap} title="Solicitud de egreso" description="Pedí tu título cuando completes el plan de estudios.">
              <Button variant="outline" className="w-full sm:w-auto" onClick={() => setShowGraduation(true)}>
                Abrir
                <ArrowRight aria-hidden="true" />
              </Button>
            </LinkRow>
          </ul>
        </GlassCard>
        {changingSection && <ChangeMySectionFlow carreraId={carreraId} onClose={() => setChangingSection(false)} />}
      </TabsContent>
      <TabsContent value="approved">
        <ApprovedProceduresList items={items} onRegenerate={setOpenType} />
      </TabsContent>
    </Tabs>
  );
}
