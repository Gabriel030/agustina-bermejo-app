package com.red_escuela.backend.notification.broadcast.domain;

import java.time.LocalDate;

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

    public LocalDate completionDate(long pending, LocalDate today, long transactionalSentToday, long broadcastSentToday) {
        if (pending <= 0) {
            return null;
        }
        long left = pending - remaining(transactionalSentToday, broadcastSentToday);
        if (left <= 0) {
            return today;
        }
        long perFutureDay = remaining(0, 0);
        if (perFutureDay == 0) {
            return null;
        }
        return today.plusDays((left + perFutureDay - 1) / perFutureDay);
    }
}
