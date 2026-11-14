package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.dto.LegalAcceptanceEvidence;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class LegalAcceptanceEvidenceReader {

    public LegalAcceptanceEvidence readCurrent() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            return LegalAcceptanceEvidence.of(request.getRemoteAddr(), request.getHeader("User-Agent"));
        }
        return LegalAcceptanceEvidence.none();
    }
}
