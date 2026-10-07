import { Input } from '@/components/ui/input';

export function DatosConfirmacionStep({ register, errors, values, onChange, isLoading }) {
  function field(name) {
    if (register) return register(name);
    return {
      value: values?.[name] ?? '',
      onChange: (e) => onChange?.(name, e.target.value),
    };
  }

  const err = (name) => errors?.[name]?.message;

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-base font-semibold text-ink">Datos de confirmación</h2>
      <div className="flex flex-col gap-1">
        <label className="text-[13px] font-medium text-ink">DNI <span className="text-danger">*</span></label>
        <Input {...field('dni')} placeholder="12345678" disabled={isLoading} />
        {err('dni') && <p className="text-[12px] text-danger">{err('dni')}</p>}
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <div className="flex flex-col gap-1 sm:col-span-2">
          <label className="text-[13px] font-medium text-ink">Calle <span className="text-danger">*</span></label>
          <Input {...field('street')} placeholder="Av. Corrientes" disabled={isLoading} />
          {err('street') && <p className="text-[12px] text-danger">{err('street')}</p>}
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-[13px] font-medium text-ink">Número</label>
          <Input {...field('number')} placeholder="1234" disabled={isLoading} />
        </div>
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <label className="text-[13px] font-medium text-ink">
            Piso / Dpto <span className="text-muted-foreground font-normal">(opcional)</span>
          </label>
          <Input {...field('floor')} placeholder="3°B" disabled={isLoading} />
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-[13px] font-medium text-ink">Ciudad <span className="text-danger">*</span></label>
          <Input {...field('city')} placeholder="Buenos Aires" disabled={isLoading} />
          {err('city') && <p className="text-[12px] text-danger">{err('city')}</p>}
        </div>
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <label className="text-[13px] font-medium text-ink">Provincia</label>
          <Input {...field('state')} placeholder="CABA" disabled={isLoading} />
          {err('state') && <p className="text-[12px] text-danger">{err('state')}</p>}
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-[13px] font-medium text-ink">
            Código postal <span className="text-muted-foreground font-normal">(opcional)</span>
          </label>
          <Input {...field('postalCode')} placeholder="C1043" disabled={isLoading} />
        </div>
      </div>
      <div className="flex flex-col gap-1">
        <label className="text-[13px] font-medium text-ink">
          País <span className="text-muted-foreground font-normal">(opcional)</span>
        </label>
        <Input {...field('country')} placeholder="Argentina" disabled={isLoading} />
      </div>
    </div>
  );
}
