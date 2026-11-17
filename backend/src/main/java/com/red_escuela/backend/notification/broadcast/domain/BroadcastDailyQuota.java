package com.red_escuela.backend.notification.broadcast.domain;

public record BroadcastDailyQuota(int dailyLimit, int reservedForTransactional) {

    public BroadcastDailyQuota {
        if (dailyLimit < 0) {
            throw new IllegalArgumentException("The daily limit cannot be negative");
        }
        if (reservedForTransactional < 0) {
            throw new IllegalArgumentException("The transactional reserve cannot be negative");
        }
    }

    public int remaining(long transactionalSentToday, long broadcastSentToday) {
        long transactionalShare = Math.max(transactionalSentToday, reservedForTransactional);
        long available = dailyLimit - transactionalShare - broadcastSentToday;
        return (int) Math.max(0, available);
    }
}
