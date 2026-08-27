package ru.wolf.api.focus.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AllocationRequest(@NotNull List<Allocation> allocations) {}
