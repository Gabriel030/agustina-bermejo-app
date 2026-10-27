import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useAuth } from '@/context/AuthContext';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { formatMoney } from '@/lib/billing';
import { useRefundEligibility, useRefundPreview, useRequestRefund } from '../hooks/useRefunds';
import { RefundBlockAlert } from './RefundBlockAlert';
import { RefundSplitPreview } from './RefundSplitPreview';
import {
  ACCOUNT_MAX_LENGTH,
  AMOUNT_CODES,
  DESTINATION_CODES,
  HOLDER_MAX_LENGTH,
  PREVIEW_DEBOUNCE_MS,
  describeRefundBlock,
  parseAmountInput,
  refundableNowCap,
  requestRefundErrorMessage,
  validateRefundForm,
} from './refundForm';

const EXCEEDS_CODES = ['INSUFFICIENT_ACCOUNT_CREDIT', 'REFUND_EXCEEDS_AVAILABLE_AMOUNT'];

function FieldCounter({ length, max }) {
  return <span className={length > max ? 'text-xs text-danger' : 'text-xs text-ink-faint'}>{length}/{max}</span>;
}

function FieldError({ id, message }) {
  return message ? <p id={id} className="text-xs text-danger">{message}</p> : null;
}

function buildPreviewPayload(form, currency) {
  const amount = parseAmountInput(form.amount);
  if (!amount) return null;
  return {
    amount: { amount, currency },
    destinationAccount: form.account.trim() || null,
    destinationHolder: form.holder.trim() || null,
  };
}

export function RequestRefundPage() {
  const { id: studentId } = useParams();
  const navigate = useNavigate();
  const { usuario } = useAuth();
  const roleBase = usuario?.roles[0]?.toLowerCase();
  const isDirector = roleBase === 'director';
  const studentPath = `/${roleBase}/alumnos/${studentId}?view=financiero`;

  const eligibilityQuery = useRefundEligibility(studentId);
  const eligibility = eligibilityQuery.data;
  const [form, setForm] = useState({ amount: '', account: '', holder: '' });
  const [touched, setTouched] = useState({});
  const [serverErrors, setServerErrors] = useState({});
  const mutation = useRequestRefund(studentId);

  const debouncedForm = useDebouncedValue(form, PREVIEW_DEBOUNCE_MS);
  const previewPayload = useMemo(
    () => (eligibility ? buildPreviewPayload(debouncedForm, eligibility.currency) : null),
    [debouncedForm, eligibility],
  );
  const previewQuery = useRefundPreview(studentId, previewPayload);

  const block = eligibility ? describeRefundBlock(eligibility) : null;
  const cap = eligibility ? refundableNowCap(eligibility) : null;
  const clientErrors = validateRefundForm(form);
  const hasValidAmount = !clientErrors.amount;
  const preview = previewPayload ? previewQuery.data : null;

  const previewBlockedCode = preview && !preview.allowed ? preview.blockedBy?.code : null;
  const previewDebtBlock =
    previewBlockedCode === 'REFUND_BLOCKED_BY_OVERDUE_DEBT'
      ? { tone: 'danger', title: 'Tiene deuda vencida', description: preview.blockedBy.message }
      : null;

  function previewAmountMessage() {
    if (!previewBlockedCode || !AMOUNT_CODES.includes(previewBlockedCode)) return null;
    if (EXCEEDS_CODES.includes(previewBlockedCode)) return `Se pueden devolver hasta ${formatMoney(cap)}. El resto sigue a favor.`;
    return preview.blockedBy.message;
  }

  const amountMessage = (touched.amount && clientErrors.amount) || serverErrors.amount || (hasValidAmount ? previewAmountMessage() : null);
  const accountMessage = (touched.account && clientErrors.account) || serverErrors.account || null;
  const holderMessage = (touched.holder && clientErrors.holder) || serverErrors.holder || null;

  function updateField(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
    setServerErrors((current) => ({ ...current, [field]: undefined }));
  }

  function touch(field) {
    setTouched((current) => ({ ...current, [field]: true }));
  }

  function applyServerError(error) {
    if (DESTINATION_CODES.includes(error?.code)) {
      const message = requestRefundErrorMessage(error);
      setServerErrors({ account: message, holder: error.code === 'REFUND_DESTINATION_REQUIRED' ? undefined : message });
      return;
    }
    if (AMOUNT_CODES.includes(error?.code)) {
      setServerErrors({ amount: requestRefundErrorMessage(error) });
      return;
    }
    toast.error(requestRefundErrorMessage(error));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setTouched({ amount: true, account: true, holder: true });
    setServerErrors({});
    if (Object.keys(clientErrors).length > 0) return;
    const payload = buildPreviewPayload(form, eligibility.currency);
    try {
      const result = await mutation.mutateAsync(payload);
      const awaitingAuthorization = result?.authorizationRequestId != null;
      if (awaitingAuthorization) {
        toast.success('Pedido enviado a la dirección.', { description: 'Queda pendiente de autorización del director y la plata ya está reservada.' });
      } else {
        toast.success('Reembolso aprobado.', { description: 'Falta transferir cada parte desde la sección Reembolsos.' });
      }
      navigate(studentPath);
    } catch (error) {
      applyServerError(error);
    }
  }

  const description = cap ? `Reembolsable ahora ${formatMoney(cap)}` : undefined;
  const typedAmount = hasValidAmount
    ? formatMoney({ amount: parseAmountInput(form.amount), currency: eligibility?.currency ?? 'ARS' })
    : 'Reembolso';

  if (eligibilityQuery.isLoading) {
    return (
      <PageContainer title="Pedir reembolso">
        <GlassCard className="p-5"><Skeleton className="h-48 w-full" /></GlassCard>
      </PageContainer>
    );
  }

  if (eligibilityQuery.isError) {
    return (
      <PageContainer title="Pedir reembolso">
        <GlassCard className="flex flex-col items-start gap-3 p-5">
          <p role="alert" className="text-sm text-danger">No pudimos cargar los datos del reembolso.</p>
          <Button type="button" variant="outline" onClick={() => eligibilityQuery.refetch()}>Reintentar</Button>
        </GlassCard>
      </PageContainer>
    );
  }

  if (block) {
    return (
      <PageContainer title="Pedir reembolso" description={description}>
        <RefundBlockAlert block={block}>
          <Button asChild variant="outline" size="sm">
            <Link to={studentPath}>Volver a la ficha</Link>
          </Button>
        </RefundBlockAlert>
      </PageContainer>
    );
  }

  return (
    <PageContainer title="Pedir reembolso" description={description}>
      {previewDebtBlock && <RefundBlockAlert block={previewDebtBlock} />}
      <div className="grid gap-4 lg:grid-cols-2">
        <GlassCard className="p-5">
          <form id="refund-request-form" onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
            <h2 className="font-heading text-base font-semibold text-ink">Qué se devuelve</h2>

            <div className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between gap-3">
                <Label htmlFor="refund-amount">Monto</Label>
                <span className="text-xs text-ink-faint">Podés pedir hasta {formatMoney(cap)}</span>
              </div>
              <Input
                id="refund-amount"
                inputMode="decimal"
                autoComplete="off"
                placeholder="Ej.: 80.000"
                value={form.amount}
                onChange={(event) => updateField('amount', event.target.value)}
                onBlur={() => touch('amount')}
                aria-invalid={!!amountMessage}
                aria-describedby={amountMessage ? 'refund-amount-error' : undefined}
              />
              <FieldError id="refund-amount-error" message={amountMessage} />
            </div>

            <div className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between gap-3">
                <Label htmlFor="refund-account">CBU o alias</Label>
                <FieldCounter length={form.account.length} max={ACCOUNT_MAX_LENGTH} />
              </div>
              <Input
                id="refund-account"
                autoComplete="off"
                value={form.account}
                onChange={(event) => updateField('account', event.target.value)}
                onBlur={() => touch('account')}
                aria-invalid={!!accountMessage}
                aria-describedby={accountMessage ? 'refund-account-error' : 'refund-account-hint'}
              />
              <FieldError id="refund-account-error" message={accountMessage} />
              {!accountMessage && (
                <p id="refund-account-hint" className="text-xs text-ink-faint">
                  Es obligatorio. Las partes de transferencias con cuenta registrada van a esa cuenta.
                </p>
              )}
            </div>

            <div className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between gap-3">
                <Label htmlFor="refund-holder">Titular de la cuenta</Label>
                <FieldCounter length={form.holder.length} max={HOLDER_MAX_LENGTH} />
              </div>
              <Input
                id="refund-holder"
                autoComplete="off"
                value={form.holder}
                onChange={(event) => updateField('holder', event.target.value)}
                onBlur={() => touch('holder')}
                aria-invalid={!!holderMessage}
                aria-describedby={holderMessage ? 'refund-holder-error' : undefined}
              />
              <FieldError id="refund-holder-error" message={holderMessage} />
            </div>
          </form>
        </GlassCard>

        <GlassCard className="p-5">
          <RefundSplitPreview
            hasValidAmount={hasValidAmount}
            preview={preview}
            isLoading={previewQuery.isFetching}
            isError={previewQuery.isError}
            isDirector={isDirector}
            amountMessage={previewAmountMessage()}
          />
        </GlassCard>
      </div>

      <GlassCard className="sticky bottom-4 z-10 flex flex-wrap items-center gap-3 p-3 px-5">
        <div className="flex min-w-0 flex-1 flex-col">
          <span className="font-semibold tabular-nums text-ink">{typedAmount}</span>
          <span className="text-xs text-ink-faint">{isDirector ? 'Se aprueba ahora' : 'Queda pendiente de la dirección'}</span>
        </div>
        <Button asChild variant="outline">
          <Link to={studentPath}>Cancelar</Link>
        </Button>
        <Button type="submit" form="refund-request-form" variant="gradient" loading={mutation.isPending}>
          {isDirector ? 'Registrar reembolso' : 'Pedir reembolso'}
        </Button>
      </GlassCard>
    </PageContainer>
  );
}
