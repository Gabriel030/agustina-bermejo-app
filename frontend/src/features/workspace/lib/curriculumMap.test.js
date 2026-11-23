import { describe, expect, it } from 'vitest';
import {
  buildCurriculumSections,
  buildRelationIndex,
  describeMissingPrerequisites,
  ELECTIVES_SECTION_KEY,
  getDefaultOpenYear,
  getDependentsOf,
  getHighlightedIds,
  getPrerequisitesOf,
  getSubjectPrerequisites,
} from '@/features/workspace/lib/curriculumMap';

function subject(id, overrides = {}) {
  return {
    programSubjectId: id,
    subjectId: id + 100,
    subjectCode: `S${String(id).padStart(3, '0')}`,
    subjectName: `Materia ${id}`,
    credits: 3,
    curricularYear: 1,
    curricularTerm: 'FIRST',
    category: 'MANDATORY',
    state: 'AVAILABLE',
    finalScore: null,
    blockedBy: [],
    ...overrides,
  };
}

function prereq(id, requirementType = 'APPROVED', standing = 'NOT_TAKEN') {
  return {
    subjectId: id + 100,
    subjectCode: `S${String(id).padStart(3, '0')}`,
    subjectName: `Materia ${id}`,
    requirementType,
    standing,
  };
}

function threeYearPlan() {
  return [
    subject(1, { curricularYear: 1, state: 'APPROVED', prerequisites: [] }),
    subject(2, { curricularYear: 1, curricularTerm: 'SECOND', state: 'APPROVED', prerequisites: [prereq(1, 'APPROVED', 'APPROVED')] }),
    subject(3, { curricularYear: 2, state: 'IN_PROGRESS', prerequisites: [prereq(2, 'PASSED_COURSEWORK', 'APPROVED')] }),
    subject(4, { curricularYear: 2, curricularTerm: 'ANNUAL', state: 'BLOCKED', blockedBy: [prereq(3)], prerequisites: [prereq(3), prereq(1, 'APPROVED', 'APPROVED')] }),
    subject(5, { curricularYear: 3, state: 'BLOCKED', blockedBy: [prereq(4, 'PASSED_COURSEWORK')], prerequisites: [prereq(4, 'PASSED_COURSEWORK')] }),
    subject(6, { curricularYear: null, category: 'ELECTIVE', prerequisites: [] }),
  ];
}

describe('buildCurriculumSections', () => {
  it('groups a three year plan by year with electives last', () => {
    const sections = buildCurriculumSections(threeYearPlan());
    expect(sections.map((section) => section.key)).toEqual(['year-1', 'year-2', 'year-3', ELECTIVES_SECTION_KEY]);
    expect(sections.map((section) => section.year)).toEqual([1, 2, 3, null]);
    expect(sections[3].subjects.map((s) => s.programSubjectId)).toEqual([6]);
  });

  it('orders by term FIRST, SECOND, ANNUAL then by code', () => {
    const sections = buildCurriculumSections([
      subject(1, { subjectCode: 'Z', curricularTerm: 'ANNUAL' }),
      subject(2, { subjectCode: 'B', curricularTerm: 'SECOND' }),
      subject(3, { subjectCode: 'C', curricularTerm: 'FIRST' }),
      subject(4, { subjectCode: 'A', curricularTerm: 'FIRST' }),
    ]);
    expect(sections[0].subjects.map((s) => s.subjectCode)).toEqual(['A', 'C', 'B', 'Z']);
  });

  it('sends electives with a year and subjects without a year to the electives block', () => {
    const sections = buildCurriculumSections([
      subject(1, { category: 'ELECTIVE', curricularYear: 2 }),
      subject(2, { curricularYear: null }),
      subject(3, { curricularYear: 2 }),
    ]);
    expect(sections.map((section) => section.key)).toEqual(['year-2', ELECTIVES_SECTION_KEY]);
    expect(sections[0].subjects.map((s) => s.programSubjectId)).toEqual([3]);
    expect(sections[1].subjects.map((s) => s.programSubjectId)).toEqual([2, 1]);
  });

  it('puts null terms after annual and breaks ties by id', () => {
    const sections = buildCurriculumSections([
      subject(9, { subjectCode: 'A', curricularTerm: null }),
      subject(8, { subjectCode: 'A', curricularTerm: 'ANNUAL' }),
      subject(7, { subjectCode: 'A', curricularTerm: 'ANNUAL' }),
    ]);
    expect(sections[0].subjects.map((s) => s.programSubjectId)).toEqual([7, 8, 9]);
  });

  it('skips empty years and does not create an empty electives block', () => {
    const sections = buildCurriculumSections([subject(1, { curricularYear: 3 }), subject(2, { curricularYear: 1 })]);
    expect(sections.map((section) => section.year)).toEqual([1, 3]);
  });

  it('returns no sections for empty, null or undefined input', () => {
    expect(buildCurriculumSections([])).toEqual([]);
    expect(buildCurriculumSections(null)).toEqual([]);
    expect(buildCurriculumSections(undefined)).toEqual([]);
  });

  it('is deterministic regardless of input order and does not mutate the input', () => {
    const plan = threeYearPlan();
    const snapshot = JSON.stringify(plan);
    const forward = buildCurriculumSections(plan);
    const backward = buildCurriculumSections([...plan].reverse());
    expect(JSON.stringify(backward)).toEqual(JSON.stringify(forward));
    expect(JSON.stringify(plan)).toEqual(snapshot);
  });

  it('handles 40+ subjects across five years', () => {
    const plan = Array.from({ length: 45 }, (_, i) =>
      subject(i + 1, { curricularYear: (i % 5) + 1, curricularTerm: i % 2 === 0 ? 'FIRST' : 'SECOND' }),
    );
    const sections = buildCurriculumSections(plan);
    expect(sections).toHaveLength(5);
    expect(sections.reduce((total, section) => total + section.subjects.length, 0)).toBe(45);
    for (const section of sections) {
      const terms = section.subjects.map((s) => s.curricularTerm);
      expect(terms).toEqual([...terms].sort((a, b) => (a === 'FIRST' ? 0 : 1) - (b === 'FIRST' ? 0 : 1)));
    }
  });
});

describe('getSubjectPrerequisites', () => {
  it('prefers the full prerequisites list over blockedBy', () => {
    const s = subject(4, { blockedBy: [prereq(3)], prerequisites: [prereq(3), prereq(1)] });
    expect(getSubjectPrerequisites(s)).toHaveLength(2);
  });

  it('falls back to blockedBy when prerequisites is absent', () => {
    const s = subject(4, { blockedBy: [prereq(3)] });
    expect(getSubjectPrerequisites(s)).toEqual([prereq(3)]);
  });

  it('returns an empty list when neither is present or prerequisites is empty', () => {
    expect(getSubjectPrerequisites({})).toEqual([]);
    expect(getSubjectPrerequisites(null)).toEqual([]);
    expect(getSubjectPrerequisites(subject(1, { blockedBy: [prereq(3)], prerequisites: [] }))).toEqual([]);
  });

  it('drops duplicates and entries without subjectId', () => {
    const s = subject(4, { prerequisites: [prereq(3), prereq(3), { requirementType: 'APPROVED' }, prereq(3, 'PASSED_COURSEWORK')] });
    expect(getSubjectPrerequisites(s)).toHaveLength(2);
  });
});

describe('buildRelationIndex', () => {
  const index = buildRelationIndex(threeYearPlan());

  it('lists the full prerequisites of a subject, met or not', () => {
    expect(getPrerequisitesOf(index, 4).map((p) => p.programSubjectId)).toEqual([3, 1]);
    expect(getPrerequisitesOf(index, 2)[0].standing).toBe('APPROVED');
  });

  it('inverts prerequisites into dependents', () => {
    expect(getDependentsOf(index, 1).map((d) => d.programSubjectId)).toEqual([2, 4]);
    expect(getDependentsOf(index, 3).map((d) => d.programSubjectId)).toEqual([4]);
    expect(getDependentsOf(index, 4)[0].requirementType).toBe('PASSED_COURSEWORK');
  });

  it('gives empty lists for subjects without relations or unknown ids', () => {
    expect(getPrerequisitesOf(index, 6)).toEqual([]);
    expect(getDependentsOf(index, 6)).toEqual([]);
    expect(getPrerequisitesOf(index, 999)).toEqual([]);
    expect(getDependentsOf(index, 999)).toEqual([]);
  });

  it('keeps prerequisites that are absent from the plan without a dependent link', () => {
    const idx = buildRelationIndex([subject(1, { prerequisites: [prereq(77)] })]);
    expect(getPrerequisitesOf(idx, 1)).toHaveLength(1);
    expect(getPrerequisitesOf(idx, 1)[0].programSubjectId).toBeNull();
    expect(getDependentsOf(idx, 1)).toEqual([]);
  });

  it('works with the blockedBy fallback when prerequisites is missing', () => {
    const idx = buildRelationIndex([subject(1), subject(2, { state: 'BLOCKED', blockedBy: [prereq(1)] })]);
    expect(getDependentsOf(idx, 1).map((d) => d.programSubjectId)).toEqual([2]);
  });

  it('survives cycles and self references', () => {
    const idx = buildRelationIndex([
      subject(1, { prerequisites: [prereq(2), prereq(1)] }),
      subject(2, { prerequisites: [prereq(1)] }),
    ]);
    expect(getDependentsOf(idx, 1).map((d) => d.programSubjectId)).toEqual([2]);
    expect(getDependentsOf(idx, 2).map((d) => d.programSubjectId)).toEqual([1]);
    expect([...getHighlightedIds(idx, 1)]).toEqual([2]);
  });

  it('does not duplicate a dependent that requires the same subject twice', () => {
    const idx = buildRelationIndex([
      subject(1),
      subject(2, { prerequisites: [prereq(1, 'APPROVED'), prereq(1, 'PASSED_COURSEWORK')] }),
    ]);
    expect(getDependentsOf(idx, 1)).toHaveLength(1);
  });

  it('builds an empty index for empty input', () => {
    const idx = buildRelationIndex(undefined);
    expect(getPrerequisitesOf(idx, 1)).toEqual([]);
    expect(getDependentsOf(idx, 1)).toEqual([]);
  });

  it('orders dependents by year, term and code', () => {
    const idx = buildRelationIndex([
      subject(1),
      subject(2, { curricularYear: 3, prerequisites: [prereq(1)] }),
      subject(3, { curricularYear: 2, prerequisites: [prereq(1)] }),
    ]);
    expect(getDependentsOf(idx, 1).map((d) => d.programSubjectId)).toEqual([3, 2]);
  });
});

describe('getHighlightedIds', () => {
  const index = buildRelationIndex(threeYearPlan());

  it('returns prerequisites and dependents of the selected subject', () => {
    expect([...getHighlightedIds(index, 4)].sort()).toEqual([1, 3, 5]);
    expect([...getHighlightedIds(index, 1)].sort()).toEqual([2, 4]);
  });

  it('does not include the selected subject nor transitive relations', () => {
    const highlighted = getHighlightedIds(index, 3);
    expect(highlighted.has(3)).toBe(false);
    expect(highlighted.has(1)).toBe(false);
    expect([...highlighted].sort()).toEqual([2, 4]);
  });

  it('returns an empty set without selection, unknown ids or isolated subjects', () => {
    expect(getHighlightedIds(index, null).size).toBe(0);
    expect(getHighlightedIds(index, undefined).size).toBe(0);
    expect(getHighlightedIds(index, 999).size).toBe(0);
    expect(getHighlightedIds(index, 6).size).toBe(0);
  });

  it('is deterministic across repeated calls', () => {
    expect([...getHighlightedIds(index, 4)]).toEqual([...getHighlightedIds(index, 4)]);
  });
});

describe('describeMissingPrerequisites', () => {
  it('describes each pending prerequisite as approve or regularize', () => {
    const s = subject(4, {
      blockedBy: [prereq(3, 'APPROVED', 'NOT_TAKEN'), prereq(2, 'PASSED_COURSEWORK', 'REGULAR')],
    });
    expect(describeMissingPrerequisites(s)).toEqual([
      'Aprobar Materia 3 (S003) · sin cursar',
      'Regularizar Materia 2 (S002) · cursada regular',
    ]);
  });

  it('returns an empty list when nothing is missing or blockedBy is absent', () => {
    expect(describeMissingPrerequisites(subject(1))).toEqual([]);
    expect(describeMissingPrerequisites({})).toEqual([]);
    expect(describeMissingPrerequisites(null)).toEqual([]);
  });
});

describe('getDefaultOpenYear', () => {
  it('returns the first year with pending subjects', () => {
    expect(getDefaultOpenYear(buildCurriculumSections(threeYearPlan()))).toBe(2);
  });

  it('returns the first year when everything is approved', () => {
    const sections = buildCurriculumSections([
      subject(1, { state: 'APPROVED' }),
      subject(2, { curricularYear: 2, state: 'APPROVED' }),
    ]);
    expect(getDefaultOpenYear(sections)).toBe(1);
  });

  it('returns year 1 when nothing has been approved', () => {
    expect(getDefaultOpenYear(buildCurriculumSections([subject(1), subject(2, { curricularYear: 2 })]))).toBe(1);
  });

  it('ignores the electives block and returns null without years', () => {
    expect(getDefaultOpenYear(buildCurriculumSections([subject(1, { curricularYear: null })]))).toBeNull();
    expect(getDefaultOpenYear([])).toBeNull();
    expect(getDefaultOpenYear(undefined)).toBeNull();
  });

  it('skips approved years even when electives are pending', () => {
    const sections = buildCurriculumSections([
      subject(1, { state: 'APPROVED' }),
      subject(2, { curricularYear: 2, state: 'FAILED' }),
      subject(3, { curricularYear: null, category: 'ELECTIVE' }),
    ]);
    expect(getDefaultOpenYear(sections)).toBe(2);
  });
});
