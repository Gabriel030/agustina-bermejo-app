import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Skeleton } from '@/components/Skeleton';
import { useLegajoAlumno } from '@/features/gestion-personas/hooks/useLegajoAlumno';
import { DatosPersonalesTab } from './tabs/DatosPersonalesTab';
import { AcademicoTab } from './tabs/AcademicoTab';
import { EvaluacionTab } from './tabs/EvaluacionTab';
import { FinancieroTab } from './tabs/FinancieroTab';

const ESTADO_STATUS_BADGE = {
  ACTIVE: { label: 'Activo', className: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' },
  INACTIVE: { label: 'Dado de baja', className: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400' },
};

export function LegajoAlumno() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: legajo, isLoading, isError } = useLegajoAlumno(id);

  const badge = ESTADO_STATUS_BADGE[legajo?.status];

  return (
    <PageContainer>
      <div className="flex flex-col gap-4">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="flex items-center gap-1.5 text-[13px] text-ink-faint transition-colors hover:text-ink"
          >
            <ArrowLeft size={15} />
            Volver
          </button>
        </div>

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
            <TabsList variant="line" className="w-full">
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
              <DatosPersonalesTab legajo={legajo} />
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
    </PageContainer>
  );
}
