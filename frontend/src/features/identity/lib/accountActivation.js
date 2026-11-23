import { apiFetch } from '@/api/client';

export const PASSWORD_MIN_LENGTH = 8;
export const MAX_ACTIVATION_TOKEN_LENGTH = 256;
export const ACTIVATION_STATUS = { VALID: 'VALID', EXPIRED: 'EXPIRED', INVALID: 'INVALID' };

const UPPERCASE_AND_DIGIT = /(?=.*[A-Z])(?=.*\d)/;

export function isPlausibleActivationToken(token) {
  return typeof token === 'string' && token.length > 0 && token.length <= MAX_ACTIVATION_TOKEN_LENGTH;
}

export function validateNewPassword(password) {
  if (password.length < PASSWORD_MIN_LENGTH) return `La contraseña debe tener al menos ${PASSWORD_MIN_LENGTH} caracteres.`;
  if (!UPPERCASE_AND_DIGIT.test(password)) return 'La contraseña debe tener al menos una mayúscula y un número.';
  return null;
}

export function validatePasswordConfirmation(password, confirmation) {
  return password === confirmation ? null : 'Las contraseñas no coinciden.';
}

const PRIVATE_REQUEST = { cache: 'no-store', referrerPolicy: 'no-referrer' };

export async function fetchActivationStatus(token, signal) {
  const body = await apiFetch('/auth/activation-token/status', {
    ...PRIVATE_REQUEST,
    method: 'POST',
    body: JSON.stringify({ token }),
    signal,
  });
  const status = body?.status;
  if (status === ACTIVATION_STATUS.VALID) {
    return { status, username: body.username ?? null, institution: body.institution ?? null };
  }
  if (status === ACTIVATION_STATUS.EXPIRED) {
    return { status, username: null, institution: body.institution ?? null };
  }
  return { status: ACTIVATION_STATUS.INVALID, username: null, institution: null };
}

export function activateAccount({ token, newPassword }) {
  return apiFetch('/auth/activate', {
    ...PRIVATE_REQUEST,
    method: 'POST',
    body: JSON.stringify({ token, newPassword }),
  });
}
