const MAX_CENTS = 999999999999n;
const DECIMALS_PATTERN = /^\d{1,2}$/;

export const AMOUNT_ERROR_MESSAGES = {
  EMPTY: 'Ingresá el monto a cobrar.',
  NOT_POSITIVE: 'El monto debe ser mayor a 0.',
  TOO_MANY_DECIMALS: 'El monto admite hasta 2 decimales.',
  TOO_LARGE: 'El monto supera el máximo permitido (9.999.999.999,99).',
  INVALID: 'El monto no es válido. Ejemplo: 1.500,50',
};

function splitIntegerAndFraction(text) {
  const commas = text.split(',').length - 1;
  if (commas > 1) return null;
  if (commas === 1) {
    const [integerPart, fraction] = text.split(',');
    if (integerPart.includes('.') && !/^\d{1,3}(\.\d{3})+$/.test(integerPart)) return null;
    return { integer: integerPart.replaceAll('.', ''), fraction };
  }
  const dots = text.split('.').length - 1;
  if (dots === 0) return { integer: text, fraction: '' };
  if (dots > 1) {
    if (!/^\d{1,3}(\.\d{3})+$/.test(text)) return null;
    return { integer: text.replaceAll('.', ''), fraction: '' };
  }
  const [integerPart, fraction] = text.split('.');
  if (/^\d{1,3}$/.test(integerPart) && /^\d{3}$/.test(fraction)) return { integer: integerPart + fraction, fraction: '' };
  return { integer: integerPart, fraction };
}

function centsToCanonical(cents) {
  const text = cents.toString().padStart(3, '0');
  return `${text.slice(0, -2)}.${text.slice(-2)}`;
}

export function parseAmountInput(input) {
  const text = String(input ?? '').replace(/[\s$]/g, '');
  if (text === '') return { error: 'EMPTY' };
  if (!/^[\d.,]+$/.test(text)) return { error: 'INVALID' };
  const parts = splitIntegerAndFraction(text);
  if (!parts || !/^\d*$/.test(parts.integer) || !/^\d*$/.test(parts.fraction) || (parts.integer === '' && parts.fraction === '')) {
    return { error: 'INVALID' };
  }
  if (parts.fraction.length > 2 && !DECIMALS_PATTERN.test(parts.fraction)) return { error: 'TOO_MANY_DECIMALS' };
  const cents = BigInt(parts.integer || '0') * 100n + BigInt(parts.fraction.padEnd(2, '0') || '0');
  if (cents <= 0n) return { error: 'NOT_POSITIVE' };
  if (cents > MAX_CENTS) return { error: 'TOO_LARGE' };
  return { value: centsToCanonical(cents) };
}

export function formatAmountEsAr(canonical) {
  const [integerPart, fraction = '00'] = String(canonical).split('.');
  const grouped = integerPart.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return `${grouped},${fraction.padEnd(2, '0').slice(0, 2)}`;
}

export function newIdempotencyKey() {
  const cryptoApi = globalThis.crypto;
  if (cryptoApi?.randomUUID) return cryptoApi.randomUUID();
  const bytes = new Uint8Array(16);
  if (cryptoApi?.getRandomValues) cryptoApi.getRandomValues(bytes);
  else for (let index = 0; index < bytes.length; index += 1) bytes[index] = Math.floor(Math.random() * 256);
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
}
