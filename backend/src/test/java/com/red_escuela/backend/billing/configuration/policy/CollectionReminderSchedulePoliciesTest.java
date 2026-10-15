package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import com.red_escuela.backend.billing.configuration.domain.ReminderRepetition;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderSendingOverrides;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionReminderSchedulePoliciesTest {
    private static final Set<ReminderChannel> APP = EnumSet.of(ReminderChannel.IN_APP);
    private static final Set<ReminderChannel> BOTH = EnumSet.allOf(ReminderChannel.class);

    private final List<Policy<ReminderScheduleCheck>> coherenceRules = List.of(new ReminderScheduleSizePolicy(), new ReminderNoticeTimingPolicy(),
            new ReminderNoticeChannelsPolicy(), new ReminderIssuanceNoticePolicy(), new ReminderRepetitionPolicy(), new ReminderNoticeUniquenessPolicy());
    private final ReminderScheduleFollowsGroupPolicy followsGroup = new ReminderScheduleFollowsGroupPolicy();
    private final ReminderSendingOverridesCoherencePolicy sending = new ReminderSendingOverridesCoherencePolicy();

    private PolicyVerdict firstCoherenceDenial(ReminderScheduleCheck check) {
        return coherenceRules.stream().map(rule -> rule.evaluateRule(check)).filter(verdict -> !verdict.allowed()).findFirst()
                .orElse(PolicyVerdict.allow("coherence"));
    }

    private static ReminderNotice notice(String key, ReminderMessageType type, ReminderMilestone milestone, int days, Set<ReminderChannel> channels) {
        return new ReminderNotice(key, type, milestone, days, channels, null);
    }

    private static ReminderNotice repeating(String key, ReminderMilestone milestone, int days, int everyDays, ReminderMilestone until) {
        return new ReminderNotice(key, ReminderMessageType.AFTER_DUE_REMINDER, milestone, days, BOTH, new ReminderRepetition(everyDays, until));
    }

    private static ReminderSchedule schedule(ReminderNotice... notices) {
        return new ReminderSchedule(false, List.of(notices), null);
    }

    private static Arguments invalid(String name, ReminderSchedule schedule, String expectedPath) {
        return Arguments.of(name, schedule, expectedPath);
    }

    static Stream<Arguments> invalidSchedules() {
        List<ReminderNotice> twentyOne = new ArrayList<>(IntStream.range(0, 21)
                .mapToObj(day -> notice("k" + day, ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, day - 30, BOTH)).toList());
        return Stream.of(
                invalid("emission by email", schedule(notice("a", ReminderMessageType.ISSUANCE_NOTICE, ReminderMilestone.ISSUANCE, 0, BOTH)), "notices[0].channels"),
                invalid("emission plus one day", schedule(notice("a", ReminderMessageType.ISSUANCE_NOTICE, ReminderMilestone.ISSUANCE, 1, APP)), "notices[0].messageType"),
                invalid("emission with repetition", schedule(new ReminderNotice("a", ReminderMessageType.ISSUANCE_NOTICE, ReminderMilestone.ISSUANCE, 0, APP,
                        new ReminderRepetition(2, ReminderMilestone.DUE_DATE))), "notices[0].messageType"),
                invalid("two emissions", schedule(notice("a", ReminderMessageType.ISSUANCE_NOTICE, ReminderMilestone.ISSUANCE, 0, APP),
                        notice("b", ReminderMessageType.ISSUANCE_NOTICE, ReminderMilestone.ISSUANCE, 0, APP)), "notices[1].messageType"),
                invalid("two notices on the same milestone and day", schedule(notice("a", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -1, BOTH),
                        notice("b", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, -1, BOTH)), "notices[1].offsetDays"),
                invalid("days 61", schedule(notice("a", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 61, BOTH)), "notices[0].offsetDays"),
                invalid("days minus 61", schedule(notice("a", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -61, BOTH)), "notices[0].offsetDays"),
                invalid("suspension plus one", schedule(notice("a", ReminderMessageType.BEFORE_SUSPENSION_REMINDER, ReminderMilestone.SUSPENSION, 1, BOTH)), "notices[0].offsetDays"),
                invalid("issuance minus one", schedule(notice("a", ReminderMessageType.AFTER_ISSUE_REMINDER, ReminderMilestone.ISSUANCE, -1, BOTH)), "notices[0].offsetDays"),
                invalid("no channels", schedule(notice("a", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 3, Set.of())), "notices[0].channels"),
                invalid("email on emission day for another type", schedule(notice("a", ReminderMessageType.AFTER_ISSUE_REMINDER, ReminderMilestone.ISSUANCE, 0, BOTH)),
                        "notices[0].channels"),
                invalid("repeat every 0 days", schedule(repeating("a", ReminderMilestone.DUE_DATE, 3, 0, ReminderMilestone.SUSPENSION)), "notices[0].repetition.everyDays"),
                invalid("repeat every 61 days", schedule(repeating("a", ReminderMilestone.DUE_DATE, 3, 61, ReminderMilestone.SUSPENSION)), "notices[0].repetition.everyDays"),
                invalid("repeat until due date from due date plus 3", schedule(repeating("a", ReminderMilestone.DUE_DATE, 3, 7, ReminderMilestone.DUE_DATE)),
                        "notices[0].repetition.until"),
                invalid("repeat until due date from the due date itself", schedule(repeating("a", ReminderMilestone.DUE_DATE, 0, 7, ReminderMilestone.DUE_DATE)),
                        "notices[0].repetition.until"),
                invalid("repeat until suspension from suspension", schedule(repeating("a", ReminderMilestone.SUSPENSION, -2, 1, ReminderMilestone.SUSPENSION)),
                        "notices[0].repetition.until"),
                invalid("repeat until issuance", schedule(repeating("a", ReminderMilestone.DUE_DATE, 3, 7, ReminderMilestone.ISSUANCE)), "notices[0].repetition.until"),
                invalid("repeat without until", schedule(repeating("a", ReminderMilestone.DUE_DATE, 3, 7, null)), "notices[0].repetition.until"),
                invalid("repeated notice key", schedule(notice("a", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -2, BOTH),
                        notice("a", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 5, BOTH)), "notices[1].noticeKey"),
                invalid("twenty one notices", new ReminderSchedule(false, twentyOne, null), "notices:"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSchedules")
    @Tag("D-368")
    void invalidListsAreDeniedWithTheNoticePathInTheReason(String name, ReminderSchedule schedule, String expectedPath) {
        PolicyVerdict verdict = firstCoherenceDenial(new ReminderScheduleCheck(schedule, false));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("INVALID_COLLECTION_REMINDER_SCHEDULE");
        assertThat(coherenceRules).allSatisfy(rule -> assertThat(rule.getClass().getAnnotation(PolicyRule.class).violationCodes()).contains(verdict.code()));
        assertThat(verdict.reason()).startsWith(expectedPath);
    }

    @Test
    @Tag("D-368")
    void everyBrokenRuleReportsItsOwnViolationWithTheNoticeIndex() {
        ReminderSchedule schedule = schedule(
                notice("a", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 61, BOTH),
                notice("b", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 5, Set.of()),
                repeating("c", ReminderMilestone.DUE_DATE, 9, 0, null),
                notice("c", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 10, BOTH));

        List<String> reasons = coherenceRules.stream().map(rule -> rule.evaluateRule(new ReminderScheduleCheck(schedule, false)))
                .filter(verdict -> !verdict.allowed()).map(PolicyVerdict::reason).toList();

        assertThat(reasons).hasSize(4);
        assertThat(reasons.get(0)).startsWith("notices[0].offsetDays");
        assertThat(reasons.get(1)).startsWith("notices[1].channels");
        assertThat(reasons.get(2)).startsWith("notices[2].repetition.everyDays");
        assertThat(reasons.get(3)).startsWith("notices[3].noticeKey");
    }

    @Test
    @Tag("D-368")
    void aChangeThatAsksToFollowTheGroupIsDeniedAndPointsToRemoval() {
        ReminderChangeDoesNotFollowGroupPolicy policy = new ReminderChangeDoesNotFollowGroupPolicy();

        PolicyVerdict denied = policy.evaluateRule(new ReminderScheduleChangeCheck(ReminderSchedule.followingGroup()));

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.code()).isEqualTo("INVALID_COLLECTION_REMINDER_SCHEDULE");
        assertThat(denied.reason()).contains("/removal");
        assertThat(policy.evaluateRule(new ReminderScheduleChangeCheck(ReminderSchedule.initial())).allowed()).isTrue();
    }

    static Stream<ReminderSchedule> validSchedules() {
        return Stream.of(
                ReminderSchedule.initial(),
                new ReminderSchedule(false, List.of(), null),
                new ReminderSchedule(false, IntStream.range(0, 20)
                        .mapToObj(day -> notice("k" + day, ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, day - 30, BOTH)).toList(), null),
                schedule(repeating("a", ReminderMilestone.DUE_DATE, 1, 7, ReminderMilestone.SUSPENSION)),
                schedule(repeating("a", ReminderMilestone.DUE_DATE, -5, 2, ReminderMilestone.DUE_DATE)),
                schedule(repeating("a", ReminderMilestone.DUE_DATE, -1, 2, ReminderMilestone.DUE_DATE)),
                schedule(repeating("a", ReminderMilestone.ISSUANCE, 3, 2, ReminderMilestone.DUE_DATE)),
                schedule(notice("a", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 60, APP),
                        notice("b", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -60, APP),
                        notice("c", ReminderMessageType.BEFORE_SUSPENSION_REMINDER, ReminderMilestone.SUSPENSION, 0, BOTH),
                        repeating("d", ReminderMilestone.DUE_DATE, 1, 60, ReminderMilestone.SUSPENSION)));
    }

    @ParameterizedTest
    @MethodSource("validSchedules")
    @Tag("D-368")
    void validListsAreAllowedIncludingTheEmptyListAndTheBounds(ReminderSchedule schedule) {
        assertThat(firstCoherenceDenial(new ReminderScheduleCheck(schedule, false)).allowed()).isTrue();
    }

    @Test
    @Tag("D-368")
    @Tag("D-370")
    void theInitialTemplateHasTheFiveNoticesOfD359InTimelineOrder() {
        List<ReminderNotice> ordered = ReminderSchedule.initial().orderedNotices();

        assertThat(ordered).extracting(ReminderNotice::noticeKey).containsExactly("issuance", "after-issue", "before-due", "after-due", "before-suspension");
        assertThat(ordered).extracting(ReminderNotice::offsetDays).containsExactly(0, 5, -1, 10, -1);
        assertThat(ordered.get(0).channels()).containsExactly(ReminderChannel.IN_APP);
        assertThat(ordered.get(4).channels()).containsExactly(ReminderChannel.IN_APP, ReminderChannel.EMAIL);
    }

    @Test
    @Tag("D-368")
    void followsGroupIsOnlyValidInAnExceptionTopicWithAnEmptyList() {
        ReminderSchedule following = ReminderSchedule.followingGroup();
        ReminderSchedule followingWithNotices = new ReminderSchedule(true, ReminderSchedule.initial().notices(), null);
        ReminderSchedule followingWithOverrides = new ReminderSchedule(true, List.of(), new ReminderSendingOverrides(null, null, null, null, null, null, 100, null, null));

        assertThat(followsGroup.evaluateRule(new ReminderScheduleCheck(following, true)).allowed()).isTrue();
        assertThat(followsGroup.evaluateRule(new ReminderScheduleCheck(following, false)).allowed()).isFalse();
        assertThat(followsGroup.evaluateRule(new ReminderScheduleCheck(followingWithNotices, true)).allowed()).isFalse();
        assertThat(followsGroup.evaluateRule(new ReminderScheduleCheck(followingWithOverrides, true)).allowed()).isFalse();
        assertThat(followsGroup.evaluateRule(new ReminderScheduleCheck(followingWithNotices, true)).code()).isEqualTo("INVALID_COLLECTION_REMINDER_SCHEDULE");
        assertThat(followsGroup.evaluateRule(new ReminderScheduleCheck(ReminderSchedule.initial(), false)).allowed()).isTrue();
        assertThat(firstCoherenceDenial(new ReminderScheduleCheck(following, true)).allowed()).isTrue();
    }

    static Stream<Arguments> sendingOverrides() {
        LocalTime nine = LocalTime.of(9, 0);
        LocalTime eleven = LocalTime.of(11, 0);
        return Stream.of(
                Arguments.of("window start alone", new ReminderSendingOverrides(null, null, null, null, nine, null, null, null, null), false),
                Arguments.of("window end alone", new ReminderSendingOverrides(null, null, null, null, null, eleven, null, null, null), false),
                Arguments.of("window inverted", new ReminderSendingOverrides(null, null, null, null, eleven, nine, null, null, null), false),
                Arguments.of("window ok", new ReminderSendingOverrides(null, null, null, null, nine, eleven, null, null, null), true),
                Arguments.of("cap 0", new ReminderSendingOverrides(null, null, null, null, null, null, 0, null, null), false),
                Arguments.of("cap 1", new ReminderSendingOverrides(null, null, null, null, null, null, 1, null, null), true),
                Arguments.of("spacing 0", new ReminderSendingOverrides(null, null, 0, null, null, null, null, null, null), false),
                Arguments.of("monthly max 0", new ReminderSendingOverrides(null, null, null, 0, null, null, null, null, null), false),
                Arguments.of("months 0", new ReminderSendingOverrides(null, 0, null, null, null, null, null, null, null), false),
                Arguments.of("months 25", new ReminderSendingOverrides(null, 25, null, null, null, null, null, null, null), false),
                Arguments.of("months 1", new ReminderSendingOverrides(null, 1, null, null, null, null, null, null, null), true),
                Arguments.of("months 24", new ReminderSendingOverrides(null, 24, null, null, null, null, null, null, null), true),
                Arguments.of("contact days 0", new ReminderSendingOverrides(null, null, null, null, null, null, null, 0, null), false),
                Arguments.of("contact installments 0", new ReminderSendingOverrides(null, null, null, null, null, null, null, null, 0), false),
                Arguments.of("nothing defined", ReminderSendingOverrides.none(), true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sendingOverrides")
    @Tag("D-369")
    @Tag("D-373")
    void definedSendingSettingsFollowTheSameRulesAsTheGlobalOnes(String name, ReminderSendingOverrides overrides, boolean allowed) {
        PolicyVerdict verdict = sending.evaluateRule(new ReminderScheduleCheck(new ReminderSchedule(false, List.of(), overrides), false));

        assertThat(verdict.allowed()).isEqualTo(allowed);
        if (!allowed) {
            assertThat(verdict.code()).isEqualTo("INVALID_COLLECTION_REMINDER_SCHEDULE");
            assertThat(verdict.reason()).startsWith("sendingOverrides: ");
        }
    }
}
