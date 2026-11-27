import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { describeError } from '@/api/describeError';
import { DOMAIN_ERROR_MESSAGES } from '@/api/domainErrorMessages';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { StickyActionBar } from '@/components/StickyActionBar';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { NativeSelect } from '@/components/ui/native-select';
import { FormField, fieldProps } from '@/features/academic-structure';
import { useUpdateStudent } from '@/features/identity/hooks/useUpdateStudent';
import { SEX_LABEL } from '@/features/identity/lib/person';
import {
  sensitiveChanges,
  studentPersonalDataSchema,
  studentToFormValues,
  toUpdateStudentBody,
} from '@/features/identity/lib/studentPersonalData';

function ReadOnlyField({ label, value }) {
  if (!value) return null;
  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-size-12 font-medium uppercase tracking-wide text-ink-faint">{label}</span>
      <Input value={value} readOnly className="cursor-default" aria-label={label} />
    </div>
  );
}

function BeforeAfterRow({ label, before, after }) {
  return (
    <div className="grid grid-cols-[6rem_1fr] gap-x-3 gap-y-1 text-size-13">
      <span className="font-medium uppercase tracking-wide text-ink-faint">{label}</span>
      <div className="flex flex-col gap-0.5">
        <span className="text-ink-soft">
          Antes: <span className="break-all">{before || '—'}</span>
        </span>
        <span className="font-semibold text-ink">
          Después: <span className="break-all">{after}</span>
        </span>
      </div>
    </div>
  );
}

export function EditStudentPersonalDataForm({ legajo, readOnlyFields, onClose }) {
  const [pendingValues, setPendingValues] = useState(null);
  const updateStudent = useUpdateStudent(legajo.id);
  const emailLocked = legajo.emailEditable === false;
  const {
    register,
    handleSubmit,
    setError,
    getValues,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(studentPersonalDataSchema),
    mode: 'onTouched',
    defaultValues: studentToFormValues(legajo),
  });

  async function save(values) {
    try {
      await updateStudent.mutateAsync(toUpdateStudentBody(values));
      setPendingValues(null);
      toast.success('Datos actualizados');
      onClose();
    } catch (error) {
      setPendingValues(null);
      if (error?.code === 'STUDENT_DOCUMENT_ALREADY_EXISTS') {
        setError('documentNumber', {
          type: 'server',
          message: DOMAIN_ERROR_MESSAGES.STUDENT_DOCUMENT_ALREADY_EXISTS,
        });
        return;
      }
      toast.error(describeError(error).message);
    }
  }

  function submit(values) {
    const { emailChanged, documentChanged } = sensitiveChanges(legajo, values);
    if (emailChanged || documentChanged) {
      setPendingValues(values);
      return;
    }
    save(values);
  }

  const changes = pendingValues ? sensitiveChanges(legajo, pendingValues) : { emailChanged: false, documentChanged: false };

  return (
    <form noValidate onSubmit={handleSubmit(submit)} className="flex flex-col gap-4">
      <h3 className="text-size-11 font-semibold uppercase tracking-wider text-ink-faint">Información personal</h3>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <FormField id="edit-first-name" label="Nombre" required error={errors.firstName?.message}>
          <Input {...register('firstName')} {...fieldProps('edit-first-name', errors.firstName, true)} />
        </FormField>
        <FormField id="edit-last-name" label="Apellido" required error={errors.lastName?.message}>
          <Input {...register('lastName')} {...fieldProps('edit-last-name', errors.lastName, true)} />
        </FormField>
        <ReadOnlyField label="Legajo" value={readOnlyFields.legajo} />
        <FormField id="edit-document-number" label="DNI" required error={errors.documentNumber?.message}>
          <Input {...register('documentNumber')} {...fieldProps('edit-document-number', errors.documentNumber, true)} />
        </FormField>
        <FormField id="edit-birth-date" label="Fecha de nacimiento" error={errors.birthDate?.message}>
          <Input type="date" {...register('birthDate')} {...fieldProps('edit-birth-date', errors.birthDate, false)} />
        </FormField>
        <FormField id="edit-sex" label="Sexo / Género" error={errors.sex?.message}>
          <NativeSelect {...register('sex')} id="edit-sex">
            <option value="">Sin especificar</option>
            {Object.entries(SEX_LABEL).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </NativeSelect>
        </FormField>
        <FormField id="edit-nationality" label="Nacionalidad" error={errors.nationality?.message}>
          <Input {...register('nationality')} {...fieldProps('edit-nationality', errors.nationality, false)} />
        </FormField>
        <FormField id="edit-phone" label="Teléfono" error={errors.phone?.message}>
          <Input type="tel" {...register('phone')} {...fieldProps('edit-phone', errors.phone, false)} />
        </FormField>
        <FormField
          id="edit-email"
          label="Email"
          required
          error={errors.email?.message}
          hint={emailLocked ? 'Esta cuenta inicia sesión con Google: el email no se edita por acá.' : undefined}
        >
          <Input type="email" {...register('email')} {...fieldProps('edit-email', errors.email, true)} disabled={emailLocked} />
        </FormField>
        <ReadOnlyField label="Usuario" value={readOnlyFields.username} />
        <ReadOnlyField label="Estado" value={readOnlyFields.status} />
        <ReadOnlyField label="Onboarding" value={readOnlyFields.onboarding} />
      </div>

      <StickyActionBar summary="Editando datos personales" hint="Los cambios se aplican al guardar">
        <Button type="button" variant="outline" className="min-h-11" onClick={onClose} disabled={updateStudent.isPending}>
          Cancelar
        </Button>
        <Button type="submit" variant="primary" className="min-h-11" loading={updateStudent.isPending && !pendingValues}>
          Guardar cambios
        </Button>
      </StickyActionBar>

      <ConfirmActionDialog
        open={!!pendingValues}
        onOpenChange={(open) => !open && setPendingValues(null)}
        title="Confirmar cambios"
        description={
          changes.emailChanged
            ? 'El email nuevo queda sin verificar: le enviamos un enlace de verificación y avisamos al email anterior.'
            : 'Vas a cambiar el documento de la persona.'
        }
        confirmLabel="Confirmar y guardar"
        confirmVariant="primary"
        pending={updateStudent.isPending}
        onConfirm={() => save(pendingValues ?? getValues())}
      >
        <div className="flex flex-col gap-3">
          {changes.documentChanged && <BeforeAfterRow label="DNI" before={legajo.dni} after={pendingValues.documentNumber.trim()} />}
          {changes.emailChanged && <BeforeAfterRow label="Email" before={legajo.email} after={pendingValues.email.trim()} />}
        </div>
      </ConfirmActionDialog>
    </form>
  );
}
