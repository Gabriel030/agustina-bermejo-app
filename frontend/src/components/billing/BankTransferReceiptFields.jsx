import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { ReceiptDropzone } from '@/components/billing/ReceiptDropzone';

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

export function BankTransferReceiptFields({ fields, errors, holderHint = null }) {
  return (
    <>
      <ReceiptDropzone
        file={fields.file}
        error={errors.file}
        errorId="receipt-file-error"
        onSelect={fields.selectFile}
        onRemove={fields.removeFile}
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          id="receipt-operation-number"
          label="Número de operación"
          value={fields.operationNumber}
          onChange={fields.setOperationNumber}
          error={errors.operationNumber}
          placeholder="Ej. 4821937"
          inputMode="numeric"
        />
        <TextField
          id="receipt-amount"
          label="Monto transferido"
          value={fields.amountText}
          onChange={fields.editAmount}
          onBlur={fields.normalizeAmount}
          error={errors.amount}
          placeholder="0,00"
          inputMode="decimal"
        />
        <TextField
          id="receipt-origin-account"
          label="CBU o alias de origen"
          value={fields.originAccount}
          onChange={fields.setOriginAccount}
          error={errors.originAccount}
          placeholder="CBU de 22 números o alias"
        />
        <TextField
          id="receipt-holder-name"
          label="Titular de la cuenta de origen"
          value={fields.holderName}
          onChange={fields.setHolderName}
          error={errors.holderName}
          placeholder="Nombre completo"
          autoComplete="name"
        />
        {holderHint && <p className="text-xs text-ink-faint sm:col-span-2">{holderHint}</p>}
        <div className="sm:col-span-2">
          {fields.showBank ? (
            <TextField
              id="receipt-bank-name"
              label="Banco de origen (opcional)"
              value={fields.bankName}
              onChange={fields.setBankName}
              placeholder="Ej. Banco Nación"
            />
          ) : (
            <Button type="button" variant="ghost" size="sm" onClick={fields.revealBank}>
              + Agregar banco (opcional)
            </Button>
          )}
        </div>
      </div>
    </>
  );
}
