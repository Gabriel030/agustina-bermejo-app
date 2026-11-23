import { useRef, useState } from 'react';
import { toast } from 'sonner';
import { useQueryClient } from '@tanstack/react-query';
import { describeError } from '@/api/describeError';
import { ChangeSectionSheet } from '@/components/ChangeSectionSheet';
import { Notice } from '@/components/Notice';
import { useChangeMySection, useMisInscripciones } from '@/features/enrollment';
import { OWN_INLINE_ERROR_CODES, describeOverlap, isFinalError, isInlineError } from '@/lib/enrollmentChanges';

const CHANGEABLE_STATUSES = ['ENROLLED', 'IN_PROGRESS'];

export function ChangeMySectionFlow({ carreraId, onClose }) {
  const queryClient = useQueryClient();
  const enrollments = useMisInscripciones();
  const change = useChangeMySection();
  const [serverError, setServerError] = useState(null);
  const submitting = useRef(false);

  const rows = (enrollments.data ?? []).filter(
    (enrollment) => enrollment.programEnrollmentId === carreraId && CHANGEABLE_STATUSES.includes(enrollment.status),
  );

  function close() {
    setServerError(null);
    onClose();
  }

  function handleError(error) {
    if (isInlineError(error, OWN_INLINE_ERROR_CODES)) {
      setServerError({ code: error.code, message: describeOverlap(error) ?? describeError(error).message });
      return;
    }
    toast.error(describeError(error).message);
    queryClient.invalidateQueries({ queryKey: ['me', 'section-enrollments'] });
    close();
  }

  function submit(payload) {
    if (submitting.current) return;
    submitting.current = true;
    setServerError(null);
    change.mutate(payload, {
      onSettled: () => {
        submitting.current = false;
      },
      onSuccess: () => {
        toast.success('Comisión cambiada');
        close();
      },
      onError: handleError,
    });
  }

  return (
    <ChangeSectionSheet
      own
      showReason={false}
      rows={rows}
      rowsLoading={enrollments.isLoading || enrollments.isRefetching}
      rowsError={enrollments.isError}
      onRetryRows={() => enrollments.refetch()}
      staleTarget={serverError?.code === 'SECTION_FULL'}
      onClose={close}
      onSubmit={submit}
      pending={change.isPending}
      serverError={serverError?.message}
      canChooseAnother={!isFinalError(serverError?.code)}
      onChooseAnother={() => setServerError(null)}
      confirmNotice={
        <Notice tone="warning">
          Perdés tu lugar actual. Si la nueva comisión se llena, no podemos reservártela.
        </Notice>
      }
    />
  );
}
