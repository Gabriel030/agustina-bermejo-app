package com.red_escuela.backend.platform.domain;

public record BackupDisk(long freeBytes, long totalBytes, String path) {
}
