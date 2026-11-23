package com.red_escuela.backend.testsupport;

import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.specialist.AuthTokenIssuer;
import com.red_escuela.backend.identity.specialist.ContactChangeRecorder;
import com.red_escuela.backend.identity.specialist.IdentityAuditRecorder;
import com.red_escuela.backend.identity.specialist.LegalRegistrationCompleter;
import com.red_escuela.backend.identity.specialist.OwnDocumentChanger;
import com.red_escuela.backend.identity.specialist.SessionRevoker;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.shared.storage.FileStorageService;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@MockitoSpyBean(types = {
        SessionRevoker.class,
        OwnDocumentChanger.class,
        FileStorageService.class,
        PersonRepository.class,
        LegalRegistrationCompleter.class,
        AuthTokenIssuer.class,
        IdentityAuditRecorder.class,
        ContactChangeRecorder.class,
        AuditTrail.class
}, reset = MockReset.AFTER)
public @interface IdentityBoundarySpyBeans {
}
