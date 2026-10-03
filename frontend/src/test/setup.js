import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { cleanup } from '@testing-library/react';
import { server } from './mocks/server';

// El localStorage de jsdom en este entorno queda como un objeto sin sus
// métodos (Storage sin prototype) — se reemplaza por un polyfill en memoria
// simple y determinístico, en vez de depender del storage real de jsdom.
class MemoryStorage {
  #store = new Map();
  getItem(key) {
    return this.#store.has(key) ? this.#store.get(key) : null;
  }
  setItem(key, value) {
    this.#store.set(key, String(value));
  }
  removeItem(key) {
    this.#store.delete(key);
  }
  clear() {
    this.#store.clear();
  }
}

Object.defineProperty(window, 'localStorage', { value: new MemoryStorage(), writable: true });
globalThis.localStorage = window.localStorage;

// jsdom no implementa matchMedia — ThemeContext lo usa para la preferencia de
// sistema (prefers-color-scheme) cuando no hay nada guardado en localStorage.
window.matchMedia =
  window.matchMedia ||
  function matchMedia() {
    return { matches: false, addEventListener() {}, removeEventListener() {} };
  };

// jsdom no implementa la Pointer Events API ni scrollIntoView, y el Select de
// Radix los usa al abrirse: sin esto, cualquier test que despliegue un Select
// revienta con "target.hasPointerCapture is not a function".
if (!Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false;
  Element.prototype.setPointerCapture = () => {};
  Element.prototype.releasePointerCapture = () => {};
}
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {};
}

// MSW intercepta a nivel de red (fetch real) — nunca se mockea apiFetch ni los
// hooks directamente, ver docs/context/testing.md.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => {
  cleanup();
  server.resetHandlers();
  localStorage.clear();
});
afterAll(() => server.close());
