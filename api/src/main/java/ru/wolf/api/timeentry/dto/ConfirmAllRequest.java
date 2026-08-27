package ru.wolf.api.timeentry.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmAllRequest(@NotBlank String from, @NotBlank String to) {
    /** Compatibility accessor for legacy callers. */
    public @NotBlank String getFrom() { return from(); }

    /** Compatibility accessor for legacy callers. */
    public @NotBlank String getTo() { return to(); }
}
