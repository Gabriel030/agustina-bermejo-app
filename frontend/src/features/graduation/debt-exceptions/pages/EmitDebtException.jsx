import { useState } from 'react';
import { toast } from 'sonner';
import { Info } from 'lucide-react';
import { apiFetchBlob } from '@/api/client';
import { describeError } from '@/api/describeError';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Notice } from '@/components/Notice';
import { ChoiceChip } from '@/components/ChoiceChip';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { StudentPicker } from '@/features/graduation/debt-exceptions/components/StudentPicker';
import { IssuedDebtException } from '@/features/graduation/debt-exceptions/components/IssuedDebtException';
import { useIssueDebtException } from '@/features/graduation/debt-exceptions/hooks/useDebtExceptions';
import { saveBlob } from '@/features/graduation/certificates/lib/saveBlob';
import {
  DEFAULT_DOCUMENT_TYPE,
  DEFAULT_VALIDITY_DAYS,
  DOCUMENT_TYPES,
  QUICK_VALIDITY_DAYS,
  REASON_MAX_LENGTH,
  REASON_MIN_LENGTH,
  VALIDITY_MAX_DAYS,
  VALIDITY_MIN_DAYS,
  buildRequest,
  canSubmit,
  isReasonValid,
  reasonLength,
  sanitizeReason,
  validityError,
} from '@/features/graduation/debt-exceptions/lib/debtException';

const FIELD_LABEL = 'text-size-12 font-semibold text-ink-soft';

function initialForm() {
  return { student: null, documentType: DEFAULT_DOCUMENT_TYPE, reason: '', validityDays: DEFAULT_VALIDITY_DAYS };
}

function ReasonField({ value, onChange, disabled }) {
  const length = reasonLength(value);
  const valid = isReasonValid(value);
  return (
    <div className="flex flex-col gap-2">
      <Label htmlFor="debt-exception-reason" className={FIELD_LABEL}>
        Motivo <span className="font-normal text-ink-faint">(obligatorio, mínimo {REASON_MIN_LENGTH} caracteres)</span>
      </Label>
      <Textarea
        id="debt-exception-reason"
        rows={3}
        className="min-h-24"
        value={value}
        maxLength={REASON_MAX_LENGTH}
        disabled={disabled}
        aria-describedby="debt-exception-reason-count"
        onChange={(event) => onChange(sanitizeReason(event.target.value))}
      />
      <span id="debt-exception-reason-count" className={valid ? 'text-size-12 text-success' : 'text-size-12 text-ink-faint'}>
        {length}/{REASON_MIN_LENGTH}
      </span>
    </div>
  );
}

function ValidityField({ value, onChange, disabled }) {
  const [touched, setTouched] = useState(false);
  const error = touched ? validityError(value) : null;
  return (
    <div className="flex flex-col gap-2">
      <Label htmlFor="debt-exception-days" className={FIELD_LABEL}>
        Vence en <span className="font-normal text-ink-faint">(de {VALIDITY_MIN_DAYS} a {VALIDITY_MAX_DAYS} días)</span>
      </Label>
      <div className="flex flex-wrap items-center gap-2">
        <Input
          id="debt-exception-days"
          inputMode="numeric"
          className="min-h-11 w-24"
          value={value}
          disabled={disabled}
          aria-invalid={error ? 'true' : undefined}
          aria-describedby={error ? 'debt-exception-days-error' : undefined}
          onBlur={() => setTouched(true)}
          onChange={(event) => onChange(event.target.value)}
        />
        <span className="text-size-13 text-ink-soft">días</span>
        {QUICK_VALIDITY_DAYS.map((days) => (
          <ChoiceChip
            key={days}
            size="lg"
            className="min-h-11"
            selected={value === String(days)}
            disabled={disabled}
            onClick={() => onChange(String(days))}
          >
            {days} días
          </ChoiceChip>
        ))}
      </div>
      {error && (
        <p id="debt-exception-days-error" role="alert" className="text-size-12 text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

function DeniedNotice() {
  return (
    <Notice tone="warning" role="status" title="No tenés permiso para emitir por excepción.">
      Pedíselo a un Director.
    </Notice>
  );
}

export function EmitDebtException() {
  const [form, setForm] = useState(initialForm);
  const [issued, setIssued] = useState(null);
  const [denied, setDenied] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const issue = useIssueDebtException();

  const patch = (changes) => setForm((current) => ({ ...current, ...changes }));
  const ready = canSubmit(form);

  async function submit(event) {
    event.preventDefault();
    if (!ready) return;
    try {
      setIssued(await issue.mutateAsync(buildRequest(form)));
    } catch (error) {
      if (error?.status === 403) setDenied(true);
      else toast.error(describeError(error).message);
    }
  }

  async function download() {
    setDownloading(true);
    try {
      const { blob, filename } = await apiFetchBlob(`/document-debt-exceptions/${issued.id}/pdf`);
      saveBlob(blob, filename);
    } catch (error) {
      if (error?.status === 403) setDenied(true);
      else toast.error(describeError(error).message);
    } finally {
      setDownloading(false);
    }
  }

  function issueAnother() {
    issue.reset();
    setIssued(null);
    setForm(initialForm());
  }

  return (
    <PageContainer title="Emitir por excepción" description="Administración · Constancias">
      <GlassCard className="flex max-w-3xl flex-col gap-5 p-4 md:p-6">
        <p className="max-w-prose text-size-13-5 text-ink-soft">
          Para alumnos que no están al día con los pagos. Disponible solo para Administrativo y Director.
        </p>
        {denied ? (
          <DeniedNotice />
        ) : issued ? (
          <IssuedDebtException exception={issued} downloading={downloading} onDownload={download} onIssueAnother={issueAnother} />
        ) : (
          <form className="flex flex-col gap-5" onSubmit={submit} noValidate>
            <StudentPicker student={form.student} onChange={(student) => patch({ student })} disabled={issue.isPending} />
            <div className="flex flex-col gap-2">
              <Label htmlFor="debt-exception-type" className={FIELD_LABEL}>
                Constancia
              </Label>
              <Select value={form.documentType} onValueChange={(documentType) => patch({ documentType })} disabled={issue.isPending}>
                <SelectTrigger id="debt-exception-type" className="min-h-11 w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {DOCUMENT_TYPES.map((type) => (
                    <SelectItem key={type.value} value={type.value} className="min-h-11">
                      {type.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <ReasonField value={form.reason} onChange={(reason) => patch({ reason })} disabled={issue.isPending} />
            <ValidityField value={form.validityDays} onChange={(validityDays) => patch({ validityDays })} disabled={issue.isPending} />
            <Notice tone="neutral" role="note" className="flex items-start gap-2">
              <Info className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              <span>Se puede usar una sola vez. Queda registrada de forma destacada en Auditoría.</span>
            </Notice>
            <div className="flex flex-col gap-2 sm:flex-row">
              <Button type="submit" variant="primary" className="min-h-11" disabled={!ready} loading={issue.isPending}>
                Emitir por excepción
              </Button>
            </div>
          </form>
        )}
      </GlassCard>
    </PageContainer>
  );
}
