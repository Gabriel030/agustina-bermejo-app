import { useState } from 'react';
import { toast } from 'sonner';
import { useQueryClient } from '@tanstack/react-query';
import { describeError } from '@/api/describeError';
import { ChangeSectionSheet } from '@/components/ChangeSectionSheet';
import { Notice } from '@/components/Notice';
import { useChangeMySection, useMisInscripciones } from '@/features/enrollment';

const CHANGEABLE_STATUSES = ['ENROLLED', 'IN_PROGRESS'];
const INLINE_ERROR_CODES = [
  'SECTION_FULL',
  'SECTION_SCHEDULE_OVERLAP',
  'SECTION_NOT_OPEN_FOR_ENROLLMENT',
  'INVALID_SECTION_CHANGE',
  'SECTION_HAS_RECORDS',
  'ENROLLMENT_WINDOW_CLOSED',
];
const FINAL_ERROR_CODES = ['SECTION_HAS_RECORDS', 'ENROLLMENT_WINDOW_CLOSED'];

export function ChangeMySectionFlow({ carreraId, onClose }) {
  const queryClient = useQueryClient();
  const enrollments = useMisInscripciones();
  const change = useChangeMySection();
  const [serverError, setServerError] = useState(null);

  const rows = (enrollments.data ?? []).filter(
    (enrollment) => enrollment.programEnrollmentId === carreraId && CHANGEABLE_STATUSES.includes(enrollment.status),
  );

  function close() {
    setServerError(null);
    onClose();
  }

  function handleError(error) {
    if (INLINE_ERROR_CODES.includes(error?.code)) {
      setServerError({ code: error.code, message: describeError(error).message });
      return;
    }
    toast.error(describeError(error).message);
    queryClient.invalidateQueries({ queryKey: ['me', 'section-enrollments'] });
    close();
  }

  function submit(payload) {
    setServerError(null);
    change.mutate(payload, {
      onSuccess: () => {
        toast.success('Comisión cambiada');
        close();
      },
      onError: handleError,
    });
  }

  if (enrollments.isLoading) return null;

  return (
    <ChangeSectionSheet
      own
      showReason={false}
      rows={rows}
      onClose={close}
      onSubmit={submit}
      pending={change.isPending}
      serverError={serverError?.message}
      canChooseAnother={!FINAL_ERROR_CODES.includes(serverError?.code)}
      onChooseAnother={() => setServerError(null)}
      confirmNotice={
        <Notice tone="warning">
          Perdés tu lugar actual. Si la nueva comisión se llena, no podemos reservártela.
        </Notice>
      }
    />
  );
}
