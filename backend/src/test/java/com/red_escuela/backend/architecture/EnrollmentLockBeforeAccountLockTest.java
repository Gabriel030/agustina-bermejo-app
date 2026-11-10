package com.red_escuela.backend.architecture;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-457")
class EnrollmentLockBeforeAccountLockTest {

    private static final String BILLING_NOTE = "com/red_escuela/backend/billing/note/service/";
    private static final String ENROLLMENT_SERVICE = "com/red_escuela/backend/enrollment/service/";
    private static final String ENROLLMENT_LOCKS = "lockEnrollmentsOfDiscountsAppliedOn";
    private static final String ACCOUNT_LOCK = "lockStudentCurrentAccount";
    private static final String BILLING_PORT = "registerWithdrawalForBilling";

    private record OrderedCalls(String file, String method, List<String> calls) {
    }

    private static final List<OrderedCalls> ENTRY_POINTS = List.of(
            new OrderedCalls(BILLING_NOTE + "CancelInvoiceOperation.java", "cancelWithoutAllocatingCredit", List.of(ENROLLMENT_LOCKS, ACCOUNT_LOCK)),
            new OrderedCalls(BILLING_NOTE + "LaterChargesCancellationOnWithdrawal.java", "cancelLaterChargesOf", List.of("lockEnrollmentsOfDiscountsAppliedOnInvoices", ACCOUNT_LOCK)),
            new OrderedCalls(BILLING_NOTE + "SystemAdmissionInvoiceCancellation.java", "cancelWhenUnpaid", List.of(ENROLLMENT_LOCKS, ACCOUNT_LOCK)),
            new OrderedCalls(BILLING_NOTE + "LeftOutInvoiceCancellationOnReceiptRejectionListener.java", "cancelIfLeftOutOfWithdrawal", List.of(ENROLLMENT_LOCKS, ACCOUNT_LOCK)),
            new OrderedCalls(ENROLLMENT_SERVICE + "ProgramEnrollmentService.java", "withdraw", List.of("findByIdForUpdate", "withdrawOpenEnrollmentsOf", BILLING_PORT)),
            new OrderedCalls(ENROLLMENT_SERVICE + "CourseEnrollmentService.java", "withdraw",
                    List.of("findByIdForUpdate", "cancelModuleEnrollments", "promoteNextFromCourseWaitlist", BILLING_PORT)));

    @Test
    void theKnownEntryPointsLockTheEnrollmentBeforeAnythingThatTakesTheAccountLock() throws IOException {
        List<String> violations = new ArrayList<>();
        for (OrderedCalls entryPoint : ENTRY_POINTS) {
            String source = Files.readString(Path.of("src/main/java").resolve(entryPoint.file()));
            List<String> candidates = bodiesOf(source, entryPoint.method()).stream()
                    .filter(body -> entryPoint.calls().stream().allMatch(call -> positionOf(body, call) >= 0)).toList();
            assertThat(candidates).as(entryPoint.file() + "#" + entryPoint.method() + " must call " + entryPoint.calls()).isNotEmpty();
            candidates.forEach(body -> outOfOrderCalls(body, entryPoint.calls()).forEach(call -> violations.add(entryPoint.file() + "#" + entryPoint.method() + ": " + call)));
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void anInvertedOrderIsReportedAndTheRightOrderIsNot() {
        String inverted = "{ lockStudentCurrentAccount(1L); lockEnrollmentsOfDiscountsAppliedOn(2L); }";
        String right = "{ lockEnrollmentsOfDiscountsAppliedOn(2L); lockStudentCurrentAccount(1L); }";

        assertThat(outOfOrderCalls(inverted, List.of(ENROLLMENT_LOCKS, ACCOUNT_LOCK))).containsExactly(ENROLLMENT_LOCKS + " must come before " + ACCOUNT_LOCK);
        String prefixOnly = "{ lockEnrollmentsOfDiscountsAppliedOnInvoices(ids); lockStudentCurrentAccount(1L); }";
        String commentOnly = "{ lockStudentCurrentAccount(1L); // lockEnrollmentsOfDiscountsAppliedOn(2L)\n }";
        String stringOnly = "{ log(\"lockEnrollmentsOfDiscountsAppliedOn(\"); lockStudentCurrentAccount(1L); }";
        String reference = "{ ids.forEach(accountService::lockStudentCurrentAccount); }";

        assertThat(positionOf(prefixOnly, ENROLLMENT_LOCKS)).isNegative();
        assertThat(positionOf(commentOnly, ENROLLMENT_LOCKS)).isNegative();
        assertThat(positionOf(stringOnly, ENROLLMENT_LOCKS)).isNegative();
        assertThat(positionOf(reference, ACCOUNT_LOCK)).isNotNegative();
        assertThat(outOfOrderCalls(right, List.of(ENROLLMENT_LOCKS, ACCOUNT_LOCK))).isEmpty();
    }

    private static List<String> outOfOrderCalls(String body, List<String> orderedCalls) {
        List<String> outOfOrder = new ArrayList<>();
        for (int index = 1; index < orderedCalls.size(); index++) {
            if (positionOf(body, orderedCalls.get(index)) < positionOf(body, orderedCalls.get(index - 1))) {
                outOfOrder.add(orderedCalls.get(index - 1) + " must come before " + orderedCalls.get(index));
            }
        }
        return outOfOrder;
    }

    private static int positionOf(String body, String call) {
        String code = body.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ").replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
        Matcher matcher = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(call) + "\\s*\\(|::" + Pattern.quote(call) + "(?![A-Za-z0-9_])").matcher(code);
        return matcher.find() ? matcher.start() : -1;
    }

    private static List<String> bodiesOf(String source, String method) {
        List<String> bodies = new ArrayList<>();
        int declaration = source.indexOf(" " + method + "(");
        while (declaration >= 0) {
            int open = source.indexOf('{', declaration);
            int depth = 0;
            for (int index = open; index < source.length(); index++) {
                char current = source.charAt(index);
                if (current == '{') {
                    depth++;
                } else if (current == '}' && --depth == 0) {
                    bodies.add(source.substring(open, index));
                    break;
                }
            }
            declaration = source.indexOf(" " + method + "(", declaration + 1);
        }
        return bodies;
    }
}
