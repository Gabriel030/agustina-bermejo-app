import { useId, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { normalizeCertificateCode } from '@/features/graduation/certificates/lib/certificateCode';

export function VerifyAnotherCode({ onSubmit, disabled = false, label = 'Código de verificación', primary = false }) {
  const inputId = useId();
  const [value, setValue] = useState('');
  const normalized = normalizeCertificateCode(value);

  function submit(event) {
    event.preventDefault();
    if (!normalized || disabled) return;
    onSubmit(normalized);
    setValue('');
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-2.5 px-5 py-4 sm:px-[22px]">
      <Label htmlFor={inputId} className="text-size-13 font-semibold">
        {label}
      </Label>
      <Input
        id={inputId}
        type="text"
        value={value}
        onChange={(event) => setValue(event.target.value)}
        placeholder="Código impreso debajo del QR"
        autoComplete="off"
        autoCapitalize="characters"
        spellCheck={false}
        className="h-11 text-size-15 tracking-[0.06em] uppercase placeholder:tracking-normal placeholder:normal-case"
      />
      <Button type="submit" variant={primary ? 'primary' : 'outline'} disabled={!normalized || disabled} className="h-11 px-[18px] text-size-14">
        Verificar código
      </Button>
    </form>
  );
}
