package com.red_escuela.backend.shared.permission;


import java.util.concurrent.atomic.AtomicInteger;

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
