package com.red_escuela.backend.shared.permission;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class InMemoryPermissionPolicyStore implements PermissionPolicyStore {

    private volatile PermissionSnapshot snapshot = PermissionSnapshot.EMPTY;
    private final AtomicInteger loads = new AtomicInteger();

    public void replace(PermissionSnapshot next) {
        this.snapshot = next;
    }

    public int loadCount() {
        return loads.get();
    }

    @Override
    public PermissionSnapshot load() {
        loads.incrementAndGet();
        return snapshot;
    }
}
