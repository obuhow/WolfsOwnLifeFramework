package ru.wolf.api.timeentry.dto;

public record ConfirmOneRequest(Long id, String startAt) {
    /** Compatibility accessor for legacy callers. */
    public Long getId() { return id(); }

    /** Compatibility accessor for legacy callers. */
    public String getStartAt() { return startAt(); }
}
