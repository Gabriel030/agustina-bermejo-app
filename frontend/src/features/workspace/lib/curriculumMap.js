import {
  CURRICULUM_STATE_TO_PLAN_STATUS,
  STATUS_FILTER_TO_PLAN_STATUS,
  describeBlockingPrerequisite,
} from '@/features/workspace/lib/curriculumPlan';

export const ELECTIVES_SECTION_KEY = 'electives';

const TERM_RANK = { FIRST: 0, SECOND: 1, ANNUAL: 2 };

function isElectiveBlock(subject) {
  return subject.category === 'ELECTIVE' || subject.curricularYear == null;
}

function compareSubjects(a, b) {
  const byYear = (a.curricularYear ?? 0) - (b.curricularYear ?? 0);
  if (byYear !== 0) return byYear;
  const byTerm = (TERM_RANK[a.curricularTerm] ?? 99) - (TERM_RANK[b.curricularTerm] ?? 99);
  if (byTerm !== 0) return byTerm;
  const byCode = (a.subjectCode ?? '').localeCompare(b.subjectCode ?? '');
  if (byCode !== 0) return byCode;
  return (a.programSubjectId ?? 0) - (b.programSubjectId ?? 0);
}

export function buildCurriculumSections(subjects) {
  const byYear = new Map();
  const electives = [];
  for (const subject of subjects ?? []) {
    if (isElectiveBlock(subject)) {
      electives.push(subject);
    } else {
      if (!byYear.has(subject.curricularYear)) byYear.set(subject.curricularYear, []);
      byYear.get(subject.curricularYear).push(subject);
    }
  }
  const sections = [...byYear.keys()]
    .sort((a, b) => a - b)
    .map((year) => ({
      key: `year-${year}`,
      year,
      subjects: byYear.get(year).sort(compareSubjects),
    }));
  if (electives.length > 0) {
    sections.push({ key: ELECTIVES_SECTION_KEY, year: null, subjects: electives.sort(compareSubjects) });
  }
  return sections;
}

export function getSubjectPrerequisites(subject) {
  const source = Array.isArray(subject?.prerequisites) ? subject.prerequisites : subject?.blockedBy;
  if (!Array.isArray(source)) return [];
  const seen = new Set();
  const result = [];
  for (const prerequisite of source) {
    if (prerequisite?.subjectId == null) continue;
    const key = `${prerequisite.subjectId}|${prerequisite.requirementType}`;
    if (seen.has(key)) continue;
    seen.add(key);
    result.push(prerequisite);
  }
  return result;
}

export function buildRelationIndex(subjects) {
  const list = subjects ?? [];
  const programSubjectIdBySubjectId = new Map();
  for (const subject of list) {
    if (subject.subjectId != null && !programSubjectIdBySubjectId.has(subject.subjectId)) {
      programSubjectIdBySubjectId.set(subject.subjectId, subject.programSubjectId);
    }
  }
  const prerequisitesById = new Map();
  const dependentsById = new Map();
  for (const subject of list) {
    prerequisitesById.set(subject.programSubjectId, []);
    dependentsById.set(subject.programSubjectId, []);
  }
  for (const subject of list) {
    for (const prerequisite of getSubjectPrerequisites(subject)) {
      const prerequisiteId = programSubjectIdBySubjectId.get(prerequisite.subjectId);
      const entry = { ...prerequisite, programSubjectId: prerequisiteId ?? null };
      prerequisitesById.get(subject.programSubjectId).push(entry);
      if (prerequisiteId == null || prerequisiteId === subject.programSubjectId) continue;
      const dependents = dependentsById.get(prerequisiteId);
      if (dependents.some((dependent) => dependent.programSubjectId === subject.programSubjectId)) continue;
      dependents.push({
        programSubjectId: subject.programSubjectId,
        subjectId: subject.subjectId,
        subjectCode: subject.subjectCode,
        subjectName: subject.subjectName,
        requirementType: prerequisite.requirementType,
      });
    }
  }
  const subjectsById = new Map(list.map((subject) => [subject.programSubjectId, subject]));
  for (const dependents of dependentsById.values()) {
    dependents.sort((a, b) => compareSubjects(subjectsById.get(a.programSubjectId), subjectsById.get(b.programSubjectId)));
  }
  return { prerequisitesById, dependentsById };
}

export function getPrerequisitesOf(index, programSubjectId) {
  return index.prerequisitesById.get(programSubjectId) ?? [];
}

export function getDependentsOf(index, programSubjectId) {
  return index.dependentsById.get(programSubjectId) ?? [];
}

export function getHighlightedIds(index, selectedId) {
  const highlighted = new Set();
  if (selectedId == null || !index.prerequisitesById.has(selectedId)) return highlighted;
  for (const prerequisite of index.prerequisitesById.get(selectedId)) {
    if (prerequisite.programSubjectId != null) highlighted.add(prerequisite.programSubjectId);
  }
  for (const dependent of index.dependentsById.get(selectedId)) {
    highlighted.add(dependent.programSubjectId);
  }
  highlighted.delete(selectedId);
  return highlighted;
}

export function describeMissingPrerequisites(subject) {
  if (!Array.isArray(subject?.blockedBy)) return [];
  return subject.blockedBy.map(describeBlockingPrerequisite);
}

export function getDefaultOpenYear(sections) {
  const years = (sections ?? []).filter((section) => section.year != null);
  const firstPending = years.find((section) => section.subjects.some((subject) => subject.state !== 'APPROVED'));
  if (firstPending) return firstPending.year;
  return years.length > 0 ? years[0].year : null;
}

const TERM_LABEL = { FIRST: '1.er cuatrimestre', SECOND: '2.º cuatrimestre', ANNUAL: 'Anual' };

export function describeCurricularTerm(term) {
  return TERM_LABEL[term] ?? null;
}

export function isApprovedByEquivalence(subject) {
  return subject?.approvalSource === 'EQUIVALENCE';
}

export function filterSubjectsByStatus(subjects, statusFilter) {
  const status = STATUS_FILTER_TO_PLAN_STATUS[statusFilter];
  if (!status) return subjects ?? [];
  return (subjects ?? []).filter((subject) => CURRICULUM_STATE_TO_PLAN_STATUS[subject.state] === status);
}

export function countSubjectsByStatus(subjects) {
  const counts = Object.fromEntries(Object.values(CURRICULUM_STATE_TO_PLAN_STATUS).map((status) => [status, 0]));
  for (const subject of subjects ?? []) {
    const status = CURRICULUM_STATE_TO_PLAN_STATUS[subject.state];
    if (status) counts[status] += 1;
  }
  return counts;
}
