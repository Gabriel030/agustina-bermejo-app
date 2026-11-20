import { useEffect, useMemo, useState } from 'react';
import { useActiveRole } from '@/hooks/useActiveRole';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { InfoTip } from '@/components/charts/InfoTip';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useTerminos } from '@/features/academic-structure';
import { FEE_TYPE_LABELS, formatDate, formatMoney, formatSignedMoney, todayIsoDate } from '@/lib/billing';
import { detailPath } from '../authorization/authorizationRequests';
import { RadioChoice } from '../discounts/RadioChoice';
import { ViolationList } from '../discounts/ViolationList';
import { useOfferNames } from '../hooks/useOfferNames';
import { PriceImpactPreview } from './PriceImpactPreview';
import {
  DISCOUNT_KIND,
  REASON_MAX_LENGTH,
  amountOf,
  buildPriceChangePayload,
  computeFinalAmount,
  isFormComplete,
  lectiveYearOptions,
  offerName,
  whatHappens,
} from './pricing';
import { usePriceImpact, usePriceValidation, usePrices, usePublishPriceVersion } from './usePrices';

const NO_YEAR = 'none';
const DEBOUNCE_MS = 400;

function WhatHappensPanel({ lines }) {
  if (lines.length === 0) return null;
  return (
    <GlassCard className="flex flex-col gap-2 p-5">
      <h2 className="font-heading text-lg font-semibold text-ink">Qué va a pasar</h2>
      <ul className="flex list-disc flex-col gap-1.5 pl-5 text-sm text-ink-soft">
        {lines.map((line) => {
          if (line.kind === 'advance') {
            return (
              <li key={line.key}>
                Quien adelante la matrícula {line.year} paga <strong className="text-ink">{formatMoney({ amount: line.finalAmount.toFixed(2), currency: line.currency })}</strong>, y el
                precio queda fijo en la factura aunque después cambie. Quien espere a la emisión regular paga el precio que esté vigente ese día.
              </li>
            );
          }
          if (line.kind === 'regular') {
            return (
              <li key={line.key}>
                Desde el {formatDate(line.effectiveFrom)} se factura <strong className="text-ink">{formatMoney({ amount: line.finalAmount.toFixed(2), currency: line.currency })}</strong>.
              </li>
            );
          }
          if (line.kind === 'difference') {
            return (
              <li key={line.key}>
                Contra el precio actual: {formatSignedMoney({ amount: line.difference.toFixed(2), currency: line.currency })}.
              </li>
            );
          }
          return <li key={line.key}>Lo que ya se generó conserva su precio.</li>;
        })}
      </ul>
    </GlassCard>
  );
}

export function NewPriceVersionPage() {
  const { priceId } = useParams();
  const navigate = useNavigate();
  const { roleBase } = useActiveRole();
  const isDirector = roleBase === 'director';
  const listPath = `/${roleBase}/precios`;
  const prices = usePrices();
  const names = useOfferNames();
  const terms = useTerminos();
  const impact = usePriceImpact(priceId);
  const validation = usePriceValidation(priceId);
  const publish = usePublishPriceVersion(priceId);
  const today = todayIsoDate();

  const [amount, setAmount] = useState('');
  const [discountKind, setDiscountKind] = useState(DISCOUNT_KIND.NONE);
  const [discountValue, setDiscountValue] = useState('');
  const [startMode, setStartMode] = useState('today');
  const [effectiveFrom, setEffectiveFrom] = useState(today);
  const [lectiveYear, setLectiveYear] = useState(NO_YEAR);
  const [reason, setReason] = useState('');
  const [serverViolations, setServerViolations] = useState([]);

  const price = (prices.data ?? []).find((item) => String(item.priceId) === priceId);
  const currency = String(price?.currency ?? 'ARS');
  const appliesToLectiveYear = lectiveYear === NO_YEAR ? null : lectiveYear;
  const start = startMode === 'today' ? today : effectiveFrom;
  const complete = isFormComplete({ amount, discountKind, discountValue, effectiveFrom: start, reason });
  const computed = amount === '' ? null : computeFinalAmount({ amount, discountKind, discountValue, effectiveFrom: start, reason });
  const termYears = useMemo(() => (terms.data ?? []).map((term) => Number(term.year)), [terms.data]);
  const yearOptions = lectiveYearOptions(today, termYears);

  const payload = useMemo(
    () =>
      complete
        ? buildPriceChangePayload({ amount, currency, discountKind, discountValue, effectiveFrom: start, reason, appliesToLectiveYear })
        : null,
    [complete, amount, currency, discountKind, discountValue, start, reason, appliesToLectiveYear],
  );

  const { mutate: runImpact } = impact;
  const { mutate: runValidation } = validation;
  useEffect(() => {
    if (!payload) return undefined;
    const timer = setTimeout(() => {
      runImpact(payload);
      runValidation(payload);
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [payload, runImpact, runValidation]);

  const lines = computed
    ? whatHappens({
        finalAmount: computed.final,
        currentFinal: price?.currentVersion ? amountOf(price.currentVersion.finalAmount) : null,
        effectiveFrom: start,
        appliesToLectiveYear,
      }).map((line) => ({ ...line, currency }))
    : [];
  const unlocksCatalog = computed && price && !price.currentVersion && !appliesToLectiveYear;

  const liveViolations = validation.data?.violations ?? [];
  const violations = [...liveViolations, ...serverViolations];
  const canSubmit = complete && !publish.isPending && liveViolations.length === 0 && start >= today;

  async function submit() {
    setServerViolations([]);
    try {
      const result = await publish.mutateAsync(payload);
      if (result?.authorizationRequestId != null) {
        toast.success('Propuesta enviada al director.');
        navigate(detailPath(roleBase, result.authorizationRequestId));
      } else {
        toast.success('Cambio publicado.');
        navigate(`${listPath}/${priceId}`);
      }
    } catch (error) {
      setServerViolations(error.violations?.length ? error.violations : [{ message: error.message }]);
    }
  }

  if (prices.isLoading) {
    return (
      <PageContainer title="Nueva versión de precio">
        <Skeleton className="h-64 w-full rounded-3xl" aria-label="Cargando precio" />
      </PageContainer>
    );
  }
  if (prices.isError && !price) {
    return (
      <PageContainer title="Nueva versión de precio">
        <LoadErrorState error={prices.error} resource="el precio" onRetry={prices.refetch} backTo={listPath} backLabel="Precios" />
      </PageContainer>
    );
  }
  if (!price) {
    return (
      <PageContainer title="Nueva versión de precio">
        <GlassCard className="flex flex-col items-start gap-3 p-5">
          <p role="alert" className="text-sm text-danger">No encontramos este precio.</p>
          <Button asChild variant="outline">
            <Link to={listPath}>Volver a Precios</Link>
          </Button>
        </GlassCard>
      </PageContainer>
    );
  }

  return (
    <PageContainer
      title={`${price.currentVersion ? 'Cambiar' : 'Cargar'} el precio · ${FEE_TYPE_LABELS[price.feeType] ?? price.description}`}
      description={offerName(price, names)}
    >
      <GlassCard className="flex flex-col gap-5 p-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="price-amount">Precio de lista</Label>
            <div className="flex items-center gap-2">
              <span className="text-sm font-semibold text-ink-soft">{currency === 'ARS' ? '$' : currency}</span>
              <Input id="price-amount" type="number" min="0" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} />
            </div>
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="price-final">Precio final</Label>
            <Input
              id="price-final"
              readOnly
              className="cursor-default"
              value={computed ? formatMoney({ amount: computed.final.toFixed(2), currency }) : ''}
            />
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <Label>Descuento de marketing</Label>
          <RadioChoice
            name="discount-kind"
            ariaLabel="Descuento de marketing"
            value={discountKind}
            onChange={setDiscountKind}
            options={[
              { value: DISCOUNT_KIND.NONE, label: 'Sin descuento' },
              { value: DISCOUNT_KIND.PERCENTAGE, label: 'Porcentaje' },
              { value: DISCOUNT_KIND.FIXED, label: 'Monto fijo' },
            ]}
          />
          {discountKind !== DISCOUNT_KIND.NONE && (
            <Input
              aria-label={discountKind === DISCOUNT_KIND.PERCENTAGE ? 'Porcentaje de descuento' : 'Monto del descuento'}
              type="number"
              min="0"
              step="0.01"
              className="max-w-[200px]"
              value={discountValue}
              onChange={(event) => setDiscountValue(event.target.value)}
            />
          )}
        </div>

        <div className="flex flex-col gap-2">
          <div className="flex items-center gap-1">
            <Label>Vigencia</Label>
            <InfoTip label="Qué significa Vigencia">
              Es la fecha desde la que rige el precio. Las facturas que se emitan antes conservan el precio anterior; las que se emitan desde esa fecha usan este.
            </InfoTip>
          </div>
          <RadioChoice
            name="start-mode"
            ariaLabel="Vigencia"
            value={startMode}
            onChange={setStartMode}
            options={[
              { value: 'today', label: 'Desde hoy' },
              { value: 'date', label: 'Desde una fecha' },
            ]}
          />
          {startMode === 'date' && (
            <Input aria-label="Fecha de vigencia" type="date" min={today} className="max-w-[200px]" value={effectiveFrom} onChange={(event) => setEffectiveFrom(event.target.value)} />
          )}
          <p className="text-size-12 text-ink-faint">Rige desde el {formatDate(start)}.</p>
        </div>

        <div className="flex flex-col gap-1.5">
          <div className="flex items-center gap-1">
            <Label htmlFor="price-lective-year">Año lectivo al que aplica</Label>
            <InfoTip label="Qué significa Año lectivo al que aplica">
              Es opcional. Con un año elegido, esta versión se usa para la matrícula de ese año que se paga por adelantado, y no cambia el precio de hoy. Dejalo en Ninguno para un precio común.
            </InfoTip>
          </div>
          <Select value={lectiveYear} onValueChange={setLectiveYear}>
            <SelectTrigger id="price-lective-year" className="max-w-[260px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={NO_YEAR}>Ninguno</SelectItem>
              {yearOptions.map((option) => (
                <SelectItem key={option.year} value={String(option.year)} disabled={!option.enabled}>
                  {option.year}
                  {option.enabled ? '' : ' · sin año lectivo cargado'}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="price-reason">Motivo (obligatorio)</Label>
          <Textarea id="price-reason" maxLength={REASON_MAX_LENGTH} value={reason} onChange={(event) => setReason(event.target.value)} />
        </div>
      </GlassCard>

      {computed && <WhatHappensPanel lines={lines} />}
      {unlocksCatalog && (
        <p role="status" className="rounded-2xl bg-success-bg/70 px-4 py-3 text-sm text-ink">
          Con este precio la oferta vuelve a aparecer en el catálogo y se puede facturar.
        </p>
      )}
      <PriceImpactPreview impact={impact} effectiveFrom={complete ? start : null} />
      <ViolationList violations={violations} title="No pudimos guardar el cambio" />

      <div className="flex flex-wrap justify-end gap-2">
        <Button asChild variant="outline">
          <Link to={`${listPath}/${priceId}`}>Cancelar</Link>
        </Button>
        <Button variant="gradient" onClick={submit} disabled={!canSubmit} loading={publish.isPending}>
          {isDirector ? 'Publicar cambio' : 'Enviar propuesta'}
        </Button>
      </div>
    </PageContainer>
  );
}
