import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { describeError } from '@/api/describeError';
import { useChangeSection, useWithdrawEnrollment } from '@/features/identity/hooks/useEnrollmentChanges';
import { describeOverlap, isInlineError } from '@/features/identity/lib/enrollmentChanges';

const WITHDRAW_DONE = {
  'withdraw-subject': 'Materia dada de baja',
  'withdraw-module': 'Módulo dado de baja',
  'withdraw-course': 'Curso dado de baja',
  'withdraw-program': 'Carrera dada de baja',
};

export function useEnrollmentChangeFlow(studentId) {
  const queryClient = useQueryClient();
  const withdraw = useWithdrawEnrollment(studentId);
  const change = useChangeSection(studentId);
  const [withdrawTarget, setWithdrawTarget] = useState(null);
  const [changeRow, setChangeRow] = useState(null);
  const [serverError, setServerError] = useState(null);
  const [announcement, setAnnouncement] = useState('');

  function refreshStale() {
    queryClient.invalidateQueries({ queryKey: ['program-enrollment'] });
    queryClient.invalidateQueries({ queryKey: ['course-enrollment'] });
  }

  function handleError(error, close) {
    if (isInlineError(error)) {
      setServerError(describeOverlap(error) ?? describeError(error).message);
      return;
    }
    toast.error(describeError(error).message);
    close();
    refreshStale();
  }

  function startWithdraw(target) {
    setServerError(null);
    setWithdrawTarget(target);
  }

  function startChange(row) {
    setServerError(null);
    setChangeRow(row);
  }

  function closeWithdraw() {
    setWithdrawTarget(null);
    setServerError(null);
  }

  function closeChange() {
    setChangeRow(null);
    setServerError(null);
  }

  function confirmWithdraw({ reason, effectiveDate }) {
    setServerError(null);
    withdraw.mutate(
      { kind: withdrawTarget.kind, id: withdrawTarget.id, reason, effectiveDate },
      {
        onSuccess: () => {
          const message = `${WITHDRAW_DONE[withdrawTarget.kind]}: ${withdrawTarget.name}`;
          toast.success(WITHDRAW_DONE[withdrawTarget.kind]);
          setAnnouncement(message);
          closeWithdraw();
        },
        onError: (error) => handleError(error, closeWithdraw),
      },
    );
  }

  function confirmChange(payload) {
    setServerError(null);
    change.mutate(payload, {
      onSuccess: () => {
        toast.success('Comisión cambiada');
        setAnnouncement(`Comisión cambiada: ${changeRow.subjectName}`);
        closeChange();
      },
      onError: (error) => handleError(error, closeChange),
    });
  }

  return {
    withdrawTarget,
    changeRow,
    serverError,
    announcement,
    withdrawPending: withdraw.isPending,
    changePending: change.isPending,
    startWithdraw,
    startChange,
    closeWithdraw,
    closeChange,
    confirmWithdraw,
    confirmChange,
    clearServerError: () => setServerError(null),
  };
}
