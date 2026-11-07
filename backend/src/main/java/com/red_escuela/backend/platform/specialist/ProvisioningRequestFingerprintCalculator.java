package com.red_escuela.backend.platform.specialist;

import com.red_escuela.backend.platform.domain.FirstDirectorProvisioningCommand;
import com.red_escuela.backend.shared.domain.EmailAddress;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class ProvisioningRequestFingerprintCalculator {

    private static final char SEPARATOR = '\u0000';

    public String of(FirstDirectorProvisioningCommand command) {
        List<String> fields = List.of(
                command.institutionName().trim(),
                command.firstName().trim(),
                command.lastName().trim(),
                EmailAddress.of(command.email()).value(),
                command.documentType().name(),
                command.documentNumber().trim(),
                command.reason().trim());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(String.join(String.valueOf(SEPARATOR), fields).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
