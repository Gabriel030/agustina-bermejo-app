package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.AllocationSettings;
import com.red_escuela.backend.billing.configuration.domain.AnnualUpfrontSettings;
import com.red_escuela.backend.billing.configuration.domain.BillingDashboardGoals;
import com.red_escuela.backend.billing.configuration.domain.CohortEntryDiscount;
import com.red_escuela.backend.billing.configuration.domain.CohortEntryVersion;
import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentVersion;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.CourseReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.CourseTerms;
import com.red_escuela.backend.billing.configuration.domain.ProgramReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleProposal;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleResolver;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTopicState;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleVersion;
import com.red_escuela.backend.billing.configuration.domain.TeachingGroup;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;
import com.red_escuela.backend.billing.configuration.domain.ResolvedReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.IrregularTermTypes;
import com.red_escuela.backend.billing.configuration.domain.IssuanceSettings;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.domain.EffectiveConfigurations;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

@Component
@Transactional(readOnly = true)
public class BillingConfigurationReader {
    private final ConfigurationVersionRepository repository;

    public BillingConfigurationReader(ConfigurationVersionRepository repository) {
        this.repository = repository;
    }

    public Optional<YearBillingTerms> findYearTermsEffectiveOn(Long programId, Long termId, LocalDate date) {
        return findEffective(ConfigurationTopic.ofYearTerms(programId, termId), date, YearBillingTerms.class);
    }

    public Optional<CohortEntryDiscount> findCohortEntryEffectiveOn(Long cohortId, LocalDate date) {
        return findEffective(ConfigurationTopic.ofCohortEntry(cohortId), date, CohortEntryDiscount.class);
    }

    public Optional<CohortEntryVersion> findCohortEntryVersionEffectiveOn(Long cohortId, LocalDate date) {
        ConfigurationTopic topic = ConfigurationTopic.ofCohortEntry(cohortId);
        return repository.findEffectiveVersion(topic.scope().name(), topic.key(), date)
                .map(version -> new CohortEntryVersion(version.getId(), CohortEntryDiscount.class.cast(version.values())));
    }

    public Optional<LatePaymentTerms> findLatePaymentSettingsEffectiveOn(LocalDate date) {
        return findEffective(ConfigurationTopic.ofGlobal(ConfigurationScope.LATE_PAYMENT), date, LatePaymentTerms.class);
    }

    public Optional<LatePaymentTerms> findFirstLatePaymentSettings() {
        ConfigurationTopic topic = ConfigurationTopic.ofGlobal(ConfigurationScope.LATE_PAYMENT);
        return repository.findFirstVersion(topic.scope().name(), topic.key()).map(version -> LatePaymentTerms.class.cast(version.values()));
    }

    public Optional<LatePaymentVersion> findLatePaymentVersionEffectiveOn(LocalDate date) {
        ConfigurationTopic topic = ConfigurationTopic.ofGlobal(ConfigurationScope.LATE_PAYMENT);
        return repository.findEffectiveVersion(topic.scope().name(), topic.key(), date).map(BillingConfigurationReader::toLatePaymentVersion);
    }

    public NavigableMap<LocalDate, LatePaymentVersion> latePaymentTimelineUpTo(LocalDate date) {
        ConfigurationTopic topic = ConfigurationTopic.ofGlobal(ConfigurationScope.LATE_PAYMENT);
        NavigableMap<LocalDate, LatePaymentVersion> timeline = new TreeMap<>();
        repository.findVersionsEffectiveUpTo(topic.scope().name(), topic.key(), date)
                .forEach(version -> timeline.put(version.getEffectiveFrom(), toLatePaymentVersion(version)));
        return timeline;
    }

    public Optional<LatePaymentVersion> findFirstLatePaymentVersion() {
        ConfigurationTopic topic = ConfigurationTopic.ofGlobal(ConfigurationScope.LATE_PAYMENT);
        return repository.findFirstVersion(topic.scope().name(), topic.key()).map(BillingConfigurationReader::toLatePaymentVersion);
    }

    private static LatePaymentVersion toLatePaymentVersion(ConfigurationVersion version) {
        return new LatePaymentVersion(version.getId(), LatePaymentTerms.class.cast(version.values()));
    }

    public Optional<IssuanceSettings> findIssuanceSettingsEffectiveOn(LocalDate date) {
        return findEffective(ConfigurationTopic.ofGlobal(ConfigurationScope.ISSUANCE), date, IssuanceSettings.class);
    }

    public Optional<AllocationSettings> findAllocationSettingsEffectiveOn(LocalDate date) {
        return findEffective(ConfigurationTopic.ofGlobal(ConfigurationScope.ALLOCATION), date, AllocationSettings.class);
    }

    public Optional<AnnualUpfrontSettings> findAnnualUpfrontSettingsEffectiveOn(Long programId, Long termId, LocalDate date) {
        return findEffective(ConfigurationTopic.ofAnnualUpfront(programId, termId), date, AnnualUpfrontSettings.class);
    }

    public Optional<CourseTerms> findCourseTermsEffectiveOn(Long courseId, LocalDate date) {
        return findEffective(ConfigurationTopic.ofCourseTerms(courseId), date, CourseTerms.class);
    }

    public Optional<IrregularTermTypes> findIrregularTermTypesEffectiveOn(LocalDate date) {
        return findEffective(ConfigurationTopic.ofGlobal(ConfigurationScope.IRREGULAR_TERMS), date, IrregularTermTypes.class);
    }

    public Optional<BillingDashboardGoals> findBillingDashboardGoalsEffectiveOn(LocalDate date) {
        return findEffective(ConfigurationTopic.ofGlobal(ConfigurationScope.BILLING_DASHBOARD_GOALS), date, BillingDashboardGoals.class);
    }

    public Optional<CollectionReminderSettings> findCollectionReminderSettingsEffectiveOn(LocalDate date) {
        return findEffective(ConfigurationTopic.ofGlobal(ConfigurationScope.COLLECTION_REMINDERS), date, CollectionReminderSettings.class);
    }

    public CollectionReminderSettings collectionReminderSettingsEffectiveOn(LocalDate date) {
        return findCollectionReminderSettingsEffectiveOn(date).orElse(CollectionReminderSettings.initial());
    }

    public ResolvedReminderSchedule reminderScheduleFor(ReminderScheduleTarget target, LocalDate date) {
        return resolveReminderSchedule(target, date, null);
    }

    public ResolvedReminderSchedule reminderScheduleWithProposal(ReminderScheduleTarget target, LocalDate date, ReminderScheduleProposal proposal) {
        return resolveReminderSchedule(target, date, Objects.requireNonNull(proposal, "proposal"));
    }

    private ResolvedReminderSchedule resolveReminderSchedule(ReminderScheduleTarget target, LocalDate date, ReminderScheduleProposal proposal) {
        Optional<ReminderScheduleVersion> exception = target.targetId() == null ? Optional.empty()
                : proposedVersionFor(proposal, target.group(), target.targetId())
                        .or(() -> findReminderScheduleVersion(ConfigurationTopic.ofReminderScheduleException(target.group(), target.targetId()), date));
        Optional<ReminderScheduleVersion> group = proposedVersionFor(proposal, target.group(), null)
                .or(() -> findReminderScheduleVersion(ConfigurationTopic.ofReminderScheduleGroup(target.group()), date));
        ConfigurationTopic globalTopic = ConfigurationTopic.ofGlobal(ConfigurationScope.COLLECTION_REMINDERS);
        Optional<ConfigurationVersion> globalVersion = repository.findEffectiveVersion(globalTopic.scope().name(), globalTopic.key(), date);
        CollectionReminderSettings globalSettings = globalVersion.map(version -> CollectionReminderSettings.class.cast(version.values()))
                .orElse(CollectionReminderSettings.initial());
        return ReminderScheduleResolver.resolve(exception, group, globalSettings, globalVersion.map(ConfigurationVersion::getId).orElse(null));
    }

    private static Optional<ReminderScheduleVersion> proposedVersionFor(ReminderScheduleProposal proposal, TeachingGroup group, Long targetId) {
        if (proposal == null || proposal.target().group() != group || !Objects.equals(proposal.target().targetId(), targetId)) {
            return Optional.empty();
        }
        return Optional.of(new ReminderScheduleVersion(null, proposal.schedule()));
    }

    public List<ReminderScheduleTopicState> listReminderScheduleTopicsEffectiveOn(TeachingGroup group, LocalDate date) {
        List<ConfigurationTopic> topics = new ArrayList<>(List.of(ConfigurationTopic.ofReminderScheduleGroup(group)));
        repository.findTopicsByScope(group.scope()).stream().filter(ConfigurationTopic::isReminderScheduleException)
                .sorted(Comparator.comparing(ConfigurationTopic::key)).forEach(topics::add);
        return topics.stream().map(topic -> reminderScheduleTopicState(topic, date)).toList();
    }

    private ReminderScheduleTopicState reminderScheduleTopicState(ConfigurationTopic topic, LocalDate date) {
        Optional<ConfigurationVersion> effective = repository.findEffectiveVersion(topic.scope().name(), topic.key(), date);
        boolean openProposal = repository.existsByScopeAndTopicKeyAndStatus(topic.scope(), topic.key(), ConfigurationVersionStatus.PROPOSED);
        LocalDate followsGroupFrom = repository.findByScopeAndTopicKeyAndStatus(topic.scope(), topic.key(), ConfigurationVersionStatus.SCHEDULED).stream()
                .filter(version -> version.getEffectiveFrom().isAfter(date) && reminderScheduleOf(version.values()).followsGroupSchedule())
                .map(ConfigurationVersion::getEffectiveFrom).min(Comparator.naturalOrder()).orElse(null);
        return new ReminderScheduleTopicState(topic, effective.map(ConfigurationVersion::getId).orElse(null), effective.map(ConfigurationVersion::getEffectiveFrom).orElse(null),
                effective.map(version -> reminderScheduleOf(version.values())).orElse(null), openProposal, followsGroupFrom);
    }

    private Optional<ReminderScheduleVersion> findReminderScheduleVersion(ConfigurationTopic topic, LocalDate date) {
        return repository.findEffectiveVersion(topic.scope().name(), topic.key(), date)
                .map(version -> new ReminderScheduleVersion(version.getId(), reminderScheduleOf(version.values())));
    }

    private static ReminderSchedule reminderScheduleOf(ConfigurationValues values) {
        return switch (values) {
            case ProgramReminderSchedule program -> program.schedule();
            case CourseReminderSchedule course -> course.schedule();
            default -> throw new IllegalStateException("La versión no es una lista de avisos: " + values.scope());
        };
    }

    public YearBillingTerms requireYearTermsEffectiveOn(Long programId, Long termId, LocalDate date) {
        return EffectiveConfigurations.requireInEffect(findYearTermsEffectiveOn(programId, termId, date), ConfigurationTopic.ofYearTerms(programId, termId), date);
    }

    public LatePaymentTerms requireLatePaymentSettingsEffectiveOn(LocalDate date) {
        return EffectiveConfigurations.requireInEffect(findLatePaymentSettingsEffectiveOn(date), ConfigurationTopic.ofGlobal(ConfigurationScope.LATE_PAYMENT), date);
    }

    public Percentage cohortEntryDiscountEffectiveOn(Long cohortId, LocalDate date) {
        return findCohortEntryEffectiveOn(cohortId, date).map(CohortEntryDiscount::discount).orElse(Percentage.ofFraction(java.math.BigDecimal.ZERO));
    }

    public CourseTerms courseTermsEffectiveOn(Long courseId, LocalDate date) {
        return findCourseTermsEffectiveOn(courseId, date).orElse(CourseTerms.initial());
    }

    public IrregularTermTypes irregularTermTypesEffectiveOn(LocalDate date) {
        return findIrregularTermTypesEffectiveOn(date).orElse(new IrregularTermTypes(java.util.List.of()));
    }

    public BillingDashboardGoals billingDashboardGoalsEffectiveOn(LocalDate date) {
        return findBillingDashboardGoalsEffectiveOn(date).orElse(BillingDashboardGoals.initial());
    }

    private <V extends ConfigurationValues> Optional<V> findEffective(ConfigurationTopic topic, LocalDate date, Class<V> valuesType) {
        return repository.findEffectiveVersion(topic.scope().name(), topic.key(), date).map(version -> valuesType.cast(version.values()));
    }
}
