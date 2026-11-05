package com.red_escuela.backend.notification.delivery.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailDispatchPlannerTest {
    private static final LocalDate MARCH_NINTH = LocalDate.of(2027, 3, 9);
    private static final LocalDate MARCH_TENTH = LocalDate.of(2027, 3, 10);
    private static final LocalDate MARCH_ELEVENTH = LocalDate.of(2027, 3, 11);
    private static final LocalDate MARCH_TWELFTH = LocalDate.of(2027, 3, 12);
    private static final int DAILY_CAP = 250;

    private final EmailDispatchPlanner planner = new EmailDispatchPlanner();

    private static DispatchItem preDueItem(String key, LocalDate dueDate, int priority) {
        return new DispatchItem(key, dueDate, true, priority);
    }

    private static DispatchItem postDueItem(String key, LocalDate dueDate, int priority) {
        return new DispatchItem(key, dueDate, false, priority);
    }

    private static DispatchCandidate mail(long deliveryId, DispatchItem... items) {
        return new DispatchCandidate(deliveryId, List.of(items));
    }

    private static List<Long> idsOf(List<PlannedDispatch> dispatches) {
        return dispatches.stream().map(PlannedDispatch::deliveryId).toList();
    }

    @Test
    @Tag("D-343")
    void threeHundredMailsAgainstACapOf250SendTheMostUrgentFirstAndDeferTheRestToTheNextBusinessDay() {
        List<DispatchCandidate> candidates = new ArrayList<>();
        IntStream.rangeClosed(1, 260).forEach(id -> candidates.add(mail(id, preDueItem("i" + id, MARCH_ELEVENTH, 3))));
        IntStream.rangeClosed(261, 300).forEach(id -> candidates.add(mail(id, postDueItem("i" + id, MARCH_NINTH, 1))));

        EmailDispatchPlan plan = planner.planFor(candidates, MARCH_TENTH, MARCH_ELEVENTH, DAILY_CAP);

        assertThat(plan.toSend()).hasSize(DAILY_CAP);
        assertThat(plan.toSend()).extracting(PlannedDispatch::priority).containsExactlyElementsOf(
                java.util.stream.Stream.concat(IntStream.range(0, 40).mapToObj(i -> 1), IntStream.range(0, 210).mapToObj(i -> 3)).toList());
        assertThat(idsOf(plan.toSend()).subList(40, 250)).containsExactlyElementsOf(IntStream.rangeClosed(1, 210).mapToObj(Long::valueOf).toList());
        assertThat(idsOf(plan.toDefer())).containsExactlyElementsOf(IntStream.rangeClosed(211, 260).mapToObj(Long::valueOf).toList());
        assertThat(plan.toDefer()).extracting(PlannedDispatch::scheduledFor).containsOnly(MARCH_ELEVENTH);
        assertThat(plan.toSend()).extracting(PlannedDispatch::scheduledFor).containsOnly(MARCH_TENTH);
        assertThat(plan.toDiscard()).isEmpty();
    }

    @Test
    @Tag("D-343")
    void aDeferredMailStillTimelyOnItsDueDateGoesOutAndOneThatWouldBeAfterItIsDiscarded() {
        DispatchCandidate deferred = mail(7L, preDueItem("march", MARCH_ELEVENTH, 3));

        EmailDispatchPlan onDueDate = planner.planFor(List.of(deferred), MARCH_ELEVENTH, MARCH_TWELFTH, DAILY_CAP);
        EmailDispatchPlan dayAfter = planner.planFor(List.of(deferred), MARCH_TWELFTH, MARCH_TWELFTH.plusDays(3), DAILY_CAP);

        assertThat(idsOf(onDueDate.toSend())).containsExactly(7L);
        assertThat(onDueDate.toDiscard()).isEmpty();
        assertThat(dayAfter.toSend()).isEmpty();
        assertThat(dayAfter.toDefer()).isEmpty();
        assertThat(dayAfter.toDiscard()).containsExactly(new DispatchDiscard(7L, DeliveryStatusReason.NO_LONGER_TIMELY));
    }

    @Test
    @Tag("D-343")
    void aMailThatDoesNotFitOnItsDueDateIsStillDeferredAndTheDiscardHappensTheNextDay() {
        DispatchCandidate urgent = mail(1L, postDueItem("urgent", MARCH_NINTH, 1));
        DispatchCandidate preDue = mail(2L, preDueItem("march", MARCH_ELEVENTH, 3));

        EmailDispatchPlan onDueDate = planner.planFor(List.of(urgent, preDue), MARCH_ELEVENTH, MARCH_TWELFTH, 1);

        assertThat(idsOf(onDueDate.toSend())).containsExactly(1L);
        assertThat(idsOf(onDueDate.toDefer())).containsExactly(2L);
        assertThat(onDueDate.toDiscard()).isEmpty();
    }

    @Test
    @Tag("D-343")
    void anEmptyListOfCandidatesProducesAnEmptyPlan() {
        EmailDispatchPlan plan = planner.planFor(List.of(), MARCH_TENTH, MARCH_ELEVENTH, DAILY_CAP);

        assertThat(plan.toSend()).isEmpty();
        assertThat(plan.toDefer()).isEmpty();
        assertThat(plan.toDiscard()).isEmpty();
    }

    @Test
    @Tag("D-343")
    void aNoticeThatWasAlreadyAfterItsDueDateNeverExpires() {
        DispatchCandidate overdue = mail(1L, postDueItem("overdue", MARCH_NINTH, 2));

        EmailDispatchPlan plan = planner.planFor(List.of(overdue), MARCH_TWELFTH, MARCH_TWELFTH.plusDays(1), DAILY_CAP);

        assertThat(idsOf(plan.toSend())).containsExactly(1L);
        assertThat(plan.toDiscard()).isEmpty();
    }

    @Test
    @Tag("D-343")
    void onlyTheExpiredItemIsDroppedAndTheRestOfTheMailGoesOut() {
        DispatchItem expired = preDueItem("expired", MARCH_TENTH, 3);
        DispatchItem alive = preDueItem("alive", MARCH_TWELFTH, 4);
        DispatchItem overdue = postDueItem("overdue", MARCH_NINTH, 2);

        EmailDispatchPlan plan = planner.planFor(List.of(mail(5L, expired, alive, overdue)), MARCH_ELEVENTH, MARCH_TWELFTH, DAILY_CAP);

        assertThat(plan.toDiscard()).isEmpty();
        PlannedDispatch sent = plan.toSend().get(0);
        assertThat(sent.keptItems()).containsExactly(alive, overdue);
        assertThat(sent.droppedItems()).containsExactly(expired);
        assertThat(sent.dropsItems()).isTrue();
    }

    @Test
    @Tag("D-343")
    void aMailWithoutExpiredItemsDropsNothing() {
        PlannedDispatch sent = planner.planFor(List.of(mail(5L, preDueItem("a", MARCH_TWELFTH, 3))), MARCH_ELEVENTH, MARCH_TWELFTH, DAILY_CAP).toSend().get(0);

        assertThat(sent.dropsItems()).isFalse();
        assertThat(sent.droppedItems()).isEmpty();
    }

    @Test
    @Tag("D-343")
    void thePriorityOfAMailIsTheMostUrgentOfTheItemsThatRemain() {
        DispatchItem expiredButUrgent = preDueItem("expired", MARCH_TENTH, 1);
        DispatchItem alive = preDueItem("alive", MARCH_TWELFTH, 4);
        DispatchCandidate trimmed = mail(1L, expiredButUrgent, alive);
        DispatchCandidate other = mail(2L, postDueItem("other", MARCH_NINTH, 3));

        EmailDispatchPlan plan = planner.planFor(List.of(trimmed, other), MARCH_ELEVENTH, MARCH_TWELFTH, 1);

        assertThat(idsOf(plan.toSend())).containsExactly(2L);
        assertThat(plan.toDefer()).extracting(PlannedDispatch::priority).containsExactly(4);
        assertThat(plan.toDefer().get(0).droppedItems()).containsExactly(expiredButUrgent);
    }

    @Test
    @Tag("D-343")
    void aMailWithSeveralItemsTakesTheMostUrgentPriorityAmongThem() {
        DispatchCandidate digest = mail(1L, preDueItem("a", MARCH_TWELFTH, 3), postDueItem("b", MARCH_NINTH, 2), preDueItem("c", MARCH_TWELFTH, 4));

        assertThat(planner.planFor(List.of(digest), MARCH_TENTH, MARCH_ELEVENTH, DAILY_CAP).toSend().get(0).priority()).isEqualTo(2);
    }

    @Test
    @Tag("D-343")
    void mailsOfTheSamePriorityKeepTheOrderOfTheirDeliveryId() {
        List<DispatchCandidate> candidates = List.of(mail(30L, postDueItem("c", MARCH_NINTH, 2)), mail(10L, postDueItem("a", MARCH_NINTH, 2)),
                mail(20L, postDueItem("b", MARCH_NINTH, 2)));

        EmailDispatchPlan plan = planner.planFor(candidates, MARCH_TENTH, MARCH_ELEVENTH, 2);

        assertThat(idsOf(plan.toSend())).containsExactly(10L, 20L);
        assertThat(idsOf(plan.toDefer())).containsExactly(30L);
    }

    @Test
    @Tag("D-343")
    void aCapOfZeroDefersEverything() {
        EmailDispatchPlan plan = planner.planFor(List.of(mail(1L, postDueItem("a", MARCH_NINTH, 2)), mail(2L, postDueItem("b", MARCH_NINTH, 3))),
                MARCH_TENTH, MARCH_ELEVENTH, 0);

        assertThat(plan.toSend()).isEmpty();
        assertThat(idsOf(plan.toDefer())).containsExactly(1L, 2L);
    }

    @Test
    @Tag("D-343")
    void aCapThatFitsEveryMailDefersNothing() {
        EmailDispatchPlan plan = planner.planFor(List.of(mail(1L, postDueItem("a", MARCH_NINTH, 2)), mail(2L, postDueItem("b", MARCH_NINTH, 3))),
                MARCH_TENTH, MARCH_ELEVENTH, 2);

        assertThat(idsOf(plan.toSend())).containsExactly(1L, 2L);
        assertThat(plan.toDefer()).isEmpty();
    }

    @Test
    @Tag("D-343")
    void theCapComesFromTheCallerSoAnApprovedChangeFrom250To280TakesEffect() {
        List<DispatchCandidate> candidates = IntStream.rangeClosed(1, 300).mapToObj(id -> mail(id, postDueItem("i" + id, MARCH_NINTH, 2))).toList();

        EmailDispatchPlan withTheOldCap = planner.planFor(candidates, MARCH_TENTH, MARCH_ELEVENTH, 250);
        EmailDispatchPlan withTheNewCap = planner.planFor(candidates, MARCH_TENTH, MARCH_ELEVENTH, 280);

        assertThat(withTheOldCap.toSend()).hasSize(250);
        assertThat(withTheOldCap.toDefer()).hasSize(50);
        assertThat(withTheNewCap.toSend()).hasSize(280);
        assertThat(withTheNewCap.toDefer()).hasSize(20);
    }

    @Test
    @Tag("D-343")
    @Tag("D-372")
    void thePreDueFlagIsRecomputedForEachItemWithTheDateOfTheDispatch() {
        DispatchItem dueOnTheEleventh = preDueItem("a", MARCH_ELEVENTH, 3);
        DispatchItem dueOnTheTwelfth = preDueItem("b", MARCH_TWELFTH, 3);
        DispatchCandidate mail = mail(1L, dueOnTheEleventh, dueOnTheTwelfth);

        PlannedDispatch onTheTenth = planner.planFor(List.of(mail), MARCH_TENTH, MARCH_ELEVENTH, DAILY_CAP).toSend().get(0);
        PlannedDispatch onTheEleventh = planner.planFor(List.of(mail), MARCH_ELEVENTH, MARCH_TWELFTH, DAILY_CAP).toSend().get(0);

        assertThat(onTheTenth.precedesDueDate()).isTrue();
        assertThat(onTheEleventh.precedesDueDate()).isFalse();
    }

    @Test
    @Tag("D-343")
    @Tag("D-372")
    void aDeferredMailCarriesTheFlagComputedForItsNewDate() {
        DispatchCandidate preDue = mail(2L, preDueItem("march", MARCH_ELEVENTH, 3));
        DispatchCandidate urgent = mail(1L, postDueItem("urgent", MARCH_NINTH, 1));

        EmailDispatchPlan plan = planner.planFor(List.of(urgent, preDue), MARCH_TENTH, MARCH_ELEVENTH, 1);

        PlannedDispatch deferred = plan.toDefer().get(0);
        assertThat(deferred.scheduledFor()).isEqualTo(MARCH_ELEVENTH);
        assertThat(deferred.precedesDueDate()).isFalse();
    }

    @Test
    @Tag("D-343")
    @Tag("D-372")
    void aSentMailThatKeepsOnlyOverdueItemsAfterDroppingTheExpiredOneIsNotPreDue() {
        DispatchItem expired = preDueItem("expired", MARCH_TENTH, 3);
        DispatchItem overdue = postDueItem("overdue", MARCH_NINTH, 2);

        PlannedDispatch sent = planner.planFor(List.of(mail(1L, expired, overdue)), MARCH_ELEVENTH, MARCH_TWELFTH, DAILY_CAP).toSend().get(0);

        assertThat(sent.precedesDueDate()).isFalse();
        assertThat(sent.priority()).isEqualTo(2);
    }

    @Test
    @Tag("D-343")
    void theNextBusinessDayMustBeAfterTheDispatchDate() {
        assertThatThrownBy(() -> planner.planFor(List.of(), MARCH_TENTH, MARCH_TENTH, DAILY_CAP)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planner.planFor(List.of(), MARCH_TENTH, MARCH_NINTH, DAILY_CAP)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Tag("D-343")
    void aNegativeCapIsRejected() {
        assertThatThrownBy(() -> planner.planFor(List.of(), MARCH_TENTH, MARCH_ELEVENTH, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Tag("D-343")
    void datesAreRequired() {
        assertThatThrownBy(() -> planner.planFor(List.of(), null, MARCH_ELEVENTH, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> planner.planFor(List.of(), MARCH_TENTH, null, 1)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @Tag("D-343")
    void aDeliveryAppearingTwiceIsRejected() {
        List<DispatchCandidate> candidates = List.of(mail(1L, postDueItem("a", MARCH_NINTH, 2)), mail(1L, postDueItem("b", MARCH_NINTH, 2)));

        assertThatThrownBy(() -> planner.planFor(candidates, MARCH_TENTH, MARCH_ELEVENTH, DAILY_CAP)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Tag("D-343")
    void aCandidateWithoutItemsIsRejected() {
        assertThatThrownBy(() -> new DispatchCandidate(1L, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Tag("D-343")
    void anItemNeedsAKeyADueDateAndAValidPriority() {
        assertThatThrownBy(() -> new DispatchItem(" ", MARCH_NINTH, false, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DispatchItem(null, MARCH_NINTH, false, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DispatchItem("a", null, false, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DispatchItem("a", MARCH_NINTH, false, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new DispatchItem("a", MARCH_NINTH, false, 1).priority()).isEqualTo(1);
    }

    @Test
    @Tag("D-343")
    void anItemIsNoLongerTimelyOnlyAfterItsDueDateAndOnlyWhenItWasPlannedBeforeIt() {
        DispatchItem preDue = preDueItem("a", MARCH_TENTH, 3);
        DispatchItem postDue = postDueItem("b", MARCH_TENTH, 3);

        assertThat(preDue.isNoLongerTimelyOn(MARCH_NINTH)).isFalse();
        assertThat(preDue.isNoLongerTimelyOn(MARCH_TENTH)).isFalse();
        assertThat(preDue.isNoLongerTimelyOn(MARCH_ELEVENTH)).isTrue();
        assertThat(postDue.isNoLongerTimelyOn(MARCH_ELEVENTH)).isFalse();
        assertThat(preDue.precedesDueDateWhenSentOn(MARCH_NINTH)).isTrue();
        assertThat(preDue.precedesDueDateWhenSentOn(MARCH_TENTH)).isFalse();
    }

    @Test
    @Tag("D-343")
    void theNewStatusReasonsFitTheColumnAndAreDistinct() {
        List<String> reasons = List.of(DeliveryStatusReason.NO_LONGER_TIMELY, DeliveryStatusReason.NO_LONGER_APPLICABLE, DeliveryStatusReason.DAILY_CAP_REACHED);

        assertThat(reasons).doesNotHaveDuplicates();
        reasons.forEach(reason -> assertThat(DeliveryStatusReason.require(reason)).isEqualTo(reason));
        assertThat(reasons).allMatch(reason -> reason.length() <= DeliveryStatusReason.MAX_LENGTH);
        assertThatThrownBy(() -> new DispatchDiscard(1L, " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
