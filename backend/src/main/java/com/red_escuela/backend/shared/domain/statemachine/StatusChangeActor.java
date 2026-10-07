package com.red_escuela.backend.shared.domain.statemachine;

public record StatusChangeActor(Long personId, String processName) {

    private static final int MAX_PROCESS_NAME_LENGTH = 80;

    public StatusChangeActor {
        if ((personId == null) == (processName == null)) {
            throw new IllegalArgumentException("Exactly one of personId or processName must be present");
        }
        if (processName != null && (processName.isBlank() || processName.length() > MAX_PROCESS_NAME_LENGTH)) {
            throw new IllegalArgumentException("processName must be between 1 and " + MAX_PROCESS_NAME_LENGTH + " characters");
        }
    }

    public static StatusChangeActor ofPerson(Long personId) {
        return new StatusChangeActor(personId, null);
    }

    public static StatusChangeActor ofProcess(String processName) {
        return new StatusChangeActor(null, processName);
    }
}
