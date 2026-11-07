export const CERTIFICATE_CODE_LENGTH = 26;

const GROUP_SIZES = [5, 5, 5, 5, 6];
const VALID_CODE = /^[0-9A-HJKMNP-TV-Z]{26}$/;

export function normalizeCertificateCode(raw) {
  return String(raw ?? '')
    .replace(/[^0-9A-Za-z]/g, '')
    .toUpperCase();
}

export function formatCertificateCode(raw) {
  const normalized = normalizeCertificateCode(raw).slice(0, CERTIFICATE_CODE_LENGTH);
  const groups = [];
  let offset = 0;
  for (const size of GROUP_SIZES) {
    if (offset >= normalized.length) break;
    groups.push(normalized.slice(offset, offset + size));
    offset += size;
  }
  return groups.join('-');
}

export function isWellFormedCertificateCode(normalized) {
  return VALID_CODE.test(normalized);
}
