package com.red_escuela.backend.graduation.certificate.approvedsubjects.port;

import java.util.function.Supplier;

public interface ApprovedSubjectsTransaction {

    <T> T run(Supplier<T> work);
}
