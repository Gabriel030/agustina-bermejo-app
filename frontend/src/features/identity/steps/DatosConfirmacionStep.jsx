import { Controller } from 'react-hook-form';
import { Input } from '@/components/ui/input';
import { DocumentField } from '@/components/DocumentField';
import { FormField, SelectField, fieldProps } from '@/features/academic-structure';
import { SEX_OPTIONS, ageOn, todayIso } from '@/features/identity/lib/studentRegistration';

export function DatosConfirmacionStep({ register, control, errors, values, onChange, isLoading, birthDate }) {
  function field(name) {
    if (register) return register(name);
    return {
      value: values?.[name] ?? '',
      onChange: (e) => onChange?.(name, e.target.value),
    };
  }

  const err = (name) => errors?.[name]?.message;
  const age = ageOn(birthDate);
  const minor = age !== null && age < 18 && age >= 0;

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-base font-semibold text-ink">Datos de confirmación</h2>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <FormField id="birthDate" label="Fecha de nacimiento" required error={err('birthDate')}>
          <Input
            type="date"
            max={todayIso()}
            {...field('birthDate')}
            {...fieldProps('birthDate', err('birthDate'), true)}
            disabled={isLoading}
          />
        </FormField>
        <Controller
          control={control}
          name="sex"
          render={({ field: sex }) => (
            <SelectField id="sex" label="Sexo" value={sex.value ?? ''} onChange={sex.onChange} options={SEX_OPTIONS} disabled={isLoading} />
          )}
        />
        <FormField id="nationality" label="Nacionalidad" error={err('nationality')}>
          <Input {...field('nationality')} {...fieldProps('nationality', err('nationality'))} placeholder="Argentina" disabled={isLoading} />
        </FormField>
      </div>
      <div aria-live="polite">
        {minor && (
          <p className="rounded-lg border border-accent/30 bg-accent-soft p-3 text-size-13 text-ink">
            Es menor de 18 años ({age}). En el próximo paso vas a cargar el tutor y un contacto de emergencia; son obligatorios.
          </p>
        )}
      </div>
      <div className="flex flex-col gap-1">
        <label className="text-size-13 font-medium text-ink">Documento <span className="text-danger">*</span></label>
        <Controller
          control={control}
          name="documentType"
          render={({ field: tipo }) => (
            <Controller
              control={control}
              name="dni"
              render={({ field: numero }) => (
                <DocumentField
                  type={tipo.value}
                  number={numero.value}
                  onTypeChange={tipo.onChange}
                  onNumberChange={numero.onChange}
                  onBlur={numero.onBlur}
                  error={err('dni')}
                  disabled={isLoading}
                />
              )}
            />
          )}
        />
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <div className="flex flex-col gap-1 sm:col-span-2">
          <label className="text-size-13 font-medium text-ink">Calle <span className="text-danger">*</span></label>
          <Input {...field('street')} placeholder="Av. Corrientes" disabled={isLoading} />
          {err('street') && <p className="text-size-12 text-danger">{err('street')}</p>}
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-size-13 font-medium text-ink">Número</label>
          <Input {...field('number')} placeholder="1234" disabled={isLoading} />
        </div>
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <label className="text-size-13 font-medium text-ink">
            Piso / Dpto <span className="text-muted-foreground font-normal">(opcional)</span>
          </label>
          <Input {...field('floor')} placeholder="3°B" disabled={isLoading} />
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-size-13 font-medium text-ink">Ciudad <span className="text-danger">*</span></label>
          <Input {...field('city')} placeholder="Buenos Aires" disabled={isLoading} />
          {err('city') && <p className="text-size-12 text-danger">{err('city')}</p>}
        </div>
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <label className="text-size-13 font-medium text-ink">Provincia</label>
          <Input {...field('state')} placeholder="CABA" disabled={isLoading} />
          {err('state') && <p className="text-size-12 text-danger">{err('state')}</p>}
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-size-13 font-medium text-ink">
            Código postal <span className="text-muted-foreground font-normal">(opcional)</span>
          </label>
          <Input {...field('postalCode')} placeholder="C1043" disabled={isLoading} />
        </div>
      </div>
      <div className="flex flex-col gap-1">
        <label className="text-size-13 font-medium text-ink">
          País <span className="text-muted-foreground font-normal">(opcional)</span>
        </label>
        <Input {...field('country')} placeholder="Argentina" disabled={isLoading} />
      </div>
    </div>
  );
}
