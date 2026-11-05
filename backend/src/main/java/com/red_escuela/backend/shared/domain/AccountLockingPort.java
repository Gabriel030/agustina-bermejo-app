package com.red_escuela.backend.shared.domain;

public interface AccountLockingPort {
    void lockAccountById(Long accountId);
}
