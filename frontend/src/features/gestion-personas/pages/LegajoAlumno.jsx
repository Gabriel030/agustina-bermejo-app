import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Skeleton } from '@/components/Skeleton';
import { useAuth } from '@/context/AuthContext';
import { useLegajoAlumno } from '@/features/gestion-personas/hooks/useLegajoAlumno';
import { useWithdrawStudent, useResetStudentPassword } from '@/features/gestion-personas/hooks/useAccionesLegajo';
import { DarDeBajaModal } from '@/features/gestion-personas/components/DarDeBajaModal';
import { ResetPasswordModal } from '@/features/gestion-personas/components/ResetPasswordModal';
import { DatosPersonalesTab } from './tabs/DatosPersonalesTab';
import { AcademicoTab } from './tabs/AcademicoTab';
import { EvaluacionTab } from './tabs/EvaluacionTab';
import { FinancieroTab } from './tabs/FinancieroTab';

const ESTADO_STATUS_BADGE = {
  ACTIVE: { label: 'Activo', className: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' },
  DELETED: { label: 'Dado de baja', className: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400' },
};

export function LegajoAlumno() {
  const { id } = useParams();
  const { usuario } = useAuth();
  const { data: legajo, isLoading, isError } = useLegajoAlumno(id);

  const [bajaOpen, setBajaOpen] = useState(false);
  const [resetOpen, setResetOpen] = useState(false);

  const withdrawMutation = useWithdrawStudent(id);
  const resetMutation = useResetStudentPassword(id);

  const rolBase = usuario?.roles[0]?.toLowerCase();
  const puedeAccionar = rolBase === 'preceptor' || rolBase === 'administrativo';
  const puededarDeBaja = puedeAccionar && legajo?.status !== 'DELETED';

  const badge = ESTADO_STATUS_BADGE[legajo?.status];

  return (
    <PageContainer>
      <div className="flex flex-col gap-4">
        <GlassCard className="flex flex-col gap-4 p-5">
          {isLoading ? (
            <Skeleton className="h-10 w-64" />
          ) : isError ? (
            <p className="text-[14px] text-ink-faint">No se pudo cargar el legajo.</p>
          ) : (
            <div className="flex items-center gap-3">
              <div>
                <h1 className="font-heading text-xl font-bold text-ink">
                  {legajo.apellido}, {legajo.nombre}
                </h1>
                <p className="text-[13px] text-ink-faint">DNI {legajo.dni}</p>
              </div>
              {badge && (
                <span className={`rounded-full px-3 py-0.5 text-[11px] font-semibold ${badge.className}`}>
                  {badge.label}
                </span>
              )}
            </div>
          )}

          <Tabs defaultValue="datos-personales">
            <TabsList variant="segmented" className="w-full">
              <TabsTrigger value="datos-personales" className="flex-1">
                Datos personales
              </TabsTrigger>
              <TabsTrigger value="academico" className="flex-1">
                Académico
              </TabsTrigger>
              <TabsTrigger value="evaluacion" className="flex-1">
                Evaluación
              </TabsTrigger>
              <TabsTrigger value="financiero" className="flex-1">
                Financiero
              </TabsTrigger>
            </TabsList>

            <TabsContent value="datos-personales" className="mt-4">
              <DatosPersonalesTab
                legajo={legajo}
                puedeAccionar={puedeAccionar}
                puedeDarDeBaja={puededarDeBaja}
                onReset={() => setResetOpen(true)}
                onBaja={() => setBajaOpen(true)}
              />
            </TabsContent>
            <TabsContent value="academico" className="mt-4">
              <AcademicoTab />
            </TabsContent>
            <TabsContent value="evaluacion" className="mt-4">
              <EvaluacionTab />
            </TabsContent>
            <TabsContent value="financiero" className="mt-4">
              <FinancieroTab />
            </TabsContent>
          </Tabs>
        </GlassCard>
      </div>

      <DarDeBajaModal
        open={bajaOpen}
        onClose={() => setBajaOpen(false)}
        onConfirmar={(data) => withdrawMutation.mutate(data, { onSuccess: () => setBajaOpen(false) })}
        isPending={withdrawMutation.isPending}
      />

      <ResetPasswordModal
        open={resetOpen}
        onClose={() => setResetOpen(false)}
        email={legajo?.email}
        onConfirmar={() => resetMutation.mutateAsync()}
        isPending={resetMutation.isPending}
      />
    </PageContainer>
  );
}
