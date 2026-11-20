import { Controller } from 'react-hook-form';
import { Input } from '@/components/ui/input';
import { FormField, SelectField, fieldProps } from '@/features/academic-structure';
import { EMERGENCY_RELATIONSHIPS, GUARDIAN_RELATIONSHIPS, ageOn } from '@/features/identity/lib/studentRegistration';

function TextField({ id, label, required, register, errors, placeholder, type, disabled, hint }) {
  const error = errors?.[id]?.message;
  return (
    <FormField id={id} label={label} required={required} error={error} hint={hint}>
      <Input type={type} {...register(id)} {...fieldProps(id, error, required)} placeholder={placeholder} disabled={disabled} />
    </FormField>
  );
}

function RelationshipField({ id, control, errors, options, disabled }) {
  return (
    <Controller
      control={control}
      name={id}
      render={({ field }) => (
        <SelectField
          id={id}
          label="Parentesco"
          required
          error={errors?.[id]?.message}
          value={field.value ?? ''}
          onChange={field.onChange}
          options={options}
          disabled={disabled}
        />
      )}
    />
  );
}

export function GuardianStep({ register, control, errors, isLoading, fullName, birthDate }) {
  return (
    <div className="flex flex-col gap-5">
      <h2 className="text-base font-semibold text-ink">Responsables</h2>
      <p role="status" className="rounded-lg border border-accent/30 bg-accent-soft p-3 text-size-13 text-ink">
        {fullName} tiene {ageOn(birthDate)} años. Tutor y contacto de emergencia son obligatorios. El tutor no tiene usuario ni acceso a la app.
      </p>
      {errors?.responsibles?.message && (
        <p role="alert" className="rounded-lg border border-danger/40 bg-danger-bg p-3 text-size-13 text-danger">
          {errors.responsibles.message}
        </p>
      )}
      <fieldset className="flex flex-col gap-3">
        <legend className="mb-1 text-size-12 font-semibold uppercase tracking-wide text-ink-faint">Tutor</legend>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <TextField id="guardianName" label="Nombre y apellido" required register={register} errors={errors} disabled={isLoading} />
          <TextField id="guardianDni" label="DNI" required register={register} errors={errors} placeholder="12345678" disabled={isLoading} />
          <RelationshipField id="guardianRelationship" control={control} errors={errors} options={GUARDIAN_RELATIONSHIPS} disabled={isLoading} />
          <TextField id="guardianPhone" label="Teléfono" required type="tel" register={register} errors={errors} disabled={isLoading} />
          <TextField id="guardianEmail" label="Email (opcional)" type="email" register={register} errors={errors} disabled={isLoading} />
        </div>
      </fieldset>
      <fieldset className="flex flex-col gap-3">
        <legend className="mb-1 text-size-12 font-semibold uppercase tracking-wide text-ink-faint">Contacto de emergencia</legend>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <TextField id="emergencyName" label="Nombre y apellido" required register={register} errors={errors} disabled={isLoading} />
          <RelationshipField id="emergencyRelationship" control={control} errors={errors} options={EMERGENCY_RELATIONSHIPS} disabled={isLoading} />
          <TextField id="emergencyPhone" label="Teléfono" required type="tel" register={register} errors={errors} disabled={isLoading} />
        </div>
      </fieldset>
    </div>
  );
}
