package ru.wolf.api.timeentry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.wolf.api.timeentry.TimeEntry;

public record GridClickRequest(
        @NotBlank String slotStart,
        Long deloId,
        @Size(max = 500) String adHocText,
        TimeEntry.Status status
) {
    /** Compatibility accessor for legacy callers. */
    public @NotBlank String getSlotStart() { return slotStart(); }

    /** Compatibility accessor for legacy callers. */
    public Long getDeloId() { return deloId(); }

    /** Compatibility accessor for legacy callers. */
    public @Size(max = 500) String getAdHocText() { return adHocText(); }

    /** Compatibility accessor for legacy callers. */
    public TimeEntry.Status getStatus() { return status(); }
}
