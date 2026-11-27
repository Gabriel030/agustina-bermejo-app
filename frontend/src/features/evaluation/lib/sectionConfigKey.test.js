import { describe, expect, it } from 'vitest';
import { sectionConfigKey, sectionConfigKind, sectionConfigSectionId } from './sectionConfigKey';

describe('sectionConfigKey', () => {
  it('uses the subject section id for program configs', () => {
    const config = { sectionKind: 'PROGRAM', subjectSectionId: 7, courseSectionId: null };
    expect(sectionConfigKind(config)).toBe('PROGRAM');
    expect(sectionConfigSectionId(config)).toBe(7);
    expect(sectionConfigKey(config)).toBe('PROGRAM-7');
  });

  it('uses the course section id for course configs', () => {
    const config = { sectionKind: 'COURSE', subjectSectionId: null, courseSectionId: 9 };
    expect(sectionConfigSectionId(config)).toBe(9);
    expect(sectionConfigKey(config)).toBe('COURSE-9');
  });

  it('falls back to program when the kind is missing', () => {
    expect(sectionConfigKey({ subjectSectionId: 3 })).toBe('PROGRAM-3');
  });
});
