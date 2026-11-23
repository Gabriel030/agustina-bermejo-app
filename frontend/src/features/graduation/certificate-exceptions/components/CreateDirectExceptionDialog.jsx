import { useMemo, useState } from 'react';
import { toast } from 'sonner';
import { describeError } from '@/api/describeError';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select';
import { Notice } from '@/components/Notice';
import { formatMoney } from '@/lib/utils';
import { ResponsiveFormDialog } from '@/features/graduation/certificate-exceptions/components/ResponsiveFormDialog';
import { OrganismFields, StudentDebtContext } from '@/features/graduation/certificate-exceptions/components/ExceptionFormFields';
import { useExceptionForm } from '@/features/graduation/certificate-exceptions/hooks/useExceptionForm';
import { useCreateDirectCertificateException } from '@/features/graduation/certificate-exceptions/hooks/useCertificateExceptions';
import { candidateKey, indebtedCandidates } from '@/features/graduation/certificate-exceptions/lib/certificateExceptions';

const ALREADY_OPEN_MESSAGE = 'Ya hay una excepción abierta para este alumno. Revisá la bandeja antes de crear otra.';

export function CreateDirectExceptionDialog({ requests, onClose }) {
  const candidates = useMemo(() => indebtedCandidates(requests), [requests]);
  const form = useExceptionForm();
  const create = useCreateDirectCertificateException();
  const [enrollmentId, setEnrollmentId] = useState('');
  const [studentTouched, setStudentTouched] = useState(false);
  const [serverError, setServerError] = useState('');
  const selected = candidates.find((candidate) => candidateKey(candidate) === enrollmentId);

  async function submit(event) {
    event.preventDefault();
    if (!form.isValid || !selected) return;
    setServerError('');
    try {
      await create.mutateAsync({
        enrollmentId: selected.enrollmentId,
        kind: selected.kind,
        organismKind: form.values.organismKind,
        organismReference: form.values.organismReference.trim(),
        reason: form.values.reason.trim(),
      });
      toast.success('Excepción creada y aprobada');
      onClose();
    } catch (error) {
      setServerError(error?.code === 'CERTIFICATE_EXCEPTION_ALREADY_OPEN' ? ALREADY_OPEN_MESSAGE : describeError(error).message);
    }
  }

  return (
    <ResponsiveFormDialog
      title="Crear excepción"
      description={`Nace aprobada, sin pedir autorización. Vale 7 días y se usa una sola vez.`}
      pending={create.isPending}
      onClose={onClose}
    >
      <form className="flex flex-col gap-3" onSubmit={submit} noValidate>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="direct-exception-student">Alumno con deuda</Label>
          <NativeSelect
            id="direct-exception-student"
            className="[&_select]:h-11 md:[&_select]:h-8"
            value={enrollmentId}
            required
            aria-required="true"
            aria-invalid={studentTouched && !selected}
            aria-describedby={studentTouched && !selected ? 'direct-exception-student-error' : undefined}
            onChange={(event) => {
              setEnrollmentId(event.target.value);
              setStudentTouched(true);
            }}
            onBlur={() => setStudentTouched(true)}
          >
            <NativeSelectOption value="">Elegí un alumno</NativeSelectOption>
            {candidates.map((candidate) => (
              <NativeSelectOption key={candidateKey(candidate)} value={candidateKey(candidate)}>
                {`${candidate.studentName} · ${formatMoney(candidate.totalDue)}`}
              </NativeSelectOption>
            ))}
          </NativeSelect>
          {studentTouched && !selected && (
            <p id="direct-exception-student-error" className="text-size-12 font-semibold text-danger">
              Elegí el alumno.
            </p>
          )}
        </div>
        {selected && (
          <StudentDebtContext
            studentName={selected.studentName}
            programName={selected.programName}
            totalDue={selected.totalDue}
            overdueInstallments={selected.overdueInstallments}
          />
        )}
        <OrganismFields values={form.values} errors={form.errors} onChange={form.onChange} onBlur={form.onBlur} />
        {serverError && (
          <Notice tone="danger" role="alert">
            {serverError}
          </Notice>
        )}
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" className="min-h-11 md:min-h-0" disabled={create.isPending} onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" variant="primary" className="min-h-11 md:min-h-0" disabled={!form.isValid || !selected || create.isPending}>
            {create.isPending ? 'Creando…' : 'Crear y aprobar'}
          </Button>
        </div>
      </form>
    </ResponsiveFormDialog>
  );
}
