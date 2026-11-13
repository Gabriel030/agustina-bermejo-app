const FORMAT_VERSION = 1;
const IV_BYTES = 12;
const encoder = new TextEncoder();
const decoder = new TextDecoder();

function toBase64(bytes) {
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

function fromBase64(text) {
  const binary = atob(text);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

export const buildAad = (userId, scope) => `v${FORMAT_VERSION}|${userId}|${scope}`;

export async function encryptText(key, plainText, aad) {
  const iv = globalThis.crypto.getRandomValues(new Uint8Array(IV_BYTES));
  const cipher = await globalThis.crypto.subtle.encrypt(
    { name: 'AES-GCM', iv, additionalData: encoder.encode(aad) },
    key,
    encoder.encode(plainText),
  );
  return JSON.stringify({ v: FORMAT_VERSION, iv: toBase64(iv), ct: toBase64(new Uint8Array(cipher)) });
}

export async function decryptText(key, envelopeText, aad) {
  const envelope = JSON.parse(envelopeText);
  if (envelope?.v !== FORMAT_VERSION || typeof envelope.iv !== 'string' || typeof envelope.ct !== 'string') {
    throw new Error('unrecognized envelope');
  }
  const plain = await globalThis.crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: fromBase64(envelope.iv), additionalData: encoder.encode(aad) },
    key,
    fromBase64(envelope.ct),
  );
  return decoder.decode(plain);
}

export async function encryptToBinary(key, plainText, aad) {
  const iv = globalThis.crypto.getRandomValues(new Uint8Array(IV_BYTES));
  const cipher = await globalThis.crypto.subtle.encrypt(
    { name: 'AES-GCM', iv, additionalData: encoder.encode(aad) },
    key,
    encoder.encode(plainText),
  );
  return { v: FORMAT_VERSION, iv, ct: cipher };
}

export async function decryptFromBinary(key, sealed, aad) {
  if (sealed?.v !== FORMAT_VERSION || !ArrayBuffer.isView(sealed.iv) || Object.prototype.toString.call(sealed.ct) !== '[object ArrayBuffer]') {
    throw new Error('unrecognized sealed entry');
  }
  const plain = await globalThis.crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: sealed.iv, additionalData: encoder.encode(aad) },
    key,
    sealed.ct,
  );
  return decoder.decode(plain);
}

export async function digestHex(text) {
  const digest = await globalThis.crypto.subtle.digest('SHA-256', encoder.encode(text));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('');
}
