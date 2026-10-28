import { useEffect, useRef, useState } from 'react';
import { AlertTriangle, Clock } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { InvoiceCoverageList } from '@/components/billing/InvoiceCoverageList';
import { ReceiptDropzone } from '@/components/billing/ReceiptDropzone';
import { useSubmitBankTransferReceipt } from '@/hooks/useBilling';
import { formatDate, formatMoney, receiptFileError } from '@/lib/billing';
import {
  buildReceiptSubmission,
  formatAmountInput,
  parseAmountInput,
  receiptFormErrors,
  receiptOutcomeLines,
  receiptSubmissionFailure,
  selectedTotal,
  todayIsoDate,
} from '@/lib/bankTransferReceipt';
import { cn, moneyValue } from '@/lib/utils';

const TONE_DOT = { success: 'bg-success', warning: 'bg-warning', neutral: 'bg-neutral-ink' };

function newIdempotencyKey() {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function TextField({ id, label, value, onChange, onBlur, error, placeholder, inputMode, autoComplete = 'off' }) {
  const errorId = `${id}-error`;
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onBlur={onBlur}
        placeholder={placeholder}
        inputMode={inputMode}
        autoComplete={autoComplete}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? errorId : undefined}
        className="h-10"
      />
      {error && (
        <span id={errorId} role="alert" className="text-xs text-danger">
          {error}
        </span>
      )}
    </div>
  );
}

export function BankTransferReceiptForm({
  invoices,
  underReview = [],
  initialCount = 1,
  defaultHolderName = '',
  onSubmitted,
  onViewReceipt,
}) {
  const formRef = useRef(null);
  const idempotencyKey = useRef(null);
  const { mutate, isPending } = useSubmitBankTransferReceipt();
  const currency = invoices[0].outstanding.currency;
  const today = todayIsoDate();

  const [count, setCount] = useState(Math.min(Math.max(initialCount, 1), invoices.length));
  const [amountText, setAmountText] = useState(() =>
    formatAmountInput(selectedTotal(invoices, Math.min(Math.max(initialCount, 1), invoices.length))),
  );
  const [amountTouched, setAmountTouched] = useState(false);
  const [file, setFile] = useState(null);
  const [fileError, setFileError] = useState(null);
  const [operationNumber, setOperationNumber] = useState('');
  const [originAccount, setOriginAccount] = useState('');
  const [holderName, setHolderName] = useState(defaultHolderName);
  const [bankName, setBankName] = useState('');
  const [showBank, setShowBank] = useState(false);
  const [showErrors, setShowErrors] = useState(false);
  const [serverErrors, setServerErrors] = useState({});
  const [formMessage, setFormMessage] = useState(null);
  const [duplicate, setDuplicate] = useState(false);
  const [focusRequest, setFocusRequest] = useState(0);

  const effectiveCount = Math.min(count, invoices.length);
  const amount = parseAmountInput(amountText);
  const total = selectedTotal(invoices, effectiveCount);

  const localErrors = showErrors
    ? receiptFormErrors({
        file,
        operationNumber,
        originAccount,
        holderName,
        amount,
        firstInvoiceOutstanding: moneyValue(invoices[0].outstanding),
      })
    : {};
  const errors = { ...serverErrors, ...localErrors };
  if (fileError) errors.file = fileError;

  useEffect(() => {
    if (focusRequest === 0) return;
    const target = formRef.current?.querySelector('[aria-invalid="true"], [role="alert"]');
    if (target) {
      target.scrollIntoView?.({ block: 'center', behavior: 'smooth' });
      target.focus?.({ preventScroll: true });
    }
  }, [focusRequest]);

  function resetOutcome() {
    idempotencyKey.current = null;
    setDuplicate(false);
    setFormMessage(null);
    setServerErrors({});
  }

  function pickCount(next) {
    resetOutcome();
    setCount(next);
    if (!amountTouched) setAmountText(formatAmountInput(selectedTotal(invoices, next)));
  }

  function handleSelectFile(selected) {
    resetOutcome();
    const problem = receiptFileError(selected);
    setFileError(problem);
    setFile(problem ? null : selected);
  }

  function handleRemoveFile() {
    resetOutcome();
    setFile(null);
    setFileError(null);
  }

  function field(setter) {
    return (value) => {
      resetOutcome();
      setter(value);
    };
  }

  function handleSubmit(event) {
    event.preventDefault();
    const found = receiptFormErrors({
      file,
      operationNumber,
      originAccount,
      holderName,
      amount,
      firstInvoiceOutstanding: moneyValue(invoices[0].outstanding),
    });
    if (Object.keys(found).length > 0 || fileError) {
      setShowErrors(true);
      setFocusRequest((value) => value + 1);
      return;
    }
    idempotencyKey.current ??= newIdempotencyKey();
    const submission = buildReceiptSubmission({
      file,
      invoiceIds: invoices.slice(0, effectiveCount).map((invoice) => invoice.id),
      amount,
      currency,
      operationNumber,
      originAccount,
      holderName,
      bankName,
      transferDate: today,
    });
    mutate(
      { submission, idempotencyKey: idempotencyKey.current },
      {
        onSuccess: () => onSubmitted(),
        onError: (error) => {
          const failure = receiptSubmissionFailure(error);
          setDuplicate(failure.duplicate);
          setServerErrors(failure.fieldErrors);
          setFormMessage(failure.message);
          setFocusRequest((value) => value + 1);
        },
      },
    );
  }

  const outcome = receiptOutcomeLines({ invoices, count: effectiveCount, amount, currency });
  const summaryLabel = effectiveCount === 1 ? '1 cuota' : `${effectiveCount} cuotas`;

  return (
    <form ref={formRef} onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
      <GlassCard className="flex flex-col gap-4 p-5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-heading text-base font-bold text-ink">Cuotas que cubrís</h2>
          <span className="inline-flex items-center gap-1.5 rounded-full bg-neutral-bg px-3 py-1 text-xs font-semibold text-ink-soft">
            <Clock className="size-3.5" aria-hidden="true" />
            Transferencia de hoy, {formatDate(today)}
          </span>
        </div>
        <InvoiceCoverageList invoices={invoices} underReview={underReview} count={effectiveCount} amount={amount} onPick={pickCount} />
        <p className="text-xs text-ink-faint">Si transferiste otro día, avisale a Secretaría.</p>
      </GlassCard>

      <GlassCard className="flex flex-col gap-4 p-5">
        <h2 className="font-heading text-base font-bold text-ink">Datos del comprobante</h2>
        <ReceiptDropzone
          file={file}
          error={errors.file}
          errorId="receipt-file-error"
          onSelect={handleSelectFile}
          onRemove={handleRemoveFile}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            id="receipt-operation-number"
            label="Número de operación"
            value={operationNumber}
            onChange={field(setOperationNumber)}
            error={errors.operationNumber}
            placeholder="Ej. 4821937"
            inputMode="numeric"
          />
          <TextField
            id="receipt-amount"
            label="Monto transferido"
            value={amountText}
            onChange={(value) => {
              resetOutcome();
              setAmountTouched(true);
              setAmountText(value);
            }}
            onBlur={() => {
              if (Number.isFinite(amount) && amount > 0) setAmountText(formatAmountInput(amount));
            }}
            error={errors.amount}
            placeholder="0,00"
            inputMode="decimal"
          />
          <TextField
            id="receipt-origin-account"
            label="CBU o alias de origen"
            value={originAccount}
            onChange={field(setOriginAccount)}
            error={errors.originAccount}
            placeholder="CBU de 22 números o alias"
          />
          <TextField
            id="receipt-holder-name"
            label="Titular de la cuenta de origen"
            value={holderName}
            onChange={field(setHolderName)}
            error={errors.holderName}
            placeholder="Nombre completo"
            autoComplete="name"
          />
          <div className="sm:col-span-2">
            {showBank ? (
              <TextField
                id="receipt-bank-name"
                label="Banco de origen (opcional)"
                value={bankName}
                onChange={field(setBankName)}
                placeholder="Ej. Banco Nación"
              />
            ) : (
              <Button type="button" variant="ghost" size="sm" onClick={() => setShowBank(true)}>
                + Agregar banco (opcional)
              </Button>
            )}
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <h3 className="text-sm font-bold text-ink">Qué va a pasar</h3>
          <ul className="flex flex-col gap-2">
            {outcome.map((line) => (
              <li key={line.text} className="grid grid-cols-[8px_minmax(0,1fr)] items-start gap-2.5 text-sm leading-snug text-ink-soft">
                <span aria-hidden="true" className={cn('mt-1.5 size-2 rounded-full', TONE_DOT[line.tone])} />
                <span>{line.text}</span>
              </li>
            ))}
          </ul>
        </div>

        {duplicate && (
          <div role="alert" className="flex flex-col gap-3 rounded-xl border border-danger/40 bg-danger-bg p-4">
            <div className="flex items-start gap-3">
              <AlertTriangle className="mt-0.5 size-5 shrink-0 text-danger" aria-hidden="true" />
              <div className="flex flex-col gap-0.5">
                <span className="text-sm font-bold text-ink">Ya tenemos registrada esa transferencia</span>
                <span className="text-sm text-ink-soft">
                  Mismo número de operación, fecha y monto. Si ya la enviaste, no hace falta repetirla.
                </span>
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="outline" size="sm" onClick={onViewReceipt}>
                Ver mi comprobante
              </Button>
              <Button type="button" variant="ghost" size="sm" onClick={() => setDuplicate(false)}>
                Revisar los datos
              </Button>
            </div>
          </div>
        )}

        {formMessage && (
          <p role="alert" className="rounded-xl border border-danger/40 bg-danger-bg p-4 text-sm text-ink">
            {formMessage}
          </p>
        )}
      </GlassCard>

      <GlassCard className="sticky bottom-4 z-10 flex items-center justify-between gap-4 p-4">
        <div className="flex min-w-0 flex-col">
          <span className="text-lg font-bold tabular-nums text-ink">
            {formatMoney({ amount: Number.isFinite(amount) ? amount.toFixed(2) : '0.00', currency })}
          </span>
          <span className="truncate text-xs text-ink-faint">
            {summaryLabel} · total elegido {formatMoney({ amount: total.toFixed(2), currency })}
          </span>
        </div>
        <Button type="submit" variant="gradient" loading={isPending}>
          {isPending ? 'Enviando…' : 'Enviar comprobante'}
        </Button>
      </GlassCard>
    </form>
  );
}
