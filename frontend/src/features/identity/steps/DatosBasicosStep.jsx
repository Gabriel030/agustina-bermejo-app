import { Input } from '@/components/ui/input';
import { FormField, fieldProps } from '@/features/academic-structure';

export function DatosBasicosStep({ register, errors, isLoading, locked }) {
  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-base font-semibold text-ink">Datos básicos</h2>
      {locked && (
        <p className="text-size-12 text-ink-faint">
          Estos datos ya quedaron registrados. Seguí con los documentos para completar el alta.
        </p>
      )}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <FormField id="student-first-name" label="Nombre" required error={errors.nombre?.message}>
          <Input
            {...register('nombre')}
            {...fieldProps('student-first-name', errors.nombre, true)}
            placeholder="Juan"
            disabled={isLoading}
            readOnly={locked}
          />
        </FormField>
        <FormField id="student-last-name" label="Apellido" required error={errors.apellido?.message}>
          <Input
            {...register('apellido')}
            {...fieldProps('student-last-name', errors.apellido, true)}
            placeholder="Pérez"
            disabled={isLoading}
            readOnly={locked}
          />
        </FormField>
      </div>
      <FormField id="student-email" label="Email" required error={errors.email?.message}>
        <Input
          {...register('email')}
          {...fieldProps('student-email', errors.email, true)}
          type="email"
          placeholder="juan.perez@email.com"
          disabled={isLoading}
          readOnly={locked}
        />
      </FormField>
      <FormField id="student-phone" label="Teléfono (opcional)">
        <Input
          {...register('telefono')}
          id="student-phone"
          type="tel"
          placeholder="1122334455"
          disabled={isLoading}
          readOnly={locked}
        />
      </FormField>
    </div>
  );
}
