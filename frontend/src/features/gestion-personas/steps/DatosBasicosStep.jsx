import { Input } from '@/components/ui/input';

export function DatosBasicosStep({ register, errors, isLoading }) {
  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-base font-semibold text-ink">Datos básicos</h2>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <label className="text-[13px] font-medium text-ink">Nombre</label>
          <Input
            {...register('nombre')}
            placeholder="Juan"
            disabled={isLoading}
          />
          {errors.nombre && <p className="text-[12px] text-danger">{errors.nombre.message}</p>}
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-[13px] font-medium text-ink">Apellido</label>
          <Input
            {...register('apellido')}
            placeholder="Pérez"
            disabled={isLoading}
          />
          {errors.apellido && <p className="text-[12px] text-danger">{errors.apellido.message}</p>}
        </div>
      </div>
      <div className="flex flex-col gap-1">
        <label className="text-[13px] font-medium text-ink">Email</label>
        <Input
          {...register('email')}
          type="email"
          placeholder="juan.perez@email.com"
          disabled={isLoading}
        />
        {errors.email && <p className="text-[12px] text-danger">{errors.email.message}</p>}
      </div>
      <div className="flex flex-col gap-1">
        <label className="text-[13px] font-medium text-ink">
          Teléfono <span className="text-muted-foreground font-normal">(opcional)</span>
        </label>
        <Input
          {...register('telefono')}
          type="tel"
          placeholder="1122334455"
          disabled={isLoading}
        />
      </div>
    </div>
  );
}
