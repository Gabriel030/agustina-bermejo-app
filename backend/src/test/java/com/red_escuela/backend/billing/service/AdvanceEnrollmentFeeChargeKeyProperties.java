package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestContextManager;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("simulation")
@Tag("D-523")
@Tag("D-009")
@SlowTest(reason = "jqwik properties executed against the Spring context and Postgres")
class AdvanceEnrollmentFeeChargeKeyProperties extends AbstractAdvanceEnrollmentFeeTest {

    @BeforeProperty
    void injectSpringBeans() throws Exception {
        new TestContextManager(getClass()).prepareTestInstance(this);
    }

    @AfterProperty
    void resetState() {
        MutableClock.INSTANCE.reset();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Property(tries = 4, seed = "20261107")
    void neverTwoLiveInvoicesForTheSameChargeKeyWhateverTheRequestSequence(@ForAll @IntRange(min = 1, max = 4) int requests, @ForAll boolean staffFirst,
                                                                          @ForAll boolean januaryRunBetween) {
        Setup setup = september();
        for (int attempt = 0; attempt < requests; attempt++) {
            if (staffFirst == (attempt % 2 == 0)) {
                actAsStaff(setup.career());
                issuer.issueForStaff(setup.enrollment());
            } else {
                actAsStudentOf(setup.enrollment());
                issuer.issueForCurrentStudent(setup.enrollment());
            }
            if (januaryRunBetween && attempt == 0) {
                today(date(2028, 1, 4));
                job.run();
                today(date(2027, 9, 15));
            }
        }

        assertThat(advanceInvoices(setup.enrollment())).hasSize(1);
        assertThat(invoices(setup.enrollment(), FeeType.ENROLLMENT_FEE).stream().filter(invoice -> setup.nextYear().templateId().equals(invoice.getTemplateId()))
                .filter(invoice -> !invoice.getPrimaryLine().getChargeKeyReleased())).hasSize(1);
    }
}
