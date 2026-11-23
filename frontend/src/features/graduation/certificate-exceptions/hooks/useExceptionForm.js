import { useState } from 'react';
import { hasFormErrors, requestFormErrors } from '@/features/graduation/certificate-exceptions/lib/certificateExceptions';

const EMPTY_VALUES = { organismKind: '', organismReference: '', reason: '' };

export function useExceptionForm() {
  const [values, setValues] = useState(EMPTY_VALUES);
  const [touched, setTouched] = useState({});

  const allErrors = requestFormErrors(values);
  const visibleErrors = Object.fromEntries(Object.entries(allErrors).map(([field, message]) => [field, touched[field] ? message : '']));

  return {
    values,
    errors: visibleErrors,
    isValid: !hasFormErrors(allErrors),
    onChange: (field, value) => {
      setValues((current) => ({ ...current, [field]: value }));
      setTouched((current) => ({ ...current, [field]: true }));
    },
    onBlur: (field) => setTouched((current) => ({ ...current, [field]: true })),
  };
}
