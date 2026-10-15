package com.red_escuela.backend.shared.storage;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class TransactionBoundFileCleanup {
    private final FileStorageService fileStorageService;

    public TransactionBoundFileCleanup(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public void deleteAfterCommit(String relativePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fileStorageService.delete(relativePath);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileStorageService.delete(relativePath);
            }
        });
    }

    public void deleteIfRolledBack(String relativePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    fileStorageService.delete(relativePath);
                }
            }
        });
    }
}
