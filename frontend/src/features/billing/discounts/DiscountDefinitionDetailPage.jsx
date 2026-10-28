import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useAuth } from '@/context/AuthContext';
import { useProgramCohorts } from '@/features/academic-structure';
import { formatMoney } from '@/lib/billing';
import { useDiscountDefinition } from '../hooks/useDiscountDefinitions';
import { scopeLabel, useOfferNames } from '../hooks/useOfferNames';
import { inboxPath, roleBaseOf } from '../authorization/authorizationRequests';
import { CloseDefinitionDialog } from './CloseDefinitionDialog';
import { DiscountStatusPill, PromotionStatusPill } from './StatusPills';
import {
  appliesToLabel,
  formatCalculation,
  formatCalculationWithInstallments,
  formatIsoDate,
  formatQuota,
  formatRange,
  typeLabel,
} from './discountLabels';

function ReadOnlyField({ label, value }) {
  const id = `detail-${label.toLowerCase().replace(/[^a-z]+/g, '-')}`;
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} value={value} readOnly className="cursor-default" />
    </div>
  );
}

function DetailError({ listPath, notFound, onRetry }) {
  return (
    <PageContainer title="Descuento">
      <GlassCard className="flex flex-col items-start gap-3 p-5">
        <p role="alert" className="text-sm text-danger">
          {notFound ? 'No encontramos este descuento o esta promoción.' : 'No pudimos cargar los datos.'}
        </p>
        <div className="flex gap-2">
          {!notFound && <Button type="button" variant="outline" onClick={onRetry}>Reintentar</Button>}
          <Button asChild variant="outline">
            <Link to={listPath}>Volver a Descuentos y promociones</Link>
          </Button>
        </div>
      </GlassCard>
    </PageContainer>
  );
}

export function DiscountDefinitionDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { usuario } = useAuth();
  const roleBase = roleBaseOf(usuario);
  const isDirector = roleBase === 'director';
  const query = useDiscountDefinition(id);
  const names = useOfferNames();
  const [closing, setClosing] = useState(false);
  const definition = query.data;
  const promotion = definition?.discountType === 'COHORT_PROMOTION';
  const listPath = `/${roleBase}/descuentos${promotion ? '?tab=promotions' : ''}`;
  const cohorts = useProgramCohorts(promotion && definition?.programCohortId ? definition.programId : null);

  if (query.isLoading) {
    return (
      <PageContainer title="Cargando…">
        <GlassCard className="p-5"><Skeleton className="h-48 w-full" /></GlassCard>
      </PageContainer>
    );
  }

  if (query.isError) {
    return <DetailError listPath={`/${roleBase}/descuentos`} notFound={query.error?.status === 404} onRetry={() => query.refetch()} />;
  }

  const closed = definition.status === 'CLOSED';
  const cohortLabel = definition.programCohortId
    ? (cohorts.data ?? []).find((cohort) => cohort.id === definition.programCohortId)?.label
    : null;
  const scope = [scopeLabel(definition, names), cohortLabel].filter(Boolean).join(' · ');
  const noun = promotion ? 'promoción' : 'descuento';
  const closeLabel = isDirector ? `Cerrar ${noun}` : 'Proponer cierre';

  return (
    <PageContainer title={definition.name}>
      <Button asChild variant="ghost" size="sm" className="-mt-3 self-start text-ink-soft">
        <Link to={listPath}>
          <ArrowLeft />
          Volver a Descuentos y promociones
        </Link>
      </Button>
      <GlassCard className="flex flex-col gap-5 p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-3">
            {promotion ? <PromotionStatusPill status={definition.cohortPromotionStatus} /> : <DiscountStatusPill entry={definition} />}
            <span className="text-sm text-ink-faint">
              {typeLabel(definition.discountType)} · creado el {formatIsoDate(definition.createdAt)}
              {definition.directPublication ? '' : ' · aprobado por el director'}
            </span>
          </div>
          {!closed && (
            <Button type="button" variant={isDirector ? 'destructive' : 'outline'} onClick={() => setClosing(true)}>
              {closeLabel}
            </Button>
          )}
        </div>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <ReadOnlyField label="Aplica a" value={appliesToLabel(definition.applicableInvoiceType)} />
          <ReadOnlyField label="Cálculo" value={promotion ? formatCalculationWithInstallments(definition) : formatCalculation(definition.calculation)} />
          <ReadOnlyField label="Disponible desde" value={formatIsoDate(definition.availableFrom)} />
          <ReadOnlyField label="Disponible hasta" value={definition.availableUntil ? formatIsoDate(definition.availableUntil) : 'Sin fin'} />
          <ReadOnlyField label="Prioridad" value={String(definition.priority)} />
          <ReadOnlyField label="Tope por factura" value={definition.capPerInvoice ? formatMoney(definition.capPerInvoice) : 'Sin tope'} />
          <ReadOnlyField label="Alcance" value={scope} />
          {promotion && <ReadOnlyField label="Ventana de inscripción" value={formatRange(definition.enrollmentWindowFrom, definition.enrollmentWindowTo)} />}
          <ReadOnlyField label="Cupo" value={definition.quota == null ? 'Sin cupo' : `${formatQuota(definition)} usados`} />
          {definition.estimatedQuotaCostStatus === 'ESTIMATED' && definition.estimatedQuotaCost && (
            <ReadOnlyField label="Costo estimado del cupo completo" value={formatMoney(definition.estimatedQuotaCost)} />
          )}
        </div>

        {closed && (
          <p className="rounded-xl bg-neutral-bg px-4 py-3 text-sm text-ink-soft">
            <b className="text-ink">Cerrada el {formatIsoDate(definition.closedAt)}.</b>
            {definition.closureReason && <> Motivo: {definition.closureReason}</>}
          </p>
        )}
      </GlassCard>

      {closing && (
        <CloseDefinitionDialog
          open
          definition={definition}
          isDirector={isDirector}
          onClose={() => setClosing(false)}
          onClosed={() => navigate(listPath)}
          onSeeRequests={() => navigate(inboxPath(roleBase))}
        />
      )}
    </PageContainer>
  );
}
