export function firstDirectorFieldAria(id, error, hasHint) {
  const describedBy = error ? `${id}-error` : hasHint ? `${id}-hint` : undefined;
  return { id, 'aria-invalid': !!error, 'aria-describedby': describedBy };
}
