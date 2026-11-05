import { describe, expect, it } from 'vitest';
import { TEACHING_MODE_LABELS, TEACHING_MODE_OPTIONS } from './teachingMode';
import { TEACHING_MODE_LABEL } from './catalog';

describe('teachingMode', () => {
  it('exposes one label per mode and an unspecified option first', () => {
    expect(TEACHING_MODE_LABELS).toEqual({ IN_PERSON: 'Presencial', ONLINE: 'Virtual', HYBRID: 'Híbrido' });
    expect(TEACHING_MODE_OPTIONS).toEqual([
      { value: '', label: 'Sin especificar' },
      { value: 'IN_PERSON', label: 'Presencial' },
      { value: 'ONLINE', label: 'Virtual' },
      { value: 'HYBRID', label: 'Híbrido' },
    ]);
  });

  it('is the same map the catalog uses', () => {
    expect(TEACHING_MODE_LABEL).toBe(TEACHING_MODE_LABELS);
  });
});
