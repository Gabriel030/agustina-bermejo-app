export function DatosConfirmacionStep({ register, errors, isLoading }) {
  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-base font-semibold text-ink">Datos de confirmación</h2>
      <div className="flex flex-col gap-1">
        <label className="text-[13px] font-medium text-ink">DNI</label>
        <input
          {...register('dni')}
          className="input"
          placeholder="12345678"
          disabled={isLoading}
        />
        {errors.dni && <p className="text-[12px] text-danger">{errors.dni.message}</p>}
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <div className="flex flex-col gap-1 sm:col-span-2">
          <label className="text-[13px] font-medium text-ink">Calle</label>
          <input
            {...register('street')}
            className="input"
            placeholder="Av. Corrientes"
            disabled={isLoading}
          />
          {errors.street && <p className="text-[12px] text-danger">{errors.street.message}</p>}
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-[13px] font-medium text-ink">Número</label>
          <input
            {...register('number')}
            className="input"
            placeholder="1234"
            disabled={isLoading}
          />
        </div>
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <label className="text-[13px] font-medium text-ink">
            Piso / Dpto <span className="text-muted-foreground font-normal">(opcional)</span>
          </label>
          <input
            {...register('floor')}
            className="input"
            placeholder="3°B"
            disabled={isLoading}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-[13px] font-medium text-ink">Ciudad</label>
          <input
            {...register('city')}
            className="input"
            placeholder="Buenos Aires"
            disabled={isLoading}
          />
          {errors.city && <p className="text-[12px] text-danger">{errors.city.message}</p>}
        </div>
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <label className="text-[13px] font-medium text-ink">Provincia</label>
          <input
            {...register('state')}
            className="input"
            placeholder="CABA"
            disabled={isLoading}
          />
          {errors.state && <p className="text-[12px] text-danger">{errors.state.message}</p>}
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-[13px] font-medium text-ink">
            Código postal <span className="text-muted-foreground font-normal">(opcional)</span>
          </label>
          <input
            {...register('postalCode')}
            className="input"
            placeholder="C1043"
            disabled={isLoading}
          />
        </div>
      </div>
      <div className="flex flex-col gap-1">
        <label className="text-[13px] font-medium text-ink">
          País <span className="text-muted-foreground font-normal">(opcional)</span>
        </label>
        <input
          {...register('country')}
          className="input"
          placeholder="Argentina"
          disabled={isLoading}
        />
      </div>
    </div>
  );
}
