import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { TIPO_NOTIFICACION_LABEL, TONO_NOTIFICACION } from './notificaciones';

const BACKEND_NOTIFICATION_TYPE_FILE = join(
  import.meta.dirname,
  '..',
  '..',
  '..',
  'backend',
  'src',
  'main',
  'java',
  'com',
  'red_escuela',
  'backend',
  'notification',
  'entity',
  'NotificationType.java',
);

function backendNotificationTypes() {
  const source = readFileSync(BACKEND_NOTIFICATION_TYPE_FILE, 'utf8');
  const enumBody = source.match(/enum\s+NotificationType\s*\{([\s\S]*?)\}/)?.[1];
  if (enumBody === undefined) throw new Error('No se encontró el enum NotificationType en el backend');
  return enumBody
    .split(',')
    .map((entry) => entry.trim())
    .filter((entry) => /^[A-Z][A-Z0-9_]*$/.test(entry));
}

describe('NotificationType del backend', () => {
  it('everyBackendNotificationTypeHasLabelAndTone', () => {
    const types = backendNotificationTypes();
    expect(types.length).toBeGreaterThan(0);

    const withoutLabel = types.filter((type) => !TIPO_NOTIFICACION_LABEL[type]);
    const withoutTone = types.filter((type) => !TONO_NOTIFICACION[type]);

    expect(withoutLabel, 'tipos sin label en TIPO_NOTIFICACION_LABEL').toEqual([]);
    expect(withoutTone, 'tipos sin tono en TONO_NOTIFICACION').toEqual([]);
  });
});
