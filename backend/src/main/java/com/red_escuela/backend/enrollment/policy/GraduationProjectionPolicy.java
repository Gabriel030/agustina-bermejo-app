package com.red_escuela.backend.enrollment.policy;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.policy.GraduationProjection.Basis;
import com.red_escuela.backend.enrollment.policy.GraduationProjection.Message;
import com.red_escuela.backend.enrollment.policy.GraduationProjection.PeriodRange;
import com.red_escuela.backend.enrollment.policy.GraduationProjectionInput.ProjectionElectiveGroup;
import com.red_escuela.backend.enrollment.policy.GraduationProjectionInput.ProjectionSubject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GraduationProjectionPolicy {

    public static final int MAX_SIMULATED_PERIODS = 40;
    public static final int MIN_CLOSED_PERIODS_FOR_STUDENT_PACE = 2;
    public static final int MEDIUM_MIN_CLOSED_PERIODS = 2;
    public static final int HIGH_MIN_CLOSED_PERIODS = 4;
    public static final int MEDIUM_MIN_APPROVED_SUBJECTS = 3;
    public static final int HIGH_MIN_APPROVED_SUBJECTS = 8;
    public static final int PERIODS_PER_YEAR = 2;

    private static final Comparator<ProjectionSubject> BY_CURRICULAR_ORDER = Comparator
            .comparing(ProjectionSubject::curricularYear, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ProjectionSubject::subjectId);

    public GraduationProjection project(GraduationProjectionInput input) {
        if (input.enrollmentStatus() == ProgramEnrollmentStatus.COMPLETED) {
            return requirementsMet(input);
        }
        if (input.enrollmentStatus() == ProgramEnrollmentStatus.WITHDRAWN) {
            return notProjectable(input, ProjectionBlockReason.ENROLLMENT_WITHDRAWN);
        }
        if (input.enrollmentStatus() == ProgramEnrollmentStatus.PAUSED) {
            return notProjectable(input, ProjectionBlockReason.ENROLLMENT_PAUSED);
        }
        if (input.subjects().isEmpty()) {
            return notProjectable(input, ProjectionBlockReason.NO_PLAN_SUBJECTS);
        }
        if (input.requiredCredits() == 0) {
            return notProjectable(input, ProjectionBlockReason.PLAN_WITHOUT_CREDITS);
        }
        if (requirementsAreMet(input)) {
            return requirementsMet(input);
        }

        BigDecimal planPace = planPaceOf(input.requiredCredits(), input.programTotalYears());
        boolean usesStudentPace = input.closedPeriods() >= MIN_CLOSED_PERIODS_FOR_STUDENT_PACE
                && input.courseworkCredits() > 0;
        if (!usesStudentPace && planPace == null) {
            return notProjectable(input, ProjectionBlockReason.PLAN_WITHOUT_DURATION);
        }
        BigDecimal pace = usesStudentPace
                ? BigDecimal.valueOf(input.courseworkCredits())
                        .divide(BigDecimal.valueOf(input.closedPeriods()), 2, RoundingMode.HALF_UP)
                : planPace;

        Plan plan = new Plan(input);
        Set<ProjectionBlockReason> reasons = plan.selectTargets();
        if (reasons.isEmpty()) {
            reasons = plan.detectStructuralProblems();
        }
        if (!reasons.isEmpty()) {
            return notProjectable(input, reasons.toArray(ProjectionBlockReason[]::new));
        }

        int criticalPath = plan.criticalPathPeriods();
        int simulated = plan.simulate(pace, firstPeriodTermOf(input.today()));
        if (simulated < 0) {
            return notProjectable(input, ProjectionBlockReason.PROJECTION_HORIZON_EXCEEDED);
        }
        int estimated = Math.max(simulated, criticalPath);
        int floor = criticalPath;
        if (planPace != null) {
            floor = Math.max(floor, ceilDivide(input.remainingCredits(), planPace));
        }
        int minPeriods = Math.min(floor, estimated);

        ProjectionConfidence confidence = usesStudentPace
                ? confidenceOf(input.closedPeriods(), input.approvedSubjects())
                : ProjectionConfidence.LOW;
        Integer year = confidence == ProjectionConfidence.LOW ? null : graduationYearOf(input.today(), estimated);
        CreditsPerPeriodSource source = usesStudentPace ? CreditsPerPeriodSource.STUDENT_HISTORY
                : CreditsPerPeriodSource.PLAN_THEORETICAL;
        ProjectionMethod method = usesStudentPace ? ProjectionMethod.HISTORICAL_PACE_WITH_PREREQUISITES
                : ProjectionMethod.PLAN_PACE_WITH_PREREQUISITES;
        Basis basis = new Basis(input.remainingCredits(), input.mandatoryTotal() - input.mandatoryApproved(),
                plan.targetElectiveCount(), pace, source, input.closedPeriods(), criticalPath);
        return new GraduationProjection(input.programEnrollmentId(), ProjectionStatus.PROJECTED, estimated,
                new PeriodRange(minPeriods, estimated), year, method, confidence, basis, assumptionsOf(),
                warningsOf(input, usesStudentPace, plan), List.of());
    }

    public ProjectionConfidence confidenceOf(int closedPeriods, int approvedSubjects) {
        if (closedPeriods >= HIGH_MIN_CLOSED_PERIODS && approvedSubjects >= HIGH_MIN_APPROVED_SUBJECTS) {
            return ProjectionConfidence.HIGH;
        }
        boolean mediumByPeriods = closedPeriods >= MEDIUM_MIN_CLOSED_PERIODS;
        boolean mediumBySubjects = approvedSubjects >= MEDIUM_MIN_APPROVED_SUBJECTS;
        return mediumByPeriods || mediumBySubjects ? ProjectionConfidence.MEDIUM : ProjectionConfidence.LOW;
    }

    public int closedPeriodsBetween(LocalDate firstCourseStart, LocalDate today) {
        if (firstCourseStart == null) {
            return 0;
        }
        return Math.max(0, periodIndexOf(today) - periodIndexOf(firstCourseStart));
    }

    public int graduationYearOf(LocalDate today, int periods) {
        return (periodIndexOf(today) + periods - 1) / PERIODS_PER_YEAR;
    }

    private int periodIndexOf(LocalDate date) {
        return date.getYear() * PERIODS_PER_YEAR + (date.getMonthValue() <= 6 ? 0 : 1);
    }

    private CurricularTerm firstPeriodTermOf(LocalDate today) {
        return today.getMonthValue() <= 6 ? CurricularTerm.FIRST : CurricularTerm.SECOND;
    }

    private BigDecimal planPaceOf(int requiredCredits, Integer totalYears) {
        if (totalYears == null || totalYears <= 0) {
            return null;
        }
        return BigDecimal.valueOf(requiredCredits)
                .divide(BigDecimal.valueOf((long) totalYears * PERIODS_PER_YEAR), 2, RoundingMode.HALF_UP);
    }

    private int ceilDivide(int credits, BigDecimal pace) {
        if (pace.signum() <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(credits).divide(pace, 0, RoundingMode.CEILING).intValue();
    }

    private boolean requirementsAreMet(GraduationProjectionInput input) {
        return input.remainingCredits() == 0 && input.mandatoryApproved() >= input.mandatoryTotal()
                && input.electiveGroups().stream().allMatch(ProjectionElectiveGroup::fulfilled);
    }

    private GraduationProjection requirementsMet(GraduationProjectionInput input) {
        return new GraduationProjection(input.programEnrollmentId(), ProjectionStatus.REQUIREMENTS_MET, 0, null,
                null, null, null, null, List.of(), List.of(), List.of());
    }

    private GraduationProjection notProjectable(GraduationProjectionInput input, ProjectionBlockReason... reasons) {
        return new GraduationProjection(input.programEnrollmentId(), ProjectionStatus.NOT_PROJECTABLE, null,
                null, null, null, null, null, List.of(), List.of(), List.of(reasons));
    }

    private List<Message> assumptionsOf() {
        return List.of(
                new Message("PACE_CONSTANT", "Se asume que mantenés el ritmo de créditos por cuatrimestre."),
                new Message("COURSED_SUBJECTS_APPROVED", "Se asume que aprobás todas las materias que cursás."),
                new Message("CURRENT_TERM_APPROVED_AT_CLOSE", "Las materias en curso y las regulares se aprueban al cierre del cuatrimestre actual."),
                new Message("SUBJECTS_OFFERED_EVERY_TERM", "Se asume que cada materia se dicta en el cuatrimestre que le corresponde."),
                new Message("PLAN_UNCHANGED", "Se asume que el plan de estudios no cambia."));
    }

    private List<Message> warningsOf(GraduationProjectionInput input, boolean usesStudentPace, Plan plan) {
        List<Message> warnings = new ArrayList<>();
        if (input.planChangedAfterEnrollment()) {
            warnings.add(new Message("PLAN_CHANGED_AFTER_ENROLLMENT",
                    "El plan de estudios cambió desde la inscripción: hay materias aprobadas que ya no forman parte del plan y no suman créditos."));
        }
        if (!usesStudentPace) {
            warnings.add(new Message("PACE_BASED_ON_FEW_PERIODS",
                    "No hay suficiente historial: la estimación usa el ritmo teórico del plan."));
        }
        if (plan.assumesFinalsPassed()) {
            warnings.add(new Message("REGULAR_FINALS_ASSUMED_PASSED",
                    "Se asume que aprobás los finales pendientes y las materias en curso."));
        }
        if (input.hasCorequisites()) {
            warnings.add(new Message("COREQUISITES_NOT_ENFORCED",
                    "Las materias correlativas simultáneas (corequisitos) no se exigen en la inscripción."));
        }
        return warnings;
    }

    private static final class Plan {
        private final GraduationProjectionInput input;
        private final Map<Long, ProjectionSubject> planBySubjectId = new HashMap<>();
        private final Set<Long> approvedSubjectIds = new HashSet<>();
        private final Set<Long> securedSubjectIds = new HashSet<>();
        private final Set<Long> targetSubjectIds = new LinkedHashSet<>();
        private final Map<Long, Integer> heightBySubjectId = new HashMap<>();

        Plan(GraduationProjectionInput input) {
            this.input = input;
            for (ProjectionSubject subject : input.subjects()) {
                planBySubjectId.put(subject.subjectId(), subject);
                switch (subject.state()) {
                    case APPROVED -> approvedSubjectIds.add(subject.subjectId());
                    case IN_PROGRESS, REGULAR_PENDING_FINAL -> securedSubjectIds.add(subject.subjectId());
                    default -> {
                    }
                }
            }
        }

        boolean assumesFinalsPassed() {
            return !securedSubjectIds.isEmpty();
        }

        int targetElectiveCount() {
            return (int) targetSubjectIds.stream().filter(id -> planBySubjectId.get(id).electiveGroupId() != null).count();
        }

        Set<ProjectionBlockReason> selectTargets() {
            Set<ProjectionBlockReason> reasons = EnumSet.noneOf(ProjectionBlockReason.class);
            input.subjects().stream()
                    .filter(subject -> isPending(subject) && subject.electiveGroupId() == null)
                    .forEach(subject -> targetSubjectIds.add(subject.subjectId()));
            Map<Long, Integer> takenByGroup = new HashMap<>();
            for (ProjectionSubject subject : input.subjects()) {
                if (subject.electiveGroupId() != null && !isPending(subject)) {
                    takenByGroup.merge(subject.electiveGroupId(), 1, Integer::sum);
                }
            }
            for (ProjectionElectiveGroup group : input.electiveGroups()) {
                int missing = group.minRequired() - takenByGroup.getOrDefault(group.electiveGroupId(), 0);
                List<ProjectionSubject> candidates = pendingElectivesOf(group.electiveGroupId());
                if (missing > candidates.size()) {
                    reasons.add(ProjectionBlockReason.CREDITS_UNREACHABLE);
                }
                for (int index = 0; index < Math.min(Math.max(0, missing), candidates.size()); index++) {
                    targetSubjectIds.add(candidates.get(index).subjectId());
                    takenByGroup.merge(group.electiveGroupId(), 1, Integer::sum);
                }
            }
            includePendingPrerequisitesOfTargets(takenByGroup);
            addExtraElectivesUntilCreditsAreReached(takenByGroup, reasons);
            return reasons;
        }

        private void includePendingPrerequisitesOfTargets(Map<Long, Integer> takenByGroup) {
            boolean grew = true;
            while (grew) {
                grew = false;
                for (Long targetId : new ArrayList<>(targetSubjectIds)) {
                    for (Long blockerId : planBySubjectId.get(targetId).unmetPrerequisiteSubjectIds()) {
                        ProjectionSubject prerequisite = planBySubjectId.get(blockerId);
                        if (prerequisite != null && isPending(prerequisite) && targetSubjectIds.add(prerequisite.subjectId())) {
                            if (prerequisite.electiveGroupId() != null) {
                                takenByGroup.merge(prerequisite.electiveGroupId(), 1, Integer::sum);
                            }
                            grew = true;
                        }
                    }
                }
            }
        }

        private void addExtraElectivesUntilCreditsAreReached(Map<Long, Integer> takenByGroup,
                Set<ProjectionBlockReason> reasons) {
            int projected = projectedCredits();
            int required = input.requiredCredits();
            if (projected >= required) {
                return;
            }
            List<ProjectionSubject> pool = input.subjects().stream()
                    .filter(subject -> isPending(subject) && subject.electiveGroupId() != null
                            && !targetSubjectIds.contains(subject.subjectId()))
                    .sorted(BY_CURRICULAR_ORDER).toList();
            Map<Long, Integer> maxByGroup = new HashMap<>();
            input.electiveGroups()
                    .forEach(group -> maxByGroup.put(group.electiveGroupId(), group.maxAllowed()));
            for (ProjectionSubject candidate : pool) {
                if (projected >= required) {
                    break;
                }
                Long groupId = candidate.electiveGroupId();
                Integer max = maxByGroup.get(groupId);
                if (max != null && takenByGroup.getOrDefault(groupId, 0) >= max) {
                    continue;
                }
                targetSubjectIds.add(candidate.subjectId());
                takenByGroup.merge(groupId, 1, Integer::sum);
                projected += candidate.credits();
            }
            if (projected < required) {
                reasons.add(ProjectionBlockReason.CREDITS_UNREACHABLE);
            }
        }

        private int projectedCredits() {
            return input.accumulatedCredits()
                    + securedSubjectIds.stream().mapToInt(id -> planBySubjectId.get(id).credits()).sum()
                    + targetSubjectIds.stream().mapToInt(id -> planBySubjectId.get(id).credits()).sum();
        }

        private List<ProjectionSubject> pendingElectivesOf(Long groupId) {
            return input.subjects().stream()
                    .filter(subject -> isPending(subject) && groupId.equals(subject.electiveGroupId())
                            && !targetSubjectIds.contains(subject.subjectId()))
                    .sorted(BY_CURRICULAR_ORDER).toList();
        }

        private boolean isPending(ProjectionSubject subject) {
            return !approvedSubjectIds.contains(subject.subjectId()) && !securedSubjectIds.contains(subject.subjectId());
        }

        private List<Long> unmetPrerequisitesOf(Long subjectId) {
            return planBySubjectId.get(subjectId).unmetPrerequisiteSubjectIds();
        }

        Set<ProjectionBlockReason> detectStructuralProblems() {
            Set<ProjectionBlockReason> reasons = EnumSet.noneOf(ProjectionBlockReason.class);
            for (Long targetId : targetSubjectIds) {
                if (unmetPrerequisitesOf(targetId).stream().anyMatch(id -> !planBySubjectId.containsKey(id))) {
                    reasons.add(ProjectionBlockReason.REQUIRED_SUBJECT_INACTIVE);
                }
            }
            if (!reasons.isEmpty()) {
                return reasons;
            }
            if (hasCycle()) {
                reasons.add(ProjectionBlockReason.PREREQUISITE_CYCLE);
            }
            return reasons;
        }

        private boolean hasCycle() {
            Set<Long> finished = new HashSet<>();
            for (Long targetId : targetSubjectIds) {
                if (visitFindsCycle(targetId, new HashSet<>(), finished)) {
                    return true;
                }
            }
            return false;
        }

        private boolean visitFindsCycle(Long subjectId, Set<Long> path, Set<Long> finished) {
            if (finished.contains(subjectId)) {
                return false;
            }
            if (!path.add(subjectId)) {
                return true;
            }
            for (Long prerequisiteId : unmetPrerequisitesOf(subjectId)) {
                if (targetSubjectIds.contains(prerequisiteId) && visitFindsCycle(prerequisiteId, path, finished)) {
                    return true;
                }
            }
            path.remove(subjectId);
            finished.add(subjectId);
            return false;
        }

        private int weightOf(Long subjectId) {
            return planBySubjectId.get(subjectId).curricularTerm() == CurricularTerm.ANNUAL ? 2 : 1;
        }

        int criticalPathPeriods() {
            Map<Long, Integer> depthBySubjectId = new HashMap<>();
            int longest = 0;
            for (Long targetId : targetSubjectIds) {
                longest = Math.max(longest, depthOf(targetId, depthBySubjectId));
            }
            targetSubjectIds.forEach(this::heightOf);
            return longest;
        }

        private int depthOf(Long subjectId, Map<Long, Integer> memo) {
            Integer known = memo.get(subjectId);
            if (known != null) {
                return known;
            }
            int deepestPrerequisite = unmetPrerequisitesOf(subjectId).stream()
                    .filter(targetSubjectIds::contains).mapToInt(id -> depthOf(id, memo)).max().orElse(0);
            int depth = weightOf(subjectId) + deepestPrerequisite;
            memo.put(subjectId, depth);
            return depth;
        }

        private int heightOf(Long subjectId) {
            Integer known = heightBySubjectId.get(subjectId);
            if (known != null) {
                return known;
            }
            int tallestDependent = targetSubjectIds.stream()
                    .filter(id -> unmetPrerequisitesOf(id).contains(subjectId)).mapToInt(this::heightOf).max().orElse(0);
            int height = weightOf(subjectId) + tallestDependent;
            heightBySubjectId.put(subjectId, height);
            return height;
        }

        int simulate(BigDecimal creditsPerPeriod, CurricularTerm firstPeriodTerm) {
            Set<Long> approved = new HashSet<>(approvedSubjectIds);
            Set<Long> started = new HashSet<>();
            Map<Integer, List<Long>> endingByPeriod = new HashMap<>();
            Map<Integer, Integer> carriedLoadByPeriod = new HashMap<>();
            int inProgressCredits = input.subjects().stream()
                    .filter(subject -> subject.state() == CurriculumSubjectState.IN_PROGRESS)
                    .mapToInt(ProjectionSubject::credits).sum();
            for (int period = 1; period <= MAX_SIMULATED_PERIODS; period++) {
                CurricularTerm periodTerm = termOf(period, firstPeriodTerm);
                int load = carriedLoadByPeriod.getOrDefault(period, 0) + (period == 1 ? inProgressCredits : 0);
                List<ProjectionSubject> startable = targetSubjectIds.stream()
                        .filter(id -> !started.contains(id))
                        .map(planBySubjectId::get)
                        .filter(subject -> approved.containsAll(unmetPrerequisitesOf(subject.subjectId())))
                        .filter(subject -> isOfferedIn(subject, periodTerm))
                        .sorted(Comparator.comparing((ProjectionSubject subject) -> -heightBySubjectId.get(subject.subjectId()))
                                .thenComparing(BY_CURRICULAR_ORDER))
                        .toList();
                for (ProjectionSubject subject : startable) {
                    BigDecimal loadWithSubject = BigDecimal.valueOf((long) load + firstPeriodLoadOf(subject));
                    if (load > 0 && loadWithSubject.compareTo(creditsPerPeriod) > 0) {
                        continue;
                    }
                    load += firstPeriodLoadOf(subject);
                    started.add(subject.subjectId());
                    boolean annual = subject.curricularTerm() == CurricularTerm.ANNUAL;
                    int endPeriod = annual ? period + 1 : period;
                    endingByPeriod.computeIfAbsent(endPeriod, key -> new ArrayList<>()).add(subject.subjectId());
                    if (annual) {
                        carriedLoadByPeriod.merge(period + 1, subject.credits() / 2, Integer::sum);
                    }
                }
                approved.addAll(endingByPeriod.getOrDefault(period, List.of()));
                if (period == 1) {
                    approved.addAll(securedSubjectIds);
                }
                if (approved.containsAll(targetSubjectIds)) {
                    return period;
                }
            }
            return -1;
        }

        private int firstPeriodLoadOf(ProjectionSubject subject) {
            return subject.curricularTerm() == CurricularTerm.ANNUAL ? (subject.credits() + 1) / 2 : subject.credits();
        }

        private CurricularTerm termOf(int period, CurricularTerm firstPeriodTerm) {
            boolean sameAsFirst = (period - 1) % 2 == 0;
            if (sameAsFirst) {
                return firstPeriodTerm;
            }
            return firstPeriodTerm == CurricularTerm.FIRST ? CurricularTerm.SECOND : CurricularTerm.FIRST;
        }

        private boolean isOfferedIn(ProjectionSubject subject, CurricularTerm periodTerm) {
            CurricularTerm term = subject.curricularTerm();
            if (term == null) {
                return true;
            }
            return term == CurricularTerm.ANNUAL ? periodTerm == CurricularTerm.FIRST : term == periodTerm;
        }
    }
}
