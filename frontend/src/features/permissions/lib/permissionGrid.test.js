import { describe, expect, it } from 'vitest';
import { moduleLabel } from '@/features/permissions/lib/permissionGrid';

describe('moduleLabel', () => {
  it('translates every catalog module to Spanish', () => {
    expect(moduleLabel('dataimport')).toBe('Importación de datos');
    expect(moduleLabel('audit')).toBe('Auditoría');
    expect(moduleLabel('certificate')).toBe('Certificados');
    expect(moduleLabel('enrollment')).toBe('Inscripciones');
    expect(moduleLabel('notification')).toBe('Notificaciones');
    expect(moduleLabel('platform')).toBe('Configuración del instituto');
  });

  it('capitalizes an unknown module as a fallback', () => {
    expect(moduleLabel('some_module')).toBe('Some module');
  });
});
