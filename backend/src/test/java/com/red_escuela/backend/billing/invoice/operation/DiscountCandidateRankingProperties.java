package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountCandidateOrigin;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountCandidateRanking;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-073")
@Tag("D-295")
@Tag("D-301")
@Tag("D-384")
@Tag("D-385")
class DiscountCandidateRankingProperties {

    private static final String SEED = "20271016";
    private static final int TRIES = 1000;
    private static final InvoiceType INVOICE_TYPE = InvoiceType.MONTHLY_INSTALLMENT;
    private static final long SAVING_STEP_CENTS = 1_000L;
    private static final Instant GRANT_EPOCH = Instant.parse("2027-02-01T00:00:00Z");

    private final InvoiceDiscountCalculator calculator = new InvoiceDiscountCalculator(
            new InvoiceIssuingOperationConfiguration().invoiceDiscountSelectionPolicySet());
    private final DiscountCandidateRanking ranking = new DiscountCandidateRanking();

    record Head(int lineNumber, DiscountType type, InvoiceType invoiceType, long savingSeed) {
    }

    record Tail(Integer priority, Integer grantDay, Long studentDiscountId, DiscountCandidateOrigin origin) {
    }

    record Spec(Head head, Tail tail, long sourceId) {
    }

    static long lineBaseCents(int lineNumber) {
        return lineNumber * 100_000L;
    }

    static InvoiceDiscountCandidate candidateOf(Spec spec, long sourceId) {
        long baseCents = lineBaseCents(spec.head().lineNumber());
        long savingCents = (spec.head().savingSeed() + 1) * SAVING_STEP_CENTS;
        Instant grantedAt = spec.tail().grantDay() == null ? null : GRANT_EPOCH.plusSeconds(spec.tail().grantDay() * 86_400L);
        return new InvoiceDiscountCandidate(spec.head().type(), sourceId, spec.tail().studentDiscountId(), "d" + sourceId, spec.head().invoiceType(),
                IssuingTestData.ars(BigDecimal.valueOf(baseCents, 2).toPlainString()), IssuingTestData.ars(BigDecimal.valueOf(savingCents, 2).toPlainString()),
                null, null, spec.head().lineNumber(), spec.tail().priority(), grantedAt, null, spec.tail().origin());
    }

    @Provide
    Arbitrary<Spec> specs() {
        Arbitrary<Head> heads = Combinators.combine(
                Arbitraries.integers().between(1, 3), Arbitraries.of(DiscountType.values()),
                Arbitraries.of(INVOICE_TYPE, InvoiceType.ENROLLMENT_FEE), Arbitraries.longs().between(0, 2)).as(Head::new);
        Arbitrary<Tail> tails = Combinators.combine(
                Arbitraries.integers().between(1, 2).injectNull(0.3), Arbitraries.integers().between(0, 1).injectNull(0.3),
                Arbitraries.longs().between(1, 2).injectNull(0.3), Arbitraries.of(DiscountCandidateOrigin.values())).as(Tail::new);
        return Combinators.combine(heads, tails, Arbitraries.longs().between(1, 3)).as(Spec::new);
    }

    @Provide
    Arbitrary<List<Spec>> specLists() {
        return specs().list().ofMinSize(0).ofMaxSize(8);
    }

    private List<InvoiceDiscountCandidate> uniquelyIdentified(List<Spec> specs) {
        return IntStream.range(0, specs.size()).mapToObj(index -> candidateOf(specs.get(index), index + 1L)).toList();
    }

    private List<InvoiceDiscountCandidate> ofTheInvoiceType(List<InvoiceDiscountCandidate> candidates) {
        return candidates.stream().filter(candidate -> candidate.applicableInvoiceType() == INVOICE_TYPE).toList();
    }

    @Property(tries = TRIES, seed = SEED)
    void theWinnerDoesNotDependOnTheCandidateOrder(@ForAll("specLists") List<Spec> specs, @ForAll long shuffleSeed) {
        List<InvoiceDiscountCandidate> candidates = uniquelyIdentified(specs);
        List<InvoiceDiscountCandidate> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, new Random(shuffleSeed));

        assertThat(calculator.selectBestDiscountPerInvoiceLine(INVOICE_TYPE, shuffled)).isEqualTo(calculator.selectBestDiscountPerInvoiceLine(INVOICE_TYPE, candidates));
    }

    @Property(tries = TRIES, seed = SEED)
    void theWinnerBeatsEveryOtherCandidateOfItsLineCriterionByCriterion(@ForAll("specLists") List<Spec> specs) {
        List<InvoiceDiscountCandidate> candidates = uniquelyIdentified(specs);

        Map<Integer, InvoiceDiscountCandidate> winners = calculator.selectBestDiscountPerInvoiceLine(INVOICE_TYPE, candidates);

        winners.forEach((lineNumber, winner) -> ofTheInvoiceType(candidates).stream()
                .filter(other -> other.lineNumber() == lineNumber && other != winner)
                .forEach(other -> assertThat(precedes(winner, other)).as("%s should precede %s", winner, other).isTrue()));
    }

    @Property(tries = TRIES, seed = SEED)
    void theWinnerHasTheMaximumSavingOfItsLine(@ForAll("specLists") List<Spec> specs) {
        List<InvoiceDiscountCandidate> candidates = uniquelyIdentified(specs);

        Map<Integer, InvoiceDiscountCandidate> winners = calculator.selectBestDiscountPerInvoiceLine(INVOICE_TYPE, candidates);

        winners.forEach((lineNumber, winner) -> assertThat(ofTheInvoiceType(candidates).stream()
                .filter(other -> other.lineNumber() == lineNumber)
                .allMatch(other -> other.discountAmount().value().amount().compareTo(winner.discountAmount().value().amount()) <= 0)).isTrue());
    }

    private static int expectedTypeRank(DiscountType type) {
        if (type == DiscountType.SCHOLARSHIP) {
            return 1;
        }
        return type == DiscountType.ANNUAL_UPFRONT ? 3 : 2;
    }

    private static List<Long> expectedKey(InvoiceDiscountCandidate candidate) {
        long never = Long.MAX_VALUE;
        return List.of(
                -candidate.discountAmount().value().amount().movePointRight(2).longValueExact(),
                (long) expectedTypeRank(candidate.discountType()),
                candidate.priority() == null ? never : candidate.priority(),
                candidate.grantedAt() == null ? never : candidate.grantedAt().getEpochSecond(),
                (long) candidate.origin().ordinal(),
                candidate.studentDiscountId() == null ? never : candidate.studentDiscountId(),
                candidate.sourceId());
    }

    private static boolean precedes(InvoiceDiscountCandidate first, InvoiceDiscountCandidate second) {
        List<Long> firstKey = expectedKey(first);
        List<Long> secondKey = expectedKey(second);
        for (int position = 0; position < firstKey.size(); position++) {
            int comparison = Long.compare(firstKey.get(position), secondKey.get(position));
            if (comparison != 0) {
                return comparison < 0;
            }
        }
        return false;
    }

    @Property(tries = TRIES, seed = SEED)
    void atMostOneWinnerPerLine(@ForAll("specLists") List<Spec> specs) {
        List<InvoiceDiscountCandidate> candidates = uniquelyIdentified(specs);

        Map<Integer, InvoiceDiscountCandidate> winners = calculator.selectBestDiscountPerInvoiceLine(INVOICE_TYPE, candidates);

        TreeSet<Integer> linesWithCandidates = new TreeSet<>(ofTheInvoiceType(candidates).stream().map(InvoiceDiscountCandidate::lineNumber).toList());
        assertThat(winners.keySet()).containsExactlyElementsOf(linesWithCandidates);
        winners.forEach((lineNumber, winner) -> assertThat(winner.lineNumber()).isEqualTo(lineNumber));
    }

    @Property(tries = TRIES, seed = SEED)
    void theWinnerAlwaysMatchesTheInvoiceType(@ForAll("specLists") List<Spec> specs) {
        Map<Integer, InvoiceDiscountCandidate> winners = calculator.selectBestDiscountPerInvoiceLine(INVOICE_TYPE, uniquelyIdentified(specs));

        assertThat(winners.values()).allSatisfy(winner -> assertThat(winner.applicableInvoiceType()).isEqualTo(INVOICE_TYPE));
    }

    @Property(tries = TRIES, seed = SEED)
    void theRankingIsATotalOrder(@ForAll("specs") Spec first, @ForAll("specs") Spec second, @ForAll("specs") Spec third) {
        InvoiceDiscountCandidate a = candidateOf(first, first.sourceId());
        InvoiceDiscountCandidate b = candidateOf(second, second.sourceId());
        InvoiceDiscountCandidate c = candidateOf(third, third.sourceId());

        assertThat(Integer.signum(ranking.compare(a, b))).isEqualTo(-Integer.signum(ranking.compare(b, a)));
        assertThat(ranking.compare(a, a)).isZero();
        if (ranking.compare(a, b) <= 0 && ranking.compare(b, c) <= 0) {
            assertThat(ranking.compare(a, c)).isLessThanOrEqualTo(0);
        }
        if (ranking.compare(a, b) < 0 && ranking.compare(b, c) < 0) {
            assertThat(ranking.compare(a, c)).isLessThan(0);
        }
    }
}
