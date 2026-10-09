import { useMemo } from 'react';
import { useMisSolicitudesCarrera } from '@/features/enrollment';
import { isAdmissionInvoiceActionable, isAdmissionInvoicePending } from '@/lib/admissionInvoice';

export function useAdmissionInvoicesPending() {
  const { data, isLoading, isError } = useMisSolicitudesCarrera();
  const pendientes = useMemo(
    () =>
      (data ?? [])
        .filter(isAdmissionInvoicePending)
        .sort(
          (a, b) =>
            Number(isAdmissionInvoiceActionable(b)) - Number(isAdmissionInvoiceActionable(a)) ||
            String(a.admissionInvoice.dueDate).localeCompare(String(b.admissionInvoice.dueDate)),
        ),
    [data],
  );
  return { pendientes, isLoading, isError };
}
