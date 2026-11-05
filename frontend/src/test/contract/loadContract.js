import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

let cached;

export function loadContract() {
  if (!cached) {
    cached = JSON.parse(readFileSync(resolve(globalThis.process.cwd(), '..', 'contracts', 'openapi.json'), 'utf8'));
  }
  return cached;
}
