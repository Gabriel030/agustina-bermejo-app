import { hasWebCrypto } from './offlineKeyVault';

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

export function createEncryptedStorage({ vault, rawStorage, resolveUserId, scope }) {
  const discard = async (reason, storageKey, userId) => {
    console.warn(`offline cache discarded: ${reason}`);
    if (userId !== undefined && userId !== null) await Promise.allSettled([vault.delete(userId)]);
    await Promise.allSettled([rawStorage.removeItem(storageKey)]);
  };

  return {
    getItem: async (storageKey) => {
      try {
        const stored = await rawStorage.getItem(storageKey);
        if (stored === null || stored === undefined) return null;
        if (!hasWebCrypto()) {
          await discard('webcrypto unavailable', storageKey);
          return null;
        }
        const userId = await resolveUserId();
        if (userId === undefined || userId === null) {
          await discard('no owner', storageKey);
          return null;
        }
        const key = await vault.find(userId);
        if (!key) {
          await discard('missing key', storageKey, userId);
          return null;
        }
        return await decryptText(key, stored, buildAad(userId, scope));
      } catch {
        await discard('decryption failed', storageKey, await resolveUserId().catch(() => undefined));
        return null;
      }
    },
    setItem: async (storageKey, value) => {
      try {
        if (!hasWebCrypto()) return;
        const userId = await resolveUserId();
        if (userId === undefined || userId === null) return;
        const key = await vault.getOrCreate(userId);
        if (!key) return;
        const envelope = await encryptText(key, value, buildAad(userId, scope));
        if ((await resolveUserId()) !== userId) return;
        await rawStorage.setItem(storageKey, envelope);
      } catch {
        return;
      }
    },
    removeItem: async (storageKey) => {
      const userId = await resolveUserId().catch(() => undefined);
      if (userId !== undefined && userId !== null) await Promise.allSettled([vault.delete(userId)]);
      await rawStorage.removeItem(storageKey);
    },
  };
}
