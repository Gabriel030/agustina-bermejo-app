package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.AllocationSettings;
import com.red_escuela.backend.billing.configuration.domain.AnnualUpfrontSettings;
import com.red_escuela.backend.billing.configuration.domain.BillingDashboardGoals;
import com.red_escuela.backend.billing.configuration.domain.CohortEntryDiscount;
import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentVersion;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.CourseReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.CourseTerms;
import com.red_escuela.backend.billing.configuration.domain.ProgramReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestoneDates;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleResolver;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleVersion;
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
import java.util.NavigableMap;
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
        return reminderScheduleFor(target, date, Optional.empty());
    }

    public ResolvedReminderSchedule reminderScheduleFor(ReminderScheduleTarget target, LocalDate date, Optional<ReminderMilestoneDates> milestoneDates) {
        Optional<ReminderScheduleVersion> exception = target.targetId() == null ? Optional.empty()
                : findReminderScheduleVersion(ConfigurationTopic.ofReminderScheduleException(target.group(), target.targetId()), date);
        Optional<ReminderScheduleVersion> group = findReminderScheduleVersion(ConfigurationTopic.ofReminderScheduleGroup(target.group()), date);
        ConfigurationTopic globalTopic = ConfigurationTopic.ofGlobal(ConfigurationScope.COLLECTION_REMINDERS);
        Optional<ConfigurationVersion> globalVersion = repository.findEffectiveVersion(globalTopic.scope().name(), globalTopic.key(), date);
        CollectionReminderSettings globalSettings = globalVersion.map(version -> CollectionReminderSettings.class.cast(version.values()))
                .orElse(CollectionReminderSettings.initial());
        return ReminderScheduleResolver.resolve(exception, group, globalSettings, globalVersion.map(ConfigurationVersion::getId).orElse(null), milestoneDates);
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
