import { useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, Settings2 } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Button } from '@/components/ui/button';
import {
  useProgramBillingTemplates,
  useBillingConfigurationAlerts,
} from '@/features/academic-structure/hooks/useCohorts';
import { descripcionMatriculas } from '@/features/academic-structure/lib/aniosLectivos';
import { formatIso } from '@/features/academic-structure/lib/billingConfigForm';
import { useTerminos } from '@/features/academic-structure/hooks/useTerminos';
import { useRoleBasePath } from '@/features/academic-structure/hooks/useRoleBasePath';
import { YearBillingConfigPage } from './YearBillingConfigPage';

function advanceLine(template, term) {
  if (template.advanceEnrollmentFeeOpensOn) return `Matrícula anticipada: abre el ${formatIso(template.advanceEnrollmentFeeOpensOn)}`;
  if (term?.enrollmentStart) return `Matrícula anticipada: abre con la inscripción, el ${formatIso(term.enrollmentStart)}`;
  return null;
}

function Alert({ tone, children }) {
  const classes = tone === 'danger' ? 'bg-danger-bg text-ink' : 'bg-warning-bg text-ink';
  const iconClasses = tone === 'danger' ? 'text-danger' : 'text-warning';
  return (
    <div role="alert" className={`flex items-start gap-2 rounded-xl px-3 py-2 text-[13px] ${classes}`}>
      <AlertTriangle className={`mt-0.5 h-4 w-4 shrink-0 ${iconClasses}`} />
      <span>{children}</span>
    </div>
  );
}

export function AniosLectivosSection({ programId, canEdit = false }) {
  const { data: templates, isLoading, isError, error, refetch } = useProgramBillingTemplates(programId);
  const { data: alertas } = useBillingConfigurationAlerts();
  const { data: terminos } = useTerminos();
  const basePath = useRoleBasePath();
  const [editando, setEditando] = useState(null);

  const alertasDeLaCarrera = (alertas ?? []).filter((a) => String(a.programId) === String(programId));

  function termOf(template) {
    return (terminos ?? []).find((t) => String(t.id) === String(template.termId));
  }

  function tituloDe(template) {
    const term = termOf(template);
    return term ? `Año lectivo ${term.year}` : `Año lectivo (término ${template.termId})`;
  }

  if (editando) {
    return (
      <YearBillingConfigPage template={editando} title={tituloDe(editando)} term={termOf(editando)} onClose={() => setEditando(null)} />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {alertasDeLaCarrera.length > 0 && (
        <Alert tone="warning">
          Esta carrera no tiene un año lectivo configurado para {alertasDeLaCarrera[0].period}: no se emite ninguna cuota ni
          matrícula a {alertasDeLaCarrera[0].affectedEnrollments} inscripciones activas. Cargá el año lectivo para completar lo pendiente.
        </Alert>
      )}

      {!canEdit && (
        <p className="text-[13px] text-ink-faint">Solo el director puede modificar la configuración de cobro.</p>
      )}

      {isLoading ? (
        <Skeleton rows={2} />
      ) : isError ? (
        <LoadErrorState error={error} onRetry={refetch} resource="los años lectivos" bare />
      ) : !templates?.length ? (
        <GlassCard className="p-6">
          <EmptyState title="Sin años lectivos" description="Dirección tiene que cargar el año lectivo de la carrera para que se emitan cuotas y matrículas." />
        </GlassCard>
      ) : (
        <div className="flex flex-col gap-3">
          {templates.map((template) => {
            const term = termOf(template);
            const line = advanceLine(template, term);
            return (
              <GlassCard key={template.id} className="flex flex-col gap-3 p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="min-w-0">
                    <p className="text-[14px] font-semibold text-ink">{tituloDe(template)} — {template.recurringFeeInvoicesCount} cuotas</p>
                    <p className="text-[12px] text-ink-faint">{descripcionMatriculas(template)}</p>
                    {line && <p className="text-[12px] text-ink-faint">{line}</p>}
                  </div>
                  {canEdit && (
                    <Button variant="outline" size="sm" onClick={() => setEditando(template)} aria-label={`Configurar cobro de ${tituloDe(template)}`}>
                      <Settings2 className="mr-1.5 h-3.5 w-3.5" />
                      Configurar cobro
                    </Button>
                  )}
                </div>
                {template.nextYearPriceMissing && (
                  <Alert tone="danger">
                    Falta el precio de la matrícula del año siguiente: hasta cargarlo, nadie puede generarla por adelantado.{' '}
                    {canEdit && (
                      <Link to={`${basePath}/precios`} className="font-semibold text-primary hover:underline">
                        Cargar el precio
                      </Link>
                    )}
                  </Alert>
                )}
                {template.advanceOpeningAfterRegularEmission && (
                  <Alert tone="warning">
                    La apertura de la matrícula anticipada es posterior a la emisión regular: nadie va a poder adelantarla. Elegí una fecha anterior.
                  </Alert>
                )}
              </GlassCard>
            );
          })}
        </div>
      )}
    </div>
  );
}
