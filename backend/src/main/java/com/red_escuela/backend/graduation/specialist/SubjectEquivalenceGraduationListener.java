package com.red_escuela.backend.graduation.specialist;

import com.red_escuela.backend.enrollment.events.SubjectEquivalenceRecordedEvent;
import com.red_escuela.backend.enrollment.events.SubjectEquivalenceVoidedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SubjectEquivalenceGraduationListener {

    private static final Logger log = LoggerFactory.getLogger(SubjectEquivalenceGraduationListener.class);

    private final GraduationRequestAutoRequester graduationRequestAutoRequester;
    private final GraduationRequestOutdatedFlagger graduationRequestOutdatedFlagger;
    private final TransactionTemplate nestedTransaction;

    public SubjectEquivalenceGraduationListener(GraduationRequestAutoRequester graduationRequestAutoRequester,
                                                GraduationRequestOutdatedFlagger graduationRequestOutdatedFlagger,
                                                PlatformTransactionManager transactionManager) {
        this.graduationRequestAutoRequester = graduationRequestAutoRequester;
        this.graduationRequestOutdatedFlagger = graduationRequestOutdatedFlagger;
        this.nestedTransaction = new TransactionTemplate(transactionManager);
        this.nestedTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    }

    @EventListener
    public void onEquivalenceRecorded(SubjectEquivalenceRecordedEvent event) {
        try {
            nestedTransaction.executeWithoutResult(status ->
                    graduationRequestAutoRequester.createIfEligible(event.programEnrollmentId()));
        } catch (DataIntegrityViolationException exception) {
            log.warn("Solicitud de egreso ya creada por otra transacción: programEnrollmentId={}", event.programEnrollmentId());
        }
    }

    @EventListener
    public void onEquivalenceVoided(SubjectEquivalenceVoidedEvent event) {
        graduationRequestOutdatedFlagger.flagOutdatedRequests(event.programEnrollmentId());
    }
}
