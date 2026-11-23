package com.red_escuela.backend.testsupport;

import com.red_escuela.backend.announcement.events.AnnouncementDeletedEvent;
import com.red_escuela.backend.announcement.events.AnnouncementPublishedEvent;
import com.red_escuela.backend.announcement.events.AnnouncementUpdatedEvent;
import com.red_escuela.backend.enrollment.events.EnrollmentFeeExemptionRevokedEvent;
import com.red_escuela.backend.enrollment.events.ProgramApplicationApprovedEvent;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigApprovedEvent;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigProposedEvent;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigRejectedEvent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

@TestConfiguration
public class FailingEventListenersConfiguration {

    @Bean
    FailingExemptionRevocationListener failingExemptionRevocationListener() {
        return new FailingExemptionRevocationListener();
    }

    @Bean
    FailingConfigListener failingConfigListener() {
        return new FailingConfigListener();
    }

    @Bean
    FailingAnnouncementListener failingAnnouncementListener() {
        return new FailingAnnouncementListener();
    }

    public static class FailingExemptionRevocationListener {
        private volatile boolean armed;
        private volatile boolean armedOnApproval;

        public void arm() {
            this.armed = true;
        }

        public void armOnApproval() {
            this.armedOnApproval = true;
        }

        public void disarm() {
            this.armed = false;
            this.armedOnApproval = false;
        }

        @EventListener
        public void onApproved(ProgramApplicationApprovedEvent event) {
            if (armedOnApproval) {
                throw new IllegalStateException("listener failed on approval");
            }
        }

        @EventListener
        public void onRevoked(EnrollmentFeeExemptionRevokedEvent event) {
            if (armed) {
                throw new IllegalStateException("listener failed on revocation");
            }
        }
    }

    public static class FailingConfigListener {
        private volatile Class<?> failingOn;

        public void failOn(Class<?> eventType) {
            this.failingOn = eventType;
        }

        public void stopFailing() {
            this.failingOn = null;
        }

        @EventListener
        public void onProposed(SectionEvaluationConfigProposedEvent event) {
            failIfArmedFor(SectionEvaluationConfigProposedEvent.class);
        }

        @EventListener
        public void onApproved(SectionEvaluationConfigApprovedEvent event) {
            failIfArmedFor(SectionEvaluationConfigApprovedEvent.class);
        }

        @EventListener
        public void onRejected(SectionEvaluationConfigRejectedEvent event) {
            failIfArmedFor(SectionEvaluationConfigRejectedEvent.class);
        }

        private void failIfArmedFor(Class<?> eventType) {
            if (eventType.equals(failingOn)) {
                throw new IllegalStateException("listener failed on " + eventType.getSimpleName());
            }
        }
    }

    public static class FailingAnnouncementListener {
        private volatile Class<?> failingOn;

        public void failOn(Class<?> eventType) {
            this.failingOn = eventType;
        }

        public void stopFailing() {
            this.failingOn = null;
        }

        @EventListener
        public void onPublished(AnnouncementPublishedEvent event) {
            failIfArmedFor(AnnouncementPublishedEvent.class);
        }

        @EventListener
        public void onUpdated(AnnouncementUpdatedEvent event) {
            failIfArmedFor(AnnouncementUpdatedEvent.class);
        }

        @EventListener
        public void onDeleted(AnnouncementDeletedEvent event) {
            failIfArmedFor(AnnouncementDeletedEvent.class);
        }

        private void failIfArmedFor(Class<?> eventType) {
            if (eventType.equals(failingOn)) {
                throw new IllegalStateException("listener failed on " + eventType.getSimpleName());
            }
        }
    }
}
