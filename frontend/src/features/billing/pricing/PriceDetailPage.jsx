import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '@/context/AuthContext';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { ModalMotivo } from '@/components/ModalMotivo';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { FEE_TYPE_LABELS, formatDate, formatMoney } from '@/lib/billing';
import { inboxPath, roleBaseOf } from '../authorization/authorizationRequests';
import { useOfferNames } from '../hooks/useOfferNames';
import { PRICE_STATUS, REASON_MAX_LENGTH, VERSION_STATUS, amountOf, offerName, priceStatusKey } from './pricing';
import { useCancelPriceVersion, usePriceHistory, usePrices } from './usePrices';

function Breakdown({ version }) {
  const hasDiscount = amountOf(version.marketingDiscountAmount) > 0;
  return (
    <p className="text-[13px] text-ink-soft">
      Precio de lista {formatMoney(version.baseAmount)}
      {hasDiscount && ` − Descuento de marketing ${formatMoney(version.marketingDiscountAmount)}`}
      {hasDiscount && ` = Precio final ${formatMoney(version.finalAmount)}`}
    </p>
  );
}

function UpcomingList({ price }) {
  const items = [
    price.nextScheduledVersion && { key: 'scheduled', tone: 'accent', label: 'Programada', version: price.nextScheduledVersion },
    price.openProposal && { key: 'proposal', tone: 'warning', label: 'Propuesta', version: price.openProposal },
  ].filter(Boolean);
  if (items.length === 0) return null;
  return (
    <GlassCard className="flex flex-col gap-3 p-5">
      <h2 className="font-heading text-lg font-semibold text-ink">Qué viene</h2>
      <ul className="flex flex-col gap-2">
        {items.map(({ key, tone, label, version }) => (
          <li key={key} className="flex flex-wrap items-center gap-2 text-sm text-ink">
            <StatusPill tone={tone}>{label}</StatusPill>
            <span className="font-semibold tabular-nums">{formatMoney(version.finalAmount)}</span>
            <span className="text-ink-soft">desde el {formatDate(version.effectiveFrom)}</span>
            {version.appliesToLectiveYear != null && <span className="text-ink-faint">· Matrícula anticipada {version.appliesToLectiveYear}</span>}
          </li>
        ))}
      </ul>
    </GlassCard>
  );
}

function HistoryTable({ priceId }) {
  const history = usePriceHistory(priceId);
  if (history.isLoading) return <Skeleton className="h-32 w-full rounded-2xl" aria-label="Cargando historial" />;
  if (history.isError) return <p className="text-sm text-ink-soft">No pudimos cargar el historial.</p>;
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Lista</TableHead>
          <TableHead>Descuento</TableHead>
          <TableHead>Final</TableHead>
          <TableHead>Vigente desde</TableHead>
          <TableHead>Estado</TableHead>
          <TableHead>Motivo</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {history.data.map((entry) => {
          const status = VERSION_STATUS[entry.status];
          return (
            <TableRow key={entry.id}>
              <TableCell className="tabular-nums">{formatMoney(entry.baseAmount)}</TableCell>
              <TableCell className="tabular-nums">{amountOf(entry.marketingDiscountAmount) > 0 ? formatMoney(entry.marketingDiscountAmount) : '—'}</TableCell>
              <TableCell className="font-semibold tabular-nums">{formatMoney(entry.finalAmount)}</TableCell>
              <TableCell>{formatDate(entry.effectiveFrom)}</TableCell>
              <TableCell>
                <StatusPill tone={status.tone}>{status.label}</StatusPill>
              </TableCell>
              <TableCell className="max-w-[260px] truncate text-ink-soft" title={entry.changeReason}>
                {entry.changeReason}
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}

export function PriceDetailPage() {
  const { priceId } = useParams();
  const { usuario } = useAuth();
  const roleBase = roleBaseOf(usuario);
  const isDirector = roleBase === 'director';
  const prices = usePrices();
  const names = useOfferNames();
  const cancel = useCancelPriceVersion();
  const [cancelling, setCancelling] = useState(false);
  const listPath = `/${roleBase}/precios`;
  const price = (prices.data ?? []).find((item) => String(item.priceId) === priceId);

  if (prices.isLoading) {
    return (
      <PageContainer title="Precio">
        <Skeleton className="h-64 w-full rounded-3xl" aria-label="Cargando precio" />
      </PageContainer>
    );
  }
  if (prices.isError) {
    return (
      <PageContainer title="Precio">
        <LoadErrorState error={prices.error} onRetry={prices.refetch} resource="el precio" bare />
      </PageContainer>
    );
  }
  if (!price) {
    return (
      <PageContainer title="Precio">
        <GlassCard className="flex flex-col items-start gap-3 p-5">
          <p role="alert" className="text-sm text-danger">No encontramos este precio.</p>
          <Button asChild variant="outline">
            <Link to={listPath}>Volver a Precios</Link>
          </Button>
        </GlassCard>
      </PageContainer>
    );
  }

  const statusKey = priceStatusKey(price);
  const status = PRICE_STATUS[statusKey];
  const current = price.currentVersion;
  const hasProposal = Boolean(price.openProposal);
  const ctaLabel = !current ? 'Cargar el primer precio' : isDirector ? 'Publicar cambio de precio' : 'Proponer cambio de precio';

  async function confirmCancel(reason) {
    try {
      await cancel.mutateAsync({ versionId: price.nextScheduledVersion.id, reason });
      toast.success('Se canceló el cambio programado.');
      setCancelling(false);
    } catch (error) {
      toast.error(error.message || 'No se pudo cancelar el cambio programado.');
      return false;
    }
    return true;
  }

  return (
    <PageContainer
      title={`${FEE_TYPE_LABELS[price.feeType] ?? price.description} · ${offerName(price, names)}`}
      action={
        <Button asChild variant="outline" size="sm">
          <Link to={listPath}>
            <ArrowLeft className="mr-1.5 h-3.5 w-3.5" />
            Volver a Precios
          </Link>
        </Button>
      }
    >
      <GlassCard className="flex flex-col gap-2 p-5">
        <div className="flex flex-wrap items-center gap-2">
          <StatusPill tone={status.tone}>{status.label}</StatusPill>
          {current && <span className="text-[12px] text-ink-faint">Vigente desde el {formatDate(current.effectiveFrom)}</span>}
        </div>
        {current ? (
          <>
            <p className="font-heading text-4xl font-bold tabular-nums text-ink">{formatMoney(current.finalAmount)}</p>
            <Breakdown version={current} />
          </>
        ) : (
          <p className="text-sm text-ink-soft">No se factura y la oferta no aparece en el catálogo hasta que se cargue un precio.</p>
        )}
      </GlassCard>

      {hasProposal && (
        <div role="status" className="flex flex-wrap items-center justify-between gap-3 rounded-2xl bg-warning-bg/70 px-4 py-3 text-sm text-ink">
          <span>Hay una propuesta abierta. Hasta que se resuelva no se puede cargar otro cambio.</span>
          <Link to={inboxPath(roleBase)} className="font-semibold text-primary hover:underline">
            Ver en Aprobaciones
          </Link>
        </div>
      )}

      <UpcomingList price={price} />

      <div className="flex flex-wrap gap-2">
        <Button asChild={!hasProposal} variant="gradient" disabled={hasProposal}>
          {hasProposal ? <span>{ctaLabel}</span> : <Link to={`${listPath}/${price.priceId}/nueva-version`}>{ctaLabel}</Link>}
        </Button>
        {isDirector && price.nextScheduledVersion && (
          <Button variant="outline" onClick={() => setCancelling(true)}>
            Cancelar el cambio del {formatDate(price.nextScheduledVersion.effectiveFrom)}
          </Button>
        )}
      </div>

      <GlassCard className="flex flex-col gap-3 p-5">
        <h2 className="font-heading text-lg font-semibold text-ink">Historial completo</h2>
        <HistoryTable priceId={price.priceId} />
      </GlassCard>

      <ModalMotivo
        open={cancelling}
        onClose={() => setCancelling(false)}
        onConfirmar={confirmCancel}
        confirmando={cancel.isPending}
        requerido
        titulo="Cancelar el cambio programado"
        descripcion="El cambio no llega a regir y el precio vigente sigue como está."
        placeholder="Motivo de la cancelación"
        confirmarLabel="Cancelar el cambio"
        maxLength={REASON_MAX_LENGTH}
        mensajeRequerido="El motivo de la cancelación es obligatorio."
      />
    </PageContainer>
  );
}
