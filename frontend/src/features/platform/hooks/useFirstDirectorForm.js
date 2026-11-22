import { useState } from 'react';
import { EMPTY_FORM, firstInvalidField, missingFieldLabels, validateForm } from '@/features/platform/lib/firstDirectorForm';

export function useFirstDirectorForm(lockedInstitution) {
  const [form, setForm] = useState(EMPTY_FORM);
  const [touched, setTouched] = useState({});
  const [attempted, setAttempted] = useState(false);
  const [serverErrors, setServerErrors] = useState({});

  const effectiveForm = lockedInstitution ? { ...form, institutionName: lockedInstitution } : form;
  const errors = { ...validateForm(effectiveForm), ...serverErrors };
  const shownErrors = Object.fromEntries(
    Object.keys(errors).filter((field) => errors[field] && (attempted || touched[field] || serverErrors[field])).map((field) => [field, errors[field]]),
  );
  const missing = missingFieldLabels(errors);

  function clearServerError(field) {
    setServerErrors((previous) => (previous[field] ? { ...previous, [field]: undefined } : previous));
  }

  function changeField(field, value) {
    setForm((previous) => ({ ...previous, [field]: value }));
    clearServerError(field);
  }

  function changeDocumentType(value) {
    setForm((previous) => ({ ...previous, documentType: value }));
    clearServerError('documentNumber');
  }

  function blurField(field) {
    setTouched((previous) => (previous[field] ? previous : { ...previous, [field]: true }));
  }

  function markAttempted() {
    setAttempted(true);
    return firstInvalidField(errors);
  }

  function setServerFieldError(field, message) {
    setServerErrors({ [field]: message });
  }

  return {
    form,
    effectiveForm,
    shownErrors,
    missing,
    isValid: missing.length === 0,
    changeField,
    changeDocumentType,
    blurField,
    markAttempted,
    setServerFieldError,
  };
}
