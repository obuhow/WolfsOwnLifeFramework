package ru.wolf.api.demo;

/**
 * Thrown when demo fixture generation is attempted for a user that already has data.
 * Mapped to HTTP 409 Conflict — the load is rejected, never a silent overwrite.
 */
public class DemoDataConflictException extends RuntimeException {
    public DemoDataConflictException(String message) {
        super(message);
    }
}
