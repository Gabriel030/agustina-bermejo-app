import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { cn } from '@/lib/utils';
import { formatCertificateCode, normalizeCertificateCode } from '@/features/graduation/certificates/lib/certificateCode';

export function VerifyAnotherCode({ onSubmit, disabled = false }) {
  const [open, setOpen] = useState(false);
  const [value, setValue] = useState('');
  const normalized = normalizeCertificateCode(value);

  function submit(event) {
    event.preventDefault();
    if (!normalized || disabled) return;
    onSubmit(normalized);
    setOpen(false);
    setValue('');
  }

  return (
    <div className="flex flex-col gap-2.5 px-4 pt-1.5 pb-[18px] sm:px-5">
      <Button
        variant="text"
        size="inline"
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
        className="flex min-h-11 items-center gap-1.5 self-start font-bold text-brand"
        >
        Verificar otro código
        <ChevronDown className={cn('size-5 transition-transform', open && 'rotate-180')} aria-hidden="true" />
      </Button>
      {open && (
        <>
          <form onSubmit={submit} className="flex flex-wrap gap-2.5">
            <Label htmlFor="certificate-code" className="sr-only">
              Código del documento
            </Label>
            <Input
              id="certificate-code"
              type="text"
              autoFocus
              value={value}
              onChange={(event) => setValue(formatCertificateCode(event.target.value))}
              placeholder="Ej. 7QK3M-9XH2V-BN4T8-RGJDA-1B2C3X"
              autoComplete="off"
              autoCapitalize="characters"
              spellCheck={false}
              className="h-12 min-w-0 flex-[1_1_200px] bg-dialog-bg px-3.5 tracking-[0.06em]"
            />
            <Button type="submit" variant="gradient" disabled={!normalized || disabled} className="h-12 px-[18px]">
              Verificar
            </Button>
          </form>
          <p className="text-[13px] text-ink-faint">Está impreso debajo del QR. Con o sin guiones.</p>
        </>
      )}
    </div>
  );
}
