import { buildRelationIndex, getDependentsOf } from '@/features/workspace/lib/curriculumMap';
import { CURRICULUM_STATE_TO_PLAN_STATUS } from '@/features/workspace/lib/curriculumPlan';

const BLOCKING_STATES = ['IN_PROGRESS', 'APPROVED'];

export function canRecordFor(subject) {
  return subject != null && !BLOCKING_STATES.includes(subject.state);
}

function scoreOf(subject) {
  if (subject.finalScore != null) return subject.finalScore;
  return subject.equivalence?.originScore ?? null;
}

function averageWith(subjects, extraScore) {
  const scores = subjects.filter((subject) => subject.state === 'APPROVED').map(scoreOf).filter((score) => score != null);
  if (extraScore != null) scores.push(extraScore);
  if (scores.length === 0) return null;
  return scores.reduce((total, score) => total + score, 0) / scores.length;
}

function progressPercent(summary, approvedMandatory, accumulatedCredits) {
  if (summary.requiredCredits > 0) return Math.min(100, (accumulatedCredits / summary.requiredCredits) * 100);
  return summary.mandatoryTotal > 0 ? (approvedMandatory / summary.mandatoryTotal) * 100 : 0;
}

export function computeEquivalenceEffect(progress, programSubjectId, originScore) {
  const { summary, subjects } = progress;
  const subject = subjects.find((candidate) => candidate.programSubjectId === programSubjectId);
  if (!subject) return null;
  const credits = Number(subject.credits) || 0;
  const mandatoryDelta = subject.category === 'MANDATORY' ? 1 : 0;
  const unlocked = getDependentsOf(buildRelationIndex(subjects), programSubjectId)
    .map((dependent) => subjects.find((candidate) => candidate.programSubjectId === dependent.programSubjectId))
    .filter(
      (dependent) =>
        dependent.state === 'BLOCKED' &&
        dependent.blockedBy.length > 0 &&
        dependent.blockedBy.every((blocker) => blocker.subjectId === subject.subjectId),
    );
  const accumulatedAfter = summary.accumulatedCredits + credits;
  const average = averageWith(subjects, null);
  const averageAfter = originScore == null ? average : averageWith(subjects.filter((candidate) => candidate.programSubjectId !== programSubjectId), originScore);
  return {
    subject,
    statusBefore: CURRICULUM_STATE_TO_PLAN_STATUS[subject.state],
    approvedBefore: summary.counts.approved,
    approvedAfter: summary.counts.approved + 1,
    creditsBefore: summary.accumulatedCredits,
    creditsAfter: accumulatedAfter,
    percentBefore: progressPercent(summary, summary.mandatoryApproved, summary.accumulatedCredits),
    percentAfter: progressPercent(summary, summary.mandatoryApproved + mandatoryDelta, accumulatedAfter),
    averageBefore: summary.averageScore ?? average,
    averageAfter,
    unlocked,
  };
}

export function equivalenceFieldForCode(code) {
  switch (code) {
    case 'INVALID_SCORE':
      return 'originScore';
    case 'FUTURE_DATE':
      return 'recognizedOn';
    case 'EQUIVALENCE_REASON_INVALID':
      return 'reason';
    case 'EQUIVALENCE_DOCUMENT_INVALID':
      return 'studentDocumentId';
    case 'SUBJECT_NOT_IN_PLAN':
      return 'programSubjectId';
    default:
      return null;
  }
}

export const EQUIVALENCE_CONFLICT_CODES = [
  'EQUIVALENCE_ALREADY_ACTIVE',
  'SUBJECT_ALREADY_APPROVED',
  'SUBJECT_IN_PROGRESS',
  'ENROLLMENT_NOT_ACTIVE',
  'EQUIVALENCE_ALREADY_VOIDED',
];
