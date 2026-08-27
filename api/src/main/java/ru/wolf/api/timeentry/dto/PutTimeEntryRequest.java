package ru.wolf.api.timeentry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.wolf.api.timeentry.TimeEntry;

public record PutTimeEntryRequest(
        @NotBlank String startAt,
        String endAt,
        Long deloId,
        @Size(max = 500) String adHocText,
        TimeEntry.Status status
) {
    /** Compatibility accessor for legacy callers. */
    public @NotBlank String getStartAt() { return startAt(); }

    /** Compatibility accessor for legacy callers. */
    public String getEndAt() { return endAt(); }

    /** Compatibility accessor for legacy callers. */
    public Long getDeloId() { return deloId(); }

    /** Compatibility accessor for legacy callers. */
    public @Size(max = 500) String getAdHocText() { return adHocText(); }

    /** Compatibility accessor for legacy callers. */
    public TimeEntry.Status getStatus() { return status(); }
}
