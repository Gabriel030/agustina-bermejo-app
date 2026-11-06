package com.red_escuela.backend.graduation.certificate.engine;

import java.util.Optional;

public interface CertificateFileStore {

    String save(String code, byte[] pdf);

    Optional<byte[]> load(String path);

    void deleteIfRolledBack(String path);
}
