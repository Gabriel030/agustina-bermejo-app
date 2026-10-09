export const DOCUMENT_TYPES = [
  { value: 'DNI', label: 'DNI', placeholder: '12345678', hint: 'El DNI tiene 7 u 8 números, sin puntos ni espacios' },
  { value: 'PASSPORT', label: 'Pasaporte', placeholder: 'AB123456', hint: 'El pasaporte tiene entre 4 y 20 letras o números, sin espacios ni guiones' },
  { value: 'OTHER', label: 'Otro', placeholder: 'X1234567', hint: 'El documento tiene entre 4 y 20 letras o números, sin espacios ni guiones' },
];

const FORMATS = {
  DNI: /^\d{7,8}$/,
  PASSPORT: /^[A-Za-z0-9]{4,20}$/,
  OTHER: /^[A-Za-z0-9]{4,20}$/,
};

export function documentTypeOf(value) {
  return DOCUMENT_TYPES.find((tipo) => tipo.value === value) ?? DOCUMENT_TYPES[0];
}

export function documentLabel(value) {
  return documentTypeOf(value).label;
}

export function validateDocument(type, number) {
  const trimmed = (number ?? '').trim();
  if (!trimmed) return 'Ingresá tu documento';
  return FORMATS[documentTypeOf(type).value].test(trimmed) ? null : documentTypeOf(type).hint;
}
