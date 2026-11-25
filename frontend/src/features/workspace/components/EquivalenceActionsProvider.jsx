import { useMemo, useRef, useState } from 'react';
import { useEquivalenceAccess } from '@/features/enrollment';
import { EquivalenceRecordDialog } from '@/features/workspace/components/EquivalenceRecordDialog';
import { EquivalenceVoidDialog } from '@/features/workspace/components/EquivalenceVoidDialog';
import { EquivalenceActionsContext } from '@/features/workspace/hooks/EquivalenceActionsContext';
import { EQUIVALENCES_SECTION_ID } from '@/features/workspace/lib/subjectEquivalences';
import { useActiveRole } from '@/hooks/useActiveRole';

const ROLES_WITH_ACTIONS = ['administrativo', 'director'];

export function EquivalenceActionsProvider({ studentId, programEnrollmentId, children }) {
  const { roleBase } = useActiveRole();
  const access = useEquivalenceAccess();
  const [recordRequest, setRecordRequest] = useState(null);
  const [voidRequest, setVoidRequest] = useState(null);
  const triggerRef = useRef(null);
  const visible = ROLES_WITH_ACTIONS.includes(roleBase);

  const value = useMemo(
    () => ({
      visible,
      canRecord: access.record,
      canVoid: access.void,
      recordDeniedReason: access.record ? null : 'No tenés permiso para cargar equivalencias',
      voidDeniedReason: access.void ? null : 'No tenés permiso para anular equivalencias',
      openRecord: (subjectId, trigger) => {
        triggerRef.current = trigger ?? document.activeElement;
        setRecordRequest({ subjectId: subjectId ?? null });
      },
      openVoid: (equivalence, trigger) => {
        triggerRef.current = trigger ?? document.activeElement;
        setVoidRequest({ equivalence });
      },
    }),
    [visible, access.record, access.void],
  );

  function restoreFocus(event) {
    event.preventDefault();
    const trigger = triggerRef.current;
    trigger?.focus?.();
    if (!trigger || document.activeElement !== trigger) document.getElementById(EQUIVALENCES_SECTION_ID)?.focus();
  }

  return (
    <EquivalenceActionsContext.Provider value={value}>
      {children}
      {visible && recordRequest && (
        <EquivalenceRecordDialog
          request={recordRequest}
          studentId={studentId}
          programEnrollmentId={programEnrollmentId}
          onClose={() => setRecordRequest(null)}
          onCloseAutoFocus={restoreFocus}
        />
      )}
      {visible && voidRequest && (
        <EquivalenceVoidDialog
          request={voidRequest}
          programEnrollmentId={programEnrollmentId}
          onClose={() => setVoidRequest(null)}
          onCloseAutoFocus={restoreFocus}
        />
      )}
    </EquivalenceActionsContext.Provider>
  );
}
